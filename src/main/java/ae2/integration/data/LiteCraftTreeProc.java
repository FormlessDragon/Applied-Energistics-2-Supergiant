package ae2.integration.data;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEKey;
import ae2.crafting.CraftingTreeNode;
import ae2.crafting.CraftingTreeProcess;
import ae2.crafting.execution.CraftingSupplierLocation;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

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
    @SuppressWarnings("unused")
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

        var packetBuffer = new PacketBuffer(buf);
        int size = packetBuffer.readVarInt();
        limits.checkProcessChildCount(size);
        int machineCount = packetBuffer.readVarInt();
        limits.checkMachineCount(machineCount);
        List<PatternContainerGroup> machines = new ArrayList<>(machineCount);
        Map<PatternContainerGroup, List<CraftingSupplierLocation>> machineLocations =
            new Object2ObjectLinkedOpenHashMap<>(machineCount);
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

    @SuppressWarnings("unused")
    public void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet) {
        writeToBuffer(buf, stackSet, new CraftingTreeStackRegistry.DecodeLimits(), 0);
    }

    void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet,
                       final CraftingTreeStackRegistry.DecodeLimits limits, final int depth) {
        limits.addProcess();
        limits.checkProcessChildCount(inputs.size());
        limits.checkMachineCount(machines.size());
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(inputs.size());
        packetBuffer.writeVarInt(machines.size());
        if (machines instanceof RandomAccess) {
            for (int machineIndex = 0, machineCount = machines.size(); machineIndex < machineCount; machineIndex++) {
                writeMachine(packetBuffer, machines.get(machineIndex), limits);
            }
        } else {
            for (PatternContainerGroup machine : machines) {
                writeMachine(packetBuffer, machine, limits);
            }
        }
        if (inputs instanceof RandomAccess) {
            for (int inputIndex = 0, inputCount = inputs.size(); inputIndex < inputCount; inputIndex++) {
                inputs.get(inputIndex).writeToBuffer(buf, stackSet, limits, depth);
            }
        } else {
            for (LiteCraftTreeNode input : inputs) {
                input.writeToBuffer(buf, stackSet, limits, depth);
            }
        }
    }

    private void writeMachine(PacketBuffer packetBuffer, PatternContainerGroup machine,
                              CraftingTreeStackRegistry.DecodeLimits limits) {
        machine.writeToPacket(packetBuffer);
        List<CraftingSupplierLocation> locations = machineLocations(machine);
        limits.addMachineLocations(locations.size());
        packetBuffer.writeVarInt(locations.size());
        if (locations instanceof RandomAccess) {
            for (int locationIndex = 0, locationCount = locations.size(); locationIndex < locationCount;
                 locationIndex++) {
                locations.get(locationIndex).write(packetBuffer);
            }
        } else {
            for (CraftingSupplierLocation location : locations) {
                location.write(packetBuffer);
            }
        }
    }

    public static int diveToDeep(final LiteCraftTreeProc proc, final int depth, final LiteCraftTreeNode.DepthRecorder recorder) {
        if (proc.inputs instanceof RandomAccess) {
            for (int inputIndex = 0, inputCount = proc.inputs.size(); inputIndex < inputCount; inputIndex++) {
                LiteCraftTreeNode node = proc.inputs.get(inputIndex);
                List<LiteCraftTreeProc> subProcesses = node.inputs();
                if (subProcesses instanceof RandomAccess) {
                    for (int subProcessIndex = 0, subProcessCount = subProcesses.size();
                         subProcessIndex < subProcessCount; subProcessIndex++) {
                        int newDepth = depth + 1;
                        recorder.dive(newDepth);
                        diveToDeep(subProcesses.get(subProcessIndex), newDepth, recorder);
                    }
                } else {
                    for (LiteCraftTreeProc subProc : subProcesses) {
                        int newDepth = depth + 1;
                        recorder.dive(newDepth);
                        diveToDeep(subProc, newDepth, recorder);
                    }
                }
            }
        } else {
            for (LiteCraftTreeNode node : proc.inputs) {
                List<LiteCraftTreeProc> subProcesses = node.inputs();
                if (subProcesses instanceof RandomAccess) {
                    for (int subProcessIndex = 0, subProcessCount = subProcesses.size();
                         subProcessIndex < subProcessCount; subProcessIndex++) {
                        int newDepth = depth + 1;
                        recorder.dive(newDepth);
                        diveToDeep(subProcesses.get(subProcessIndex), newDepth, recorder);
                    }
                } else {
                    for (LiteCraftTreeProc subProc : subProcesses) {
                        int newDepth = depth + 1;
                        recorder.dive(newDepth);
                        diveToDeep(subProc, newDepth, recorder);
                    }
                }
            }
        }
        return recorder.getDepth();
    }

    public List<CraftingSupplierLocation> machineLocations(PatternContainerGroup machine) {
        return machineLocations.getOrDefault(machine, List.of());
    }

    public LiteCraftTreeProc withMissingOnly() {
        List<LiteCraftTreeNode> missingInputs = new ArrayList<>();
        if (inputs instanceof RandomAccess) {
            for (int inputIndex = 0, inputCount = inputs.size(); inputIndex < inputCount; inputIndex++) {
                LiteCraftTreeNode input = inputs.get(inputIndex);
                if (LiteCraftTreeNode.isMissing(input)) {
                    missingInputs.add(input.withMissingOnly());
                }
            }
        } else {
            for (LiteCraftTreeNode input : inputs) {
                if (LiteCraftTreeNode.isMissing(input)) {
                    missingInputs.add(input.withMissingOnly());
                }
            }
        }
        return new LiteCraftTreeProc(missingInputs, machines, machineLocations);
    }

    public void sort() {
        sort(new LiteCraftTreeNode.SortDepthCache());
    }

    void sort(LiteCraftTreeNode.SortDepthCache depthCache) {
        inputs.sort(Comparator.comparingInt(depthCache::nodeDepth).reversed());
        if (inputs instanceof RandomAccess) {
            for (int inputIndex = 0, inputCount = inputs.size(); inputIndex < inputCount; inputIndex++) {
                inputs.get(inputIndex).sort(depthCache);
            }
        } else {
            for (LiteCraftTreeNode input : inputs) {
                input.sort(depthCache);
            }
        }
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
