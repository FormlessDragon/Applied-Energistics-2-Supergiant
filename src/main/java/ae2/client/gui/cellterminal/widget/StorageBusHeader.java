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

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class StorageBusHeader extends StorageHeader {
    private final SmallButton ioModeButton;
    private IntSupplier accessModeSupplier;
    private BooleanSupplier supportsIOModeSupplier;
    private Runnable onIOModeClick;

    public StorageBusHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
        this.ioModeButton = new SmallButton(CellTerminalLayout.BUTTON_IO_MODE_X, y, ButtonType.READ_WRITE,
            () -> {
                if (onIOModeClick != null) {
                    onIOModeClick.run();
                }
            });
    }

    public void setAccessModeSupplier(IntSupplier supplier) {
        this.accessModeSupplier = supplier;
    }

    public void setSupportsIOModeSupplier(BooleanSupplier supplier) {
        this.supportsIOModeSupplier = supplier;
    }

    public void setOnIOModeClick(Runnable callback) {
        this.onIOModeClick = callback;
    }

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        this.nameMaxWidth = getNameMaxWidth();
        drawLocation();
        drawExpandIcon();
        drawIOModeButton(mouseX, mouseY);
        if (prioritizable != null && prioritizable.supportsPriority()) {
            PriorityFieldManager.getInstance().registerField(prioritizable, y, guiLeft, guiTop, fontRenderer,
                guiStyle);
        }
        return supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean()
            ? CellTerminalLayout.BUTTON_IO_MODE_X
            : CellTerminalLayout.EXPAND_ICON_X;
    }

    private int getNameMaxWidth() {
        int rightEdge = CellTerminalLayout.EXPAND_ICON_X - 4;
        if (prioritizable != null && prioritizable.supportsPriority()) {
            rightEdge = CellTerminalLayout.CONTENT_RIGHT_EDGE
                - PriorityFieldManager.FIELD_WIDTH - PriorityFieldManager.RIGHT_MARGIN - 4;
        }
        if (supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean()) {
            rightEdge = CellTerminalLayout.BUTTON_IO_MODE_X - 4;
        }
        return Math.max(0, rightEdge - CellTerminalLayout.HEADER_NAME_X);
    }

    @Override
    protected int getHeaderHoverRightBound() {
        return supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean()
            ? CellTerminalLayout.BUTTON_IO_MODE_X
            : CellTerminalLayout.EXPAND_ICON_X;
    }

    private void drawIOModeButton(int mouseX, int mouseY) {
        boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean();
        if (!supportsIOMode) {
            return;
        }
        int accessMode = accessModeSupplier != null ? accessModeSupplier.getAsInt() : 3;
        switch (accessMode) {
            case 1 -> ioModeButton.setType(ButtonType.READ_ONLY);
            case 2 -> ioModeButton.setType(ButtonType.WRITE_ONLY);
            default -> ioModeButton.setType(ButtonType.READ_WRITE);
        }
        ioModeButton.setPosition(CellTerminalLayout.BUTTON_IO_MODE_X, y);
        ioModeButton.draw(mouseX, mouseY);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 0) {
            boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean();
            if (supportsIOMode && ioModeButton.handleClick(mouseX, mouseY, button)) {
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
        boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.getAsBoolean();
        if (supportsIOMode && ioModeButton.isHovered(mouseX, mouseY)) {
            return ioModeButton.getTooltip(mouseX, mouseY);
        }
        return super.getTooltip(mouseX, mouseY);
    }
}
