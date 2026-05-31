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

import ae2.client.gui.Icon;
import ae2.client.gui.style.Blitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public abstract class IconButton extends GuiButton implements ITooltip {

    private final Runnable onPress;
    private final RenderItem itemRenderer;

    private boolean halfSize;
    private boolean disableClickSound;
    private boolean disableBackground;
    private boolean focused;
    private ITextComponent message = new TextComponentString("");

    public IconButton(Runnable onPress) {
        super(0, 0, 0, 16, 16, "");
        this.onPress = onPress;
        this.itemRenderer = Minecraft.getMinecraft().getRenderItem();
        this.packedFGColour = 0;
    }

    public void setVisibility(boolean vis) {
        this.visible = vis;
        this.enabled = vis;
    }

    @Override
    public void playPressSound(SoundHandler soundHandler) {
        if (!disableClickSound) {
            super.playPressSound(soundHandler);
        }
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        this.focused = pressed;
        return pressed;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean releasedInside = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        if (releasedInside && this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

        var icon = this.getIcon();
        var item = this.getItemStackOverlay();
        int yOffset = this.hovered ? 1 : 0;

        if (this.halfSize) {
            if (!disableBackground) {
                Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(this.x, this.y).zOffset(10).blit();
            }
            if (!item.isEmpty()) {
                renderItem(minecraft, item, this.x, this.y, 20);
            } else if (icon != null) {
                Blitter blitter = icon.getBlitter().copy();
                if (!this.enabled) {
                    blitter.opacity(0.5f);
                }
                blitter.dest(this.x, this.y).zOffset(20).blit();
            }
        } else {
            if (!disableBackground) {
                Icon bgIcon = this.hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

                bgIcon.getBlitter()
                      .dest(this.x - 1, this.y + yOffset, 18, 20)
                      .zOffset(2)
                      .blit();
            }
            if (!item.isEmpty()) {
                renderItem(minecraft, item, this.x, this.y + 1 + yOffset, 3);
            } else if (icon != null) {
                icon.getBlitter().dest(this.x, this.y + 1 + yOffset).zOffset(3).blit();
            }
        }
    }

    protected abstract Icon getIcon();

    @Nullable
    protected Item getItemOverlay() {
        return null;
    }

    protected ItemStack getItemStackOverlay() {
        Item item = getItemOverlay();
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public void setMessage(ITextComponent message) {
        this.message = message == null ? new TextComponentString("") : message;
        this.displayString = this.message.getFormattedText();
    }

    public ITextComponent getMessageComponent() {
        return this.message;
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return Collections.singletonList(getMessageComponent());
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(
            this.x,
            this.y,
            this.halfSize ? 8 : 16,
            this.halfSize ? 8 : 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    @SuppressWarnings("unused")
    public boolean isHalfSize() {
        return this.halfSize;
    }

    public void setHalfSize(boolean halfSize) {
        this.halfSize = halfSize;
        this.width = halfSize ? 8 : 16;
        this.height = halfSize ? 8 : 16;
    }

    @SuppressWarnings("unused")
    public boolean isDisableClickSound() {
        return disableClickSound;
    }

    public void setDisableClickSound(boolean disableClickSound) {
        this.disableClickSound = disableClickSound;
    }

    @SuppressWarnings("unused")
    public boolean isDisableBackground() {
        return disableBackground;
    }

    public void setDisableBackground(boolean disableBackground) {
        this.disableBackground = disableBackground;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    private void renderItem(Minecraft minecraft, ItemStack itemStack, int x, int y, int zOffset) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, zOffset);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRenderer.renderItemAndEffectIntoGUI(itemStack, x, y);
        this.itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, itemStack, x, y, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }
}
