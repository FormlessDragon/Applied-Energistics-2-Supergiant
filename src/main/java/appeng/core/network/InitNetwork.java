package appeng.core.network;

import appeng.core.Tags;
import appeng.core.network.bidirectional.ConfigValuePacket;
import appeng.core.network.clientbound.AssemblerAnimationPacket;
import appeng.core.network.clientbound.BlockTransitionEffectPacket;
import appeng.core.network.clientbound.ClearCompassCachePacket;
import appeng.core.network.clientbound.ClearPatternAccessTerminalPacket;
import appeng.core.network.clientbound.CompassResponsePacket;
import appeng.core.network.clientbound.CraftConfirmPlanPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.core.network.clientbound.CraftingSupplierLocationsPacket;
import appeng.core.network.clientbound.CraftingTreeDataPacket;
import appeng.core.network.clientbound.ExportedGridContent;
import appeng.core.network.clientbound.GuiDataSyncPacket;
import appeng.core.network.clientbound.ItemTransitionEffectPacket;
import appeng.core.network.clientbound.LightningPacket;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import appeng.core.network.clientbound.MatterCannonPacket;
import appeng.core.network.clientbound.MockExplosionPacket;
import appeng.core.network.clientbound.NetworkStatusPacket;
import appeng.core.network.clientbound.OpenGuiPacket;
import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import appeng.core.network.clientbound.RestorePreviousGuiPacket;
import appeng.core.network.clientbound.SetLinkStatusPacket;
import appeng.core.network.serverbound.CableBusPartLeftClickPacket;
import appeng.core.network.serverbound.ColorApplicatorSelectColorPacket;
import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.core.network.serverbound.ConfirmAutoCraftPacket;
import appeng.core.network.serverbound.CycleWirelessTerminalPacket;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.core.network.serverbound.GuiActionPacket;
import appeng.core.network.serverbound.HeiIngredientActionPacket;
import appeng.core.network.serverbound.HotkeyPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.core.network.serverbound.MEInteractionPacket;
import appeng.core.network.serverbound.MouseWheelPacket;
import appeng.core.network.serverbound.QuickMovePatternPacket;
import appeng.core.network.serverbound.RequestClosestMeteoritePacket;
import appeng.core.network.serverbound.SelectKeyTypePacket;
import appeng.core.network.serverbound.SelectWirelessTerminalPacket;
import appeng.core.network.serverbound.SwapSlotsPacket;
import appeng.core.network.serverbound.SwitchCraftingTreePacket;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.core.network.serverbound.TraceCraftingSupplierPacket;
import appeng.core.network.serverbound.UpdateHoldingCtrlPacket;
import appeng.core.network.serverbound.WirelessTerminalPickBlockPacket;
import appeng.core.network.serverbound.WirelessTerminalSettingsPacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class InitNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private static int nextPacketId;
    private static boolean initialized;

    private InitNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerClientbound(AppEngPayloadHandler.Client.class, CraftConfirmPlanPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CraftingJobStatusPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CraftingSupplierLocationsPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CraftingStatusPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, ClearCompassCachePacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CompassResponsePacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, ExportedGridContent.class);
        registerClientbound(AppEngPayloadHandler.Client.class, ClearPatternAccessTerminalPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, AssemblerAnimationPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, BlockTransitionEffectPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, MockExplosionPacket.class);
        registerClientbound(GuiDataSyncPacket.Handler.class, GuiDataSyncPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, ItemTransitionEffectPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, LightningPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, MatterCannonPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, MEInventoryUpdatePacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, NetworkStatusPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, OpenGuiPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, PatternAccessTerminalPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, RestorePreviousGuiPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, SetLinkStatusPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CraftingTreeDataPacket.class);
        CHANNEL.registerMessage(ConfigValuePacket.ClientHandler.class, ConfigValuePacket.class, nextPacketId++, Side.CLIENT);
        registerServerbound(AppEngPayloadHandler.Server.class, ColorApplicatorSelectColorPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, CableBusPartLeftClickPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, ConfigButtonPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, ConfirmAutoCraftPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, CycleWirelessTerminalPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, FillCraftingGridFromRecipePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, GuiActionPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, HeiIngredientActionPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, RequestClosestMeteoritePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, HotkeyPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, InventoryActionPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, MEInteractionPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, MouseWheelPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, QuickMovePatternPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SelectKeyTypePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SelectWirelessTerminalPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SwapSlotsPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SwitchGuisPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, TraceCraftingSupplierPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, UpdateHoldingCtrlPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, WirelessTerminalPickBlockPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, WirelessTerminalSettingsPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SwitchCraftingTreePacket.class);
        CHANNEL.registerMessage(ConfigValuePacket.ServerHandler.class, ConfigValuePacket.class, nextPacketId++, Side.SERVER);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends ClientboundPacket> void registerClientbound(
        Class<? extends IMessageHandler> handler, Class<T> packet) {
        CHANNEL.registerMessage((Class) handler, packet, nextPacketId++, Side.CLIENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends ServerboundPacket> void registerServerbound(
        Class<? extends IMessageHandler> handler, Class<T> packet) {
        CHANNEL.registerMessage((Class) handler, packet, nextPacketId++, Side.SERVER);
    }

    public static void sendToAllNearExcept(EntityPlayer excluded, double x, double y, double z, double distance,
                                           World world, ClientboundPacket packet) {
        if (world == null || packet == null || world.isRemote) {
            return;
        }

        double distanceSq = distance * distance;
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP) || player == excluded) {
                continue;
            }
            double dx = player.posX - x;
            double dy = player.posY - y;
            double dz = player.posZ - z;
            if (dx * dx + dy * dy + dz * dz <= distanceSq) {
                CHANNEL.sendTo(packet, (EntityPlayerMP) player);
            }
        }
    }

    public static void sendToClient(EntityPlayerMP player, ClientboundPacket packet) {
        CHANNEL.sendTo(packet, player);
    }

    public static void sendToServer(ServerboundPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
