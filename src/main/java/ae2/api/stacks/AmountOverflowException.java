package ae2.api.stacks;

import java.math.BigInteger;

/** An amount that cannot be represented without losing resources. */
public final class AmountOverflowException extends ArithmeticException {
    private final AEKey key;
    private final BigInteger amount;

    public AmountOverflowException(AEKey key, BigInteger amount) {
        super("Amount for " + key + " exceeds the supported range: " + amount);
        this.key = key;
        this.amount = amount;
    }

    public AEKey getKey() {
        return key;
    }

    public BigInteger getAmount() {
        return amount;
    }
}
