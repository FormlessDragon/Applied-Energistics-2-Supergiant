package ae2.tile.misc;

import ae2.api.config.CondenserOutput;
import ae2.api.stacks.AEItemKey;
import ae2.core.AELog;
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
        if (!Double.isFinite(rawPower) || rawPower < 0) {
            return;
        }
        double storedPower = sanitize(host.getStoredCondenserPower());
        double storageLimit = host.getCondenserStorageLimit();
        if (!Double.isFinite(storageLimit) || storageLimit < 0) {
            return;
        }
        // Clamp before addition so an overflowing positive input cannot erase previously held power.
        storedPower = Math.min(storedPower, storageLimit);
        host.setStoredCondenserPower(storedPower + Math.min(rawPower, storageLimit - storedPower));
        try {
            fillOutputAndNotify(host);
        } finally {
            saveChanges(host);
        }
    }

    public static void fillOutput(CondenserLogicHost host) {
        try {
            fillOutputAndNotify(host);
        } finally {
            saveChanges(host);
        }
    }

    private static void fillOutputAndNotify(CondenserLogicHost host) {
        CondenserOutput mode = host.getCondenserOutput();
        AEItemKey output = getOutputKey(mode);
        if (output == null || mode.requiredPower <= 0) {
            host.setStoredCondenserPower(0);
            return;
        }

        double storedPower = sanitize(host.getStoredCondenserPower());
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

        var receipt = host.commitCondenserOutput(output, acceptedAmount, mode.requiredPower);
        long confirmed = receipt.confirmed();
        if (!receipt.resourceKey().equals(output) || confirmed > acceptedAmount || receipt.unconfirmed() != 0
            || receipt.stillHeld() != acceptedAmount - confirmed) {
            // Implementations must not invoke a capability with an unknown mutation outcome here.
            throw new IllegalStateException("Invalid or unconfirmed condenser conversion receipt: " + receipt);
        }
        if (confirmed > 0) {
            try {
                host.notifyCondenserOutput(output, confirmed);
            } catch (RuntimeException failure) {
                // Committed output and its debit stay owned by the host; a notification is never a transfer retry.
                AELog.warn(failure, "Condenser output committed, but its change notification failed");
            }
        }
    }

    private static void saveChanges(CondenserLogicHost host) {
        try {
            host.saveCondenserChanges();
        } catch (RuntimeException failure) {
            AELog.warn(failure, "Condenser state changed, but its save notification failed");
        }
    }

    private static double sanitize(double power) {
        if (!Double.isFinite(power) || power < 0) {
            return 0;
        }
        return power;
    }
}
