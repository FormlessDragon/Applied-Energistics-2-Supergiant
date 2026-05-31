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

    static List<GenericStack> condenseStacks(List<GenericStack> sparseStacks) {
        Object2LongLinkedOpenHashMap<AEKey> totals = new Object2LongLinkedOpenHashMap<>();
        totals.defaultReturnValue(0);
        for (var stack : sparseStacks) {
            if (stack != null) {
                totals.addTo(stack.what(), stack.amount());
            }
        }

        List<GenericStack> result = new ObjectArrayList<>(totals.size());
        for (Object2LongMap.Entry<AEKey> entry : totals.object2LongEntrySet()) {
            result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }
}
