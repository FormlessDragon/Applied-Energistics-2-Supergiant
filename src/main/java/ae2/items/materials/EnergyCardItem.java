package ae2.items.materials;

import com.google.common.base.Preconditions;

public class EnergyCardItem extends UpgradeCardItem {
    private final int energyMultiplier;

    public EnergyCardItem(int energyMultiplier) {
        super();
        Preconditions.checkArgument(energyMultiplier > 0, "energyMultiplier must be > 0");
        this.energyMultiplier = energyMultiplier;
    }

    public int getEnergyMultiplier() {
        return this.energyMultiplier;
    }
}
