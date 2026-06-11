package ae2.integration.data;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEKey;
import ae2.crafting.CraftingTreeNode;
import ae2.crafting.CraftingTreeProcess;
import ae2.crafting.execution.CraftingSupplierLocation;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LiteCraftTreeProc(List<LiteCraftTreeNode> inputs,
                                List<PatternContainerGroup> machines,
                                Map<PatternContainerGroup, List<CraftingSupplierLocation>> machineLocations) implements Comparable<LiteCraftTreeProc> {

    @SuppressWarnings("unused")
    public LiteCraftTreeProc(List<LiteCraftTreeNode> inputs, List<PatternContainerGroup> machines) {
        this(inputs, machines, Map.of());
    }

    @Nullable
    public static LiteCraftTreeProc of(final CraftingTreeProcess process) {
        return of(process, null, 1);
    }

    @Nullable
    public static LiteCraftTreeProc of(final CraftingTreeProcess process, final CraftingTreeNode parent,
                                       final long parentAmount) {
        return of(process, null, null, false);
    }

    @Nullable
    @SuppressWarnings("unused")
    static LiteCraftTreeProc of(final CraftingTreeProcess process, final CraftingTreeNode parent,
                                final long parentAmount, final MissingAllocator missingAllocator) {
        return of(process, missingAllocator, null, false);
    }

    @Nullable
    static LiteCraftTreeProc of(final CraftingTreeProcess process, final MissingAllocator missingAllocator,
                                final LiteCraftTreeNode.PatternTimesAllocator patternTimesAllocator,
                                boolean recursiveDisplayNode) {
        long processTimes = patternTimesAllocator == null
            ? process.getTreeDisplayTimes()
            : recursiveDisplayNode ? process.getTreeRequestTimes() : patternTimesAllocator.allocate(process);
        if (processTimes <= 0) {
            return null;
        }
        List<LiteCraftTreeNode> inputs = new ArrayList<>();
        LiteCraftTreeProc proc = new LiteCraftTreeProc(inputs, process.getMachineGroups(),
            process.getMachineLocations());
        for (Object2LongMap.Entry<CraftingTreeNode> entry : process.getNodes().object2LongEntrySet()) {
            CraftingTreeNode node = entry.getKey();
            long amount = node.getAmount() * entry.getLongValue() * processTimes;
            if (process.hasTreeInputDisplayAmount(node)) {
                amount = process.getTreeInputDisplayAmount(node);
            }
            if (patternTimesAllocator != null) {
                amount = patternTimesAllocator.allocateSelfReturningInput(node, amount);
            }
            if (amount <= 0) {
                continue;
            }
            long missing = missingAllocator == null
                ? Math.min(node.getMissing(), amount)
                : missingAllocator.allocate(node, amount);
            inputs.add(LiteCraftTreeNode.of(node, proc, amount, missing, patternTimesAllocator, missingAllocator));
        }
        // return null if no inputs
        return inputs.isEmpty() ? null : proc;
    }

    @SuppressWarnings("unused")
    public static LiteCraftTreeProc fromBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet) {
        return fromBuffer(buf, stackSet, new CraftingTreeStackRegistry.DecodeLimits(), 0);
    }

    static LiteCraftTreeProc fromBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet,
                                        final CraftingTreeStackRegistry.DecodeLimits limits, final int depth) {
        limits.addProcess();

        int size = buf.readUnsignedByte();
        limits.checkProcessChildCount(size);
        int machineCount = buf.readUnsignedByte();
        limits.checkMachineCount(machineCount);
        List<PatternContainerGroup> machines = new ArrayList<>(machineCount);
        Map<PatternContainerGroup, List<CraftingSupplierLocation>> machineLocations =
            new LinkedHashMap<>(machineCount);
        var packetBuffer = new PacketBuffer(buf);
        for (int i = 0; i < machineCount; i++) {
            PatternContainerGroup machine = PatternContainerGroup.readFromPacket(packetBuffer);
            machines.add(machine);
            int locationCount = packetBuffer.readVarInt();
            limits.addMachineLocations(locationCount);
            List<CraftingSupplierLocation> locations = new ArrayList<>(locationCount);
            for (int locationIndex = 0; locationIndex < locationCount; locationIndex++) {
                locations.add(CraftingSupplierLocation.read(packetBuffer));
            }
            if (!locations.isEmpty()) {
                machineLocations.put(machine, locations);
            }
        }
        List<LiteCraftTreeNode> inputs = new ArrayList<>();
        LiteCraftTreeProc proc = new LiteCraftTreeProc(inputs, machines, machineLocations);
        for (int i = 0; i < size; i++) {
            inputs.add(LiteCraftTreeNode.fromBuffer(buf, stackSet, proc, limits, depth));
        }
        return proc;
    }

    public void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet) {
        if (inputs.size() > Byte.MAX_VALUE) {
            throw new IllegalStateException("Too many inputs for a single node");
        }
        if (machines.size() > 0xFF) {
            throw new IllegalStateException("Too many machines for a single process");
        }
        buf.writeByte(inputs.size());
        buf.writeByte(machines.size());
        var packetBuffer = new PacketBuffer(buf);
        for (PatternContainerGroup machine : machines) {
            machine.writeToPacket(packetBuffer);
            List<CraftingSupplierLocation> locations = machineLocations(machine);
            packetBuffer.writeVarInt(locations.size());
            for (CraftingSupplierLocation location : locations) {
                location.write(packetBuffer);
            }
        }
        inputs.forEach(node -> node.writeToBuffer(buf, stackSet));
    }

    public static int diveToDeep(final LiteCraftTreeProc proc, final int depth, final LiteCraftTreeNode.DepthRecorder recorder) {
        for (final LiteCraftTreeNode node : proc.inputs) {
            for (final LiteCraftTreeProc subProc : node.inputs()) {
                int newDepth = depth + 1;
                recorder.dive(newDepth);
                diveToDeep(subProc, newDepth, recorder);
            }
        }
        return recorder.getDepth();
    }

    public List<CraftingSupplierLocation> machineLocations(PatternContainerGroup machine) {
        return machineLocations.getOrDefault(machine, List.of());
    }

    public LiteCraftTreeProc withMissingOnly() {
        List<LiteCraftTreeNode> missingInputs = new ArrayList<>();
        for (final LiteCraftTreeNode input : inputs) {
            if (LiteCraftTreeNode.isMissing(input)) {
                missingInputs.add(input.withMissingOnly());
            } else {
                missingInputs.add(input.copyTree());
            }
        }
        return new LiteCraftTreeProc(missingInputs, machines, machineLocations);
    }

    public void sort() {
        sort(new LiteCraftTreeNode.SortDepthCache());
    }

    void sort(LiteCraftTreeNode.SortDepthCache depthCache) {
        inputs.sort(Comparator.comparingInt(depthCache::nodeDepth).reversed());
        for (final LiteCraftTreeNode input : inputs) {
            input.sort(depthCache);
        }
    }

    public LiteCraftTreeProc copyTree() {
        List<LiteCraftTreeNode> copiedInputs = new ArrayList<>(inputs.size());
        for (final LiteCraftTreeNode input : inputs) {
            copiedInputs.add(input.copyTree());
        }
        return new LiteCraftTreeProc(copiedInputs, machines, machineLocations);
    }

    static final class MissingAllocator {
        private final Object2LongLinkedOpenHashMap<AEKey> remainingMissingByKey = new Object2LongLinkedOpenHashMap<>();

        long allocate(CraftingTreeNode node, long amount) {
            AEKey key = node.getWhat();
            long remainingMissing;
            if (remainingMissingByKey.containsKey(key)) {
                remainingMissing = remainingMissingByKey.getLong(key);
            } else {
                remainingMissing = node.getMissing();
            }

            long missing = Math.min(remainingMissing, amount);
            remainingMissingByKey.put(key, remainingMissing - missing);
            return missing;
        }
    }

    @Override
    public int compareTo(@NotNull final LiteCraftTreeProc o) {
        return Integer.compare(diveToDeep(this, 0, new LiteCraftTreeNode.DepthRecorder()), diveToDeep(o, 0, new LiteCraftTreeNode.DepthRecorder()));
    }

}
