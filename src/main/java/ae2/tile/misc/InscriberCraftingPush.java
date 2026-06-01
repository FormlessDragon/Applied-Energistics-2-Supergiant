package ae2.tile.misc;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class InscriberCraftingPush {
    private InscriberCraftingPush() {
    }

    @Nullable
    static Plan plan(AEProcessingPattern pattern, KeyCounter[] inputs, State state, int parallelLimit,
                     int maxMultiplier) {
        if (maxMultiplier <= 0 || state.smash() || pattern.getSparseInputs().size() < 3
            || pattern.getOutputs().size() != 1) {
            return null;
        }

        ItemStack patternTop = toSingleItemStack(pattern.getSparseInputs().get(0));
        ItemStack patternMiddle = toSingleItemStack(pattern.getSparseInputs().get(1));
        ItemStack patternBottom = toSingleItemStack(pattern.getSparseInputs().get(2));
        if (patternMiddle.isEmpty()) {
            return null;
        }

        ItemStack targetTop = state.top().isEmpty() ? patternTop : state.top();
        ItemStack targetBottom = state.bottom().isEmpty() ? patternBottom : state.bottom();
        InscriberRecipe recipe = InscriberRecipes.findRecipe(patternMiddle, targetTop, targetBottom, true);
        if (recipe == null || !matchesOutput(pattern, recipe)) {
            return null;
        }

        boolean consumeOptional = recipe.getProcessType() == InscriberProcessType.PRESS;
        SlotPlan top = optionalSlotPlan(state.top(), patternTop, consumeOptional);
        SlotPlan middle = consumedSlotPlan(state.middle(), patternMiddle);
        SlotPlan bottom = optionalSlotPlan(state.bottom(), patternBottom, consumeOptional);
        if (top == null || middle == null || bottom == null) {
            return null;
        }
        if (!matchesInputs(top, middle, bottom, inputs)) {
            return null;
        }

        int maxRuns = Math.min(maxMultiplier, parallelLimit);
        maxRuns = Math.min(maxRuns, middle.maxRuns(state.inputCapacity()));
        if (consumeOptional) {
            maxRuns = Math.min(maxRuns, top.maxRuns(state.inputCapacity()));
            maxRuns = Math.min(maxRuns, bottom.maxRuns(state.inputCapacity()));
        } else if (top.installsTool() || bottom.installsTool()) {
            maxRuns = Math.min(maxRuns, 1);
        }
        maxRuns = Math.min(maxRuns, outputRuns(state.output(), recipe.getResultItem()));
        if (maxRuns <= 0) {
            return null;
        }

        return new Plan(recipe, top, middle, bottom, maxRuns);
    }

    private static boolean matchesOutput(AEProcessingPattern pattern, InscriberRecipe recipe) {
        GenericStack output = pattern.getOutputs().getFirst();
        return output.what() instanceof AEItemKey itemKey
            && output.amount() == recipe.getResultItem().getCount()
            && ItemStack.areItemsEqual(itemKey.toStack(), recipe.getResultItem())
            && ItemStack.areItemStackTagsEqual(itemKey.toStack(), recipe.getResultItem());
    }

    @Nullable
    private static SlotPlan consumedSlotPlan(ItemStack current, ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            return null;
        }
        if (!current.isEmpty() && !canStack(current, patternStack)) {
            return null;
        }
        return new SlotPlan(patternStack, true, true, current.isEmpty() ? 0 : current.getCount());
    }

    @Nullable
    private static SlotPlan optionalSlotPlan(ItemStack current, ItemStack patternStack, boolean consumeOptional) {
        if (patternStack.isEmpty()) {
            return current.isEmpty() ? SlotPlan.empty() : new SlotPlan(current, false, false, current.getCount());
        }
        if (!current.isEmpty() && !canStack(current, patternStack)) {
            return null;
        }
        return new SlotPlan(patternStack, consumeOptional, consumeOptional || current.isEmpty(),
            current.isEmpty() ? 0 : current.getCount());
    }

    private static int outputRuns(ItemStack currentOutput, ItemStack recipeOutput) {
        if (recipeOutput.isEmpty()) {
            return 0;
        }
        if (currentOutput.isEmpty()) {
            return recipeOutput.getMaxStackSize() / recipeOutput.getCount();
        }
        if (!canStack(currentOutput, recipeOutput)) {
            return 0;
        }
        return (currentOutput.getMaxStackSize() - currentOutput.getCount()) / recipeOutput.getCount();
    }

    private static boolean matchesInputs(SlotPlan top, SlotPlan middle, SlotPlan bottom, KeyCounter[] inputs) {
        KeyCounter expected = new KeyCounter();
        addExpectedInput(expected, top);
        addExpectedInput(expected, middle);
        addExpectedInput(expected, bottom);

        KeyCounter actual = new KeyCounter();
        for (KeyCounter input : inputs) {
            actual.addAll(input);
        }
        for (Object2LongMap.Entry<AEKey> entry : expected) {
            if (actual.get(entry.getKey()) < entry.getLongValue()) {
                return false;
            }
            actual.remove(entry.getKey(), entry.getLongValue());
        }
        return actual.isEmpty();
    }

    private static void addExpectedInput(KeyCounter expected, SlotPlan slotPlan) {
        if (!slotPlan.insertRequired() || slotPlan.stack.isEmpty()) {
            return;
        }
        AEItemKey key = AEItemKey.of(slotPlan.stack);
        if (key != null) {
            expected.add(key, 1);
        }
    }

    private static boolean canStack(ItemStack current, ItemStack incoming) {
        return ItemStack.areItemsEqual(current, incoming) && ItemStack.areItemStackTagsEqual(current, incoming);
    }

    private static ItemStack toSingleItemStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (!(stack.what() instanceof AEItemKey itemKey) || stack.amount() != 1) {
            return ItemStack.EMPTY;
        }
        return itemKey.toStack();
    }

    record State(ItemStack top, ItemStack middle, ItemStack bottom, ItemStack output, int inputCapacity,
                 boolean smash) {
    }

    record Plan(InscriberRecipe recipe, SlotPlan top, SlotPlan middle, SlotPlan bottom, int maxMultiplier) {
    }

    record SlotPlan(ItemStack stack, boolean consumed, boolean insertRequired, int currentAmount) {
        static SlotPlan empty() {
            return new SlotPlan(ItemStack.EMPTY, false, false, 0);
        }

        boolean installsTool() {
            return this.insertRequired && !this.consumed && !this.stack.isEmpty();
        }

        int maxRuns(int capacity) {
            if (this.stack.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            return this.consumed ? Math.max(0, capacity - this.currentAmount) : 1;
        }

        ItemStack stackForRuns(int runs) {
            if (this.stack.isEmpty() || !this.insertRequired) {
                return ItemStack.EMPTY;
            }
            ItemStack result = this.stack.copy();
            result.setCount(this.consumed ? runs : 1);
            return result;
        }
    }
}
