package ae2.container.me.patternencode;

import ae2.api.networking.IGrid;
import ae2.container.me.patternencode.PatternProviderUploadService.ProcessingPatternUploadPreparation;
import ae2.container.me.patternencode.PatternProviderUploadService.ProcessingPatternUploadResult;
import ae2.container.me.patternencode.PatternProviderUploadService.ProviderMappingValidationResult;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.BindResult;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.me.service.ActivePatternProviderDirectory;
import ae2.me.service.ActivePatternProviderDirectory.ProviderDescriptor;
import ae2.me.service.ActivePatternProviderDirectory.ProviderKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Server-side state and command handler for one Provider Selection window.
 * Live provider objects remain server-only and are converted to page DTOs here.
 */
public final class ProviderSelectionSession {
    private static final int DIRECTORY_SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_ACTIONS_PER_WINDOW = 32;
    private static final long ACTION_WINDOW_NANOS = 1_000_000_000L;

    interface Host {
        boolean isLinkConnected();

        @Nullable
        IGrid getProviderSelectionGrid();

        @Nullable
        IGrid requireProviderSelectionGrid();

        PatternProviderMappingData getProviderMappingData();

        EntityPlayer getPlayer();

        World getWorld();

        int getWindowId();

        ItemStack getEncodedPattern();

        void setEncodedPattern(ItemStack encodedPattern);

        ProcessingPatternUploadResult uploadProcessingPattern(ItemStack encodedPattern, IGrid grid,
                                                               ProviderKey target);

        ProcessingPatternUploadPreparation prepareProcessingPatternUpload(ItemStack encodedPattern, IGrid grid,
                                                                           ProviderKey target);

        void sendProviderDirectoryPage(ProviderDirectoryPage page);

        void sendProviderMappingPage(ProviderMappingPage page);

        void syncProviderDirectoryRevision(long revision);

        void syncProviderSelectionOverlay(ProviderSelectionOverlayOpenRequest request);
    }

    private final Host host;
    private final LongSupplier monotonicNanos;
    private final Long2ObjectMap<ProviderSelectionEntry> entriesById = new Long2ObjectLinkedOpenHashMap<>();
    private final Reference2LongMap<ProviderKey> entryIdsByProviderKey = new Reference2LongOpenHashMap<>();
    private List<ProviderEntryFingerprint> directoryFingerprint = List.of();
    private List<ProviderSelectionEntry> directoryEntries = List.of();
    @Nullable
    private IGrid observedGrid;
    private long observedMappingRevision = Long.MIN_VALUE;
    private long nextEntryId;
    private int ticksUntilDirectoryScan;
    private boolean observedLinkConnected;
    private boolean directoryInitialized;
    private final ActionRateLimiter actionRateLimiter = new ActionRateLimiter(MAX_ACTIONS_PER_WINDOW,
        ACTION_WINDOW_NANOS);
    private long directoryRevision;
    private int overlayRequestNonce;
    private boolean closed;

    ProviderSelectionSession(Host host) {
        this(host, System::nanoTime);
    }

    ProviderSelectionSession(Host host, LongSupplier monotonicNanos) {
        this.host = Objects.requireNonNull(host, "host");
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
    }

    void onBroadcastChanges() {
        if (this.closed) {
            return;
        }
        refreshDirectory(false, true);
    }

    long getDirectoryRevision() {
        return this.directoryRevision;
    }

    void open(@Nullable String searchText, @Nullable String mappingText) {
        if (this.closed) {
            return;
        }
        refreshDirectory(true, false);
        this.overlayRequestNonce = Math.incrementExact(this.overlayRequestNonce);
        this.host.syncProviderSelectionOverlay(new ProviderSelectionOverlayOpenRequest(
            this.overlayRequestNonce, searchText, mappingText));
    }

    void uploadProcessingPatternToProvider(@Nullable ProviderEntryAction action) {
        if (!allowAction("upload")) {
            return;
        }
        IGrid grid = this.host.requireProviderSelectionGrid();
        if (grid == null) {
            return;
        }
        refreshDirectory(true, false);
        ProviderSelectionEntry entry = resolveActionEntry(action, grid, "upload processing pattern to");
        if (entry == null) {
            return;
        }

        ProcessingPatternUploadResult result = this.host.uploadProcessingPattern(this.host.getEncodedPattern(), grid,
            entry.providerKey());
        if (result == ProcessingPatternUploadResult.SUCCESS) {
            refreshDirectory(true, false);
        }
    }

    void close() {
        this.closed = true;
        this.directoryEntries = List.of();
        this.directoryFingerprint = List.of();
        this.entriesById.clear();
        this.entryIdsByProviderKey.clear();
    }

    void bindProviderMapping(@Nullable ProviderMappingAction action) {
        if (!allowAction("bind-mapping") || !requireMappingEnabled()) {
            return;
        }
        IGrid grid = this.host.requireProviderSelectionGrid();
        if (grid == null) {
            return;
        }
        refreshDirectory(true, false);
        ProviderSelectionEntry entry = resolveActionEntry(action, grid, "bind mapping for");
        if (entry == null) {
            return;
        }
        String mappingText = normalizeMappingText(action.mappingText());
        if (mappingText == null) {
            return;
        }
        ProviderReference reference = requireProviderReference(entry, "bind mapping for");
        if (reference == null) {
            return;
        }

        PatternProviderMappingData mappingData = this.host.getProviderMappingData();
        if (PatternProviderUploadService.validateProviderMapping(grid, entry.providerKey(), reference,
            mappingText) != ProviderMappingValidationResult.SUCCESS) {
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return;
        }
        BindResult result = mappingData.bind(mappingText, reference);
        if (result == BindResult.ADDED) {
            refreshDirectory(false, false);
        } else if (result == BindResult.DISABLED) {
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingDisabled.text(), false);
        } else if (result == BindResult.LIMIT_REACHED) {
            this.host.getPlayer().sendStatusMessage(
                PlayerMessages.PatternProviderMappingLimitReached.text(PatternProviderMappingData.getMappingLimit()), false);
        }
    }

    void bindAndUploadProcessingPatternToProvider(@Nullable ProviderMappingAction action) {
        if (!allowAction("bind-and-upload") || !requireMappingEnabled()) {
            return;
        }
        IGrid grid = this.host.requireProviderSelectionGrid();
        if (grid == null) {
            return;
        }
        refreshDirectory(true, false);
        ProviderSelectionEntry entry = resolveActionEntry(action, grid, "bind mapping and upload to");
        if (entry == null) {
            return;
        }
        String mappingText = normalizeMappingText(action.mappingText());
        if (mappingText == null) {
            return;
        }
        ProviderReference reference = requireProviderReference(entry, "bind mapping and upload to");
        if (reference == null) {
            return;
        }

        PatternProviderMappingData mappingData = this.host.getProviderMappingData();
        if (PatternProviderUploadService.validateProviderMapping(grid, entry.providerKey(), reference,
            mappingText) != ProviderMappingValidationResult.SUCCESS) {
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return;
        }
        if (mappingData.getRecipeTypeCount(reference) >= PatternProviderMappingData.getMappingLimit()
            && !mappingData.getRecipeTypes(reference).contains(mappingText)) {
            this.host.getPlayer().sendStatusMessage(
                PlayerMessages.PatternProviderMappingLimitReached.text(PatternProviderMappingData.getMappingLimit()), false);
            return;
        }

        ItemStack sourceBeforeCommit = this.host.getEncodedPattern().copy();
        ProcessingPatternUploadPreparation upload = this.host.prepareProcessingPatternUpload(sourceBeforeCommit, grid,
            entry.providerKey());
        if (!upload.ready()) {
            return;
        }
        try {
            if (!upload.commit()) {
                throw new IllegalStateException("Provider rejected a processing pattern after simulated insertion");
            }
            this.host.setEncodedPattern(ItemStack.EMPTY);
            if (mappingData.bind(mappingText, reference) == BindResult.LIMIT_REACHED) {
                throw new IllegalStateException("Provider mapping limit reached after upload");
            }
        } catch (RuntimeException e) {
            upload.restoreTargetSlotAfterFailure(e);
            try {
                this.host.setEncodedPattern(sourceBeforeCommit);
            } catch (RuntimeException restoreFailure) {
                e.addSuppressed(restoreFailure);
            }
            NetworkPacketHelper.warnFailedPacket(e, "provider-bind-and-upload",
                "Failed to atomically bind and upload a processing pattern to provider %s", reference);
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderBindAndUploadFailed.text(), false);
            refreshDirectory(true, false);
            return;
        }
        refreshDirectory(true, false);
    }

    void unbindProviderMapping(@Nullable ProviderMappingAction action) {
        if (!allowAction("unbind-mapping") || !requireMappingEnabled()) {
            return;
        }
        IGrid grid = this.host.requireProviderSelectionGrid();
        if (grid == null) {
            return;
        }
        refreshDirectory(true, false);
        ProviderSelectionEntry entry = resolveActionEntry(action, grid, "unbind mapping for");
        if (entry == null) {
            return;
        }
        ProviderReference reference = requireProviderReference(entry, "unbind mapping for");
        if (reference == null) {
            return;
        }
        String requestedMappingText = action.mappingText();
        String mappingText = requestedMappingText == null ? null : normalizeMappingText(requestedMappingText);
        if (requestedMappingText != null && mappingText == null) {
            return;
        }
        boolean changed = mappingText == null
            ? this.host.getProviderMappingData().unbindAll(reference)
            : this.host.getProviderMappingData().unbind(reference, mappingText);
        if (!changed) {
            PatternProviderUploadService.warnProviderAction("unbind-mapping:no-match:" + action.providerEntryId(),
                "Cannot unbind provider mapping without matching mappings: %d", action.providerEntryId());
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }
        refreshDirectory(false, false);
    }

    void rebuildMappingsFromActiveProviders() {
        if (!allowAction("rebuild-mappings") || !requireMappingEnabled()) {
            return;
        }
        IGrid grid = this.host.requireProviderSelectionGrid();
        if (grid == null) {
            return;
        }
        refreshDirectory(true, false);
        PatternProviderUploadService.rebuildMappingsFromActiveProviders(this.host.getProviderMappingData(),
            this.host.getWorld(), grid);
        refreshDirectory(false, false);
    }

    void requestProviderDirectoryPage(@Nullable ProviderDirectoryPageRequest request) {
        if (request == null || request.nonce() == null || request.query() == null || request.page() == null) {
            warnInvalidDirectoryRequest("missing-field");
            return;
        }
        if (!allowAction("directory-page")) {
            return;
        }
        if (request.nonce() <= 0 || request.page() < 0
            || request.page() > Integer.MAX_VALUE / ProviderPageLimits.PAGE_SIZE) {
            warnInvalidDirectoryRequest("invalid-number");
            return;
        }

        String query;
        ProviderDirectoryPageRequest.Focus focus;
        try {
            query = ProviderPageLimits.requireBoundedText("provider directory query", request.query().trim(),
                ProviderPageLimits.MAX_QUERY_UTF16_LENGTH, ProviderPageLimits.MAX_QUERY_UTF8_BYTES);
            focus = validateDirectoryFocus(request.focus());
        } catch (IllegalArgumentException e) {
            warnInvalidDirectoryRequest("invalid-content");
            return;
        }

        refreshDirectory(false, false);
        PatternProviderMappingData mappingData = this.host.getProviderMappingData();
        List<ProviderSelectionEntry> matches = new ObjectArrayList<>();
        for (ProviderSelectionEntry entry : this.directoryEntries) {
            if (ActivePatternProviderDirectory.matchesDirectoryQuery(entry.descriptor(), mappingData, query)) {
                matches.add(entry);
            }
        }
        promoteFocusedProvider(matches, focus);
        List<ProviderDirectoryPage.Entry> pageEntries = new ObjectArrayList<>();
        for (ProviderSelectionEntry entry : getPage(matches, request.page())) {
            pageEntries.add(ActivePatternProviderDirectory.createDirectoryPageEntry(entry.id(), entry.descriptor(),
                mappingData, query));
        }
        this.host.sendProviderDirectoryPage(new ProviderDirectoryPage(this.host.getWindowId(), request.nonce(),
            this.directoryRevision, request.page(), matches.size(), PatternProviderMappingData.isMappingEnabled(),
            pageEntries));
    }

    void requestProviderMappingPage(@Nullable ProviderMappingPageRequest request) {
        if (request == null || request.nonce() == null || request.directoryRevision() == null
            || request.providerEntryId() == null || request.page() == null || !allowAction("mapping-page")) {
            return;
        }
        refreshDirectory(false, false);
        if (request.nonce() <= 0 || request.directoryRevision() != this.directoryRevision || request.page() < 0
            || request.page() > Integer.MAX_VALUE / ProviderPageLimits.PAGE_SIZE) {
            return;
        }
        ProviderSelectionEntry entry = this.entriesById.get(request.providerEntryId().longValue());
        if (entry == null || entry.reference() == null || !isStillEligible(entry,
            this.host.getProviderSelectionGrid())) {
            return;
        }
        PatternProviderMappingData data = this.host.getProviderMappingData();
        int total = data.getRecipeTypeCount(entry.reference());
        int first = request.page() * ProviderPageLimits.PAGE_SIZE;
        if (first > total || first == total && total != 0) {
            return;
        }
        this.host.sendProviderMappingPage(new ProviderMappingPage(this.host.getWindowId(), request.nonce(),
            this.directoryRevision, request.providerEntryId(), request.page(), total,
            data.getRecipeTypePage(entry.reference(), request.page(), ProviderPageLimits.PAGE_SIZE)));
    }

    private boolean requireMappingEnabled() {
        if (PatternProviderMappingData.isMappingEnabled()) {
            return true;
        }
        this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingDisabled.text(), false);
        return false;
    }

    private boolean allowAction(String action) {
        if (this.closed) {
            return false;
        }
        long now = this.monotonicNanos.getAsLong();
        if (!this.actionRateLimiter.tryAcquire(now)) {
            PatternProviderUploadService.warnProviderAction("rate-limit:" + action,
                "Ignoring provider selection action after exceeding the per-window rate limit: %s", action);
            return false;
        }
        return true;
    }

    @Nullable
    private ProviderSelectionEntry resolveActionEntry(@Nullable ProviderEntryAction action, IGrid grid,
                                                      String actionDescription) {
        if (action == null || action.directoryRevision == null || action.providerEntryId == null) {
            PatternProviderUploadService.warnProviderAction("provider-action:missing-id",
                "Ignoring provider selection action without a directory revision and entry id");
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return null;
        }
        if (action.directoryRevision() != this.directoryRevision) {
            return null;
        }
        ProviderSelectionEntry entry = this.entriesById.get(action.providerEntryId());
        if (entry == null || !isStillEligible(entry, grid)) {
            PatternProviderUploadService.warnProviderAction("provider-action:unknown-entry:" + action.providerEntryId(),
                "Cannot %s unknown or inactive provider entry: %d", actionDescription, action.providerEntryId());
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return null;
        }
        return entry;
    }

    private boolean isStillEligible(ProviderSelectionEntry entry, @Nullable IGrid grid) {
        return grid != null && grid.getService(ActivePatternProviderDirectory.class)
            .resolveSelectableProvider(entry.providerKey()) != null;
    }

    @Nullable
    private ProviderReference requireProviderReference(ProviderSelectionEntry entry, String actionDescription) {
        if (entry.reference() != null) {
            return entry.reference();
        }
        PatternProviderUploadService.warnProviderAction("provider-action:missing-reference:" + actionDescription,
            "Cannot %s provider entry without a stable provider reference: %d", actionDescription, entry.id());
        this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
        return null;
    }

    @Nullable
    private String normalizeMappingText(@Nullable String mappingText) {
        if (mappingText == null) {
            this.host.getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingBlank.text(), false);
            return null;
        }
        try {
            return PatternProviderMappingData.normalizeRecipeTypeUid(mappingText);
        } catch (IllegalArgumentException e) {
            this.host.getPlayer().sendStatusMessage(mappingText.trim().isEmpty()
                ? PlayerMessages.PatternProviderMappingBlank.text()
                : PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return null;
        }
    }

    private void refreshDirectory(boolean forceProviderScan, boolean advanceScheduledScan) {
        boolean linkConnected = this.host.isLinkConnected();
        IGrid grid = linkConnected ? this.host.getProviderSelectionGrid() : null;
        boolean contextChanged = !this.directoryInitialized || linkConnected != this.observedLinkConnected
            || grid != this.observedGrid;
        if (grid == null) {
            updateDisconnectedDirectory(linkConnected, contextChanged);
            return;
        }

        PatternProviderMappingData mappingData = this.host.getProviderMappingData();
        long mappingRevision = mappingData.getRevision();
        boolean mappingChanged = mappingRevision != this.observedMappingRevision;
        boolean scheduledScan = forceProviderScan
            || advanceScheduledScan && --this.ticksUntilDirectoryScan <= 0;
        if (!contextChanged && !mappingChanged && !scheduledScan) {
            return;
        }

        boolean scannedProviders = contextChanged || scheduledScan;
        List<ProviderSelectionEntry> currentEntries = scannedProviders
            ? collectDirectoryEntries(grid)
            : this.directoryEntries;
        List<ProviderEntryFingerprint> currentFingerprint = createDirectoryFingerprint(currentEntries, mappingData);
        boolean directoryChanged = contextChanged || !this.directoryFingerprint.equals(currentFingerprint);
        this.observedLinkConnected = linkConnected;
        this.observedGrid = grid;
        this.observedMappingRevision = mappingRevision;
        this.directoryInitialized = true;
        if (scannedProviders) {
            this.ticksUntilDirectoryScan = DIRECTORY_SCAN_INTERVAL_TICKS;
        }
        if (!directoryChanged) {
            return;
        }
        if (scannedProviders) {
            replaceDirectoryEntries(currentEntries);
        }
        this.directoryFingerprint = currentFingerprint;
        this.directoryRevision = Math.incrementExact(this.directoryRevision);
        this.host.syncProviderDirectoryRevision(this.directoryRevision);
    }

    private void updateDisconnectedDirectory(boolean linkConnected, boolean contextChanged) {
        boolean directoryChanged = this.directoryInitialized && (contextChanged || !this.directoryEntries.isEmpty());
        this.observedLinkConnected = linkConnected;
        this.observedGrid = null;
        this.observedMappingRevision = Long.MIN_VALUE;
        this.ticksUntilDirectoryScan = DIRECTORY_SCAN_INTERVAL_TICKS;
        this.directoryInitialized = true;
        if (!directoryChanged) {
            return;
        }
        this.directoryFingerprint = List.of();
        this.directoryEntries = List.of();
        this.entriesById.clear();
        this.entryIdsByProviderKey.clear();
        this.directoryRevision = Math.incrementExact(this.directoryRevision);
        this.host.syncProviderDirectoryRevision(this.directoryRevision);
    }

    private List<ProviderSelectionEntry> collectDirectoryEntries(IGrid grid) {
        List<ProviderDescriptor> providers = grid.getService(ActivePatternProviderDirectory.class)
            .getSelectableProviderDescriptors();
        ReferenceOpenHashSet<ProviderKey> currentProviderKeys = new ReferenceOpenHashSet<>();
        for (ProviderDescriptor provider : providers) {
            currentProviderKeys.add(provider.providerKey());
        }
        this.entryIdsByProviderKey.keySet().removeIf(providerKey -> !currentProviderKeys.contains(providerKey));

        List<ProviderSelectionEntry> entries = new ObjectArrayList<>(providers.size());
        for (ProviderDescriptor provider : providers) {
            entries.add(new ProviderSelectionEntry(getOrCreateEntryId(provider.providerKey()), provider.providerKey(),
                provider));
        }
        return List.copyOf(entries);
    }

    private void replaceDirectoryEntries(List<ProviderSelectionEntry> entries) {
        this.directoryEntries = List.copyOf(entries);
        this.entriesById.clear();
        for (ProviderSelectionEntry entry : entries) {
            this.entriesById.put(entry.id(), entry);
        }
    }

    private long getOrCreateEntryId(ProviderKey providerKey) {
        if (this.entryIdsByProviderKey.containsKey(providerKey)) {
            return this.entryIdsByProviderKey.getLong(providerKey);
        }
        if (this.nextEntryId == Long.MAX_VALUE) {
            throw new IllegalStateException("Provider selection entry id space exhausted");
        }
        long providerEntryId = this.nextEntryId++;
        this.entryIdsByProviderKey.put(providerKey, providerEntryId);
        return providerEntryId;
    }

    private static List<ProviderEntryFingerprint> createDirectoryFingerprint(List<ProviderSelectionEntry> entries,
                                                                               PatternProviderMappingData mappingData) {
        List<ProviderEntryFingerprint> fingerprint = new ArrayList<>(entries.size());
        for (ProviderSelectionEntry entry : entries) {
            fingerprint.add(new ProviderEntryFingerprint(entry, mappingData));
        }
        return List.copyOf(fingerprint);
    }

    private static void promoteFocusedProvider(List<ProviderSelectionEntry> entries,
                                               @Nullable ProviderDirectoryPageRequest.Focus focus) {
        if (focus == null || entries.isEmpty()) {
            return;
        }
        int focusedIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id() == focus.providerEntryId()) {
                focusedIndex = index;
                break;
            }
        }
        if (focusedIndex < 0) {
            ProviderReference reference = new ProviderReference(focus.dimension(), focus.position(), focus.side());
            for (int index = 0; index < entries.size(); index++) {
                if (Objects.equals(entries.get(index).reference(), reference)) {
                    focusedIndex = index;
                    break;
                }
            }
        }
        if (focusedIndex > 0) {
            entries.addFirst(entries.remove(focusedIndex));
        }
    }

    private static <T> List<T> getPage(List<T> values, int page) {
        long start = (long) page * ProviderPageLimits.PAGE_SIZE;
        if (start >= values.size()) {
            return List.of();
        }
        int fromIndex = (int) start;
        return List.copyOf(values.subList(fromIndex, Math.min(values.size(), fromIndex + ProviderPageLimits.PAGE_SIZE)));
    }

    @Nullable
    private static ProviderDirectoryPageRequest.Focus validateDirectoryFocus(
        @Nullable ProviderDirectoryPageRequest.Focus focus) {
        if (focus == null) {
            return null;
        }
        return new ProviderDirectoryPageRequest.Focus(focus.providerEntryId(), focus.dimension(), focus.position(),
            focus.side());
    }

    private static void warnInvalidDirectoryRequest(String reason) {
        PatternProviderUploadService.warnProviderAction("directory-page-request:" + reason,
            "Ignoring invalid provider directory page request: %s", reason);
    }

    static final class ActionRateLimiter {
        private final int maximumActions;
        private final long windowNanos;
        private long windowStartNanos = Long.MIN_VALUE;
        private int actionCount;

        ActionRateLimiter(int maximumActions, long windowNanos) {
            if (maximumActions <= 0 || windowNanos <= 0) {
                throw new IllegalArgumentException("Action rate limits must be positive");
            }
            this.maximumActions = maximumActions;
            this.windowNanos = windowNanos;
        }

        boolean tryAcquire(long nowNanos) {
            if (this.windowStartNanos == Long.MIN_VALUE || nowNanos < this.windowStartNanos
                || nowNanos - this.windowStartNanos >= this.windowNanos) {
                this.windowStartNanos = nowNanos;
                this.actionCount = 0;
            }
            if (this.actionCount >= this.maximumActions) {
                return false;
            }
            this.actionCount++;
            return true;
        }
    }

    public static class ProviderEntryAction {
        private Long directoryRevision;
        private Long providerEntryId;

        @SuppressWarnings("unused")
        public ProviderEntryAction() {
        }

        public ProviderEntryAction(long directoryRevision, long providerEntryId) {
            this.directoryRevision = directoryRevision;
            this.providerEntryId = providerEntryId;
        }

        long directoryRevision() {
            return this.directoryRevision == null ? Long.MIN_VALUE : this.directoryRevision;
        }

        long providerEntryId() {
            return this.providerEntryId == null ? Long.MIN_VALUE : this.providerEntryId;
        }
    }

    public static final class ProviderMappingAction extends ProviderEntryAction {
        private String mappingText;

        @SuppressWarnings("unused")
        public ProviderMappingAction() {
        }

        public ProviderMappingAction(long directoryRevision, long providerEntryId, @Nullable String mappingText) {
            super(directoryRevision, providerEntryId);
            this.mappingText = mappingText;
        }

        @Nullable
        String mappingText() {
            return this.mappingText;
        }
    }

    record ProviderSelectionOverlayOpenRequest(int nonce, String searchText, String mappingText) {
        ProviderSelectionOverlayOpenRequest {
            if (nonce <= 0) {
                throw new IllegalArgumentException("Provider selection overlay nonce must be positive");
            }
            searchText = searchText == null ? "" : searchText.trim();
            mappingText = mappingText == null ? "" : mappingText;
        }
    }

    private record ProviderSelectionEntry(long id, ProviderKey providerKey, ProviderDescriptor descriptor) {
        private ProviderSelectionEntry {
            if (id < 0) {
                throw new IllegalArgumentException("Provider selection entry id must not be negative");
            }
            Objects.requireNonNull(providerKey, "providerKey");
            Objects.requireNonNull(descriptor, "descriptor");
            if (descriptor.providerKey() != providerKey) {
                throw new IllegalArgumentException("Provider selection descriptor key does not match entry key");
            }
        }

        @Nullable
        private ProviderReference reference() {
            return this.descriptor.reference();
        }
    }

    private static final class ProviderEntryFingerprint {
        private final ProviderSelectionEntry entry;
        private final int recipeTypeCount;
        private final List<String> recipeTypes;

        private ProviderEntryFingerprint(ProviderSelectionEntry entry, PatternProviderMappingData mappingData) {
            this.entry = Objects.requireNonNull(entry, "entry");
            this.recipeTypeCount = entry.reference() == null ? 0 : mappingData.getRecipeTypeCount(entry.reference());
            this.recipeTypes = entry.reference() == null ? List.of()
                : List.copyOf(mappingData.getRecipeTypes(entry.reference()));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProviderEntryFingerprint that)) {
                return false;
            }
            return this.entry.id() == that.entry.id()
                && this.entry.providerKey() == that.entry.providerKey()
                && this.entry.descriptor().equals(that.entry.descriptor())
                && this.recipeTypeCount == that.recipeTypeCount && this.recipeTypes.equals(that.recipeTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.entry.id(), System.identityHashCode(this.entry.providerKey()),
                this.entry.descriptor(), this.recipeTypeCount, this.recipeTypes);
        }
    }
}
