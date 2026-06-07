package ae2.parts.p2p;

import ae2.api.config.PowerUnit;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.core.AppEng;
import ae2.integration.Integrations;
import ae2.integration.abstraction.IC2P2PHost;
import ae2.integration.abstraction.IC2P2PTunnel;
import ae2.items.parts.PartModels;
import ae2.util.Platform;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class IC2P2PTunnelPart extends P2PTunnelPart<IC2P2PTunnelPart> implements IC2P2PTunnel {
    private static final String TAG_BUFFERED_ENERGY_1 = "bufferedEnergy1";
    private static final String TAG_BUFFERED_ENERGY_2 = "bufferedEnergy2";
    private static final String TAG_BUFFERED_VOLTAGE_1 = "bufferedVoltage1";
    private static final String TAG_BUFFERED_VOLTAGE_2 = "bufferedVoltage2";

    private static final double BUFFER_EPSILON = 0.001;
    private static final double BUFFER_SLOT_EPSILON = 0.01;
    private static final double DEMAND_AMOUNT = 2048;
    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_ic2"));

    private double bufferedEnergy1;
    private double bufferedEnergy2;
    private double bufferedVoltage1;
    private double bufferedVoltage2;
    private IC2P2PHost ic2Host;

    public IC2P2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.bufferedEnergy1 = data.getDouble(TAG_BUFFERED_ENERGY_1);
        this.bufferedEnergy2 = data.getDouble(TAG_BUFFERED_ENERGY_2);
        this.bufferedVoltage1 = data.getDouble(TAG_BUFFERED_VOLTAGE_1);
        this.bufferedVoltage2 = data.getDouble(TAG_BUFFERED_VOLTAGE_2);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setDouble(TAG_BUFFERED_ENERGY_1, this.bufferedEnergy1);
        data.setDouble(TAG_BUFFERED_ENERGY_2, this.bufferedEnergy2);
        data.setDouble(TAG_BUFFERED_VOLTAGE_1, this.bufferedVoltage1);
        data.setDouble(TAG_BUFFERED_VOLTAGE_2, this.bufferedVoltage2);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        updateIc2Host();
        if (this.ic2Host != null) {
            this.ic2Host.onLoad();
        }
    }

    @Override
    public void removeFromWorld() {
        invalidateIc2Host();
        super.removeFromWorld();
    }

    @Override
    public void onTunnelConfigChange() {
        updateIc2Host();
        this.getHost().partChanged();
    }

    @Override
    public void onTunnelNetworkChange() {
        updateIc2Host();
        this.getHost().notifyNeighbors();
        this.getHost().markForUpdate();
    }

    @Override
    public boolean isIc2Output() {
        return this.isOutput();
    }

    @Override
    public EnumFacing getIc2Facing() {
        return this.getSide();
    }

    @Override
    public double getIc2DemandedEnergy() {
        if (this.isOutput()) {
            return 0;
        }

        for (IC2P2PTunnelPart output : this.getOutputs()) {
            if (output.bufferedEnergy1 <= BUFFER_EPSILON || output.bufferedEnergy2 <= BUFFER_EPSILON) {
                return DEMAND_AMOUNT;
            }
        }
        return 0;
    }

    @Override
    public double injectIc2Energy(double amount, double voltage) {
        List<IC2P2PTunnelPart> outputs = this.getOutputs();
        if (outputs.isEmpty()) {
            return amount;
        }

        IC2P2PTunnelPart target = pickOutput(outputs);
        if (target == null) {
            return amount;
        }

        if (target.bufferedEnergy1 <= BUFFER_EPSILON) {
            this.deductEnergyCost(PowerUnit.EU, amount);
            target.bufferedEnergy1 = amount;
            target.bufferedVoltage1 = voltage;
            target.updateIc2Host();
            return 0;
        }

        if (target.bufferedEnergy2 <= BUFFER_EPSILON) {
            this.deductEnergyCost(PowerUnit.EU, amount);
            target.bufferedEnergy2 = amount;
            target.bufferedVoltage2 = voltage;
            target.updateIc2Host();
            return 0;
        }

        return amount;
    }

    @Override
    public double getIc2OfferedEnergy() {
        return this.isOutput() ? this.bufferedEnergy1 : 0;
    }

    @Override
    public void drawIc2Energy(double amount) {
        this.bufferedEnergy1 -= amount;
        if (this.bufferedEnergy1 < BUFFER_EPSILON) {
            this.bufferedEnergy1 = this.bufferedEnergy2;
            this.bufferedEnergy2 = 0;
            this.bufferedVoltage1 = this.bufferedVoltage2;
            this.bufferedVoltage2 = 0;
        }
        updateIc2Host();
    }

    private void updateIc2Host() {
        if (this.getLevel() == null || this.getTileEntity() == null || this.getSide() == null) {
            return;
        }

        if (this.ic2Host == null) {
            this.ic2Host = Integrations.ic2().createP2PHost(this.getLevel(), this.getTileEntity().getPos(), this);
        }

        this.ic2Host.update();
        Platform.notifyBlocksOfNeighbors(this.getLevel(), this.getTileEntity().getPos().offset(this.getSide()));
    }

    private void invalidateIc2Host() {
        if (this.ic2Host != null) {
            this.ic2Host.invalidate();
            this.ic2Host = null;
        }
    }

    private static IC2P2PTunnelPart pickOutput(List<IC2P2PTunnelPart> outputs) {
        List<IC2P2PTunnelPart> options = new ArrayList<>();
        for (IC2P2PTunnelPart output : outputs) {
            if (output.bufferedEnergy1 <= BUFFER_SLOT_EPSILON) {
                options.add(output);
            }
        }

        if (options.isEmpty()) {
            for (IC2P2PTunnelPart output : outputs) {
                if (output.bufferedEnergy2 <= BUFFER_SLOT_EPSILON) {
                    options.add(output);
                }
            }
        }

        if (options.isEmpty()) {
            options.addAll(outputs);
        }

        return options.isEmpty() ? null : options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
