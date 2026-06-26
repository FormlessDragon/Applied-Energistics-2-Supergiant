package ae2.core.network.serverbound;

import ae2.api.config.YesNo;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.ITerminalHost;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.container.implementations.ContainerCellRestriction;
import ae2.container.implementations.ContainerCellWorkbench;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingStatus;
import ae2.container.implementations.ContainerCrystalAssembler;
import ae2.container.implementations.ContainerInscriber;
import ae2.container.implementations.ContainerOutputSides;
import ae2.container.implementations.ContainerPortableCellPickupFilter;
import ae2.container.implementations.ContainerPortableVoidCell;
import ae2.container.implementations.ContainerPriority;
import ae2.container.implementations.ContainerProviderSelect;
import ae2.container.implementations.ContainerSetStockAmount;
import ae2.container.implementations.ContainerWirelessMagnet;
import ae2.container.implementations.ContainerWorkInterval;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.core.definitions.AEItems;
import ae2.core.gui.PatternContainerGuiReturnContext;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.OpenGuiPacket;
import ae2.core.network.clientbound.RestorePreviousGuiPacket;
import ae2.helpers.ICellWorkbenchHost;
import ae2.helpers.IOutputSideConfigHost;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.IPriorityHost;
import ae2.helpers.IWorkIntervalHost;
import ae2.helpers.InterfaceLogicHost;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.contents.PortableCellGuiHost;
import ae2.items.contents.PortableVoidCellGuiHost;
import ae2.items.tools.powered.PortableCellItem;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import ae2.parts.AEBasePart;
import ae2.tile.AEBaseTile;
import ae2.util.EmptyArrays;
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
import org.jetbrains.annotations.Nullable;

public class SwitchGuisPacket extends ServerboundPacket {

    private GuiIds.@Nullable GuiKey newGui;
    private boolean returnToParentGui;
    private boolean capturePreviousExternalGui;

    public SwitchGuisPacket() {
    }

    private SwitchGuisPacket(GuiIds.@Nullable GuiKey newGui) {
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
        return openSubGui(player, locator, guiKey, returnToContainerOverride,
            isExternalGuiReturn(returnToContainerOverride));
    }

    public static boolean openSubGui(EntityPlayer player, GuiHostLocator locator, GuiIds.GuiKey guiKey,
                                     @Nullable Container returnToContainerOverride,
                                     boolean capturePreviousExternalGui) {
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
        container.setExternalGuiReturn(capturePreviousExternalGui);
        container.setReturnToContainerOverride(returnToContainerOverride);
        container.setGuiTitle(title);
        container.windowId = windowId;

        InitNetwork.CHANNEL.sendTo(new OpenGuiPacket(guiKey, locator, false, title,
            getInitialData(guiKey, host), windowId, capturePreviousExternalGui), serverPlayer);

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
        if (!previousContainer.canInteractWith(player)) {
            return false;
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
        if (guiKey == GuiIds.GuiKey.WORK_INTERVAL) {
            return IWorkIntervalHost.class;
        }
        if (guiKey == GuiIds.GuiKey.WIRELESS_MAGNET) {
            return WirelessTerminalGuiHost.class;
        }
        if (guiKey == GuiIds.GuiKey.PORTABLE_CELL_PICKUP_FILTER) {
            return ItemGuiHost.class;
        }
        if (guiKey == GuiIds.GuiKey.CELL_RESTRICTION) {
            return ICellWorkbenchHost.class;
        }
        if (guiKey == GuiIds.GuiKey.PROVIDER_SELECT) {
            return IPatternTerminalGuiHost.class;
        }
        return null;
    }

    private static @Nullable AEBaseContainer createContainer(GuiIds.GuiKey guiKey, InventoryPlayer inventory,
                                                             Object host) {
        if (guiKey == GuiIds.GuiKey.CRAFT_AMOUNT && host instanceof ISubGuiHost subGuiHost) {
            return new ContainerCraftAmount(inventory, subGuiHost);
        }
        if (guiKey == GuiIds.GuiKey.CRAFT_CONFIRM && host instanceof ISubGuiHost subGuiHost) {
            return new ContainerCraftConfirm(inventory, subGuiHost);
        }
        if (guiKey == GuiIds.GuiKey.CRAFTING_STATUS && host instanceof ITerminalHost terminalHost) {
            return new ContainerCraftingStatus(inventory, terminalHost);
        }
        if (guiKey == GuiIds.GuiKey.OUTPUT_SIDES && host instanceof IOutputSideConfigHost outputSideConfigHost) {
            return new ContainerOutputSides(inventory, outputSideConfigHost);
        }
        if (guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT && host instanceof InterfaceLogicHost interfaceLogicHost) {
            return new ContainerSetStockAmount(inventory, interfaceLogicHost);
        }
        if (guiKey == GuiIds.GuiKey.PRIORITY && host instanceof IPriorityHost priorityHost) {
            return new ContainerPriority(inventory, priorityHost);
        }
        if (guiKey == GuiIds.GuiKey.WORK_INTERVAL && host instanceof IWorkIntervalHost workIntervalHost) {
            return new ContainerWorkInterval(inventory, workIntervalHost);
        }
        if (guiKey == GuiIds.GuiKey.WIRELESS_MAGNET && host instanceof WirelessTerminalGuiHost<?> wirelessTerminalHost) {
            return new ContainerWirelessMagnet(inventory, wirelessTerminalHost);
        }
        if (guiKey == GuiIds.GuiKey.PORTABLE_CELL_PICKUP_FILTER && host instanceof ItemGuiHost<?> itemGuiHost) {
            return new ContainerPortableCellPickupFilter(inventory, itemGuiHost);
        }
        if (guiKey == GuiIds.GuiKey.CELL_RESTRICTION && host instanceof ICellWorkbenchHost cellWorkbench) {
            return new ContainerCellRestriction(inventory, cellWorkbench);
        }
        if (guiKey == GuiIds.GuiKey.PROVIDER_SELECT && host instanceof IPatternTerminalGuiHost patternTerm) {
            return new ContainerProviderSelect(inventory, patternTerm);
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
            return EmptyArrays.EMPTY_BYTE_ARRAY;
        }

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeVarInt(((IPriorityHost) host).getPriority());
        byte[] initialData = new byte[buffer.writerIndex()];
        buffer.getBytes(0, initialData);
        return initialData;
    }

    private static boolean canSwitchFrom(AEBaseContainer source, GuiIds.GuiKey target) {
        return switch (target) {
            case CRAFTING_STATUS -> source instanceof ContainerMEStorage meStorage
                && supportsCraftingStatus(meStorage);
            case OUTPUT_SIDES -> source instanceof ContainerInscriber inscriber
                && inscriber.getAutoExport() == YesNo.YES
                || source instanceof ContainerCrystalAssembler assembler
                && assembler.getAutoExport() == YesNo.YES;
            case PRIORITY -> source.getTarget() instanceof IPriorityHost;
            case WORK_INTERVAL -> source.getTarget() instanceof IWorkIntervalHost;
            case WIRELESS_MAGNET -> source.getTarget() instanceof WirelessTerminalGuiHost<?> wirelessHost
                && wirelessHost.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
            case PORTABLE_CELL_PICKUP_FILTER -> (source instanceof ContainerMEStorage meStorage
                && meStorage.getGuiKey() == GuiIds.GuiKey.PORTABLE_ITEM_CELL
                && source.getTarget() instanceof PortableCellGuiHost<?> portableCellHost
                && portableCellHost.getItemStack().getItem() instanceof PortableCellItem portableCellItem
                && portableCellItem.getKeyType() == AEKeyType.items())
                || (source instanceof ContainerPortableVoidCell
                && source.getTarget() instanceof PortableVoidCellGuiHost portableVoidCellHost
                && PortableItemCellAutoPickup.isSupported(portableVoidCellHost.getItemStack()));
            case CELL_RESTRICTION -> source instanceof ContainerCellWorkbench cellWorkbench
                && cellWorkbench.canRestrictCell();
            case PROVIDER_SELECT -> source instanceof ContainerPatternEncodingTerm
                && source.getTarget() instanceof IPatternTerminalGuiHost;
            default -> false;
        };
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(this.newGui != null);
        if (this.newGui != null) {
            data.writeVarInt(this.newGui.getGuiId());
        }
        data.writeBoolean(this.capturePreviousExternalGui);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.newGui != null) {
            doOpenSubGui(player);
        } else if (this.returnToParentGui) {
            doReturnToParentGui(player);
        }
    }

    private static boolean supportsCraftingStatus(ContainerMEStorage source) {
        return switch (source.getGuiKey()) {
            case ME_STORAGE_TERMINAL,
                 BASIC_CELL_CHEST,
                 CRAFTING_TERMINAL,
                 PATTERN_ENCODING_TERMINAL,
                 WIRELESS_TERMINAL,
                 WIRELESS_CRAFTING_TERMINAL,
                 WIRELESS_PATTERN_ENCODING_TERMINAL -> true;
            default -> false;
        };
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
        this.capturePreviousExternalGui = data.readBoolean();
        if (data.isReadable()) {
            throw new IllegalArgumentException("Trailing switch GUIs payload bytes: " + data.readableBytes());
        }
    }

    private void doOpenSubGui(EntityPlayerMP player) {
        GuiIds.GuiKey target = this.newGui;
        if (target == null) {
            return;
        }

        if (!(player.openContainer instanceof AEBaseContainer openContainer)) {
            return;
        }

        if (!canSwitchFrom(openContainer, target)) {
            return;
        }

        GuiHostLocator locator = openContainer.getLocator();
        if (locator != null) {
            openSubGui(player, locator, target, openContainer, this.capturePreviousExternalGui);
        }
    }

    private void doReturnToParentGui(EntityPlayerMP player) {
        if (restoreExternalGui(player)) {
            return;
        }

        if (PatternContainerGuiReturnContext.restoreExternalContainer(player, player)) {
            return;
        }

        if (!(player.openContainer instanceof ISubGui currentSubGui)) {
            return;
        }
        currentSubGui.getHost().returnToMainContainer(player, currentSubGui);
    }

}
