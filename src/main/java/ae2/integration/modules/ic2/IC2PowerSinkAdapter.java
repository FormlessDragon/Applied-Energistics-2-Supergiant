package ae2.integration.modules.ic2;

import ae2.api.config.Actionable;
import ae2.api.config.PowerUnit;
import ae2.integration.abstraction.IC2PowerSink;
import ae2.tile.powersink.IExternalPowerSink;
import ic2.api.energy.prefab.BasicSink;
import ic2.api.energy.tile.IEnergyEmitter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.util.EnumSet;
import java.util.Set;

public final class IC2PowerSinkAdapter extends BasicSink implements IC2PowerSink {

    private final IExternalPowerSink powerSink;
    private final Set<EnumFacing> validFaces = EnumSet.allOf(EnumFacing.class);

    public IC2PowerSinkAdapter(TileEntity tileEntity, IExternalPowerSink powerSink) {
        super(tileEntity, 0, Integer.MAX_VALUE);
        this.powerSink = powerSink;
    }

    @Override
    public void invalidate() {
        super.onChunkUnload();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public double getDemandedEnergy() {
        return this.powerSink.getExternalPowerDemand(PowerUnit.EU, Double.MAX_VALUE);
    }

    @Override
    public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
        return this.powerSink.injectExternalPower(PowerUnit.EU, amount, Actionable.MODULATE);
    }

    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        return this.validFaces.contains(side);
    }

    @Override
    public void setValidFaces(Set<EnumFacing> faces) {
        this.validFaces.clear();
        this.validFaces.addAll(faces);
    }
}
