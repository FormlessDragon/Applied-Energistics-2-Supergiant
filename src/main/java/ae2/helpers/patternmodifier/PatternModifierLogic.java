package ae2.helpers.patternmodifier;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.definitions.AEItems;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import com.google.common.primitives.Ints;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PatternModifierLogic {
    private PatternModifierLogic() {
    }

    public static ItemStack clearPattern(ItemStack stack) {
        if (PatternDetailsHelper.isEncodedPattern(stack)) {
            return AEItems.BLANK_PATTERN.stack(stack.getCount());
        }
        return stack;
    }

    public static boolean isEncodedPattern(ItemStack stack, World world) {
        return PatternDetailsHelper.decodePattern(stack, world) != null;
    }

    public static ItemStack modifyAmounts(ItemStack stack, World world, int factor, boolean divide) {
        if (factor <= 0) {
            return stack;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, world);
        if (!(details instanceof AEProcessingPattern pattern)) {
            return stack;
        }

        List<GenericStack> inputs = pattern.getSparseInputs();
        List<GenericStack> outputs = pattern.getSparseOutputs();
        if (!canModify(inputs, factor, divide) || !canModify(outputs, factor, divide)) {
            return stack;
        }

        ItemStack newPattern = PatternDetailsHelper.encodeProcessingPattern(
            modifyStacks(inputs, factor, divide),
            modifyStacks(outputs, factor, divide));
        return isValidPattern(newPattern, world) ? newPattern : stack;
    }

    public static ItemStack replace(ItemStack stack, World world, ItemStack replacementTarget, ItemStack replacement) {
        if (replacementTarget.isEmpty()) {
            return stack;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, world);
        AEItemKey targetKey = AEItemKey.of(replacementTarget);
        AEItemKey replacementKey = AEItemKey.of(replacement);
        if (targetKey == null) {
            return stack;
        }

        if (details instanceof AEProcessingPattern pattern) {
            try {
                ItemStack newPattern = PatternDetailsHelper.encodeProcessingPattern(
                    replaceStacks(pattern.getSparseInputs(), targetKey, replacementKey),
                    replaceStacks(pattern.getSparseOutputs(), targetKey, replacementKey));
                return isValidPattern(newPattern, world) ? newPattern : stack;
            } catch (RuntimeException ignored) {
                return stack;
            }
        }

        if (details instanceof AECraftingPattern pattern) {
            ItemStack[] inputs = itemize(replaceStacks(pattern.getSparseInputs(), targetKey, replacementKey));
            try {
                ItemStack newPattern = PatternDetailsHelper.encodeCraftingPattern(
                    pattern.getRecipe(),
                    inputs,
                    itemize(pattern.getSparseOutputs().getFirst()),
                    pattern.canSubstitute(),
                    pattern.canSubstituteFluids());
                return isValidPattern(newPattern, world) ? newPattern : stack;
            } catch (RuntimeException ignored) {
                return stack;
            }
        }

        return stack;
    }

    public static ItemStack setCraftingProperty(ItemStack stack, World world, CraftingProperty property, boolean value) {
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, world);
        if (!(details instanceof AECraftingPattern pattern)) {
            return stack;
        }

        boolean substitute = property == CraftingProperty.SUBSTITUTE ? value : pattern.canSubstitute();
        boolean substituteFluids = property == CraftingProperty.FLUID_SUBSTITUTE ? value : pattern.canSubstituteFluids();
        try {
            ItemStack newPattern = PatternDetailsHelper.encodeCraftingPattern(
                pattern.getRecipe(),
                itemize(pattern.getSparseInputs()),
                itemize(pattern.getSparseOutputs().getFirst()),
                substitute,
                substituteFluids);
            return isValidPattern(newPattern, world) ? newPattern : stack;
        } catch (RuntimeException ignored) {
            return stack;
        }
    }

    private static boolean canModify(List<GenericStack> stacks, int factor, boolean divide) {
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            if (divide) {
                if (stack.amount() % factor != 0) {
                    return false;
                }
            } else {
                if (stack.amount() > PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT / factor) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<GenericStack> modifyStacks(List<GenericStack> stacks, int factor, boolean divide) {
        List<GenericStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null) {
                result.add(null);
            } else {
                long amount = divide ? stack.amount() / factor : stack.amount() * factor;
                result.add(new GenericStack(stack.what(), amount));
            }
        }
        return result;
    }

    private static List<GenericStack> replaceStacks(List<GenericStack> stacks, AEKey target,
                                                    @Nullable AEKey replacement) {
        List<GenericStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null) {
                result.add(null);
            } else if (stack.what().equals(target)) {
                result.add(replacement == null ? null : new GenericStack(replacement, stack.amount()));
            } else {
                result.add(stack);
            }
        }
        return result;
    }

    private static ItemStack[] itemize(List<GenericStack> stacks) {
        ItemStack[] items = new ItemStack[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            items[i] = itemize(stacks.get(i));
        }
        return items;
    }

    private static ItemStack itemize(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack(Ints.saturatedCast(stack.amount()));
        }
        return ItemStack.EMPTY;
    }

    private static boolean isValidPattern(ItemStack stack, World world) {
        return PatternDetailsHelper.decodePattern(stack, world) != null;
    }

    public enum CraftingProperty {
        SUBSTITUTE,
        FLUID_SUBSTITUTE
    }
}
