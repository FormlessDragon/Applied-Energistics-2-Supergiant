package ae2.integration.data;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingTreeNode;
import ae2.crafting.CraftingTreeProcess;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

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
        if (node.getDisplayNodes() != null) {
            boolean recursiveDisplayNode = node.getRecursiveDisplayAmount() > 0;
            for (CraftingTreeProcess process : node.getDisplayNodes()) {
                LiteCraftTreeProc proc = LiteCraftTreeProc.of(process, node, amount, missingAllocator,
                    patternTimesAllocator, recursiveDisplayNode);
                if (proc != null) {
                    inputs.add(proc);
                }
            }
        }
        return new LiteCraftTreeNode(parent, new GenericStack(node.getWhat(), amount), inputs, missing);
    }

    public static LiteCraftTreeNode fromBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet, final LiteCraftTreeProc parent) {
        int stackID = (int) CraftingTreeByteBuf.readVarLong(buf);
        GenericStack output = stackSet.get(stackID);

        long stackSize = CraftingTreeByteBuf.readVarLong(buf);
        output = new GenericStack(output.what(), stackSize);

        int size = buf.readByte();
        List<LiteCraftTreeProc> inputs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            inputs.add(LiteCraftTreeProc.fromBuffer(buf, stackSet));
        }

        long missing = CraftingTreeByteBuf.readVarLong(buf);
        return new LiteCraftTreeNode(parent, output, inputs, missing);
    }

    public static int diveToDeep(final LiteCraftTreeNode node, final int depth, final DepthRecorder recorder) {
        for (final LiteCraftTreeProc input : node.inputs) {
            for (final LiteCraftTreeNode subNode : input.inputs()) {
                int newDepth = depth + 1;
                recorder.dive(newDepth);
                diveToDeep(subNode, newDepth, recorder);
            }
        }
        return recorder.getDepth();
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
        for (final LiteCraftTreeProc input : node.inputs()) {
            for (final LiteCraftTreeNode subNode : input.inputs()) {
                if (isMissing(subNode)) {
                    return node.missingCached = node.missingCache = true;
                }
            }
        }
        node.missingCached = true;
        node.missingCache = false;
        return false;
    }

    public void writeToBuffer(final ByteBuf buf, final CraftingTreeStackRegistry stackSet) {
        if (inputs.size() > Byte.MAX_VALUE) {
            throw new IllegalStateException("Too many inputs for a single node");
        }

        int stackID = stackSet.add(output);
        CraftingTreeByteBuf.writeVarLong(buf, stackID);

        long stackSize = output.amount();
        CraftingTreeByteBuf.writeVarLong(buf, stackSize);

        buf.writeByte(inputs.size());
        inputs.forEach(input -> input.writeToBuffer(buf, stackSet));
        CraftingTreeByteBuf.writeVarLong(buf, missing);
    }

    private static boolean isMissing(final LiteCraftTreeProc proc) {
        for (final LiteCraftTreeNode input : proc.inputs()) {
            if (isMissing(input)) {
                return true;
            }
        }
        return false;
    }

    public void sort() {
        sort(new SortDepthCache());
    }

    @Override
    public int compareTo(@Nonnull final LiteCraftTreeNode o) {
        return Integer.compare(diveToDeep(this, 0, new DepthRecorder()), diveToDeep(o, 0, new DepthRecorder()));
    }

    void sort(SortDepthCache depthCache) {
        inputs.sort(Comparator.comparingInt(depthCache::procDepth).reversed());
        for (final LiteCraftTreeProc input : inputs) {
            input.sort(depthCache);
        }
    }

    public LiteCraftTreeNode withMissingOnly() {
        if (!isMissing(this)) {
            return null;
        }

        boolean keepCompleteSiblingProcesses = hasMissingProcess();
        List<LiteCraftTreeProc> missingInputs = new ArrayList<>();
        for (final LiteCraftTreeProc input : inputs) {
            if (isMissing(input)) {
                missingInputs.add(input.withMissingOnly());
            } else if (keepCompleteSiblingProcesses) {
                missingInputs.add(input.copyTree());
            }
        }

        LiteCraftTreeNode node = new LiteCraftTreeNode(parent, output, missingInputs, missing);
        node.missingCached = true;
        node.missingCache = true;
        return node;
    }

    public LiteCraftTreeNode copyTree() {
        List<LiteCraftTreeProc> copiedInputs = new ArrayList<>(inputs.size());
        for (final LiteCraftTreeProc input : inputs) {
            copiedInputs.add(input.copyTree());
        }

        LiteCraftTreeNode node = new LiteCraftTreeNode(parent, output, copiedInputs, missing);
        node.missingCached = missingCached;
        node.missingCache = missingCache;
        return node;
    }

    private boolean hasMissingProcess() {
        for (final LiteCraftTreeProc input : inputs) {
            if (isMissing(input)) {
                return true;
            }
        }
        return false;
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
        private final Object2LongLinkedOpenHashMap<IPatternDetails> remainingTimes =
            new Object2LongLinkedOpenHashMap<>();
        private final Object2LongLinkedOpenHashMap<AEKey> remainingSelfReturningInputs =
            new Object2LongLinkedOpenHashMap<>();

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
            remainingTimes.put(details, remaining - allocated);
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
            remainingSelfReturningInputs.put(key, remaining - allocated);
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
        private final IdentityHashMap<LiteCraftTreeNode, Integer> nodeDepths = new IdentityHashMap<>();
        private final IdentityHashMap<LiteCraftTreeProc, Integer> procDepths = new IdentityHashMap<>();

        int nodeDepth(LiteCraftTreeNode node) {
            return nodeDepths.computeIfAbsent(node, ignored -> diveToDeep(node, 0, new DepthRecorder()));
        }

        int procDepth(LiteCraftTreeProc proc) {
            return procDepths.computeIfAbsent(proc, ignored -> LiteCraftTreeProc.diveToDeep(proc, 0,
                new DepthRecorder()));
        }
    }

}
