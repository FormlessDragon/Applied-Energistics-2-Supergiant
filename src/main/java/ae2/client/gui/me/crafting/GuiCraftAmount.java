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

package ae2.client.gui.me.crafting;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.implementations.AESubGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.GuiNumberEntryButtonSettings;
import ae2.client.gui.widgets.NumberEntryButtonConfigButton;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.core.localization.GuiText;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

/**
 * When requesting to auto-craft, this dialog allows the player to enter the desired number of items to craft.
 */
public class GuiCraftAmount extends AEBaseGui<ContainerCraftAmount> {

    private final ITextComponent nextText = GuiText.Next.text();
    private final ITextComponent startText = GuiText.Start.text();
    private final AE2Button next;
    private final NumberEntryWidget amountToCraft;
    private boolean amountInitialized;
    private boolean shiftDown;

    public GuiCraftAmount(ContainerCraftAmount container, InventoryPlayer playerInventory, ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);

        this.next = widgets.addButton("next", this.nextText, this::confirm);

        AESubGui.addBackButton(container, "back", widgets,
            container.hasExternalGuiReturn() ? GuiText.ReturnToPreviousGui.text() : null);
        widgets.add("numberEntryButtonConfig", new NumberEntryButtonConfigButton(this::openNumberEntryButtonSettings));

        this.amountToCraft = widgets.addNumberEntryWidget("amountToCraft", NumberEntryType.UNITLESS);
        this.amountToCraft.setMinValue(1);
        this.amountToCraft.setMaxValue(ContainerCraftAmount.MAX_AUTO_CRAFT_AMOUNT);
        this.amountToCraft.setLongValue(1);
        this.amountToCraft.setTextFieldStyle(style.getWidget("amountToCraftInput"));
        this.amountToCraft.setPreviewFieldStyle(style.getWidget("amountToCraftPreview"));
        this.amountToCraft.setHideValidationIcon(true);
        this.amountToCraft.setOnConfirm(this::confirm);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (!this.amountInitialized) {
            var whatToCraft = container.getWhatToCraft();
            if (whatToCraft != null) {
                this.amountToCraft.setType(NumberEntryType.of(whatToCraft.what()));
                this.amountToCraft.setLongValue(container.getInitialAmount());
                this.amountInitialized = true;
            }
        }

        boolean newShiftDown = GuiScreen.isShiftKeyDown();
        if (this.shiftDown != newShiftDown) {
            this.shiftDown = newShiftDown;
            this.next.setMessage(newShiftDown ? this.startText : this.nextText);
        }
        this.next.enabled = this.amountToCraft.getIntValue().orElse(0) > 0;
    }

    private void confirm() {
        int amount = this.amountToCraft.getIntValue().orElse(0);
        boolean craftMissingAmount = this.amountToCraft.startsWithEquals();
        if (amount <= 0) {
            return;
        }
        container.confirm(amount, craftMissingAmount, GuiScreen.isShiftKeyDown());
    }

    private void openNumberEntryButtonSettings() {
        switchToScreen(new GuiNumberEntryButtonSettings(this));
    }
}
