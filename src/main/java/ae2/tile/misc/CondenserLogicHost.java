package ae2.tile.misc;

import ae2.api.config.CondenserOutput;
import ae2.api.stacks.AEItemKey;

public interface CondenserLogicHost {

    CondenserOutput getCondenserOutput();

    double getStoredCondenserPower();

    void setStoredCondenserPower(double storedPower);

    double getCondenserStorageLimit();

    long getAvailableCondenserOutputSpace(AEItemKey output);

    void addCondenserOutput(AEItemKey output, long amount);

    void saveCondenserChanges();
}
