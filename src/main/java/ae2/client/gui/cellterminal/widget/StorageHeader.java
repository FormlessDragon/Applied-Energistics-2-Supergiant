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

import ae2.client.gui.style.GuiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class StorageHeader extends AbstractHeader {
    private static final String EXPANDED_TEXT = "[-]";
    private static final String COLLAPSED_TEXT = "[+]";
    protected Supplier<String> locationSupplier;
    protected BooleanSupplier expandedSupplier;
    protected Runnable onExpandToggle;
    protected Prioritizable prioritizable;
    protected int guiLeft;
    protected int guiTop;
    @Nullable
    protected GuiStyle guiStyle;

    public StorageHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
    }

    public void setLocationSupplier(Supplier<String> supplier) {
        this.locationSupplier = supplier;
    }

    public void setExpandedSupplier(BooleanSupplier supplier) {
        this.expandedSupplier = supplier;
    }

    public void setOnExpandToggle(Runnable callback) {
        this.onExpandToggle = callback;
    }

    public void setPrioritizable(Prioritizable target) {
        this.prioritizable = target;
    }

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    public void setGuiStyle(@Nullable GuiStyle guiStyle) {
        this.guiStyle = guiStyle;
    }

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        drawLocation();
        drawExpandIcon();
        if (prioritizable != null && prioritizable.supportsPriority()) {
            PriorityFieldManager.getInstance().registerField(prioritizable, y, guiLeft, guiTop, fontRenderer,
                guiStyle);
        }
        return CellTerminalLayout.EXPAND_ICON_X;
    }

    protected void drawLocation() {
        String location = locationSupplier != null ? locationSupplier.get() : "";
        if (location.isEmpty()) {
            return;
        }
        String displayLocation = trimTextToWidth(location, CellTerminalLayout.HEADER_LOCATION_MAX_WIDTH);
        fontRenderer.drawString(displayLocation, CellTerminalLayout.HEADER_NAME_X, y + 9,
            CellTerminalLayout.COLOR_TEXT_SECONDARY);
    }

    protected void drawExpandIcon() {
        boolean expanded = expandedSupplier != null && expandedSupplier.getAsBoolean();
        String expandIcon = expanded ? EXPANDED_TEXT : COLLAPSED_TEXT;
        fontRenderer.drawString(expandIcon, CellTerminalLayout.EXPAND_ICON_X, y + 1,
            CellTerminalLayout.COLOR_TEXT_PLACEHOLDER);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 0) {
            int expandWidth = fontRenderer.getStringWidth(currentExpandIconText());
            boolean isExpandArea = mouseX >= CellTerminalLayout.EXPAND_ICON_X
                && mouseX < CellTerminalLayout.EXPAND_ICON_X + expandWidth
                && mouseY >= y + 1 && mouseY < y + 1 + fontRenderer.FONT_HEIGHT;
            if (isExpandArea && onExpandToggle != null) {
                onExpandToggle.run();
                return true;
            }
        }
        return super.handleClick(mouseX, mouseY, button);
    }

    private String currentExpandIconText() {
        boolean expanded = expandedSupplier != null && expandedSupplier.getAsBoolean();
        return expanded ? EXPANDED_TEXT : COLLAPSED_TEXT;
    }
}
