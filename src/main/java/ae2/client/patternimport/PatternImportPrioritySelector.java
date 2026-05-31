package ae2.client.patternimport;

import ae2.api.client.PatternImportPriority;
import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.util.GenericContainerHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class PatternImportPrioritySelector {
    private PatternImportPrioritySelector() {
    }

    public static GenericStack selectIngredient(List<GenericStack> possibleIngredients,
                                                PatternImportPriorityContext context,
                                                boolean preferFilledBucket) {
        if (preferFilledBucket) {
            for (GenericStack possibleIngredient : possibleIngredients) {
                if (isFilledBucketIngredient(possibleIngredient)) {
                    return possibleIngredient;
                }
            }
        }

        for (PatternImportPriority priority : PatternImportPriorityOrder.getOrderedPriorities()) {
            for (GenericStack possibleIngredient : possibleIngredients) {
                if (priority.matches(context, possibleIngredient)) {
                    return possibleIngredient;
                }
            }
        }

        if (possibleIngredients.isEmpty()) {
            throw new IllegalStateException("Expected at least one ingredient candidate");
        }
        return possibleIngredients.get(0);
    }

    private static boolean isFilledBucketIngredient(GenericStack stack) {
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }

        ItemStack itemStack = itemKey.toStack();
        return GenericContainerHelper.getContainedFluidStack(itemStack) != null
            && (itemStack.getItem() instanceof ItemBucket || itemStack.getItem() == Items.MILK_BUCKET);
    }
}
