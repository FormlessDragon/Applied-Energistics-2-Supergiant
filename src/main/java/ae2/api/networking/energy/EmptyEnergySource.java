package ae2.api.networking.energy;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;

final class EmptyEnergySource implements IEnergySource {
    static final IEnergySource INSTANCE = new EmptyEnergySource();

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        return 0;
    }
}
