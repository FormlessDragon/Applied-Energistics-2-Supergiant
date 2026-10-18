package ae2.integration.data;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingTreeNode;
import ae2.crafting.CraftingTreeProcess;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public final class LiteCraftTreeNode implements Comparable<LiteCraftTreeNode> {
    private final LiteCraftTreeProc parent;
    private final GenericStack output;
    private final List<LiteCraftTreeProc> inputs;
    private final long missing;

    private boolean missingCached = false;
    private boolean missingCache = false;

    public LiteCraftTreeNode(final LiteCraftTreeProc parent, GenericStack output, List<LiteCraftTreeProc> inputs, long missing) {
        this.parent = parent;
        this.output = output;
        this.inputs = inputs;
        this.missing = missing;
    }

    public static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent) {
        return of(node, parent, node.getAmount());
    }

    public static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent, long amount) {
        return of(node, parent, amount, node.getMissing());
    }

    public static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent, long amount,
                                       PatternTimesAllocator patternTimesAllocator) {
        return of(node, parent, amount, node.getMissing(), patternTimesAllocator,
            new LiteCraftTreeProc.MissingAllocator());
    }

    public static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent, long amount,
                                       long missing) {
        return of(node, parent, amount, missing, null);
    }

    public static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent, long amount,
                                       long missing, PatternTimesAllocator patternTimesAllocator) {
        return of(node, parent, amount, missing, patternTimesAllocator, new LiteCraftTreeProc.MissingAllocator());
    }

    static LiteCraftTreeNode of(final CraftingTreeNode node, final LiteCraftTreeProc parent, long amount,
                                long missing, PatternTimesAllocator patternTimesAllocator,
                                LiteCraftTreeProc.MissingAllocator missingAllocator) {
        List<LiteCraftTreeProc> inputs = new ArrayList<>();
        List<CraftingTreeProcess> displayNodes = node.getDisplayNodes();
        if (displayNodes != null) {
            boolean recursiveDisplayNode = node.getRecursiveDisplayAmount() > 0;
            if (displayNodes instanceof RandomAccess) {
                for (int i = 0, size = displayNodes.size(); i < size; i++) {
                    CraftingTreeProcess process = displayNodes.get(i);
                    LiteCraftTreeProc proc = LiteCraftTreeProc.of(process, missingAllocator, patternTimesAllocator,
                        recursiveDisplayNode);
                    if (proc != null) {
                        inputs.add(proc);
                    }
                }
            } else {
                for (CraftingTreeProcess process : displayNodes) {
                    LiteCraftTreeProc proc = LiteCraftTreeProc.of(process, missingAllocator, patternTimesAllocator,
                        recursiveDisplayNode);
                    if (proc != null) {
                        inputs.add(proc);
                    }
                }
            }
        }
        return new LiteCraftTreeNode(parent, new GenericStack(node.getWhat(), amount), inputs, missing);
    }

    @SuppressWarnings("unused")
    public static LiteCraftTreeNode fromBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet,
                                               final LiteCraftTreeProc parent) {
        return fromBuffer(buf, stackSet, parent, new CraftingTreeStackRegistry.DecodeLimits(), 0);
    }

    public static LiteCraftTreeNode fromBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet,
                                               final LiteCraftTreeProc parent,
                                               final CraftingTreeStackRegistry.DecodeLimits limits, final int depth) {
        limits.addNode(depth);

        long stackID = CraftingTreeByteBuf.readVarLong(buf);
        GenericStack output = stackSet.get(stackID);

        long stackSize = CraftingTreeByteBuf.readNonNegativeVarLong(buf, "Crafting tree stack size");
        output = new GenericStack(output.what(), stackSize);

        int size = new PacketBuffer(buf).readVarInt();
        limits.checkNodeChildCount(size);
        List<LiteCraftTreeProc> inputs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            inputs.add(LiteCraftTreeProc.fromBuffer(buf, stackSet, limits, depth + 1));
        }

        long missing = CraftingTreeByteBuf.readNonNegativeVarLong(buf, "Crafting tree missing amount");
        return new LiteCraftTreeNode(parent, output, inputs, missing);
    }

    public static int diveToDeep(final LiteCraftTreeNode node, final int depth, final DepthRecorder recorder) {
        if (node.inputs instanceof RandomAccess) {
            for (int i = 0, size = node.inputs.size(); i < size; i++) {
                diveToDeep(node.inputs.get(i), depth, recorder);
            }
        } else {
            for (LiteCraftTreeProc input : node.inputs) {
                diveToDeep(input, depth, recorder);
            }
        }
        return recorder.getDepth();
    }

    private static void diveToDeep(LiteCraftTreeProc proc, int depth, DepthRecorder recorder) {
        List<LiteCraftTreeNode> inputs = proc.inputs();
        int newDepth = depth + 1;
        if (inputs instanceof RandomAccess) {
            for (int i = 0, size = inputs.size(); i < size; i++) {
                recorder.dive(newDepth);
                diveToDeep(inputs.get(i), newDepth, recorder);
            }
        } else {
            for (LiteCraftTreeNode input : inputs) {
                recorder.dive(newDepth);
                diveToDeep(input, newDepth, recorder);
            }
        }
    }

    /**
     * Check if this node or sub nodes is missing ingredients.
     */
    public static boolean isMissing(final LiteCraftTreeNode node) {
        if (node.missingCached) {
            return node.missingCache;
        }
        if (node.missing() > 0) {
            node.missingCached = true;
            return node.missingCache = true;
        }
        if (node.inputs instanceof RandomAccess) {
            for (int i = 0, size = node.inputs.size(); i < size; i++) {
                if (isMissing(node.inputs.get(i))) {
                    return node.missingCached = node.missingCache = true;
                }
            }
        } else {
            for (LiteCraftTreeProc input : node.inputs) {
                if (isMissing(input)) {
                    return node.missingCached = node.missingCache = true;
                }
            }
        }
        node.missingCached = true;
        node.missingCache = false;
        return false;
    }

    public void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet) {
        writeToBuffer(buf, stackSet, new CraftingTreeStackRegistry.DecodeLimits(), 0);
    }

    void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet,
                       final CraftingTreeStackRegistry.DecodeLimits limits, final int depth) {
        limits.addNode(depth);
        limits.checkNodeChildCount(inputs.size());

        int stackID = stackSet.add(output);
        CraftingTreeByteBuf.writeVarLong(buf, stackID);

        long stackSize = output.amount();
        CraftingTreeByteBuf.writeVarLong(buf, stackSize);

        new PacketBuffer(buf).writeVarInt(inputs.size());
        if (inputs instanceof RandomAccess) {
            for (int i = 0, size = inputs.size(); i < size; i++) {
                inputs.get(i).writeToBuffer(buf, stackSet, limits, depth + 1);
            }
        } else {
            for (LiteCraftTreeProc input : inputs) {
                input.writeToBuffer(buf, stackSet, limits, depth + 1);
            }
        }
        CraftingTreeByteBuf.writeVarLong(buf, missing);
    }

    private static boolean isMissing(final LiteCraftTreeProc proc) {
        List<LiteCraftTreeNode> inputs = proc.inputs();
        if (inputs instanceof RandomAccess) {
            for (int i = 0, size = inputs.size(); i < size; i++) {
                if (isMissing(inputs.get(i))) {
                    return true;
                }
            }
        } else {
            for (LiteCraftTreeNode input : inputs) {
                if (isMissing(input)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void sort() {
        sort(new SortDepthCache());
    }

    @Override
    public int compareTo(@NotNull final LiteCraftTreeNode o) {
        return Integer.compare(diveToDeep(this, 0, new DepthRecorder()), diveToDeep(o, 0, new DepthRecorder()));
    }

    void sort(SortDepthCache depthCache) {
        inputs.sort(Comparator.comparingInt(depthCache::procDepth).reversed());
        if (inputs instanceof RandomAccess) {
            for (int i = 0, size = inputs.size(); i < size; i++) {
                inputs.get(i).sort(depthCache);
            }
        } else {
            for (LiteCraftTreeProc input : inputs) {
                input.sort(depthCache);
            }
        }
    }

    public LiteCraftTreeNode withMissingOnly() {
        if (!isMissing(this)) {
            return null;
        }

        List<LiteCraftTreeProc> missingInputs = new ArrayList<>();
        if (inputs instanceof RandomAccess) {
            for (int i = 0, size = inputs.size(); i < size; i++) {
                LiteCraftTreeProc input = inputs.get(i);
                if (isMissing(input)) {
                    missingInputs.add(input.withMissingOnly());
                }
            }
        } else {
            for (LiteCraftTreeProc input : inputs) {
                if (isMissing(input)) {
                    missingInputs.add(input.withMissingOnly());
                }
            }
        }

        LiteCraftTreeNode node = new LiteCraftTreeNode(parent, output, missingInputs, missing);
        node.missingCached = true;
        node.missingCache = true;
        return node;
    }

    public LiteCraftTreeProc parent() {
        return parent;
    }

    public GenericStack output() {
        return output;
    }

    public List<LiteCraftTreeProc> inputs() {
        return inputs;
    }

    public long missing() {
        return missing;
    }

    public static final class PatternTimesAllocator {
        private final Object2LongOpenHashMap<IPatternDetails> remainingTimes = new Object2LongOpenHashMap<>();
        private final Object2LongOpenHashMap<AEKey> remainingSelfReturningInputs = new Object2LongOpenHashMap<>();

        private PatternTimesAllocator(Object2LongMap<IPatternDetails> patternTimes) {
            remainingTimes.defaultReturnValue(0);
            remainingSelfReturningInputs.defaultReturnValue(0);
            for (Object2LongMap.Entry<IPatternDetails> entry : patternTimes.object2LongEntrySet()) {
                remainingTimes.put(entry.getKey(), entry.getLongValue());
            }
        }

        public PatternTimesAllocator(Object2LongMap<IPatternDetails> patternTimes, KeyCounter usedItems,
                                     KeyCounter missingItems) {
            this(patternTimes);
            for (var entry : usedItems) {
                remainingSelfReturningInputs.addTo(entry.getKey(), entry.getLongValue());
            }
            for (var entry : missingItems) {
                remainingSelfReturningInputs.addTo(entry.getKey(), entry.getLongValue());
            }
        }

        long allocate(CraftingTreeProcess process) {
            long requestTimes = process.getTreeRequestTimes();
            if (requestTimes <= 0) {
                return 0;
            }

            IPatternDetails details = process.getDetails();
            if (!remainingTimes.containsKey(details)) {
                return requestTimes;
            }

            long remaining = remainingTimes.getLong(details);
            long allocated = Math.min(requestTimes, remaining);
            remainingTimes.addTo(details, -allocated);
            return allocated;
        }

        long allocateSelfReturningInput(CraftingTreeNode node, long amount) {
            if (!node.hasSelfReturningRemainderInput()) {
                return amount;
            }

            var key = node.getWhat();
            if (!remainingSelfReturningInputs.containsKey(key)) {
                return amount;
            }

            long remaining = remainingSelfReturningInputs.getLong(key);
            long allocated = Math.min(amount, remaining);
            remainingSelfReturningInputs.addTo(key, -allocated);
            return allocated;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (LiteCraftTreeNode) obj;
        return Objects.equals(this.output, that.output) &&
            Objects.equals(this.inputs, that.inputs) &&
            this.missing == that.missing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(output, inputs, missing);
    }

    @Override
    public String toString() {
        return "LiteCraftTreeNode[" +
            "output=" + output + ", " +
            "inputs=" + inputs + ", " +
            "missing=" + missing + ']';
    }

    public static class DepthRecorder {

        private int depth;

        void dive(int depth) {
            this.depth = Math.max(this.depth, depth);
        }

        public int getDepth() {
            return depth;
        }

    }

    static final class SortDepthCache {
        private final Reference2IntMap<LiteCraftTreeNode> nodeDepths = new Reference2IntOpenHashMap<>();
        private final Reference2IntMap<LiteCraftTreeProc> procDepths = new Reference2IntOpenHashMap<>();

        int nodeDepth(LiteCraftTreeNode node) {
            return nodeDepths.computeIfAbsent(node, ignored -> diveToDeep(node, 0, new DepthRecorder()));
        }

        int procDepth(LiteCraftTreeProc proc) {
            return procDepths.computeIfAbsent(proc, ignored -> LiteCraftTreeProc.diveToDeep(proc, 0,
                new DepthRecorder()));
        }
    }

}
