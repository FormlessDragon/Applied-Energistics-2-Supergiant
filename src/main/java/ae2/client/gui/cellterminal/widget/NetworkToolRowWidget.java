/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.cellterminal.widget;

import ae2.client.gui.Icon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class NetworkToolRowWidget extends AbstractWidget {
    public static final int ROW_HEIGHT = 36;

    private static final int PADDING = 4;
    private static final int ICON_SIZE = 16;
    private static final int RUN_SIZE = 16;
    private static final int HELP_SIZE = 10;
    private static final int HELP_Y_OFFSET = 3;

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;
    private final Supplier<ItemStack> iconSupplier;
    private final Supplier<String> nameSupplier;
    private final Supplier<String> countTextSupplier;
    private final IntSupplier countColorSupplier;
    private final BooleanSupplier canExecuteSupplier;
    private final List<String> helpLines;
    private final List<String> tooltipLines;
    private Runnable onRunClicked;

    private int runBtnX;
    private int runBtnY;
    private boolean canExecute;

    public NetworkToolRowWidget(int y, FontRenderer fontRenderer,
                                Supplier<ItemStack> iconSupplier, Supplier<String> nameSupplier,
                                Supplier<String> countTextSupplier, IntSupplier countColorSupplier,
                                BooleanSupplier canExecuteSupplier,
                                List<String> helpLines, List<String> tooltipLines) {
        super(CellTerminalLayout.GUI_INDENT, y,
            CellTerminalLayout.CONTENT_RIGHT_EDGE - CellTerminalLayout.GUI_INDENT, ROW_HEIGHT);
        this.fontRenderer = fontRenderer;
        this.itemRender = Minecraft.getMinecraft().getRenderItem();
        this.iconSupplier = iconSupplier;
        this.nameSupplier = nameSupplier;
        this.countTextSupplier = countTextSupplier;
        this.countColorSupplier = countColorSupplier;
        this.canExecuteSupplier = canExecuteSupplier;
        this.helpLines = helpLines != null ? helpLines : Collections.emptyList();
        this.tooltipLines = tooltipLines != null ? tooltipLines : Collections.emptyList();
    }

    public void setOnRunClicked(Runnable onRunClicked) {
        this.onRunClicked = onRunClicked;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean rowHovered = isHovered(mouseX, mouseY);
        int bgColor = rowHovered ? 0x30FFFFFF : 0x20FFFFFF;
        Gui.drawRect(x, y, x + width, y + height - 1, bgColor);
        Gui.drawRect(x, y + height - 1, x + width, y + height, CellTerminalLayout.COLOR_SEPARATOR);

        canExecute = canExecuteSupplier != null && canExecuteSupplier.getAsBoolean();
        runBtnX = x + width - RUN_SIZE - PADDING;
        runBtnY = y + PADDING;
        int helpBtnX = x + PADDING;
        int helpBtnY = y + PADDING + HELP_Y_OFFSET;

        Icon.CELL_TERMINAL_HELP.getBlitter().copy().dest(helpBtnX, helpBtnY, HELP_SIZE, HELP_SIZE).blit();

        int iconX = x + PADDING + HELP_SIZE + 4;
        int iconY = y + PADDING;
        ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
        if (!icon.isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, icon, iconX, iconY);
        }
        String countText = countTextSupplier != null ? countTextSupplier.get() : "";
        if (!countText.isEmpty()) {
            int countColor = countColorSupplier != null ? countColorSupplier.getAsInt() : 0xFFFFFF;
            int countX = iconX + ICON_SIZE + 4;
            int countY = iconY + (ICON_SIZE - fontRenderer.FONT_HEIGHT) / 2;
            fontRenderer.drawString(countText, countX, countY, countColor);
        }
        drawRunButton();
        String name = nameSupplier != null ? nameSupplier.get() : "";
        String displayName = AbstractWidget.trimTextToWidth(fontRenderer, name, width - PADDING * 2);
        fontRenderer.drawString(displayName, x + PADDING, y + PADDING + ICON_SIZE + 2, 0x000000);
    }

    private void drawRunButton() {
        Icon icon;
        if (!canExecute) {
            icon = Icon.CELL_TERMINAL_RUN_DISABLED;
        } else {
            icon = Icon.CELL_TERMINAL_RUN;
        }
        icon.getBlitter().copy().dest(runBtnX, runBtnY, RUN_SIZE, RUN_SIZE).blit();
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }
        if (isRunUnderMouse(mouseX, mouseY)) {
            boolean canRun = canExecuteSupplier != null && canExecuteSupplier.getAsBoolean();
            if (canRun && onRunClicked != null) {
                onRunClicked.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        List<String> lines = new ObjectArrayList<>();
        String name = nameSupplier != null ? nameSupplier.get() : "";
        if (isHelpUnderMouse(mouseX, mouseY)) {
            lines.add("§e" + name);
            lines.add("");
            for (String line : helpLines) {
                lines.add("§7" + line);
            }
            return lines;
        }
        lines.add("§e" + name);
        if (!tooltipLines.isEmpty()) {
            lines.add("");
            lines.addAll(tooltipLines);
        }
        return lines;
    }

    private boolean isRunUnderMouse(int mouseX, int mouseY) {
        int buttonX = x + width - RUN_SIZE - PADDING;
        int buttonY = y + PADDING;
        return mouseX >= buttonX && mouseX < buttonX + RUN_SIZE
            && mouseY >= buttonY && mouseY < buttonY + RUN_SIZE;
    }

    private boolean isHelpUnderMouse(int mouseX, int mouseY) {
        int buttonX = x + PADDING;
        int buttonY = y + PADDING + HELP_Y_OFFSET;
        return mouseX >= buttonX && mouseX < buttonX + HELP_SIZE
            && mouseY >= buttonY && mouseY < buttonY + HELP_SIZE;
    }
}
