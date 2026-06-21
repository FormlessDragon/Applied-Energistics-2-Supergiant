/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.core.network.clientbound;

import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.ITerminalHost;
import ae2.client.gui.PreviousExternalGui;
import ae2.client.gui.implementations.GuiCellRestriction;
import ae2.client.gui.implementations.GuiOutputSides;
import ae2.client.gui.implementations.GuiPriority;
import ae2.client.gui.implementations.GuiWirelessMagnet;
import ae2.client.gui.me.crafting.GuiCraftAmount;
import ae2.client.gui.me.crafting.GuiCraftConfirm;
import ae2.client.gui.me.crafting.GuiCraftingStatus;
import ae2.client.gui.me.crafting.GuiSetStockAmount;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerCellRestriction;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingStatus;
import ae2.container.implementations.ContainerOutputSides;
import ae2.container.implementations.ContainerPriority;
import ae2.container.implementations.ContainerSetStockAmount;
import ae2.container.implementations.ContainerWirelessMagnet;
import ae2.core.AELog;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.network.ClientboundPacket;
import ae2.helpers.ICellWorkbenchHost;
import ae2.helpers.IOutputSideConfigHost;
import ae2.helpers.IPriorityHost;
import ae2.helpers.InterfaceLogicHost;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.text.TextComponents;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCloseWindow;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import static ae2.util.EmptyArrays.EMPTY_BYTE_ARRAY;

public class OpenGuiPacket extends ClientboundPacket {
    private static final int MAX_INITIAL_DATA_BYTES = 5;
    private static final Map<GuiScreen, Boolean> SERVER_GUI_SWITCH_SOURCES = new WeakHashMap<>();
    private GuiIds.@Nullable GuiKey guiKey;
    private GuiHostLocator locator;
    private boolean returnedFromSubScreen;
    private boolean externalGuiReturn;
    private int windowId;
    @Nullable
    private ITextComponent guiTitle;
    private byte[] initialData = EMPTY_BYTE_ARRAY;

    public OpenGuiPacket() {
    }

    public OpenGuiPacket(GuiIds.GuiKey guiKey, GuiHostLocator locator,
                         boolean returnedFromSubScreen, @Nullable ITextComponent guiTitle, byte[] initialData,
                         int windowId, boolean externalGuiReturn) {
        this.guiKey = Objects.requireNonNull(guiKey);
        this.locator = locator;
        this.returnedFromSubScreen = returnedFromSubScreen;
        this.guiTitle = guiTitle;
        this.initialData = Arrays.copyOf(initialData, initialData.length);
        this.windowId = windowId;
        this.externalGuiReturn = externalGuiReturn;
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    private static Class<?> getHostType(GuiIds.GuiKey guiKey) {
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
            return ICellWorkbenchHost.class;
        }
        return null;
    }

    public static boolean isServerGuiSwitchSource(@Nullable GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        synchronized (SERVER_GUI_SWITCH_SOURCES) {
            return SERVER_GUI_SWITCH_SOURCES.getOrDefault(screen, Boolean.FALSE);
        }
    }

    static boolean shouldCapturePreviousExternalGui(@Nullable GuiScreen screen, boolean externalGuiReturn) {
        return externalGuiReturn && !isServerGuiSwitchSource(screen);
    }

    static void markServerGuiSwitchSource(@Nullable GuiScreen screen) {
        if (screen == null) {
            return;
        }
        synchronized (SERVER_GUI_SWITCH_SOURCES) {
            SERVER_GUI_SWITCH_SOURCES.put(screen, Boolean.TRUE);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.guiKey = GuiIds.GuiKey.fromId(packetBuffer.readVarInt());
        this.locator = GuiHostLocators.readFromPacket(packetBuffer);
        this.returnedFromSubScreen = packetBuffer.readBoolean();
        this.externalGuiReturn = packetBuffer.readBoolean();
        this.windowId = packetBuffer.readVarInt();
        this.guiTitle = TextComponents.readFromPacket(packetBuffer);
        int initialDataLength = packetBuffer.readVarInt();
        if (initialDataLength < 0 || initialDataLength > MAX_INITIAL_DATA_BYTES
            || initialDataLength > packetBuffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid initial GUI data length: " + initialDataLength);
        }
        this.initialData = new byte[initialDataLength];
        packetBuffer.readBytes(this.initialData);
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(Objects.requireNonNull(this.guiKey).getGuiId());
        GuiHostLocators.writeToPacket(packetBuffer, this.locator);
        packetBuffer.writeBoolean(this.returnedFromSubScreen);
        packetBuffer.writeBoolean(this.externalGuiReturn);
        packetBuffer.writeVarInt(this.windowId);
        TextComponents.writeToPacket(packetBuffer, this.guiTitle);
        packetBuffer.writeVarInt(this.initialData.length);
        packetBuffer.writeBytes(this.initialData);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        if (this.guiKey == null) {
            AELog.warn("Cannot open unknown gui key");
            return;
        }

        AEBaseContainer container = createContainer(minecraft.player, minecraft.player.inventory);
        if (container == null) {
            AELog.warn("Cannot open gui {} on client", this.guiKey);
            closeWindow(minecraft);
            return;
        }

        GuiScreen screen = createScreen(container, minecraft.player.inventory);
        if (screen == null) {
            AELog.warn("Cannot create screen for gui {} on client", this.guiKey);
            closeWindow(minecraft);
            return;
        }

        GuiScreen previousScreen = minecraft.currentScreen;
        markServerGuiSwitchSource(previousScreen);

        minecraft.player.openContainer = container;
        container.windowId = this.windowId;
        if (shouldCapturePreviousExternalGui(previousScreen, this.externalGuiReturn)) {
            PreviousExternalGui.capture(previousScreen);
        }
        minecraft.displayGuiScreen(screen);
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    private AEBaseContainer createContainer(EntityPlayer player, InventoryPlayer inventory) {
        if (this.locator == null) {
            return null;
        }

        Class<?> hostType = getHostType(this.guiKey);
        if (hostType == null) {
            return null;
        }

        Object host = this.locator.locate(player, hostType);
        if (host == null) {
            return null;
        }

        AEBaseContainer container = createContainer(inventory, host);
        if (container != null) {
            container.setLocator(this.locator);
            container.setReturnedFromSubScreen(this.returnedFromSubScreen);
            container.setExternalGuiReturn(this.externalGuiReturn);
            container.setGuiTitle(this.guiTitle);
            container.windowId = this.windowId;
        }
        return container;
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    private AEBaseContainer createContainer(InventoryPlayer inventory, Object host) {
        if (this.guiKey == GuiIds.GuiKey.CRAFT_AMOUNT && host instanceof ISubGuiHost subGuiHost) {
            return new ContainerCraftAmount(inventory, subGuiHost);
        }
        if (this.guiKey == GuiIds.GuiKey.CRAFT_CONFIRM && host instanceof ISubGuiHost subGuiHost) {
            return new ContainerCraftConfirm(inventory, subGuiHost);
        }
        if (this.guiKey == GuiIds.GuiKey.CRAFTING_STATUS && host instanceof ITerminalHost terminalHost) {
            return new ContainerCraftingStatus(inventory, terminalHost);
        }
        if (this.guiKey == GuiIds.GuiKey.OUTPUT_SIDES && host instanceof IOutputSideConfigHost outputSideConfigHost) {
            return new ContainerOutputSides(inventory, outputSideConfigHost);
        }
        if (this.guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT && host instanceof InterfaceLogicHost interfaceLogicHost) {
            return new ContainerSetStockAmount(inventory, interfaceLogicHost);
        }
        if (this.guiKey == GuiIds.GuiKey.PRIORITY && host instanceof IPriorityHost priorityHost) {
            ContainerPriority priorityContainer = new ContainerPriority(inventory, priorityHost);
            if (this.initialData.length > 0) {
                priorityContainer.setInitialPriority(new PacketBuffer(Unpooled.wrappedBuffer(this.initialData)).readVarInt());
            }
            return priorityContainer;
        }
        if (this.guiKey == GuiIds.GuiKey.WIRELESS_MAGNET && host instanceof WirelessTerminalGuiHost<?> wirelessTerminalGuiHost) {
            return new ContainerWirelessMagnet(inventory, wirelessTerminalGuiHost);
        }
        if (this.guiKey == GuiIds.GuiKey.CELL_RESTRICTION && host instanceof ICellWorkbenchHost cellWorkbench) {
            return new ContainerCellRestriction(inventory, cellWorkbench);
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    private GuiScreen createScreen(AEBaseContainer container, InventoryPlayer inventory) {
        if (this.guiKey == GuiIds.GuiKey.CRAFT_AMOUNT) {
            return new GuiCraftAmount((ContainerCraftAmount) container, inventory,
                GuiStyleManager.loadStyleDoc("/screens/craft_amount.json"));
        }
        if (this.guiKey == GuiIds.GuiKey.CRAFT_CONFIRM) {
            return new GuiCraftConfirm((ContainerCraftConfirm) container, inventory, this.guiTitle,
                GuiStyleManager.loadStyleDoc("/screens/craft_confirm.json"));
        }
        if (this.guiKey == GuiIds.GuiKey.CRAFTING_STATUS) {
            return new GuiCraftingStatus((ContainerCraftingStatus) container, inventory, this.guiTitle,
                GuiStyleManager.loadStyleDoc("/screens/crafting_status.json"));
        }
        if (this.guiKey == GuiIds.GuiKey.OUTPUT_SIDES) {
            return new GuiOutputSides((ContainerOutputSides) container, inventory,
                this.guiTitle != null ? this.guiTitle : container.getGuiTitle());
        }
        if (this.guiKey == GuiIds.GuiKey.SET_STOCK_AMOUNT) {
            return new GuiSetStockAmount((ContainerSetStockAmount) container, inventory,
                GuiStyleManager.loadStyleDoc("/screens/set_stock_amount.json"));
        }
        if (this.guiKey == GuiIds.GuiKey.PRIORITY) {
            return new GuiPriority((ContainerPriority) container, inventory,
                this.guiTitle != null ? this.guiTitle : container.getGuiTitle());
        }
        if (this.guiKey == GuiIds.GuiKey.WIRELESS_MAGNET) {
            return new GuiWirelessMagnet((ContainerWirelessMagnet) container, inventory,
                this.guiTitle != null ? this.guiTitle : container.getGuiTitle());
        }
        if (this.guiKey == GuiIds.GuiKey.CELL_RESTRICTION) {
            return new GuiCellRestriction((ContainerCellRestriction) container, inventory,
                GuiStyleManager.loadStyleDoc("/screens/cell_restriction.json"));
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    private void closeWindow(Minecraft minecraft) {
        if (minecraft.player.connection != null) {
            minecraft.player.connection.sendPacket(new CPacketCloseWindow());
        }
    }
}
