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

import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.style.Blitter;
import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.awt.Rectangle;
import java.time.Duration;

/**
 * Implements a vertical scrollbar using Vanilla's scrollbar handle texture from the creative tab.
 * <p>
 * It is expected that the background of the UI contains a pre-baked scrollbar track border, and that the exact
 * rectangle of that track is set on this object via {@link #displayX}, {@link #displayY} and {@link #setHeight(int)}.
 * While the width of the track can also be set, the drawn handle will use vanilla's sprite width (see
 * {@link Style#handleWidth()}.
 */
public class Scrollbar implements IScrollSource, ICompositeWidget {

    public static final Style DEFAULT = Style.sheet(
        new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png"),
        new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png"),
        232,
        0,
        244,
        0,
        12,
        15,
        256,
        256);
    public static final Style BIG = Style.create(
        AppEng.makeId("textures/guis/sprites/big_scroller.png"),
        AppEng.makeId("textures/guis/sprites/big_scroller_disabled.png"),
        12,
        15);
    public static final Style SMALL = Style.create(
        AppEng.makeId("textures/guis/sprites/small_scroller.png"),
        AppEng.makeId("textures/guis/sprites/small_scroller_disabled.png"),
        7,
        15);
    private static final int MOUSE_BUTTON_LEFT = 0;
    private final Style style;
    private final EventRepeater eventRepeater = new EventRepeater(Duration.ofMillis(250), Duration.ofMillis(150));
    private boolean visible = true;
    /**
     * The screen x-coordinate of the scrollbar's inner track.
     */
    private int displayX = 0;
    /**
     * The screen y-coordinate of the scrollbar's inner track.
     */
    private int displayY = 0;
    /**
     * The inner height of the scrollbar track.
     */
    private int height = 16;
    private int pageSize = 1;
    private int maxScroll = 0;
    private int minScroll = 0;
    private int currentScroll = 0;
    /**
     * True if the scrollbar's handle is currently being dragged.
     */
    private boolean dragging;
    /**
     * The y-coordinate relative to the upper edge of the scrollbar handle, where the user pressed the mouse button to
     * drag. While dragging, this is applied as an offset to the effective scrollbar position.
     */
    private int dragYOffset;
    /**
     * Capture all mouse wheel events to make it scroll when the mouse wheel is used anywhere on the screen.
     */
    private boolean captureMouseWheel = true;

    public Scrollbar(Style style) {
        this.style = style;
    }

    public Scrollbar() {
        this(DEFAULT);
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(displayX, displayY, style.handleWidth(), height);
    }

    /**
     * Draws the handle of the scrollbar.
     * <p>
     * The GUI is assumed to already contain a prebaked scrollbar track in its background.
     */
    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        int yOffset;
        Blitter image;
        if (this.getRange() == 0) {
            yOffset = 0;
            image = style.disabledBlitter();
        } else {
            yOffset = getHandleYOffset();
            image = style.enabledBlitter();
        }

        image.dest(this.displayX, this.displayY + yOffset).blit();
    }

    /**
     * Returns the y-position of the scrollbar handle in relation to the upper edge of the scrollbar's track.
     */
    private int getHandleYOffset() {
        if (getRange() == 0) {
            return 0;
        }
        int availableHeight = this.height - style.handleHeight();
        return (this.currentScroll - this.minScroll) * availableHeight / this.getRange();
    }

    private int getRange() {
        return this.maxScroll - this.minScroll;
    }

    public Scrollbar setHeight(int v) {
        this.height = v;
        return this;
    }

    @Override
    public void setPosition(Point position) {
        this.displayX = position.x();
        this.displayY = position.y();
    }

    @Override
    public void setSize(int width, int height) {
        if (height != 0) {
            this.height = height;
        }
    }

    public void setRange(int min, int max, int pageSize) {
        this.minScroll = min;
        this.maxScroll = max;
        this.pageSize = pageSize;

        if (this.minScroll > this.maxScroll) {
            this.maxScroll = this.minScroll;
        }

        this.applyRange();
    }

    private void applyRange() {
        this.currentScroll = Math.clamp(this.currentScroll, this.minScroll, this.maxScroll);
    }

    @Override
    public int getCurrentScroll() {
        return this.currentScroll;
    }

    public void setCurrentScroll(int currentScroll) {
        this.currentScroll = currentScroll;
        this.applyRange();
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (button != MOUSE_BUTTON_LEFT) {
            return false;
        }

        this.dragging = false;

        if (getRange() == 0) {
            return true;
        }

        int relY = mousePos.y() - displayY;
        int handleYOffset = getHandleYOffset();

        if (relY < handleYOffset) {
            pageUp();
            eventRepeater.repeat(this::pageUp);
        } else if (relY < handleYOffset + style.handleHeight()) {
            this.dragging = true;
            this.dragYOffset = relY - handleYOffset;
        } else {
            pageDown();
            eventRepeater.repeat(this::pageDown);
        }

        return true;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (button == MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            this.eventRepeater.stop();
        }
        return false;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (this.getRange() == 0 || !this.dragging || this.eventRepeater.isRepeating()) {
            return false;
        }

        double handleUpperEdgeY = mousePos.y() - this.displayY - this.dragYOffset;
        double availableHeight = this.height - style.handleHeight();
        double position = MathHelper.clamp(handleUpperEdgeY / availableHeight, 0.0, 1.0);

        this.currentScroll = this.minScroll + (int) Math.round(position * this.getRange());
        this.applyRange();
        return true;
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (getRange() == 0) {
            return false;
        }

        delta = Math.clamp(-delta, -1, 1);
        this.currentScroll += (int) (delta * this.pageSize);
        this.applyRange();
        return true;
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return captureMouseWheel;
    }

    public void setCaptureMouseWheel(boolean captureMouseWheel) {
        this.captureMouseWheel = captureMouseWheel;
    }

    /**
     * Ticks the scrollbar for the purposes of input-repeats (since mouse-downs are not repeat-triggered), used to
     * repeatedly page-up or page-down when the mouse is held in the area above or below the scrollbar handle.
     */
    @Override
    public void tick() {
        this.eventRepeater.tick();
    }

    private void pageUp() {
        this.currentScroll -= this.pageSize;
        this.applyRange();
    }

    private void pageDown() {
        this.currentScroll += this.pageSize;
        this.applyRange();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public static final class Style {
        private final ResourceLocation enabledSprite;
        private final ResourceLocation disabledSprite;
        private final int enabledX;
        private final int enabledY;
        private final int disabledX;
        private final int disabledY;
        private final int handleWidth;
        private final int handleHeight;
        private final int textureWidth;
        private final int textureHeight;

        private Style(
            ResourceLocation enabledSprite,
            ResourceLocation disabledSprite,
            int enabledX,
            int enabledY,
            int disabledX,
            int disabledY,
            int handleWidth,
            int handleHeight,
            int textureWidth,
            int textureHeight) {
            this.enabledSprite = enabledSprite;
            this.disabledSprite = disabledSprite;
            this.enabledX = enabledX;
            this.enabledY = enabledY;
            this.disabledX = disabledX;
            this.disabledY = disabledY;
            this.handleWidth = handleWidth;
            this.handleHeight = handleHeight;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        public static Style create(
            ResourceLocation enabledSprite,
            ResourceLocation disabledSprite,
            int handleWidth,
            int handleHeight) {
            return new Style(
                enabledSprite,
                disabledSprite,
                0,
                0,
                0,
                0,
                handleWidth,
                handleHeight,
                handleWidth,
                handleHeight);
        }

        public static Style sheet(
            ResourceLocation enabledSprite,
            ResourceLocation disabledSprite,
            int enabledX,
            int enabledY,
            int disabledX,
            int disabledY,
            int handleWidth,
            int handleHeight,
            int textureWidth,
            int textureHeight) {
            return new Style(
                enabledSprite,
                disabledSprite,
                enabledX,
                enabledY,
                disabledX,
                disabledY,
                handleWidth,
                handleHeight,
                textureWidth,
                textureHeight);
        }

        public int handleWidth() {
            return handleWidth;
        }

        public int handleHeight() {
            return handleHeight;
        }

        private Blitter enabledBlitter() {
            return Blitter.texture(enabledSprite, textureWidth, textureHeight)
                          .src(enabledX, enabledY, handleWidth, handleHeight);
        }

        private Blitter disabledBlitter() {
            return Blitter.texture(disabledSprite, textureWidth, textureHeight)
                          .src(disabledX, disabledY, handleWidth, handleHeight);
        }
    }
}

