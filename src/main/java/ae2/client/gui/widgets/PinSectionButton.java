/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * A toolbar button that uses left/right click to grow or shrink the terminal pin section.
 */
public final class PinSectionButton extends IconButton {
    private final Listener listener;
    private final Supplier<List<ITextComponent>> tooltipSupplier;
    private boolean pressedBackwards;
    private boolean triggeredOnPress;

    public PinSectionButton(Listener listener, Supplier<List<ITextComponent>> tooltipSupplier) {
        super(null);
        this.listener = listener;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    protected Icon getIcon() {
        return Icon.PLAYER_PIN;
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        return this.tooltipSupplier.get();
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        var currentScreen = Minecraft.getMinecraft().currentScreen;
        boolean backwards = currentScreen instanceof AEBaseGui<?> baseGui && baseGui.isHandlingRightClick();
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        if (pressed) {
            this.pressedBackwards = backwards;
            this.listener.onPress(backwards);
            this.triggeredOnPress = true;
        } else {
            this.pressedBackwards = false;
            this.triggeredOnPress = false;
        }
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
        if (releasedInside && !this.triggeredOnPress) {
            this.listener.onPress(this.pressedBackwards);
        }
        this.pressedBackwards = false;
        this.triggeredOnPress = false;
    }

    @FunctionalInterface
    public interface Listener {
        void onPress(boolean backwards);
    }
}
