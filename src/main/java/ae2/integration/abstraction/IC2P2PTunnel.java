package ae2.integration.abstraction;

import net.minecraft.util.EnumFacing;

public interface IC2P2PTunnel {

    boolean isIc2Output();

    EnumFacing getIc2Facing();

    double getIc2DemandedEnergy();

    double injectIc2Energy(double amount, double voltage);

    double getIc2OfferedEnergy();

    void drawIc2Energy(double amount);
}
