package appeng.core.network.serverbound;

import appeng.api.storage.ISubGuiHost;
import appeng.api.storage.ITerminalHost;
import appeng.container.AEBaseContainer;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.ContainerPriority;
import appeng.container.implementations.ContainerSetStockAmount;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.network.InitNetwork;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.clientbound.OpenGuiPacket;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogicHost;
import appeng.parts.AEBasePart;
import appeng.tile.AEBaseTile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
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
        container.setGuiTitle(title);
        container.windowId = windowId;

        InitNetwork.CHANNEL.sendTo(new OpenGuiPacket(guiKey, locator, false, title,
            getInitialData(guiKey, host)), serverPlayer);

        serverPlayer.openContainer = container;
        serverPlayer.openContainer.windowId = windowId;
        serverPlayer.openContainer.addListener(serverPlayer);

        return true;
    }

    private static @Nullable Class<?> getHostType(GuiIds.GuiKey guiKey) {
        if (guiKey == GuiIds.GuiKey.CRAFT_AMOUNT || guiKey == GuiIds.GuiKey.CRAFT_CONFIRM) {
            return ISubGuiHost.class;
        }
        if (guiKey == GuiIds.GuiKey.CRAFTING_STATUS) {
            return ITerminalHost.class;
        }
        if (guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT) {
            return InterfaceLogicHost.class;
        }
        if (guiKey == GuiIds.GuiKey.PRIORITY) {
            return IPriorityHost.class;
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
        if (guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT) {
            return new ContainerSetStockAmount(inventory, (InterfaceLogicHost) host);
        }
        if (guiKey == GuiIds.GuiKey.PRIORITY) {
            return new ContainerPriority(inventory, (IPriorityHost) host);
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
                return tile.getCustomName();
            }
        }
        if (host instanceof AEBasePart part) {
            if (part.hasCustomName()) {
                return part.getCustomName();
            }
        }
        return null;
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
            openSubGui(player, locator, this.newGui);
        }
    }

    private void doReturnToParentGui(EntityPlayerMP player) {
        Class<?> containerClass = player.openContainer.getClass();
        if (!ISubGui.class.isAssignableFrom(containerClass)) {
            return;
        }

        ISubGui currentSubGui = (ISubGui) player.openContainer;
        currentSubGui.getHost().returnToMainContainer(player, currentSubGui);
    }
}
