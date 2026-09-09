package ae2.client.gui.me.patternaccess;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.GenericStack;
import ae2.core.AELog;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared client-side state and list-building logic for pattern access displays.
 * <p>
 * This support exists so PAT and PEAT can share provider packet handling, search indexing, grouping, row construction
 * and matched-slot tracking while each screen keeps its own layout, rendering and terminal-specific controls.
 */
final class PatternAccessDisplaySupport {
    /**
     * Number of provider pattern slots per rendered row. This is supplied by each screen because it is a layout choice.
     */
    private final int columns;
    /**
     * Supplies the current client world used when decoding encoded patterns for input/output search text.
     */
    private final Supplier<World> worldSupplier;
    /**
     * Human-readable screen name used in warnings so malformed or stale packets identify the affected display.
     */
    private final String logName;
    /**
     * Provider entries keyed by server inventory id from provider packets.
     */
    private final Long2ObjectMap<PatternContainerEntry> byId = new Long2ObjectOpenHashMap<>();
    /**
     * Provider entries grouped by terminal group for row construction and rename actions.
     */
    private final HashMultimap<PatternContainerGroup, PatternContainerEntry> byGroup = HashMultimap.create();
    /**
     * Sorted groups used to rebuild row order deterministically.
     */
    private final ObjectArrayList<PatternContainerGroup> groups = new ObjectArrayList<>();
    /**
     * Current visible row model consumed by each screen for drawing and client-side slots.
     */
    private final ObjectArrayList<Row> rows = new ObjectArrayList<>();
    /**
     * Cached lower-case input/output search text per pattern stack.
     */
    private final Map<ItemStack, PatternSearchData> patternSearchText = new Reference2ObjectOpenHashMap<>();
    /**
     * Optional world-location metadata per provider inventory.
     */
    private final Long2ObjectMap<PatternProviderInfo> providerInfo = new Long2ObjectOpenHashMap<>();

    public PatternAccessDisplaySupport(int columns, Supplier<World> worldSupplier, String logName) {
        this.columns = columns;
        this.worldSupplier = worldSupplier;
        this.logName = logName;
    }

    /**
     * Clears all provider data received from the server.
     */
    public void clear() {
        for (var entry : this.byId.values()) {
            entry.clearMatchedSlots();
        }
        this.byId.clear();
        this.providerInfo.clear();
        this.patternSearchText.clear();
        this.byGroup.clear();
        this.groups.clear();
        this.rows.clear();
    }

    /**
     * Releases one provider's display state after it leaves the observed directory.
     */
    public void removeProvider(long inventoryId) {
        var removed = this.byId.remove(inventoryId);
        if (removed != null) {
            removed.clearMatchedSlots();
            forgetSearchText(removed);
        }
        this.providerInfo.remove(inventoryId);
    }

    /**
     * Stores provider location metadata delivered independently of provider inventory packets.
     */
    public void postProviderInfo(long inventoryId, int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
        this.providerInfo.put(inventoryId, new PatternProviderInfo(dimensionId, pos, face));
    }

    /**
     * Replaces one provider inventory from a full packet update.
     *
     * @return true when the update was accepted and the row list should be rebuilt
     */
    public boolean postFullUpdate(long inventoryId, long sortBy, boolean canEditTerminalName,
                                  boolean canModifyTerminalVisibility, @Nullable PatternContainerGroup group,
                                  int inventorySize, Int2ObjectMap<ItemStack> slots) {
        if (group == null) {
            AELog.warn("Ignoring %s full update without a provider group for inventory id %d", this.logName,
                inventoryId);
            return false;
        }

        PatternContainerEntry entry = new PatternContainerEntry(inventoryId, inventorySize, sortBy,
            canEditTerminalName, canModifyTerminalVisibility, group);
        var previous = this.byId.put(inventoryId, entry);
        if (previous != null) {
            forgetSearchText(previous);
        }
        applySlotUpdates(entry, slots);
        return true;
    }

    /**
     * Applies changed provider slots from an incremental packet update.
     *
     * @return true when the update was accepted and the row list should be rebuilt
     */
    public boolean postIncrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots) {
        PatternContainerEntry entry = this.byId.get(inventoryId);
        if (entry == null) {
            AELog.warn("Ignoring %s incremental update for unknown inventory id %d", this.logName, inventoryId);
            return false;
        }

        applySlotUpdates(entry, slots);
        return true;
    }

    /**
     * Rebuilds grouped provider rows for the current search filters.
     */
    public void refreshList(String groupFilter, String inputFilter, String outputFilter) {
        this.byGroup.clear();
        for (var entry : this.byId.values()) {
            entry.clearMatchedSlots();
        }

        String normalizedGroupFilter = groupFilter.trim().toLowerCase(Locale.ROOT);
        String normalizedInputFilter = inputFilter.trim().toLowerCase(Locale.ROOT);
        String normalizedOutputFilter = outputFilter.trim().toLowerCase(Locale.ROOT);
        for (PatternContainerEntry entry : this.byId.values()) {
            if (matchesSearch(entry, normalizedGroupFilter, normalizedInputFilter, normalizedOutputFilter)) {
                this.byGroup.put(entry.getGroup(), entry);
            }
        }

        int groupCount = this.byGroup.keySet().size();
        int rowCapacity = groupCount;
        for (PatternContainerEntry container : this.byGroup.values()) {
            rowCapacity += (container.getInventory().size() + this.columns - 1) / this.columns;
        }

        this.groups.clear();
        this.groups.ensureCapacity(groupCount);
        this.groups.addAll(this.byGroup.keySet());
        this.groups.sort(Comparator.comparing(group -> group.name().getFormattedText().toLowerCase(Locale.ROOT)));

        this.rows.clear();
        this.rows.ensureCapacity(rowCapacity);
        for (int groupIndex = 0; groupIndex < this.groups.size(); groupIndex++) {
            var group = this.groups.get(groupIndex);
            this.rows.add(new GroupHeaderRow(group));

            ObjectList<PatternContainerEntry> containers = new ObjectArrayList<>(this.byGroup.get(group));
            containers.sort(null);
            for (int containerIndex = 0; containerIndex < containers.size(); containerIndex++) {
                var container = containers.get(containerIndex);
                int size = container.getInventory().size();
                for (int offset = 0; offset < size; offset += this.columns) {
                    int slots = Math.min(size - offset, this.columns);
                    this.rows.add(new SlotsRow(container, offset, slots));
                }
            }
        }
    }

    public ObjectList<Row> rows() {
        return this.rows;
    }

    public Collection<PatternContainerEntry> getGroupEntries(PatternContainerGroup group) {
        return this.byGroup.get(group);
    }

    @Nullable
    public PatternProviderInfo getProviderInfo(long inventoryId) {
        return this.providerInfo.get(inventoryId);
    }

    public boolean isMatchedPatternSlot(PatternContainerEntry entry, int slot) {
        return entry.isSlotMatched(slot);
    }

    private void forgetSearchText(PatternContainerEntry entry) {
        var inventory = entry.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            this.patternSearchText.remove(inventory.getStackInSlot(i));
        }
    }

    private void applySlotUpdates(PatternContainerEntry entry, Int2ObjectMap<ItemStack> slots) {
        var inventory = entry.getInventory();
        for (Int2ObjectMap.Entry<ItemStack> slotUpdate : slots.int2ObjectEntrySet()) {
            int slot = slotUpdate.getIntKey();
            if (slot >= 0 && slot < inventory.size()) {
                var previous = inventory.getStackInSlot(slot);
                this.patternSearchText.remove(previous);
                entry.invalidatePatternDisplay(previous);
                inventory.setItemDirect(slot, slotUpdate.getValue());
                entry.invalidatePatternDisplay(slotUpdate.getValue());
            } else {
                AELog.warn("Ignoring %s provider slot update outside inventory bounds: %d", this.logName, slot);
            }
        }
    }

    private boolean matchesSearch(PatternContainerEntry entry, String groupFilter, String inputFilter,
                                  String outputFilter) {
        if (!groupFilter.isEmpty() && !entry.getSearchName().contains(groupFilter)) {
            return false;
        }

        if (inputFilter.isEmpty() && outputFilter.isEmpty()) {
            return true;
        }

        boolean inputMatched = inputFilter.isEmpty();
        boolean outputMatched = outputFilter.isEmpty();
        for (int slot = 0; slot < entry.getInventory().size(); slot++) {
            ItemStack stack = entry.getInventory().getStackInSlot(slot);
            boolean slotInputMatched = stackMatchesInputFilter(stack, inputFilter);
            boolean slotOutputMatched = stackMatchesOutputFilter(stack, outputFilter);
            if (slotInputMatched || slotOutputMatched) {
                entry.setSlotMatched(slot);
            }
            inputMatched |= slotInputMatched;
            outputMatched |= slotOutputMatched;
        }

        return inputMatched && outputMatched;
    }

    private boolean stackMatchesInputFilter(ItemStack stack, String inputFilter) {
        if (stack.isEmpty()) {
            return false;
        }
        if (inputFilter.isEmpty()) {
            return true;
        }
        PatternSearchData searchData = this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText);
        return searchData.inputs().contains(inputFilter);
    }

    private boolean stackMatchesOutputFilter(ItemStack stack, String outputFilter) {
        if (stack.isEmpty()) {
            return false;
        }
        if (outputFilter.isEmpty()) {
            return true;
        }
        PatternSearchData searchData = this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText);
        return searchData.outputs().contains(outputFilter);
    }

    private PatternSearchData getPatternSearchText(ItemStack stack) {
        World level = this.worldSupplier.get();
        StringBuilder inputs = new StringBuilder();
        StringBuilder outputs = new StringBuilder();
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, level);

        if (pattern != null) {
            appendOutputs(pattern, outputs);
            appendInputs(pattern, inputs);
        }

        return new PatternSearchData(inputs.toString(), outputs.toString());
    }

    private static void appendOutputs(IPatternDetails pattern, StringBuilder text) {
        for (GenericStack output : pattern.getOutputs()) {
            appendStackName(text, output);
        }
    }

    private static void appendInputs(IPatternDetails pattern, StringBuilder text) {
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            for (GenericStack possibleInput : input.possibleInputs()) {
                appendStackName(text, possibleInput);
            }
        }
    }

    private static void appendStackName(StringBuilder text, @Nullable GenericStack stack) {
        if (stack == null || stack.what() == null) {
            return;
        }
        text.append(stack.what().getDisplayName().getFormattedText().toLowerCase(Locale.ROOT));
        text.append('\n');
    }

    public interface Row {
    }

    public record GroupHeaderRow(PatternContainerGroup group) implements Row {
    }

    public record SlotsRow(PatternContainerEntry container, int offset, int slots) implements Row {
    }

    public record PatternProviderInfo(int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
    }

    private record PatternSearchData(String inputs, String outputs) {
    }
}
