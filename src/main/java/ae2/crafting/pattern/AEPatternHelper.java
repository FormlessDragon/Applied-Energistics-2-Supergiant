package ae2.crafting.pattern;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

final class AEPatternHelper {
    private AEPatternHelper() {
    }

    static ObjectArrayList<GenericStack> condenseStacks(List<GenericStack> sparseStacks) {
        Object2LongLinkedOpenHashMap<AEKey> totals = new Object2LongLinkedOpenHashMap<>();
        totals.defaultReturnValue(0);
        for (int i = 0; i < sparseStacks.size(); i++) {
            var stack = sparseStacks.get(i);
            if (stack != null) {
                long amount = stack.amount();
                long previous = totals.addTo(stack.what(), amount);
                if (amount > 0 && previous > Long.MAX_VALUE - amount) {
                    throw new IllegalArgumentException("Combined pattern amount exceeds long range.");
                }
            }
        }

        ObjectArrayList<GenericStack> result = new ObjectArrayList<>(totals.size());
        for (Object2LongMap.Entry<AEKey> entry : totals.object2LongEntrySet()) {
            result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }
}
