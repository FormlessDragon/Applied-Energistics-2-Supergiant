package ae2.core.network.serverbound;

import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.ITerminalHost;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.container.implementations.ContainerCellRestriction;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingStatus;
import ae2.container.implementations.ContainerOutputSides;
import ae2.container.implementations.ContainerPriority;
import ae2.container.implementations.ContainerSetStockAmount;
import ae2.container.implementations.ContainerWirelessMagnet;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.OpenGuiPacket;
import ae2.core.network.clientbound.RestorePreviousGuiPacket;
import ae2.helpers.IOutputSideConfigHost;
import ae2.helpers.IPriorityHost;
import ae2.helpers.InterfaceLogicHost;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.parts.AEBasePart;
import ae2.tile.AEBaseTile;
import ae2.tile.misc.TileCellWorkbench;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IWorldNameable;
import org.jspecify.annotations.Nullable;

public class SwitchGuisPacket extends ServerboundPacket {

    private GuiIds.GuiKey newGui;
    private boolean returnToParentGui;

    public SwitchGuisPacket() {
    }

    private SwitchGuisPacket(GuiIds.GuiKey newGui) {
        this.newGui = newGui;
        this.returnToParentGui = newGui == null;
    }

    public static SwitchGuisPacket openSubGui(GuiIds.GuiKey guiKey) {
        return new SwitchGuisPacket(guiKey);
    }

    public static SwitchGuisPacket returnToParentGui() {
        return new SwitchGuisPacket(null);
    }

    public static boolean openSubGui(EntityPlayer player, GuiHostLocator locator, GuiIds.GuiKey guiKey) {
        return openSubGui(player, locator, guiKey, null);
    }

    public static boolean openSubGui(EntityPlayer player, GuiHostLocator locator, GuiIds.GuiKey guiKey,
                                     @Nullable Container returnToContainerOverride) {
        if (player.getClass() != EntityPlayerMP.class) {
            return false;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;

        Class<?> hostType = getHostType(guiKey);
        if (hostType == null) {
            return false;
        }

        Object host = locator.locate(player, hostType);
        if (host == null) {
            return false;
        }

        ITextComponent title = getDefaultGuiTitle(host);

        serverPlayer.closeContainer();
        serverPlayer.getNextWindowId();
        int windowId = serverPlayer.currentWindowId;

        AEBaseContainer container = createContainer(guiKey, serverPlayer.inventory, host);
        if (container == null) {
            return false;
        }

        container.setLocator(locator);
        container.setReturnedFromSubScreen(false);
        boolean externalGuiReturn = isExternalGuiReturn(returnToContainerOverride);
        container.setExternalGuiReturn(externalGuiReturn);
        container.setReturnToContainerOverride(returnToContainerOverride);
        container.setGuiTitle(title);
        container.windowId = windowId;

        InitNetwork.CHANNEL.sendTo(new OpenGuiPacket(guiKey, locator, false, title,
            getInitialData(guiKey, host), windowId, externalGuiReturn), serverPlayer);

        serverPlayer.openContainer = container;
        serverPlayer.openContainer.windowId = windowId;
        serverPlayer.openContainer.addListener(serverPlayer);

        return true;
    }

    public static boolean restorePreviousGui(EntityPlayerMP player) {
        if (!(player.openContainer instanceof AEBaseContainer currentContainer)) {
            return false;
        }

        Container previousContainer = currentContainer.getReturnToContainerOverride();
        if (previousContainer == null) {
            return false;
        }

        if (previousContainer instanceof ContainerPlayer) {
            player.closeContainer();
            InitNetwork.CHANNEL.sendTo(new RestorePreviousGuiPacket(player.inventoryContainer.windowId), player);
            return true;
        }

        player.getNextWindowId();
        player.closeContainer();
        int windowId = player.currentWindowId;

        player.openContainer = previousContainer;
        player.openContainer.windowId = windowId;
        InitNetwork.CHANNEL.sendTo(new RestorePreviousGuiPacket(windowId), player);
        rebindRestoredContainerListener(player.openContainer, player);
        return true;
    }

    public static boolean restoreExternalGui(EntityPlayerMP player) {
        if (!(player.openContainer instanceof AEBaseContainer currentContainer)) {
            return false;
        }

        if (!currentContainer.hasExternalGuiReturn()) {
            return false;
        }

        return restorePreviousGui(player);
    }

    static void rebindRestoredContainerListener(Container container, IContainerListener listener) {
        container.removeListener(listener);
        container.addListener(listener);
    }

    static boolean isExternalGuiReturn(@Nullable Container returnToContainerOverride) {
        return returnToContainerOverride != null && !(returnToContainerOverride instanceof AEBaseContainer);
    }

    private static @Nullable Class<?> getHostType(GuiIds.GuiKey guiKey) {
        if (guiKey == GuiIds.GuiKey.CRAFT_AMOUNT || guiKey == GuiIds.GuiKey.CRAFT_CONFIRM) {
            return ISubGuiHost.class;
        }
        if (guiKey == GuiIds.GuiKey.CRAFTING_STATUS) {
            return ITerminalHost.class;
        }
        if (guiKey == GuiIds.GuiKey.OUTPUT_SIDES) {
            return IOutputSideConfigHost.class;
        }
        if (guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT) {
            return InterfaceLogicHost.class;
        }
        if (guiKey == GuiIds.GuiKey.PRIORITY) {
            return IPriorityHost.class;
        }
        if (guiKey == GuiIds.GuiKey.WIRELESS_MAGNET) {
            return WirelessTerminalGuiHost.class;
        }
        if (guiKey == GuiIds.GuiKey.CELL_RESTRICTION) {
            return TileCellWorkbench.class;
        }
        return null;
    }

    private static @Nullable AEBaseContainer createContainer(GuiIds.GuiKey guiKey, InventoryPlayer inventory,
                                                             Object host) {
        if (guiKey == GuiIds.GuiKey.CRAFT_AMOUNT) {
            return new ContainerCraftAmount(inventory, (ISubGuiHost) host);
        }
        if (guiKey == GuiIds.GuiKey.CRAFT_CONFIRM) {
            return new ContainerCraftConfirm(inventory, (ISubGuiHost) host);
        }
        if (guiKey == GuiIds.GuiKey.CRAFTING_STATUS) {
            return new ContainerCraftingStatus(inventory, (ITerminalHost) host);
        }
        if (guiKey == GuiIds.GuiKey.OUTPUT_SIDES) {
            return new ContainerOutputSides(inventory, (IOutputSideConfigHost) host);
        }
        if (guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT) {
            return new ContainerSetStockAmount(inventory, (InterfaceLogicHost) host);
        }
        if (guiKey == GuiIds.GuiKey.PRIORITY) {
            return new ContainerPriority(inventory, (IPriorityHost) host);
        }
        if (guiKey == GuiIds.GuiKey.WIRELESS_MAGNET) {
            return new ContainerWirelessMagnet(inventory, (WirelessTerminalGuiHost<?>) host);
        }
        if (guiKey == GuiIds.GuiKey.CELL_RESTRICTION) {
            return new ContainerCellRestriction(inventory, (TileCellWorkbench) host);
        }
        return null;
    }

    private static @Nullable ITextComponent getDefaultGuiTitle(Object host) {
        if (host instanceof IWorldNameable titleProvider) {
            if (titleProvider.hasCustomName()) {
                return titleProvider.getDisplayName();
            }
        }
        if (host instanceof AEBaseTile tile) {
            if (tile.hasCustomName()) {
                return customTitle(tile.getCustomName());
            }
        }
        if (host instanceof AEBasePart part) {
            if (part.hasCustomName()) {
                return customTitle(part.getCustomName());
            }
        }
        return null;
    }

    private static @Nullable ITextComponent customTitle(@Nullable String customName) {
        return customName == null || customName.isEmpty() ? null : new TextComponentString(customName);
    }

    private static byte[] getInitialData(GuiIds.GuiKey guiKey, Object host) {
        if (guiKey != GuiIds.GuiKey.PRIORITY) {
            return new byte[0];
        }

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeVarInt(((IPriorityHost) host).getPriority());
        byte[] initialData = new byte[buffer.writerIndex()];
        buffer.getBytes(0, initialData);
        return initialData;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        if (data.readBoolean()) {
            this.newGui = GuiIds.GuiKey.fromId(data.readVarInt());
            this.returnToParentGui = false;
        } else {
            this.newGui = null;
            this.returnToParentGui = true;
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(this.newGui != null);
        if (this.newGui != null) {
            data.writeVarInt(this.newGui.getGuiId());
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.newGui != null) {
            doOpenSubGui(player);
        } else if (this.returnToParentGui) {
            doReturnToParentGui(player);
        }
    }

    private void doOpenSubGui(EntityPlayerMP player) {
        if (!(player.openContainer instanceof AEBaseContainer openContainer)) {
            return;
        }

        GuiHostLocator locator = openContainer.getLocator();
        if (locator != null) {
            openSubGui(player, locator, this.newGui, openContainer);
        }
    }

    private void doReturnToParentGui(EntityPlayerMP player) {
        if (restoreExternalGui(player)) {
            return;
        }

        Class<?> containerClass = player.openContainer.getClass();
        if (!ISubGui.class.isAssignableFrom(containerClass)) {
            return;
        }

        ISubGui currentSubGui = (ISubGui) player.openContainer;
        currentSubGui.getHost().returnToMainContainer(player, currentSubGui);
    }

}
