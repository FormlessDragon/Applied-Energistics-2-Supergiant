package ae2.parts.encoding;

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
            if (!canApplyAmount(stack.amount(), operation)) {
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

    static boolean canApplyAmount(long amount, Operation operation) {
        if (amount <= 0) {
            return false;
        }
        if (operation.divisor > 1 && amount % operation.divisor != 0) {
            return false;
        }
        if (operation.multiplier > 1 && amount > Long.MAX_VALUE / operation.multiplier) {
            return false;
        }
        return true;
    }

    static long applyAmount(long amount, Operation operation) {
        return amount * operation.multiplier / operation.divisor;
    }

    public enum Operation {
        MULTIPLY_2(2, 1),
        MULTIPLY_3(3, 1),
        MULTIPLY_5(5, 1),
        DIVIDE_2(1, 2),
        DIVIDE_3(1, 3),
        DIVIDE_5(1, 5);

        private final long multiplier;
        private final long divisor;

        Operation(long multiplier, long divisor) {
            this.multiplier = multiplier;
            this.divisor = divisor;
        }
    }
}
