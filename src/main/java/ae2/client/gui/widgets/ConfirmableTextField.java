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

import ae2.client.gui.style.GuiStyle;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

public class ConfirmableTextField extends AETextField {

    private Runnable onConfirm;
    private Consumer<String> responder;

    public ConfirmableTextField(GuiStyle style, FontRenderer fontRenderer, int x, int y, int width, int height) {
        super(style, fontRenderer, x, y, width, height);
    }

    @Override
    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        if (canConsumeInput() && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            if (this.onConfirm != null) {
                this.onConfirm.run();
            }
            return true;
        }

        String oldValue = this.getText();
        boolean result = super.textboxKeyTyped(typedChar, keyCode);
        if (!oldValue.equals(this.getText())) {
            this.notifyResponder();
        }
        return result;
    }

    @Override
    public void setText(String textIn) {
        String oldValue = this.getText();
        super.setText(textIn);
        if (!oldValue.equals(this.getText())) {
            this.notifyResponder();
        }
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public void setOnConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm;
    }

    private void notifyResponder() {
        if (this.responder != null) {
            this.responder.accept(this.getText());
        }
    }
}

