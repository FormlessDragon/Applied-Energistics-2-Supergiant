package ae2.parts.encoding;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.GenericStack;

import java.util.List;

public final class ProcessingPatternAmountHelper {
    private ProcessingPatternAmountHelper() {
    }

    public static boolean canApply(List<GenericStack> stacks, Operation operation) {
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            if (!canApplyAmount(stack.amount(), stack.what().getAmountPerUnit(), operation)) {
                return false;
            }
        }
        return true;
    }

    public static GenericStack apply(GenericStack stack, Operation operation) {
        if (stack == null) {
            return null;
        }
        return new GenericStack(stack.what(), applyAmount(stack.amount(), operation));
    }

    static boolean canApplyAmount(long amount, long amountPerUnit, Operation operation) {
        if (amount <= 0) {
            return false;
        }
        if (operation.divisor > 1 && amount % operation.divisor != 0) {
            return false;
        }
        if (operation.multiplier > 1 && amount > Long.MAX_VALUE / operation.multiplier) {
            return false;
        }
        long newAmount = applyAmount(amount, operation);
        if (newAmount <= 0) {
            return false;
        }
        return newAmount <= PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT;
    }

    static long applyAmount(long amount, Operation operation) {
        return amount * operation.multiplier / operation.divisor + operation.delta;
    }

    public enum Operation {
        MULTIPLY_2(2, 1, 0),
        MULTIPLY_3(3, 1, 0),
        MULTIPLY_5(5, 1, 0),
        DIVIDE_2(1, 2, 0),
        DIVIDE_3(1, 3, 0),
        DIVIDE_5(1, 5, 0);

        private final long multiplier;
        private final long divisor;
        private final long delta;

        Operation(long multiplier, long divisor, long delta) {
            this.multiplier = multiplier;
            this.divisor = divisor;
            this.delta = delta;
        }
    }
}
