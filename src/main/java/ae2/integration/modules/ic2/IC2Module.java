package ae2.integration.modules.ic2;

import ae2.api.features.P2PTunnelAttunement;
import ae2.api.ids.AEPartIds;
import ae2.integration.abstraction.IIC2;
import ae2.integration.abstraction.IC2P2PHost;
import ae2.integration.abstraction.IC2P2PTunnel;
import ae2.integration.abstraction.IC2PowerSink;
import ae2.tile.powersink.IExternalPowerSink;
import ic2.api.item.IC2Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

@SuppressWarnings("unused")
public final class IC2Module implements IIC2 {
    private static final String[] IC2_CABLE_TYPES = {"copper", "glass", "gold", "iron", "tin", "detector", "splitter"};
    private final IC2P2PHostRegistry p2pHosts = new IC2P2PHostRegistry();

    public IC2Module() {
        requireIc2Api();
    }

    @Override
    public IC2PowerSink createPowerSink(TileEntity tileEntity, IExternalPowerSink externalSink) {
        return new IC2PowerSinkAdapter(tileEntity, externalSink);
    }

    @Override
    public IC2P2PHost createP2PHost(World world, BlockPos pos, IC2P2PTunnel tunnel) {
        return new IC2P2PHostAdapter(this.p2pHosts, world, pos, tunnel);
    }

    @Override
    public void registerP2PAttunements() {
        String euTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.IC2_P2P_TUNNEL);
        for (String cableType : IC2_CABLE_TYPES) {
            ItemStack cable = IC2Items.getItem("cable", "type:" + cableType);
            if (!cable.isEmpty()) {
                OreDictionary.registerOre(euTunnelTag, cable);
            }
        }
    }

    private static void requireIc2Api() {
        require("ic2.api.energy.prefab.BasicSink");
        require("ic2.api.energy.prefab.BasicSinkSource");
        require("ic2.api.energy.tile.IEnergyEmitter");
        require("ic2.api.energy.tile.IEnergyAcceptor");
        require("ic2.api.item.IC2Items");
    }

    private static void require(String className) {
        try {
            Class.forName(className, false, IC2Module.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing IC2 API class: " + className, e);
        }
    }
}
