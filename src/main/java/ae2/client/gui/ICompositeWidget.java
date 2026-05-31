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

package ae2.client.gui;

import ae2.client.Point;
import net.minecraft.client.gui.GuiButton;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

public interface ICompositeWidget {

    default boolean isVisible() {
        return true;
    }

    void setPosition(Point position);

    void setSize(int width, int height);

    Rectangle getBounds();

    default boolean hitTest(Point mousePos) {
        return mousePos.isIn(getBounds());
    }

    default void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        Rectangle bounds = getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }

        if (bounds.x < 0
            || bounds.y < 0
            || bounds.x + bounds.width > screenBounds.width
            || bounds.y + bounds.height > screenBounds.height) {
            exclusionZones.add(new Rectangle(
                screenBounds.x + bounds.x,
                screenBounds.y + bounds.y,
                bounds.width,
                bounds.height));
        }
    }

    default void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
    }

    default void tick() {
    }

    default void updateBeforeRender() {
    }

    default void drawBackgroundLayer(Rectangle bounds, Point mouse) {
    }

    default void drawForegroundLayer(Rectangle bounds, Point mouse) {
    }

    default void drawAbsoluteLayer(Rectangle bounds, Point mouse) {
    }

    default boolean onMouseDown(Point mousePos, int button) {
        return false;
    }

    default boolean wantsAllMouseDownEvents() {
        return false;
    }

    default boolean onMouseUp(Point mousePos, int button) {
        return false;
    }

    default boolean wantsAllMouseUpEvents() {
        return false;
    }

    default boolean onMouseDrag(Point mousePos, int button) {
        return false;
    }

    default boolean onMouseWheel(Point mousePos, double delta) {
        return false;
    }

    default boolean wantsAllMouseWheelEvents() {
        return false;
    }

    default boolean onKeyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Nullable
    default Tooltip getTooltip(int mouseX, int mouseY) {
        return null;
    }

    default boolean blocksTooltips(int mouseX, int mouseY) {
        return false;
    }

}
