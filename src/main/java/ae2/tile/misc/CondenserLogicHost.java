package ae2.tile.misc;

import ae2.api.config.CondenserOutput;
import ae2.api.stacks.AEItemKey;

import java.util.Objects;

public interface CondenserLogicHost {

    CondenserOutput getCondenserOutput();

    double getStoredCondenserPower();

    void setStoredCondenserPower(double storedPower);

    double getCondenserStorageLimit();

    long getAvailableCondenserOutputSpace(AEItemKey output);

    /**
     * Commit conversion into host-owned storage and debit only the actual accepted output's power.
     * Recheck capacity and power without invoking external callbacks between the two mutations.
     * Return the confirmed amount; this operation must not notify listeners or save providers.
     */
    OutputCommit commitCondenserOutput(AEItemKey output, long amount, double powerPerOutput);

    /** Owner-thread receipt. Generation tokens are identities of this live source and its native target storage. */
    record OutputCommit(AEItemKey resourceKey, Object sourceGeneration, Object targetGeneration, long batchId,
                        long confirmed, long stillHeld, long unconfirmed) {
        public OutputCommit {
            Objects.requireNonNull(resourceKey, "resourceKey");
            Objects.requireNonNull(sourceGeneration, "sourceGeneration");
            Objects.requireNonNull(targetGeneration, "targetGeneration");
            if (confirmed < 0 || stillHeld < 0 || unconfirmed < 0) {
                throw new IllegalArgumentException("Negative condenser receipt");
            }
        }
    }

    /** Called after both sides of the conversion have committed. Never retry a committed conversion on failure. */
    void notifyCondenserOutput(AEItemKey output, long amount);

    void saveCondenserChanges();
}
