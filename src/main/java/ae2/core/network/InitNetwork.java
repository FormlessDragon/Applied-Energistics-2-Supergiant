package ae2.core.network;

import ae2.core.Tags;
import ae2.core.network.bidirectional.ConfigValuePacket;
import ae2.core.network.clientbound.AdvancedMemoryCardP2PSnapshotPacket;
import ae2.core.network.clientbound.AssemblerAnimationPacket;
import ae2.core.network.clientbound.BlockTransitionEffectPacket;
import ae2.core.network.clientbound.ClearCompassCachePacket;
import ae2.core.network.clientbound.ClearPatternAccessTerminalPacket;
import ae2.core.network.clientbound.CompassResponsePacket;
import ae2.core.network.clientbound.CraftConfirmPlanPacket;
import ae2.core.network.clientbound.CraftingJobStatusPacket;
import ae2.core.network.clientbound.CraftingStatusPacket;
import ae2.core.network.clientbound.CraftingSupplierLocationsPacket;
import ae2.core.network.clientbound.CraftingTreeDataPacket;
import ae2.core.network.clientbound.ExportedGridContent;
import ae2.core.network.clientbound.GuiDataSyncPacket;
import ae2.core.network.clientbound.ItemTransitionEffectPacket;
import ae2.core.network.clientbound.LightningPacket;
import ae2.core.network.clientbound.MEInventoryUpdatePacket;
import ae2.core.network.clientbound.MatterCannonPacket;
import ae2.core.network.clientbound.MockExplosionPacket;
import ae2.core.network.clientbound.NetworkConfigInitPacket;
import ae2.core.network.clientbound.NetworkDataUpdatePacket;
import ae2.core.network.clientbound.NetworkStatusPacket;
import ae2.core.network.clientbound.OpenGuiPacket;
import ae2.core.network.clientbound.PatternAccessTerminalInfoPacket;
import ae2.core.network.clientbound.PatternAccessTerminalPacket;
import ae2.core.network.clientbound.ProfileDataUpdatePacket;
import ae2.core.network.clientbound.RecursiveIngredientReserveAmountPacket;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.core.network.clientbound.RestorePreviousGuiPacket;
import ae2.core.network.clientbound.SetLinkStatusPacket;
import ae2.core.network.clientbound.TickConfigInitPacket;
import ae2.core.network.serverbound.AnalyserGenericPacket;
import ae2.core.network.serverbound.CableBusPartLeftClickPacket;
import ae2.core.network.serverbound.ColorApplicatorSelectColorPacket;
import ae2.core.network.serverbound.ConfigButtonPacket;
import ae2.core.network.serverbound.ConfigValueServerPacket;
import ae2.core.network.serverbound.ConfirmAutoCraftPacket;
import ae2.core.network.serverbound.CycleWirelessTerminalPacket;
import ae2.core.network.serverbound.FillCraftingGridFromRecipePacket;
import ae2.core.network.serverbound.GuiActionPacket;
import ae2.core.network.serverbound.HeiIngredientActionPacket;
import ae2.core.network.serverbound.HotkeyPacket;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.core.network.serverbound.MEInteractionPacket;
import ae2.core.network.serverbound.MouseWheelPacket;
import ae2.core.network.serverbound.NetworkConfigSavePacket;
import ae2.core.network.serverbound.QuickMovePatternPacket;
import ae2.core.network.serverbound.RequestClosestMeteoritePacket;
import ae2.core.network.serverbound.RequesterSlotUpdatePacket;
import ae2.core.network.serverbound.RequesterUpdatePacket;
import ae2.core.network.serverbound.SelectKeyTypePacket;
import ae2.core.network.serverbound.SelectWirelessTerminalPacket;
import ae2.core.network.serverbound.SetRecursiveIngredientReserveAmountPacket;
import ae2.core.network.serverbound.SwapSlotsPacket;
import ae2.core.network.serverbound.SwitchCraftingTreePacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.core.network.serverbound.TickConfigSavePacket;
import ae2.core.network.serverbound.TickProfilerRequestPacket;
import ae2.core.network.serverbound.TraceCraftingSupplierPacket;
import ae2.core.network.serverbound.UpdateHoldingCtrlPacket;
import ae2.core.network.serverbound.WirelessTerminalPickBlockPacket;
import ae2.core.network.serverbound.WirelessTerminalSettingsPacket;
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
        registerClientbound(AppEngPayloadHandler.Client.class, PatternAccessTerminalInfoPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, RestorePreviousGuiPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, SetLinkStatusPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, CraftingTreeDataPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, RequesterSyncPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, NetworkConfigInitPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, TickConfigInitPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, NetworkDataUpdatePacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, ProfileDataUpdatePacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, RecursiveIngredientReserveAmountPacket.class);
        registerClientbound(AppEngPayloadHandler.Client.class, AdvancedMemoryCardP2PSnapshotPacket.class);
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
        registerServerbound(AppEngPayloadHandler.Server.class, SetRecursiveIngredientReserveAmountPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, SwitchCraftingTreePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, RequesterSlotUpdatePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, RequesterUpdatePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, AnalyserGenericPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, NetworkConfigSavePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, TickConfigSavePacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, TickProfilerRequestPacket.class);
        registerServerbound(AppEngPayloadHandler.Server.class, ConfigValueServerPacket.class);
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
