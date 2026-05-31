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

package ae2.client.gui.widgets;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Tooltip;
import ae2.client.gui.style.Blitter;
import ae2.core.AppEng;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.GuiButton;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

public class VerticalButtonBar implements ICompositeWidget {
    private static final Blitter BACKGROUND = Blitter.texture(
        AppEng.makeId("textures/guis/sprites/vertical_buttons_bg.png"), 21, 26);
    private static final int BACKGROUND_WIDTH = 21;
    private static final int BACKGROUND_HEIGHT = 26;
    private static final int BACKGROUND_TOP_BORDER = 2;
    private static final int BACKGROUND_BOTTOM_BORDER = 4;
    private static final int VERTICAL_SPACING = 6;
    private static final int MARGIN = 2;

    private final List<GuiButton> buttons = new ObjectArrayList<>();
    private Point screenOrigin = Point.ZERO;
    private Rectangle bounds = new Rectangle(0, 0, 0, 0);
    private Point position = Point.ZERO;

    public void add(GuiButton button) {
        buttons.add(button);
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void updateBeforeRender() {
        int currentY = position.y() + MARGIN;
        int maxWidth = 0;

        for (GuiButton button : buttons) {
            if (!button.visible) {
                continue;
            }

            button.x = screenOrigin.x() + position.x() - MARGIN - button.width;
            button.y = screenOrigin.y() + currentY;

            currentY += button.height + VERTICAL_SPACING;
            maxWidth = Math.max(button.width, maxWidth);
        }

        if (maxWidth == 0) {
            bounds = new Rectangle(0, 0, 0, 0);
        } else {
            int boundX = position.x() - maxWidth - 2 * MARGIN;
            int boundY = position.y();
            bounds = new Rectangle(boundX, boundY, maxWidth + 2 * MARGIN, currentY - boundY);
        }
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        this.screenOrigin = Point.fromTopLeft(bounds);
        for (var button : this.buttons) {
            if (button instanceof TabButton tabButton && tabButton.isFocused()) {
                tabButton.setFocused(false);
            } else if (button instanceof AECheckbox checkbox && checkbox.isFocused()) {
                checkbox.setFocused(false);
            } else if (button instanceof IconButton iconButton && iconButton.isFocused()) {
                iconButton.setFocused(false);
            }
            addWidget.accept(button);
        }
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        if (this.bounds.width <= 0 || this.bounds.height <= 0) {
            return;
        }

        int x = bounds.x + this.bounds.x - 2;
        int y = bounds.y + this.bounds.y - 1;
        int width = this.bounds.width + 1;
        int height = this.bounds.height + 4;
        int middleHeight = Math.max(0, height - BACKGROUND_TOP_BORDER - BACKGROUND_BOTTOM_BORDER);

        BACKGROUND.copy()
                  .src(0, 0, BACKGROUND_WIDTH, BACKGROUND_TOP_BORDER)
                  .dest(x, y, width, BACKGROUND_TOP_BORDER)
                  .blit();

        if (middleHeight > 0) {
            BACKGROUND.copy()
                      .src(0, BACKGROUND_TOP_BORDER, BACKGROUND_WIDTH,
                          BACKGROUND_HEIGHT - BACKGROUND_TOP_BORDER - BACKGROUND_BOTTOM_BORDER)
                      .dest(x, y + BACKGROUND_TOP_BORDER, width, middleHeight)
                      .blit();
        }

        BACKGROUND.copy()
                  .src(0, BACKGROUND_HEIGHT - BACKGROUND_BOTTOM_BORDER, BACKGROUND_WIDTH,
                      BACKGROUND_BOTTOM_BORDER)
                  .dest(x, y + height - BACKGROUND_BOTTOM_BORDER, width, BACKGROUND_BOTTOM_BORDER)
                  .blit();
    }

    @Override
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        if (this.bounds.width <= 0 || this.bounds.height <= 0) {
            return;
        }

        exclusionZones.add(new Rectangle(
            screenBounds.x + this.bounds.x - 2,
            screenBounds.y + this.bounds.y - 1,
            this.bounds.width + 1,
            this.bounds.height + 4));
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        for (GuiButton button : buttons) {
            if (button instanceof ITooltip tooltip && tooltip.isTooltipAreaVisible()) {
                Rectangle area = new Rectangle(
                    button.x - screenOrigin.x(),
                    button.y - screenOrigin.y(),
                    tooltip.getTooltipArea().width,
                    tooltip.getTooltipArea().height);
                if (mouseX >= area.x) {
                    if (mouseX < area.x + area.width && mouseY >= area.y) {

                        if (mouseY < area.y + area.height) {
                            return new Tooltip(tooltip.getTooltipMessage());
                        }
                    }
                }
            }
        }
        return null;
    }
}
