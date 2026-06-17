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

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public class TabButton extends GuiButton implements ITooltip {
    private final Icon icon;
    private final ItemStack item;
    private final ITextComponent message;
    private final RenderItem itemRenderer;
    private final Runnable onPress;
    private Style style = Style.BOX;
    private boolean selected;
    private boolean disableBackground;
    private boolean focused;
    private Icon defaultBackground;
    private Icon selectedBackground;
    private Icon focusedBackground;
    private boolean focusedByMousePress;

    public TabButton(Icon icon, ITextComponent message, Runnable onPress) {
        this(icon, ItemStack.EMPTY, message, onPress);
    }

    @SuppressWarnings("unused")
    public TabButton(ItemStack item, ITextComponent message, Runnable onPress) {
        this(null, item, message, onPress);
    }

    private TabButton(Icon icon, ItemStack item, ITextComponent message, Runnable onPress) {
        super(0, 0, 0, 22, 22, "");
        this.icon = icon;
        this.item = item;
        this.message = message;
        this.itemRenderer = Minecraft.getMinecraft().getRenderItem();
        this.displayString = "";
        this.packedFGColour = 0;
        this.onPress = onPress;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean pressed = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        clearMousePressFocus();
        if (pressed && this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        boolean wasFocused = this.focused;
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        if (pressed) {
            this.focused = true;
            this.focusedByMousePress = !wasFocused;
            if (isImmediateRightClickDispatch()) {
                clearMousePressFocus();
            }
        } else {
            this.focused = false;
            this.focusedByMousePress = false;
        }
        return pressed;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

        Icon backdrop = switch (this.style) {
            case CORNER -> this.isFocused() ? Icon.TAB_BUTTON_BACKGROUND_BORDERLESS_FOCUS
                : Icon.TAB_BUTTON_BACKGROUND_BORDERLESS;
            case HORIZONTAL -> {
                if (this.isFocused() && this.focusedBackground != null) {
                    yield this.focusedBackground;
                }
                if (this.selected && this.selectedBackground != null) {
                    yield this.selectedBackground;
                }
                if (this.defaultBackground != null) {
                    yield this.defaultBackground;
                }
                if (this.isFocused()) {
                    yield Icon.HORIZONTAL_TAB_FOCUS;
                }
                if (this.selected) {
                    yield Icon.HORIZONTAL_TAB_SELECTED;
                }
                yield Icon.HORIZONTAL_TAB;
            }
            case BOX -> this.isFocused() ? Icon.TAB_BUTTON_BACKGROUND_FOCUS : Icon.TAB_BUTTON_BACKGROUND;
        };

        if (!this.disableBackground) {
            backdrop.getBlitter().dest(this.x, this.y).blit();
        }

        int iconX;
        int iconY = switch (this.style) {
            case CORNER -> {
                iconX = 1;
                yield 1;
            }
            case HORIZONTAL -> {
                iconX = 3;
                yield 3;
            }
            default -> {
                iconX = 2;
                yield 2;
            }
        };

        if (this.icon != null) {
            this.icon.getBlitter().dest(this.x + iconX, this.y + iconY - 1).blit();
        }

        if (!this.item.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, -1, 100);
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRenderer.renderItemAndEffectIntoGUI(this.item, this.x + iconX, this.y + iconY);
            this.itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, this.item, this.x + iconX, this.y + iconY,
                null);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return Collections.singletonList(this.message);
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(this.x, this.y, this.width, this.height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    public Style getStyle() {
        return this.style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @SuppressWarnings("unused")
    public boolean isDisableBackground() {
        return this.disableBackground;
    }

    @SuppressWarnings("unused")
    public void setDisableBackground(boolean disableBackground) {
        this.disableBackground = disableBackground;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        this.focusedByMousePress = false;
    }

    private void clearMousePressFocus() {
        if (this.focusedByMousePress) {
            this.focused = false;
        }
        this.focusedByMousePress = false;
    }

    private static boolean isImmediateRightClickDispatch() {
        var currentScreen = Minecraft.getMinecraft().currentScreen;
        return currentScreen instanceof AEBaseGui<?> baseGui && baseGui.isHandlingRightClick();
    }

    public void setHorizontalBackgrounds(Icon defaultBackground, Icon selectedBackground, Icon focusedBackground) {
        this.defaultBackground = defaultBackground;
        this.selectedBackground = selectedBackground;
        this.focusedBackground = focusedBackground;
    }

    public enum Style {
        CORNER,
        BOX,
        HORIZONTAL
    }
}
