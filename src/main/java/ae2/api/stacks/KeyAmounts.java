package ae2.api.stacks;

import java.math.BigInteger;

/** Checked arithmetic that preserves the material identity when a quantity overflows. */
public final class KeyAmounts {
    private KeyAmounts() {
    }

    public static long add(AEKey key, long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new AmountOverflowException(key, BigInteger.valueOf(left).add(BigInteger.valueOf(right)));
        }
    }

    public static long multiply(AEKey key, long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new AmountOverflowException(key, BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)));
        }
    }

    public static long subtract(AEKey key, long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new AmountOverflowException(key, BigInteger.valueOf(left).subtract(BigInteger.valueOf(right)));
        }
    }
}
