/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package ae2.client.gui.me.networktool;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.StackWithBounds;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.CommonButtons;
import ae2.client.gui.widgets.Scrollbar;
import ae2.container.AEBaseContainer;
import ae2.container.networking.INetworkStatusContainer;
import ae2.container.networking.MachineGroup;
import ae2.core.AEConfig;
import ae2.core.localization.GuiText;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;

public class GuiNetworkStatus<T extends AEBaseContainer & INetworkStatusContainer> extends AEBaseGui<T> {
    private static final int ROWS = 4;
    private static final int COLUMNS = 5;
    private static final int TABLE_X = 14;
    private static final int TABLE_Y = 41;
    private static final int CELL_WIDTH = 30;
    private static final int CELL_HEIGHT = 18;

    private final Scrollbar scrollbar;
    private final AE2Button exportGridButton;
    @Nullable
    private StackWithBounds hoveredMachine;
    @Nullable
    private List<ITextComponent> hoveredMachineTooltip;
    private int hoveredMachineTooltipMouseX;
    private int hoveredMachineTooltipMouseY;

    public GuiNetworkStatus(T container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit());
        this.exportGridButton = widgets.addButton("export_grid", GuiText.ExportGrid.text(), container::exportGrid);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var status = container.getStatus();
        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.NetworkDetails.text(status.getChannelsUsed()));
        setTextContent("stored_power", GuiText.StoredPower.text(
            formatPowerStatus(status.isInfiniteStoredPower(), status.getStoredPower(), false)));
        setTextContent("max_power", GuiText.MaxPower.text(
            formatPowerStatus(status.isInfiniteMaxStoredPower(), status.getMaxStoredPower(), false)));
        setTextContent("power_input_rate",
            GuiText.PowerInputRate.text(
                formatPowerStatus(status.isInfiniteAveragePowerInjection(), status.getAveragePowerInjection(), true)));
        setTextContent("power_usage_rate",
            GuiText.PowerUsageRate.text(
                formatPowerStatus(status.isInfiniteAveragePowerUsage(), status.getAveragePowerUsage(), true)));
        setTextContent("channel_power_rate",
            GuiText.ChannelEnergyDrain.text(Platform.formatPower(status.getChannelPower(), true)));

        int overflowRows = (status.getGroupedMachines().size() + COLUMNS - 1) / COLUMNS - ROWS;
        scrollbar.setRange(0, Math.max(0, overflowRows), 1);
        this.exportGridButton.visible = container.canExportGrid();
        this.exportGridButton.enabled = container.canExportGrid();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        hoveredMachine = null;
        hoveredMachineTooltip = null;
        int localMouseX = mouseX - offsetX;
        int localMouseY = mouseY - offsetY;

        int x = 0;
        int y = 0;
        int viewStart = scrollbar.getCurrentScroll() * COLUMNS;
        int viewEnd = viewStart + COLUMNS * ROWS;

        List<ITextComponent> tooltip = null;
        List<MachineGroup> machines = new ObjectArrayList<>(container.getStatus().getGroupedMachines());
        machines.sort(MachineGroup.COMPARATOR);

        for (int i = viewStart; i < Math.min(viewEnd, machines.size()); i++) {
            MachineGroup entry = machines.get(i);

            int cellX = TABLE_X + x * CELL_WIDTH;
            int cellY = TABLE_Y + y * CELL_HEIGHT;
            int itemX = cellX + CELL_WIDTH - 17;
            int itemY = cellY + 1;

            if (entry.isMissingChannel()) {
                Gui.drawRect(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, 0x55FF5555);
            }

            drawMachineCount(itemX, cellY, entry.getCount());
            AEKeyRendering.drawInGui(Minecraft.getMinecraft(), itemX, itemY, entry.getDisplay());

            if (localMouseX >= cellX && localMouseX < cellX + CELL_WIDTH
                && localMouseY >= cellY && localMouseY < cellY + CELL_HEIGHT) {
                tooltip = new ObjectArrayList<>(AEKeyRendering.getTooltip(entry.getDisplay()));
                if (entry.isMissingChannel()) {
                    ITextComponent noChannel = GuiText.NoChannel.text();
                    noChannel.getStyle().setColor(TextFormatting.RED);
                    tooltip.add(noChannel);
                }
                tooltip.add(GuiText.Installed.text(entry.getCount()));
                if (entry.getIdlePowerUsage() > 0) {
                    tooltip.add(GuiText.EnergyDrain.text(Platform.formatPower(entry.getIdlePowerUsage(), true)));
                }
                if (entry.getPowerGenerationCapacity() > 0) {
                    tooltip.add(GuiText.EnergyGenerationCapacity
                        .text(Platform.formatPower(entry.getPowerGenerationCapacity(), true)));
                }
                hoveredMachine = new StackWithBounds(
                    new GenericStack(entry.getDisplay(), 0),
                    new Rectangle(guiLeft + cellX, guiTop + cellY, CELL_WIDTH, CELL_HEIGHT));
            }

            if (++x >= COLUMNS) {
                y++;
                x = 0;
            }
        }

        if (tooltip != null) {
            hoveredMachineTooltip = tooltip;
            hoveredMachineTooltipMouseX = mouseX;
            hoveredMachineTooltipMouseY = mouseY;
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        if (hoveredMachineTooltip != null) {
            drawTooltipWithHeader(hoveredMachineTooltipMouseX, hoveredMachineTooltipMouseY, hoveredMachineTooltip);
        }
    }

    private void drawMachineCount(int x, int y, long count) {
        String text = count >= 10000 ? Long.toString(count / 1000) + 'k' : Long.toString(count);
        float textWidth = fontRenderer.getStringWidth(text) / 2.0f;
        float textHeight = fontRenderer.FONT_HEIGHT / 2.0f;
        int textColor = style != null ? style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB() : 0x404040;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x - 1 - textWidth, y + (CELL_HEIGHT - textHeight) / 2.0f, 0);
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawString(text, 0, 0, textColor, false);
        GlStateManager.popMatrix();
    }

    @Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        if (hoveredMachine != null) {
            return hoveredMachine;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    private String formatPowerStatus(boolean infinite, double value, boolean isRate) {
        if (!infinite) {
            return Platform.formatPower(value, isRate);
        }

        return GuiText.ChannelModeInfinite.getLocal() + " "
            + AEConfig.instance().getSelectedEnergyUnit().getSymbolName() + (isRate ? "/t" : "");
    }
}

