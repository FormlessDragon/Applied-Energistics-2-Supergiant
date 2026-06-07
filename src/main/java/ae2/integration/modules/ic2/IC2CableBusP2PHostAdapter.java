package ae2.integration.modules.ic2;

import ae2.integration.abstraction.IC2P2PTunnel;
import ic2.api.energy.prefab.BasicSinkSource;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyEmitter;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

public final class IC2CableBusP2PHostAdapter extends BasicSinkSource {
    private static final int CAPACITY = 2048;
    private static final int SINK_TIER = 4;
    private static final int SOURCE_TIER = 4;

    private final Map<EnumFacing, IC2P2PTunnel> tunnels;

    public IC2CableBusP2PHostAdapter(World world, BlockPos pos, Map<EnumFacing, IC2P2PTunnel> tunnels) {
        super(world, pos, CAPACITY, SINK_TIER, SOURCE_TIER);
        this.tunnels = tunnels;
    }

    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        IC2P2PTunnel tunnel = getTunnel(side);
        return tunnel != null && !tunnel.isIc2Output();
    }

    @Override
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing side) {
        IC2P2PTunnel tunnel = getTunnel(side);
        return tunnel != null && tunnel.isIc2Output();
    }

    @Override
    public double getDemandedEnergy() {
        for (IC2P2PTunnel tunnel : this.tunnels.values()) {
            if (!tunnel.isIc2Output()) {
                double demand = tunnel.getIc2DemandedEnergy();
                if (demand > 0) {
                    return demand;
                }
            }
        }
        return 0;
    }

    @Override
    public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
        IC2P2PTunnel tunnel = getInputTunnel(directionFrom);
        if (tunnel == null && directionFrom != null) {
            tunnel = getInputTunnel(directionFrom.getOpposite());
        }
        if (tunnel == null) {
            tunnel = getAnyDemandingInputTunnel();
        }

        return tunnel == null ? amount : tunnel.injectIc2Energy(amount, voltage);
    }

    @Override
    public double getOfferedEnergy() {
        double offered = 0;
        for (IC2P2PTunnel tunnel : this.tunnels.values()) {
            if (tunnel.isIc2Output()) {
                offered += tunnel.getIc2OfferedEnergy();
            }
        }
        return offered;
    }

    @Override
    public void drawEnergy(double amount) {
        double remaining = amount;
        for (IC2P2PTunnel tunnel : this.tunnels.values()) {
            if (!tunnel.isIc2Output()) {
                continue;
            }

            double offered = tunnel.getIc2OfferedEnergy();
            if (offered <= 0) {
                continue;
            }

            double drawn = Math.min(remaining, offered);
            tunnel.drawIc2Energy(drawn);
            remaining -= drawn;
            if (remaining <= 0) {
                return;
            }
        }
    }

    private IC2P2PTunnel getTunnel(EnumFacing side) {
        return side == null ? null : this.tunnels.get(side);
    }

    private IC2P2PTunnel getInputTunnel(EnumFacing side) {
        IC2P2PTunnel tunnel = getTunnel(side);
        return tunnel != null && !tunnel.isIc2Output() ? tunnel : null;
    }

    private IC2P2PTunnel getAnyDemandingInputTunnel() {
        for (IC2P2PTunnel tunnel : this.tunnels.values()) {
            if (!tunnel.isIc2Output() && tunnel.getIc2DemandedEnergy() > 0) {
                return tunnel;
            }
        }
        return null;
    }
}
