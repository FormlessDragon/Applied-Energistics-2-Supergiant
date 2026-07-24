package ae2.container.me.patternencode;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.ILinkStatus;
import ae2.container.me.patternaccess.PatternAccessSupport;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.crafting.pattern.RecipeTypeUid;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.parts.AEBasePart;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class PatternProviderSelectionSupport {
    private static final Comparator<ProviderReference> PROVIDER_REFERENCE_ORDER = Comparator
        .comparingInt(ProviderReference::dimension)
        .thenComparingLong(ProviderReference::pos)
        .thenComparingInt(ProviderReference::side);
    private static final Comparator<PatternContainer> SELECTABLE_PROVIDER_ORDER = Comparator
        .comparingLong(PatternContainer::getTerminalSortOrder)
        .thenComparing(PatternProviderSelectionSupport::createProviderReference,
            Comparator.nullsLast(PROVIDER_REFERENCE_ORDER))
        .thenComparing(PatternProviderSelectionSupport::getProviderName)
        .thenComparing(container -> container.getClass().getName());
    private static final long WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PROVIDER_ACTION_WARNING_KEYS = 256;
    private static final ProviderActionWarningLimiter PROVIDER_ACTION_WARNING_LIMITER =
        new ProviderActionWarningLimiter(MAX_PROVIDER_ACTION_WARNING_KEYS, WARNING_INTERVAL_NANOS);
    private static final AtomicLong LAST_INVALID_RECIPE_TYPE_WARNING = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_PROVIDER_SCAN_WARNING = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_PROVIDER_UPLOAD_WARNING = new AtomicLong(Long.MIN_VALUE);

    private PatternProviderSelectionSupport() {
    }

    public enum ProcessingPatternUploadResult {
        SUCCESS,
        NO_ENCODED_PATTERN,
        PROCESSING_PATTERN_REQUIRED,
        DUPLICATE_IN_CONTAINER,
        NO_PROVIDER_TARGET
    }

    enum ProviderMappingValidationResult {
        SUCCESS,
        INVALID_RECIPE_TYPE,
        ASSEMBLER_PROVIDER
    }

    static boolean isSelectableProvider(PatternContainer container) {
        Objects.requireNonNull(container, "container");
        return container.isVisibleInTerminal() && !container.isAssemblerPatternContainer();
    }

    static List<PatternContainer> collectSelectableProviders(IGrid grid) {
        List<PatternContainer> containers = new ObjectArrayList<>();
        for (PatternContainer container : PatternAccessSupport.ProviderDiscoverySnapshot.discover(grid).providers()) {
            if (isSelectableProvider(container)) {
                containers.add(container);
            }
        }
        containers.sort(SELECTABLE_PROVIDER_ORDER);
        return containers;
    }

    static List<ProviderDirectoryEntry> collectProcessingPatternUploadProviders(IGrid grid) {
        Objects.requireNonNull(grid, "grid");

        List<ProviderDirectoryEntry> providers = new ObjectArrayList<>();
        for (PatternContainer container : collectSelectableProviders(grid)) {
            providers.add(ProviderDirectoryEntry.of(container));
        }
        return List.copyOf(providers);
    }

    static boolean isActiveProviderOnGrid(IGrid grid, PatternContainer container) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(container, "container");

        for (Class<?> machineClass : grid.getMachineClasses()) {
            Class<? extends PatternContainer> containerClass = tryCastMachineToContainer(machineClass);
            if (containerClass == null || !containerClass.isInstance(container)) {
                continue;
            }
            for (PatternContainer activeContainer : grid.getActiveMachines(containerClass)) {
                if (activeContainer == container) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    static ProviderReference createProviderReference(PatternContainer container) {
        Objects.requireNonNull(container, "container");
        ProviderLocation location = getProviderLocation(container);
        if (location == null) {
            return null;
        }

        return new ProviderReference(location.dimensionId(), location.pos(), location.side());
    }

    private static int countEmptySlots(PatternContainer container) {
        Objects.requireNonNull(container, "container");

        InternalInventory inventory = container.getTerminalPatternInventory();
        int emptySlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private static String getProviderName(PatternContainer container) {
        Objects.requireNonNull(container, "container");

        ITextComponent name = container.getTerminalGroup().name();
        if (name == null) {
            return container.getClass().getSimpleName();
        }
        return name.getUnformattedText();
    }

    private static String getProviderName(ProviderDirectoryEntry provider) {
        Objects.requireNonNull(provider, "provider");

        ITextComponent name = provider.group().name();
        if (name == null) {
            return provider.container().getClass().getSimpleName();
        }
        return name.getUnformattedText();
    }

    static ProviderDirectoryPage.Entry createProviderDirectoryPageEntry(long id, ProviderDirectoryEntry provider,
                                                                        PatternProviderMappingData mappingData,
                                                                        String query) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(mappingData, "mappingData");
        ProviderReference reference = provider.reference();

        String providerName = limitPageText("provider-name", getProviderName(provider), id,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF16_LENGTH,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF8_BYTES);
        int recipeTypeCount = reference == null ? 0 : mappingData.getRecipeTypeCount(reference);
        List<String> recipeTypeUids = reference == null ? List.of()
            : mappingData.getRecipeTypePreview(reference, query.trim().toLowerCase(Locale.ROOT));
        return new ProviderDirectoryPage.Entry(id, provider.group().icon(), providerName,
            provider.emptySlots(), recipeTypeCount, recipeTypeUids, provider.acceptsProcessingPatterns(), provider.hasLocation(),
            provider.locationDimension(), provider.locationPos(), provider.locationSide());
    }


    static boolean matchesProviderDirectoryQuery(ProviderDirectoryEntry provider,
                                                 PatternProviderMappingData mappingData,
                                                 String query) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(mappingData, "mappingData");
        String normalizedQuery = Objects.requireNonNull(query, "query").trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        if (getProviderName(provider).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (provider.hasLocation() && formatLocationSearchText(provider).contains(normalizedQuery)) {
            return true;
        }
        ProviderReference reference = provider.reference();
        if (reference != null) {
            for (String recipeType : mappingData.getRecipeTypes(reference)) {
                if (recipeType.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatLocationSearchText(ProviderDirectoryEntry provider) {
        BlockPos pos = BlockPos.fromLong(provider.locationPos());
        String side = provider.locationSide() < 0
            ? ""
            : EnumFacing.VALUES[provider.locationSide()].getName().toLowerCase(Locale.ROOT);
        return (provider.locationDimension() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            + " " + provider.locationDimension() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
            + " " + side).toLowerCase(Locale.ROOT);
    }

    private static String limitPageText(String field, String value, Object providerIdentity,
                                        int maxUtf16Length, int maxUtf8Bytes) {
        if (value.length() <= maxUtf16Length
            && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxUtf8Bytes) {
            return value;
        }

        StringBuilder result = new StringBuilder(Math.min(value.length(), maxUtf16Length));
        int utf8Bytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (result.length() + character.length() > maxUtf16Length
                || utf8Bytes + characterBytes > maxUtf8Bytes) {
                break;
            }
            result.append(character);
            utf8Bytes += characterBytes;
            offset += character.length();
        }
        warnProviderAction("directory-" + field + "-truncation:" + providerIdentity,
            "Truncated provider directory %s for provider identity %s to fit packet bounds", field,
            providerIdentity);
        return result.toString();
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
            String normalizedRecipeTypeUid = RecipeTypeUid.normalize(recipeTypeUid);
            if (normalizedRecipeTypeUid == null) {
                if (recipeTypeUid != null && !recipeTypeUid.isEmpty()) {
                    warnInvalidRecipeTypeUid("provider inventory", recipeTypeUid);
                }
                continue;
            }
            recipeTypes.add(normalizedRecipeTypeUid);
        }
        return List.copyOf(recipeTypes);
    }

    public static void reloadProviderMappings(PatternProviderMappingData mappingData, World world,
                                              List<ProviderMappingReloadTarget> targets) {
        Objects.requireNonNull(mappingData, "mappingData");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(targets, "targets");

        List<ProviderMappingReplacement> replacements = new ObjectArrayList<>(targets.size());
        for (ProviderMappingReloadTarget target : targets) {
            Objects.requireNonNull(target, "target");
            replacements.add(new ProviderMappingReplacement(target.reference(),
                collectProcessingPatternRecipeTypeUids(target.container(), world)));
        }

        boolean changed = false;
        for (ProviderMappingReplacement replacement : replacements) {
            changed |= mappingData.replaceProviderMappings(replacement.reference(), replacement.recipeTypes());
        }
    }

    record ProviderMappingReloadTarget(PatternContainer container, ProviderReference reference) {
        public ProviderMappingReloadTarget {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(reference, "reference");
        }
    }

    private record ProviderMappingReplacement(ProviderReference reference, List<String> recipeTypes) {
        private ProviderMappingReplacement {
            Objects.requireNonNull(reference, "reference");
            recipeTypes = List.copyOf(Objects.requireNonNull(recipeTypes, "recipeTypes"));
        }
    }

    public static List<PatternContainer> findProcessingPatternUploadTargets(World world, IGrid grid,
                                                                            String recipeType) {
        Objects.requireNonNull(world, "world");
        return findProcessingPatternUploadTargets(PatternProviderMappingData.get(world), grid, recipeType);
    }

    static List<PatternContainer> findProcessingPatternUploadTargets(PatternProviderMappingData mappingData,
                                                                     IGrid grid,
                                                                     String recipeType) {
        Objects.requireNonNull(mappingData, "mappingData");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(recipeType, "recipeType");

        String normalizedRecipeType = RecipeTypeUid.normalize(recipeType);
        if (normalizedRecipeType == null) {
            warnInvalidRecipeTypeUid("automatic provider lookup", recipeType);
            return Collections.emptyList();
        }

        List<ProviderReference> mappedReferences = mappingData.getReferences(normalizedRecipeType);
        if (mappedReferences.isEmpty()) {
            return Collections.emptyList();
        }

        Set<ProviderReference> mappedReferenceSet = new ObjectLinkedOpenHashSet<>(mappedReferences);
        List<PatternContainer> uploadTargets = new ObjectArrayList<>();
        for (PatternContainer container : collectSelectableProviders(grid)) {
            ProviderReference reference = createProviderReference(container);
            if (reference != null && mappedReferenceSet.contains(reference) && countEmptySlots(container) > 0) {
                uploadTargets.add(container);
            }
        }
        return List.copyOf(uploadTargets);
    }

    public static boolean hasAvailableProvider(IGrid grid) {
        Objects.requireNonNull(grid, "grid");

        for (PatternContainer container : collectSelectableProviders(grid)) {
            if (countEmptySlots(container) > 0) {
                return true;
            }
        }
        return false;
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

    static ProcessingPatternUploadPreparation prepareProcessingPatternUpload(EntityPlayer player,
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

        if (grid == null || !isSelectableProvider(container) || !isActiveProviderOnGrid(grid, container)) {
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

    static ProviderMappingValidationResult validateProviderMapping(PatternProviderMappingData mappingData,
                                                                   PatternContainer container,
                                                                   ProviderReference reference,
                                                                   String recipeType) {
        Objects.requireNonNull(mappingData, "mappingData");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipeType, "recipeType");

        String normalizedRecipeType = RecipeTypeUid.normalize(recipeType);
        if (normalizedRecipeType == null) {
            warnInvalidRecipeTypeUid("provider mapping", recipeType);
            return ProviderMappingValidationResult.INVALID_RECIPE_TYPE;
        }
        if (container.isAssemblerPatternContainer()) {
            warnProviderAction("mapping-assembler:" + reference,
                "Cannot bind processing pattern provider mapping to assembler provider: %s", reference);
            return ProviderMappingValidationResult.ASSEMBLER_PROVIDER;
        }

        return ProviderMappingValidationResult.SUCCESS;
    }

    static void warnInvalidRecipeTypeUid(String source, @Nullable String candidate) {
        Objects.requireNonNull(source, "source");
        if (!shouldLog(LAST_INVALID_RECIPE_TYPE_WARNING)) {
            return;
        }
        int utf16Length = candidate == null ? -1 : candidate.length();
        int utf8Length = candidate == null ? -1
            : candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        AELog.warn("Ignoring invalid recipe type UID from %s (UTF-16 length: %d, UTF-8 bytes: %d)",
            source, utf16Length, utf8Length);
    }

    static void warnProviderAction(Object key, String message, Object... params) {
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
        return shouldLogWarning(lastWarning, System.nanoTime());
    }

    static boolean shouldLogWarning(AtomicLong lastWarning, long now) {
        Objects.requireNonNull(lastWarning, "lastWarning");
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

    @Nullable
    private static ProviderLocation getProviderLocation(PatternContainer container) {
        if (container instanceof TileEntity tile) {
            return getProviderLocation(tile, null);
        }
        if (container instanceof AEBasePart part) {
            return getProviderLocation(part.getTileEntity(), part.getSide());
        }
        if (container instanceof PatternProviderLogicHost host) {
            return getProviderLocation(host.getTileEntity(), null);
        }
        return null;
    }

    @Nullable
    private static ProviderLocation getProviderLocation(@Nullable TileEntity tile, @Nullable EnumFacing side) {
        if (tile == null || tile.getWorld() == null) {
            return null;
        }

        return new ProviderLocation(tile.getWorld().provider.getDimension(), tile.getPos().toLong(),
            side == null ? -1 : side.ordinal());
    }

    @Nullable
    private static Class<? extends PatternContainer> tryCastMachineToContainer(Class<?> machineClass) {
        if (PatternContainer.class.isAssignableFrom(machineClass)) {
            return machineClass.asSubclass(PatternContainer.class);
        }
        return null;
    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
    }

    private record PatternSlotFilter(PatternContainer container, World level) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty()
                && isAcceptedByContainer(this.container, PatternDetailsHelper.decodePattern(stack, this.level));
        }
    }

    static final class ProcessingPatternUploadPreparation {
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

        ProcessingPatternUploadResult result() {
            return this.result;
        }

        boolean ready() {
            return this.result == ProcessingPatternUploadResult.SUCCESS && this.targetSlot != null;
        }

        boolean commit() {
            if (!ready()) {
                throw new IllegalStateException("Cannot commit an unsuccessful provider upload preparation");
            }
            return Objects.requireNonNull(this.targetSlot, "targetSlot").addItems(this.encodedPattern.copy()).isEmpty();
        }

        void restoreTargetSlot() {
            if (this.targetSlot != null) {
                this.targetSlot.setItemDirect(0, this.originalTargetStack.copy());
            }
        }

        void restoreTargetSlotAfterFailure(RuntimeException originalFailure) {
            Objects.requireNonNull(originalFailure, "originalFailure");
            try {
                restoreTargetSlot();
            } catch (RuntimeException restoreFailure) {
                originalFailure.addSuppressed(restoreFailure);
            }
        }
    }

    private record ProviderLocation(int dimensionId, long pos, int side) {
    }

    record ProviderDirectoryEntry(PatternContainer container, long sortBy, PatternContainerGroup group,
                                  int inventorySize, int emptySlots, boolean acceptsProcessingPatterns,
                                  boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                  @Nullable ProviderReference reference, boolean hasLocation,
                                  int locationDimension, long locationPos, int locationSide) {
        public ProviderDirectoryEntry {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(group, "group");
            if (inventorySize < 0) {
                throw new IllegalArgumentException("inventorySize must not be negative");
            }
            if (emptySlots < 0 || emptySlots > inventorySize) {
                throw new IllegalArgumentException("emptySlots must be between zero and inventorySize");
            }
        }

        static ProviderDirectoryEntry of(PatternContainer container) {
            Objects.requireNonNull(container, "container");

            ProviderLocation location = getProviderLocation(container);
            ProviderReference reference = location == null
                ? null
                : new ProviderReference(location.dimensionId(), location.pos(), location.side());
            return new ProviderDirectoryEntry(container, container.getTerminalSortOrder(), container.getTerminalGroup(),
                container.getTerminalPatternInventory().size(), countEmptySlots(container),
                !container.isAssemblerPatternContainer(),
                container.canEditTerminalName(), container.canModifyTerminalVisibility(), reference,
                location != null, location == null ? 0 : location.dimensionId(),
                location == null ? 0L : location.pos(), location == null ? -1 : location.side());
        }
    }

}
