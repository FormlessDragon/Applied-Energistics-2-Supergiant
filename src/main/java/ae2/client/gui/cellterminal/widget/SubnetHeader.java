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
import ae2.client.gui.widgets.AE2Button;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class SubnetHeader extends AbstractHeader {
    private static final int STAR_X = 2;
    private static final int STAR_SIZE = 16;
    private static final int LOAD_BUTTON_MARGIN = 4;
    private static final int LOAD_BUTTON_Y_OFFSET = 1;
    private static final int LOAD_BUTTON_HEIGHT = 16;
    private static final int COLOR_NAME_INACCESSIBLE = 0xFF909090;
    private static final int ARROW_X = CellTerminalLayout.GUI_INDENT + 18 - 2;
    private static final int ARROW_SIZE = 10;
    private static final int ARROW_NAME_MARGIN = 2;
    private final boolean isMain;
    private BooleanSupplier directionSupplier;
    private BooleanSupplier isFavoriteSupplier;
    private BooleanSupplier canLoadSupplier;
    private Supplier<String> locationSupplier;
    private Runnable onStarClick;
    private Runnable onLoadClick;
    private Supplier<List<String>> headerTooltipSupplier;
    private Supplier<List<String>> mainTooltipSupplier;
    private Supplier<List<String>> loadTooltipSupplier;
    private boolean arrowHovered = false;
    private int loadButtonWidth;

    public SubnetHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        this(y, fontRenderer, itemRender, false);
    }

    public SubnetHeader(int y, FontRenderer fontRenderer, RenderItem itemRender, boolean isMainNetwork) {
        super(y, fontRenderer, itemRender);
        this.isMain = isMainNetwork;
    }

    public void setDirectionSupplier(BooleanSupplier supplier) {
        this.directionSupplier = supplier;
    }

    public void setIsFavoriteSupplier(BooleanSupplier supplier) {
        this.isFavoriteSupplier = supplier;
    }

    public void setCanLoadSupplier(BooleanSupplier supplier) {
        this.canLoadSupplier = supplier;
    }

    public void setLocationSupplier(Supplier<String> supplier) {
        this.locationSupplier = supplier;
    }

    public void setOnStarClick(Runnable callback) {
        this.onStarClick = callback;
    }

    public void setOnLoadClick(Runnable callback) {
        this.onLoadClick = callback;
    }

    public void setHeaderTooltipSupplier(Supplier<List<String>> supplier) {
        this.headerTooltipSupplier = supplier;
    }

    public void setMainTooltipSupplier(Supplier<List<String>> supplier) {
        this.mainTooltipSupplier = supplier;
    }

    public void setLoadTooltipSupplier(Supplier<List<String>> supplier) {
        this.loadTooltipSupplier = supplier;
    }

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        arrowHovered = false;
        boolean canLoad = canLoadSupplier != null && canLoadSupplier.getAsBoolean();
        String loadText = GuiText.CellTerminalSubnetLoad.getLocal();
        int loadTextWidth = fontRenderer.getStringWidth(loadText);
        loadButtonWidth = loadTextWidth + 8;
        int loadButtonX = CellTerminalLayout.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - loadButtonWidth;
        boolean hasArrow = directionSupplier != null;
        int nameStartX = hasArrow ? (ARROW_X + ARROW_SIZE + ARROW_NAME_MARGIN) : CellTerminalLayout.HEADER_NAME_X;
        if (hasArrow) {
            drawDirectionArrow();
            int arrowY = y + (height - ARROW_SIZE) / 2;
            arrowHovered = mouseX >= ARROW_X && mouseX < ARROW_X + ARROW_SIZE
                && mouseY >= arrowY && mouseY < arrowY + ARROW_SIZE;
        }
        this.nameMaxWidth = loadButtonX - nameStartX - 4;
        drawStar();
        if (!isMain) {
            drawLocation();
        }
        int loadButtonY = y + LOAD_BUTTON_Y_OFFSET;
        drawLoadButton(loadButtonX, loadButtonY, canLoad, mouseX, mouseY);
        return loadButtonX;
    }

    private void drawStar() {
        boolean isFav = isFavoriteSupplier != null && isFavoriteSupplier.getAsBoolean();
        int offset = (CellTerminalLayout.ROW_HEIGHT - STAR_SIZE) / 2;
        Icon icon;
        if (isFav) {
            icon = Icon.CELL_TERMINAL_STAR_ON;
        } else {
            icon = Icon.CELL_TERMINAL_STAR_OFF;
        }
        icon.getBlitter().copy().dest(STAR_X + offset, y + offset, STAR_SIZE, STAR_SIZE).blit();
    }

    private void drawDirectionArrow() {
        boolean outbound = directionSupplier.getAsBoolean();
        Icon icon = outbound ? Icon.CELL_TERMINAL_ARROW_OUT : Icon.CELL_TERMINAL_ARROW_IN;
        icon.getBlitter().copy().dest(ARROW_X, y + (height - ARROW_SIZE) / 2, ARROW_SIZE, ARROW_SIZE).blit();
    }

    private void drawLocation() {
        String location = locationSupplier != null ? locationSupplier.get() : "";
        if (location.isEmpty()) {
            return;
        }
        int locationX = directionSupplier != null ? (ARROW_X + ARROW_SIZE + ARROW_NAME_MARGIN)
            : CellTerminalLayout.HEADER_NAME_X;
        int locationMaxWidth = CellTerminalLayout.CONTENT_RIGHT_EDGE - locationX - 4;
        String displayLocation = trimTextToWidth(location, locationMaxWidth);
        fontRenderer.drawString(displayLocation, locationX, y + 9, CellTerminalLayout.COLOR_TEXT_SECONDARY);
    }

    private void drawLoadButton(int x, int btnY, boolean isEnabled, int mouseX, int mouseY) {
        AE2Button button = new AE2Button(x, btnY, loadButtonWidth, LOAD_BUTTON_HEIGHT,
            GuiText.CellTerminalSubnetLoad.text(), null);
        button.enabled = isEnabled;
        button.drawButton(Minecraft.getMinecraft(), mouseX, mouseY, 0.0F);
    }

    @Override
    protected void drawIcon() {
        if (isMain) {
            Icon.CELL_TERMINAL_MAIN_NET.getBlitter().copy()
                                       .dest(CellTerminalLayout.GUI_INDENT, y, 16, 16).blit();
        } else {
            super.drawIcon();
        }
    }

    @Override
    protected void drawName(int mouseX, int mouseY) {
        String name = nameSupplier != null ? nameSupplier.get() : "";
        if (name.isEmpty()) {
            return;
        }
        boolean canLoad = canLoadSupplier != null && canLoadSupplier.getAsBoolean();
        String displayName = trimTextToWidth(name, nameMaxWidth);
        int nameColor;
        if (isMain) {
            nameColor = CellTerminalLayout.COLOR_MAIN_NETWORK;
        } else if (!canLoad) {
            nameColor = COLOR_NAME_INACCESSIBLE;
        } else if (hasCustomNameSupplier != null && hasCustomNameSupplier.getAsBoolean()) {
            nameColor = CellTerminalLayout.COLOR_CUSTOM_NAME;
        } else {
            nameColor = CellTerminalLayout.COLOR_TEXT_NORMAL;
        }
        int nameX = directionSupplier != null ? (ARROW_X + ARROW_SIZE + ARROW_NAME_MARGIN)
            : CellTerminalLayout.HEADER_NAME_X;
        int nameY = isMain ? (y + 5) : (y + 1);
        fontRenderer.drawString(displayName, nameX, nameY, nameColor);
        if (mouseX >= nameX && mouseX < nameX + nameMaxWidth && mouseY >= nameY && mouseY < nameY + 9) {
            nameHovered = true;
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 0 && isStarUnderMouse(mouseX, mouseY) && onStarClick != null) {
            onStarClick.run();
            return true;
        }
        if (button == 0 && isLoadButtonUnderMouse(mouseX, mouseY) && onLoadClick != null) {
            boolean canLoad = canLoadSupplier != null && canLoadSupplier.getAsBoolean();
            if (canLoad) {
                onLoadClick.run();
                return true;
            }
        }
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        if (isStarUnderMouse(mouseX, mouseY)) {
            return Collections.singletonList(GuiText.CellTerminalSubnetStar.getLocal());
        }
        if (isMain) {
            if (this.mainTooltipSupplier != null) {
                return this.mainTooltipSupplier.get();
            }
            return Collections.singletonList(GuiText.CellTerminalSubnetMainNetwork.getLocal());
        }
        if (isLoadButtonUnderMouse(mouseX, mouseY)) {
            return getLoadButtonTooltip();
        }
        if (arrowHovered && directionSupplier != null) {
            return getDirectionTooltip();
        }
        if (this.headerTooltipSupplier != null) {
            return this.headerTooltipSupplier.get();
        }
        return super.getTooltip(mouseX, mouseY);
    }

    private List<String> getLoadButtonTooltip() {
        if (this.loadTooltipSupplier != null) {
            return this.loadTooltipSupplier.get();
        }
        boolean canLoad = canLoadSupplier != null && canLoadSupplier.getAsBoolean();
        List<String> lines = new ArrayList<>(2);
        lines.add(GuiText.CellTerminalSubnetLoad.getLocal());
        lines.add((canLoad ? "§7" : "§c") + (canLoad
            ? GuiText.CellTerminalSubnetLoadTooltip.getLocal()
            : GuiText.CellTerminalSubnetLoadDisabled.getLocal()));
        return lines;
    }

    private List<String> getDirectionTooltip() {
        boolean outbound = directionSupplier.getAsBoolean();
        List<String> lines = new ArrayList<>(3);
        lines.add(outbound ? GuiText.CellTerminalSubnetOutbound.getLocal() : GuiText.CellTerminalSubnetInbound.getLocal());
        lines.add("");
        lines.add("§7" + (outbound
            ? GuiText.CellTerminalSubnetOutboundDesc.getLocal()
            : GuiText.CellTerminalSubnetInboundDesc.getLocal()));
        return lines;
    }

    private boolean isStarUnderMouse(int mouseX, int mouseY) {
        if (onStarClick == null) {
            return false;
        }
        int offset = (CellTerminalLayout.ROW_HEIGHT - STAR_SIZE) / 2;
        int starX = STAR_X + offset;
        return mouseX >= starX && mouseX < starX + STAR_SIZE
            && mouseY >= y + offset && mouseY < y + offset + STAR_SIZE;
    }

    private boolean isLoadButtonUnderMouse(int mouseX, int mouseY) {
        int loadButtonX = CellTerminalLayout.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - loadButtonWidth;
        int loadButtonY = y + LOAD_BUTTON_Y_OFFSET;
        return mouseX >= loadButtonX && mouseX < loadButtonX + loadButtonWidth
            && mouseY >= loadButtonY && mouseY < loadButtonY + LOAD_BUTTON_HEIGHT;
    }
}
