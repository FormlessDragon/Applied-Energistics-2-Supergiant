package ae2.container.me.patternencode;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.ILinkStatus;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.me.service.ActivePatternProviderDirectory;
import ae2.me.service.ActivePatternProviderDirectory.ProviderDescriptor;
import ae2.me.service.ActivePatternProviderDirectory.ProviderKey;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class PatternProviderUploadService {
    private static final long WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PROVIDER_ACTION_WARNING_KEYS = 256;
    private static final ProviderActionWarningLimiter PROVIDER_ACTION_WARNING_LIMITER =
        new ProviderActionWarningLimiter(MAX_PROVIDER_ACTION_WARNING_KEYS, WARNING_INTERVAL_NANOS);
    private static final AtomicLong LAST_PROVIDER_SCAN_WARNING = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_PROVIDER_UPLOAD_WARNING = new AtomicLong(Long.MIN_VALUE);

    private PatternProviderUploadService() {
    }

    public enum ProcessingPatternUploadResult {
        SUCCESS,
        NO_ENCODED_PATTERN,
        PROCESSING_PATTERN_REQUIRED,
        DUPLICATE_IN_CONTAINER,
        NO_PROVIDER_TARGET
    }

    public enum ProviderMappingValidationResult {
        SUCCESS,
        NO_PROVIDER_TARGET,
        INVALID_RECIPE_TYPE,
        ASSEMBLER_PROVIDER
    }

    public static List<String> collectProcessingPatternRecipeTypeUids(PatternContainer container, World world) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(world, "world");

        Set<String> recipeTypes = new ObjectLinkedOpenHashSet<>();
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            IPatternDetails details;
            try {
                details = PatternDetailsHelper.decodePattern(stack, world);
            } catch (RuntimeException e) {
                warnProviderScanFailure(e, container, slot);
                continue;
            }
            if (!(details instanceof AEProcessingPattern processingPattern)) {
                continue;
            }

            String recipeTypeUid = processingPattern.getRecipeTypeUid();
            if (recipeTypeUid != null && !recipeTypeUid.isEmpty()) {
                recipeTypes.add(recipeTypeUid);
            }
        }
        return List.copyOf(recipeTypes);
    }

    /** Rebuilds persisted mappings from every currently selectable provider in the active directory. */
    public static void rebuildMappingsFromActiveProviders(PatternProviderMappingData mappingData, World world,
                                                          IGrid grid) {
        Objects.requireNonNull(mappingData, "mappingData");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(grid, "grid");

        ActivePatternProviderDirectory directory = grid.getService(ActivePatternProviderDirectory.class);
        List<ProviderMappingReplacement> replacements = new ObjectArrayList<>();
        for (ProviderDescriptor descriptor : directory.getSelectableProviderDescriptors()) {
            ProviderReference reference = descriptor.reference();
            PatternContainer container = directory.resolveSelectableProvider(descriptor.providerKey());
            if (reference != null && container != null) {
                replacements.add(new ProviderMappingReplacement(reference,
                    collectProcessingPatternRecipeTypeUids(container, world)));
            }
        }

        for (ProviderMappingReplacement replacement : replacements) {
            mappingData.replaceProviderMappings(replacement.reference(), replacement.recipeTypes());
        }
    }

    private record ProviderMappingReplacement(ProviderReference reference, List<String> recipeTypes) {
        private ProviderMappingReplacement {
            Objects.requireNonNull(reference, "reference");
            recipeTypes = List.copyOf(Objects.requireNonNull(recipeTypes, "recipeTypes"));
        }
    }

    public static List<PatternContainer> findProcessingPatternUploadTargets(PatternProviderMappingData mappingData,
                                                                            IGrid grid,
                                                                            String recipeTypeUid) {
        Objects.requireNonNull(mappingData, "mappingData");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(recipeTypeUid, "recipeTypeUid");

        if (!PatternProviderMappingData.isMappingEnabled()) {
            return List.of();
        }

        List<ProviderReference> mappedReferences = mappingData.getReferences(recipeTypeUid);
        if (mappedReferences.isEmpty()) {
            return List.of();
        }

        Set<ProviderReference> mappedReferenceSet = new ObjectLinkedOpenHashSet<>(mappedReferences);
        List<PatternContainer> uploadTargets = new ObjectArrayList<>();
        ActivePatternProviderDirectory directory = grid.getService(ActivePatternProviderDirectory.class);
        for (ProviderDescriptor descriptor : directory.getSelectableProviderDescriptors()) {
            ProviderReference reference = descriptor.reference();
            PatternContainer container = directory.resolveSelectableProvider(descriptor.providerKey());
            if (reference != null && mappedReferenceSet.contains(reference) && descriptor.emptySlots() > 0
                && container != null) {
                uploadTargets.add(container);
            }
        }
        return List.copyOf(uploadTargets);
    }

    public static boolean hasAvailableProvider(IGrid grid) {
        Objects.requireNonNull(grid, "grid");

        for (ProviderDescriptor descriptor : grid.getService(ActivePatternProviderDirectory.class)
            .getSelectableProviderDescriptors()) {
            if (descriptor.emptySlots() > 0) {
                return true;
            }
        }
        return false;
    }

    public static ProcessingPatternUploadResult tryUploadProcessingPatternToProvider(EntityPlayer player,
                                                                                     @Nullable IPatternTerminalGuiHost host,
                                                                                     @Nullable IGrid grid,
                                                                                     ProviderKey providerKey,
                                                                                     ItemStack encodedPattern) {
        PatternContainer container = resolveSelectableProvider(player, grid, providerKey);
        return container == null ? ProcessingPatternUploadResult.NO_PROVIDER_TARGET
            : tryUploadProcessingPatternToProvider(player, host, grid, container, encodedPattern);
    }

    public static ProcessingPatternUploadResult tryUploadProcessingPatternToProvider(EntityPlayer player,
                                                                                     @Nullable IPatternTerminalGuiHost host,
                                                                                     @Nullable IGrid grid,
                                                                                     PatternContainer container,
                                                                                     ItemStack encodedPattern) {
        ProcessingPatternUploadPreparation preparation = prepareProcessingPatternUpload(
            player, host, grid, container, encodedPattern);
        if (!preparation.ready()) {
            return preparation.result();
        }

        try {
            if (!preparation.commit()) {
                preparation.restoreTargetSlot();
                warnProviderUploadFailure(null, container,
                    "Provider rejected a processing pattern after accepting the simulated insertion");
                player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
                return ProcessingPatternUploadResult.NO_PROVIDER_TARGET;
            }
            Objects.requireNonNull(host, "host").getLogic().getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
            return ProcessingPatternUploadResult.SUCCESS;
        } catch (RuntimeException e) {
            preparation.restoreTargetSlotAfterFailure(e);
            warnProviderUploadFailure(e, container, "Failed to upload processing pattern to provider");
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return ProcessingPatternUploadResult.NO_PROVIDER_TARGET;
        }
    }

    public static ProcessingPatternUploadPreparation prepareProcessingPatternUpload(EntityPlayer player,
                                                                                    @Nullable IPatternTerminalGuiHost host,
                                                                                    @Nullable IGrid grid,
                                                                                    ProviderKey providerKey,
                                                                                    ItemStack encodedPattern) {
        PatternContainer container = resolveSelectableProvider(player, grid, providerKey);
        return container == null ? ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_PROVIDER_TARGET)
            : prepareProcessingPatternUpload(player, host, grid, container, encodedPattern);
    }

    public static ProcessingPatternUploadPreparation prepareProcessingPatternUpload(EntityPlayer player,
                                                                                    @Nullable IPatternTerminalGuiHost host,
                                                                                    @Nullable IGrid grid,
                                                                                    PatternContainer container,
                                                                                    ItemStack encodedPattern) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(encodedPattern, "encodedPattern");

        if (host == null) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_PROVIDER_TARGET);
        }

        ILinkStatus linkStatus = host.getLinkStatus();
        if (!linkStatus.connected()) {
            if (linkStatus.statusDescription() != null) {
                player.sendStatusMessage(linkStatus.statusDescription(), false);
            }
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_PROVIDER_TARGET);
        }

        if (grid == null || !grid.getService(ActivePatternProviderDirectory.class).isSelectableActiveProvider(container)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_PROVIDER_TARGET);
        }

        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_ENCODED_PATTERN);
        }

        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(encodedPattern, player.world);
        } catch (RuntimeException e) {
            warnProviderUploadFailure(e, container, "Failed to decode processing pattern before provider upload");
            player.sendStatusMessage(PlayerMessages.PatternUploadProcessingOnly.text(), false);
            return ProcessingPatternUploadPreparation.failure(
                ProcessingPatternUploadResult.PROCESSING_PATTERN_REQUIRED);
        }
        if (details == null || details instanceof IAssemblerPattern) {
            player.sendStatusMessage(PlayerMessages.PatternUploadProcessingOnly.text(), false);
            return ProcessingPatternUploadPreparation.failure(
                ProcessingPatternUploadResult.PROCESSING_PATTERN_REQUIRED);
        }

        AEItemKey patternKey = AEItemKey.of(encodedPattern);
        if (patternKey == null) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_ENCODED_PATTERN);
        }

        if (container.containsPattern(patternKey)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadDuplicateInContainer.text(), false);
            return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.DUPLICATE_IN_CONTAINER);
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }

            InternalInventory targetSlot = new FilteredInternalInventory(inventory.getSlotInv(slot),
                new PatternSlotFilter(container, player.world));
            try {
                if (targetSlot.simulateAdd(encodedPattern.copy()).isEmpty()) {
                    return ProcessingPatternUploadPreparation.ready(targetSlot, encodedPattern);
                }
            } catch (RuntimeException e) {
                warnProviderUploadFailure(e, container,
                    "Failed to simulate processing pattern insertion into provider");
            }
        }

        player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
        return ProcessingPatternUploadPreparation.failure(ProcessingPatternUploadResult.NO_PROVIDER_TARGET);
    }

    public static ProviderMappingValidationResult validateProviderMapping(IGrid grid, ProviderKey providerKey,
                                                                          ProviderReference reference,
                                                                          String recipeType) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(providerKey, "providerKey");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipeType, "recipeType");

        PatternContainer container = grid.getService(ActivePatternProviderDirectory.class)
            .resolveSelectableProvider(providerKey);
        if (container == null) {
            return ProviderMappingValidationResult.NO_PROVIDER_TARGET;
        }

        if (container.isAssemblerPatternContainer()) {
            warnProviderAction("mapping-assembler:" + reference,
                "Cannot bind processing pattern provider mapping to assembler provider: %s", reference);
            return ProviderMappingValidationResult.ASSEMBLER_PROVIDER;
        }

        return ProviderMappingValidationResult.SUCCESS;
    }

    @Nullable
    private static PatternContainer resolveSelectableProvider(EntityPlayer player, @Nullable IGrid grid,
                                                               ProviderKey providerKey) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(providerKey, "providerKey");
        PatternContainer container = grid == null ? null
            : grid.getService(ActivePatternProviderDirectory.class).resolveSelectableProvider(providerKey);
        if (container == null) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
        }
        return container;
    }

    public static void warnProviderAction(Object key, String message, Object... params) {
        Objects.requireNonNull(message, "message");
        if (PROVIDER_ACTION_WARNING_LIMITER.shouldLog(key, System.nanoTime())) {
            AELog.warn(message, params);
        }
    }

    private static void warnProviderScanFailure(RuntimeException exception, PatternContainer container, int slot) {
        if (shouldLog(LAST_PROVIDER_SCAN_WARNING)) {
            AELog.warn(exception, "Failed to decode processing pattern while scanning provider %s slot %d",
                container.getClass().getName(), slot);
        }
    }

    private static void warnProviderUploadFailure(@Nullable RuntimeException exception, PatternContainer container,
                                                  String message) {
        if (!shouldLog(LAST_PROVIDER_UPLOAD_WARNING)) {
            return;
        }
        if (exception == null) {
            AELog.warn("%s: %s", message, container.getClass().getName());
        } else {
            AELog.warn(exception, "%s: %s", message, container.getClass().getName());
        }
    }

    private static boolean shouldLog(AtomicLong lastWarning) {
        Objects.requireNonNull(lastWarning, "lastWarning");
        long now = System.nanoTime();
        while (true) {
            long previous = lastWarning.get();
            if (previous != Long.MIN_VALUE && now - previous < WARNING_INTERVAL_NANOS) {
                return false;
            }
            if (lastWarning.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    static final class ProviderActionWarningLimiter {
        private final int maximumTrackedKeys;
        private final long intervalNanos;
        private final LinkedHashMap<Object, Long> lastWarningNanos = new LinkedHashMap<>(16, 0.75f, true);

        ProviderActionWarningLimiter(int maximumTrackedKeys, long intervalNanos) {
            if (maximumTrackedKeys <= 0) {
                throw new IllegalArgumentException("maximumTrackedKeys must be positive");
            }
            if (intervalNanos <= 0) {
                throw new IllegalArgumentException("intervalNanos must be positive");
            }
            this.maximumTrackedKeys = maximumTrackedKeys;
            this.intervalNanos = intervalNanos;
        }

        synchronized boolean shouldLog(Object key, long nowNanos) {
            Objects.requireNonNull(key, "key");
            Long lastWarning = this.lastWarningNanos.get(key);
            if (lastWarning != null) {
                long elapsed = nowNanos - lastWarning;
                if (elapsed >= 0 && elapsed < this.intervalNanos) {
                    return false;
                }
            }

            if (lastWarning == null && this.lastWarningNanos.size() >= this.maximumTrackedKeys) {
                Object oldestKey = this.lastWarningNanos.keySet().iterator().next();
                this.lastWarningNanos.remove(oldestKey);
            }
            this.lastWarningNanos.put(key, nowNanos);
            return true;
        }

    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
    }

    public static UploadPlan getImmediateProcessingPatternUploadPlan(@Nullable String recipeTypeUid,
                                                                     @Nullable String recipeTypeTitle) {
        String recipeTypeUidOrEmpty = recipeTypeUid == null ? "" : recipeTypeUid;
        String initialSearchText = getInitialSearchText(recipeTypeUid, recipeTypeTitle);
        return recipeTypeUidOrEmpty.isEmpty()
            ? UploadPlan.openProviderSelection(recipeTypeUidOrEmpty, initialSearchText)
            : UploadPlan.continueAutomaticUpload(recipeTypeUidOrEmpty, initialSearchText);
    }

    public static UploadPlan getProcessingPatternFallbackPlan(@Nullable String recipeTypeUid,
                                                              @Nullable String recipeTypeTitle,
                                                              boolean hasAvailableProvider) {
        String recipeTypeUidOrEmpty = recipeTypeUid == null ? "" : recipeTypeUid;
        String initialSearchText = getInitialSearchText(recipeTypeUid, recipeTypeTitle);
        if (!hasAvailableProvider) {
            return UploadPlan.noProviderTarget(recipeTypeUidOrEmpty, initialSearchText);
        }
        return UploadPlan.openProviderSelection(recipeTypeUidOrEmpty, "",
            recipeTypeUid == null ? "" : recipeTypeUid);
    }

    public static UploadPlan getProcessingPatternUploadTargetPlan(int uploadTargetCount,
                                                                  @Nullable String recipeTypeUid,
                                                                  @Nullable String recipeTypeTitle,
                                                                  boolean hasAvailableProvider) {
        if (uploadTargetCount < 0) {
            throw new IllegalArgumentException("uploadTargetCount must not be negative");
        }

        String recipeTypeUidOrEmpty = recipeTypeUid == null ? "" : recipeTypeUid;
        if (uploadTargetCount == 0) {
            return getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle, hasAvailableProvider);
        }

        String initialSearchText = getInitialSearchText(recipeTypeUid, recipeTypeTitle);
        if (uploadTargetCount == 1) {
            return UploadPlan.continueAutomaticUpload(recipeTypeUidOrEmpty, initialSearchText);
        }
        if (!hasAvailableProvider) {
            return UploadPlan.noProviderTarget(recipeTypeUidOrEmpty, initialSearchText);
        }
        return UploadPlan.openProviderSelection(recipeTypeUidOrEmpty,
            getRecipeTypeUidSearchText(recipeTypeUid, recipeTypeTitle), "");
    }

    private static String getInitialSearchText(@Nullable String recipeTypeUid, @Nullable String recipeTypeTitle) {
        String title = trimText(recipeTypeTitle);
        return title.isEmpty() ? recipeTypeUid == null ? "" : recipeTypeUid : title;
    }

    private static String getRecipeTypeUidSearchText(@Nullable String recipeTypeUid,
                                                     @Nullable String recipeTypeTitle) {
        String recipeTypeUidOrEmpty = recipeTypeUid == null ? "" : recipeTypeUid;
        return recipeTypeUidOrEmpty.isEmpty() ? trimText(recipeTypeTitle) : recipeTypeUidOrEmpty;
    }

    private static String trimText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public record UploadPlan(String recipeTypeUid, String initialSearchText, String initialMappingText,
                             boolean openProviderSelection) {
        public UploadPlan {
            Objects.requireNonNull(recipeTypeUid, "recipeTypeUid");
            Objects.requireNonNull(initialSearchText, "initialSearchText");
            Objects.requireNonNull(initialMappingText, "initialMappingText");
            if (!openProviderSelection && !initialMappingText.isEmpty()) {
                throw new IllegalArgumentException("Automatic processing upload cannot carry provider mapping text");
            }
        }

        private static UploadPlan openProviderSelection(String recipeTypeUid, String initialSearchText) {
            return openProviderSelection(recipeTypeUid, initialSearchText, "");
        }

        private static UploadPlan openProviderSelection(String recipeTypeUid, String initialSearchText,
                                                     String initialMappingText) {
            return new UploadPlan(recipeTypeUid, initialSearchText, initialMappingText, true);
        }

        private static UploadPlan continueAutomaticUpload(String recipeTypeUid, String initialSearchText) {
            return new UploadPlan(recipeTypeUid, initialSearchText, "", false);
        }

        private static UploadPlan noProviderTarget(String recipeTypeUid, String initialSearchText) {
            return new UploadPlan(recipeTypeUid, initialSearchText, "", false);
        }
    }

    private record PatternSlotFilter(PatternContainer container, World level) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty()
                && isAcceptedByContainer(this.container, PatternDetailsHelper.decodePattern(stack, this.level));
        }
    }

    public static final class ProcessingPatternUploadPreparation {
        private final ProcessingPatternUploadResult result;
        @Nullable
        private final InternalInventory targetSlot;
        private final ItemStack originalTargetStack;
        private final ItemStack encodedPattern;

        private ProcessingPatternUploadPreparation(ProcessingPatternUploadResult result,
                                                   @Nullable InternalInventory targetSlot,
                                                   ItemStack originalTargetStack,
                                                   ItemStack encodedPattern) {
            this.result = Objects.requireNonNull(result, "result");
            this.targetSlot = targetSlot;
            this.originalTargetStack = Objects.requireNonNull(originalTargetStack, "originalTargetStack").copy();
            this.encodedPattern = Objects.requireNonNull(encodedPattern, "encodedPattern").copy();
        }

        static ProcessingPatternUploadPreparation failure(ProcessingPatternUploadResult result) {
            if (result == ProcessingPatternUploadResult.SUCCESS) {
                throw new IllegalArgumentException("Successful upload preparation requires a target slot");
            }
            return new ProcessingPatternUploadPreparation(result, null, ItemStack.EMPTY, ItemStack.EMPTY);
        }

        static ProcessingPatternUploadPreparation ready(InternalInventory targetSlot, ItemStack encodedPattern) {
            Objects.requireNonNull(targetSlot, "targetSlot");
            return new ProcessingPatternUploadPreparation(ProcessingPatternUploadResult.SUCCESS, targetSlot,
                targetSlot.getStackInSlot(0), encodedPattern);
        }

        public ProcessingPatternUploadResult result() {
            return this.result;
        }

        public boolean ready() {
            return this.result == ProcessingPatternUploadResult.SUCCESS && this.targetSlot != null;
        }

        public boolean commit() {
            if (!ready()) {
                throw new IllegalStateException("Cannot commit an unsuccessful provider upload preparation");
            }
            return Objects.requireNonNull(this.targetSlot, "targetSlot").addItems(this.encodedPattern.copy()).isEmpty();
        }

        public void restoreTargetSlot() {
            if (this.targetSlot != null) {
                this.targetSlot.setItemDirect(0, this.originalTargetStack.copy());
            }
        }

        public void restoreTargetSlotAfterFailure(RuntimeException originalFailure) {
            Objects.requireNonNull(originalFailure, "originalFailure");
            try {
                restoreTargetSlot();
            } catch (RuntimeException restoreFailure) {
                originalFailure.addSuppressed(restoreFailure);
            }
        }
    }

}
