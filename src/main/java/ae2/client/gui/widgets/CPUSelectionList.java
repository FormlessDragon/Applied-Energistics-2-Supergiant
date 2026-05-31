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

package ae2.client.gui.widgets;

import ae2.api.client.AEKeyRendering;
import ae2.api.config.CpuSelectionMode;
import ae2.api.stacks.AmountFormat;
import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.client.gui.me.crafting.CraftingTimeDisplay;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.Color;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.container.implementations.ContainerCraftingStatus;
import ae2.core.definitions.AEParts;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;

public class CPUSelectionList implements ICompositeWidget {
    private static final int ROWS = 6;
    private static final float SMALL_TEXT_SCALE = 0.666f;
    private static final float MODE_BUTTON_SCALE = 0.5f;
    private static final int MODE_BUTTON_X = 55;
    private static final int MODE_BUTTON_Y = 9;
    private static final int MODE_BUTTON_SIZE = 10;
    private static final int MODE_BUTTON_CONTENT_OFFSET = 1;

    private final Blitter background;
    private final Blitter buttonBg;
    private final Blitter buttonBgSelected;
    private final ContainerCraftingStatus container;
    private final Color textColor;
    private final int selectedColor;
    private final Scrollbar scrollbar;
    private Rectangle bounds = new Rectangle(0, 0, 0, 0);

    public CPUSelectionList(ContainerCraftingStatus container, Scrollbar scrollbar, GuiStyle style) {
        this.container = container;
        this.scrollbar = scrollbar;
        this.background = style.getImage("cpuList");
        this.buttonBg = style.getImage("cpuListButton");
        this.buttonBgSelected = style.getImage("cpuListButtonSelected");
        this.textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR);
        this.selectedColor = style.getColor(PaletteColor.SELECTION_COLOR).toARGB();
        this.scrollbar.setCaptureMouseWheel(false);
    }

    private static ITextComponent gray(ITextComponent text) {
        return text.setStyle(new Style().setColor(TextFormatting.GRAY));
    }

    private static void drawScaledString(String text, int x, int y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1.0f);
        Minecraft.getMinecraft().fontRenderer.drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private static void drawScaledKey(int x, int y, float scale, ae2.api.stacks.AEKey what) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        AEKeyRendering.drawInGui(Minecraft.getMinecraft(), 0, 0, what);
        GlStateManager.popMatrix();
    }

    private static void drawScaledToolbarBackground(int x, int y, boolean hovered) {
        Icon backgroundIcon = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(MODE_BUTTON_SCALE, MODE_BUTTON_SCALE, 1.0f);
        backgroundIcon.getBlitter().dest(0, 0).zOffset(10).blit();
        GlStateManager.popMatrix();
    }

    private static void drawScaledModeIcon(int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(MODE_BUTTON_SCALE, MODE_BUTTON_SCALE, 1.0f);
        Icon.CRAFT_HAMMER.getBlitter().dest(0, 0).zOffset(20).blit();
        GlStateManager.popMatrix();
    }

    private static void drawScaledModeItemStack(int x, int y, ItemStack stack) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 20);
        GlStateManager.scale(MODE_BUTTON_SCALE, MODE_BUTTON_SCALE, 1.0f);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            stack,
            0,
            0,
            null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    public void setPosition(Point position) {
        this.bounds = new Rectangle(position.x(), position.y(), bounds.width, bounds.height);
    }

    @Override
    public void setSize(int width, int height) {
        this.bounds = new Rectangle(bounds.x, bounds.y, width, height);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        scrollbar.onMouseWheel(mousePos, delta);
        return true;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        var modeButtonCpu = hitTestModeButton(mousePos);
        if (modeButtonCpu != null) {
            container.cycleCpuMode(modeButtonCpu.serial(), button == 1);
            return true;
        }

        var cpu = hitTestCpu(mousePos);
        if (cpu != null) {
            container.selectCpu(cpu.serial());
            return true;
        }

        return false;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        var modeButtonCpu = hitTestModeButton(new Point(mouseX, mouseY));
        if (modeButtonCpu != null) {
            var tooltipLines = new ObjectArrayList<ITextComponent>();
            tooltipLines.add(ButtonToolTips.CpuSelectionMode.text());
            tooltipLines.add(gray(getModeButtonTooltip(modeButtonCpu.mode()).text()));
            return new Tooltip(tooltipLines);
        }

        var cpu = hitTestCpu(new Point(mouseX, mouseY));
        if (cpu == null) {
            return null;
        }

        var tooltipLines = new ObjectArrayList<ITextComponent>();
        tooltipLines.add(getCpuName(cpu));
        tooltipLines.add(gray(ButtonToolTips.CpuStatusStorage.text(formatStorage(cpu))));

        int coProcessors = cpu.coProcessors();
        if (coProcessors == 1) {
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCoProcessor.text(String.valueOf(coProcessors))));
        } else if (coProcessors > 1) {
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCoProcessors.text(String.valueOf(coProcessors))));
        }

        switch (cpu.mode()) {
            case PLAYER_ONLY -> tooltipLines.add(gray(ButtonToolTips.CpuSelectionModePlayersOnly.text()));
            case MACHINE_ONLY -> tooltipLines.add(gray(ButtonToolTips.CpuSelectionModeAutomationOnly.text()));
            default -> {
            }
        }

        var currentJob = cpu.currentJob();
        if (currentJob != null) {
            String amount = currentJob.what().formatAmount(currentJob.amount(), AmountFormat.FULL);
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCrafting.text(amount)
                                                                  .appendText(" ")
                                                                  .appendSibling(currentJob.what().getDisplayName())));
            var elapsedTimeTooltip = CraftingTimeDisplay.getElapsedTimeTooltip(cpu.progress(), cpu.elapsedTimeNanos());
            tooltipLines.add(gray(new TextComponentTranslation(
                elapsedTimeTooltip.translationKey(),
                elapsedTimeTooltip.args())));
        }

        return new Tooltip(tooltipLines);
    }

    @Override
    public void updateBeforeRender() {
        int hiddenRows = Math.max(0, container.cpuList.cpus().size() - ROWS);
        scrollbar.setRange(0, hiddenRows, Math.max(1, ROWS / 3));
    }

    @Override
    public void drawBackgroundLayer(Rectangle screenBounds, Point mouse) {
        int x = screenBounds.x + this.bounds.x;

        int y = screenBounds.y + this.bounds.y;
        background.dest(x, y, this.bounds.width, this.bounds.height).blit();

        x += 8;
        y += 19;

        var cpus = container.cpuList.cpus().subList(
            MathHelper.clamp(scrollbar.getCurrentScroll(), 0, container.cpuList.cpus().size()),
            MathHelper.clamp(scrollbar.getCurrentScroll() + ROWS, 0, container.cpuList.cpus().size()));
        var hoveredModeButtonCpu = hitTestModeButton(mouse);
        for (var cpu : cpus) {
            if (cpu.serial() == container.getSelectedCpuSerial()) {
                buttonBgSelected.dest(x, y).blit();
            } else {
                buttonBg.dest(x, y).blit();
            }

            drawScaledString(getCpuName(cpu).getFormattedText(), x + 3, y + 2, textColor.toARGB());

            var currentJob = cpu.currentJob();
            if (currentJob != null) {
                Icon.S_CRAFT.getBlitter().dest(x + 2, y + 9).blit();
                drawScaledString(currentJob.what().formatAmount(currentJob.amount(), AmountFormat.SLOT),
                    x + 14, y + 13, textColor.toARGB());
                drawScaledKey(x + 55, y + 9, SMALL_TEXT_SCALE, currentJob.what());

                int progress = (int) (cpu.progress() * (buttonBg.getSrcWidth() - 1));
                if (progress > 0) {
                    net.minecraft.client.gui.Gui.drawRect(
                        x,
                        y + buttonBg.getSrcHeight() - 2,
                        x + progress,
                        y + buttonBg.getSrcHeight() - 1,
                        container.getSelectedCpuSerial() == cpu.serial() ? 0xFF7da9d2 : selectedColor);
                }
            } else {
                Icon.S_STORAGE.getBlitter().dest(x + 27, y + 9).blit();
                drawScaledString(formatStorage(cpu), x + 39, y + 13, textColor.toARGB());

                if (cpu.coProcessors() > 0) {
                    Icon.S_PROCESSOR.getBlitter().dest(x + 2, y + 9).blit();
                    drawScaledString(String.valueOf(cpu.coProcessors()),
                        x + 14, y + 13, textColor.toARGB());
                }
                drawModeButton(
                    x + MODE_BUTTON_X,
                    y + MODE_BUTTON_Y,
                    cpu.mode(),
                    hoveredModeButtonCpu != null && hoveredModeButtonCpu.serial() == cpu.serial());
            }

            y += buttonBg.getSrcHeight() + 1;
        }
    }

    @Nullable
    private ContainerCraftingStatus.CraftingCpuListEntry hitTestCpu(Point mousePos) {
        int relX = mousePos.x() - bounds.x - 8;
        if (relX < 0 || relX >= buttonBg.getSrcWidth()) {
            return null;
        }

        int relY = mousePos.y() - bounds.y - 19;
        int buttonHeight = buttonBg.getSrcHeight() + 1;
        int buttonIdx = scrollbar.getCurrentScroll() + relY / buttonHeight;
        if (relY < 0 || relY % buttonHeight == buttonBg.getSrcHeight()) {
            return null;
        }

        var cpus = container.cpuList.cpus();
        if (buttonIdx < 0 || buttonIdx >= cpus.size()) {
            return null;
        }
        return cpus.get(buttonIdx);
    }

    @Nullable
    private ContainerCraftingStatus.CraftingCpuListEntry hitTestModeButton(Point mousePos) {
        var cpu = hitTestCpu(mousePos);
        if (cpu == null || cpu.currentJob() != null) {
            return null;
        }

        int relX = mousePos.x() - bounds.x - 8;

        int relY = mousePos.y() - bounds.y - 19;
        int buttonHeight = buttonBg.getSrcHeight() + 1;
        int rowY = relY % buttonHeight;

        if (relX < MODE_BUTTON_X || relX >= MODE_BUTTON_X + MODE_BUTTON_SIZE) {
            return null;
        }

        if (rowY < MODE_BUTTON_Y || rowY >= MODE_BUTTON_Y + MODE_BUTTON_SIZE) {
            return null;
        }

        return cpu;
    }

    private void drawModeButton(int x, int y, CpuSelectionMode mode, boolean hovered) {
        drawScaledToolbarBackground(x, y, hovered);

        switch (mode) {
            case ANY -> drawScaledModeIcon(x + MODE_BUTTON_CONTENT_OFFSET, y + MODE_BUTTON_CONTENT_OFFSET);
            case PLAYER_ONLY -> drawScaledModeItemStack(x + MODE_BUTTON_CONTENT_OFFSET, y + MODE_BUTTON_CONTENT_OFFSET,
                AEParts.TERMINAL.stack());
            case MACHINE_ONLY -> drawScaledModeItemStack(x + MODE_BUTTON_CONTENT_OFFSET, y + MODE_BUTTON_CONTENT_OFFSET,
                AEParts.EXPORT_BUS.stack());
        }
    }

    private ButtonToolTips getModeButtonTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> ButtonToolTips.CpuSelectionModeAny;
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly;
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly;
        };
    }

    private String formatStorage(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        long storage = cpu.storage();
        if (storage >= 1024 * 1024) {
            return (storage / (1024 * 1024)) + "M";
        }
        return (storage / 1024) + "k";
    }

    private ITextComponent getCpuName(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return cpu.name() != null ? cpu.name() : GuiText.CPUs.text().appendText(" #" + cpu.serial());
    }
}
