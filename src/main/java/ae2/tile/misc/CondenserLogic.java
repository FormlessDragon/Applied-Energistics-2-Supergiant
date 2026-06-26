package ae2.tile.misc;

import ae2.api.config.CondenserOutput;
import ae2.api.stacks.AEItemKey;
import ae2.core.definitions.AEItems;

public final class CondenserLogic {
    private CondenserLogic() {
    }

    public static AEItemKey getOutputKey(CondenserOutput mode) {
        return switch (mode) {
            case MATTER_BALLS -> AEItemKey.of(AEItems.MATTER_BALL.item());
            case SINGULARITY -> AEItemKey.of(AEItems.SINGULARITY.item());
            case TRASH -> null;
        };
    }

    public static void addPower(CondenserLogicHost host, double rawPower) {
        double storedPower = sanitize(host.getStoredCondenserPower() + rawPower);
        double storageLimit = host.getCondenserStorageLimit();
        if (Double.isFinite(storageLimit) && storageLimit >= 0) {
            storedPower = Math.clamp(storedPower, 0.0, storageLimit);
        }
        host.setStoredCondenserPower(storedPower);
        fillOutput(host);
        host.saveCondenserChanges();
    }

    public static void fillOutput(CondenserLogicHost host) {
        CondenserOutput mode = host.getCondenserOutput();
        AEItemKey output = getOutputKey(mode);
        if (output == null || mode.requiredPower <= 0) {
            host.setStoredCondenserPower(0);
            return;
        }

        double storedPower = host.getStoredCondenserPower();
        long outputAmount = storedPower >= Long.MAX_VALUE * (double) mode.requiredPower
            ? Long.MAX_VALUE
            : (long) (storedPower / mode.requiredPower);
        if (outputAmount <= 0) {
            return;
        }

        long acceptedAmount = Math.min(outputAmount, host.getAvailableCondenserOutputSpace(output));
        if (acceptedAmount <= 0) {
            return;
        }

        host.addCondenserOutput(output, acceptedAmount);
        host.setStoredCondenserPower(sanitize(storedPower - acceptedAmount * (double) mode.requiredPower));
    }

    private static double sanitize(double power) {
        if (!Double.isFinite(power) || power < 0) {
            return 0;
        }
        return power;
    }
}
