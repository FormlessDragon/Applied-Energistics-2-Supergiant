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

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.me.common.ClientDisplaySlot;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * Shows detailed error information about a failed attempt at submitting a crafting job.
 */
public class GuiCraftError extends AEBaseGui<ContainerCraftConfirm> {
    private static final String STYLE_PATH = "/screens/craft_error.json";

    private final GuiCraftConfirm parent;

    public GuiCraftError(GuiCraftConfirm parent, CraftingSubmitErrorCode errorCode, Object details) {
        this(parent, GuiStyleManager.loadStyleDoc(STYLE_PATH), errorCode, details);
    }

    private GuiCraftError(GuiCraftConfirm parent, GuiStyle style, CraftingSubmitErrorCode errorCode, Object details) {
        super(parent.getContainer(), getPlayerInventory(parent), style);
        this.parent = parent;

        ITextComponent errorText;
        switch (errorCode) {
            case INCOMPLETE_PLAN -> errorText = GuiText.CraftErrorIncompletePlan.text();
            case NO_CPU_FOUND -> errorText = GuiText.CraftErrorNoCpuFound.text();
            case NO_SUITABLE_CPU_FOUND -> errorText = getNoSuitableCpuErrorText(details);
            case CPU_BUSY -> errorText = GuiText.CraftErrorCpuBusy.text();
            case CPU_OFFLINE -> errorText = GuiText.CraftErrorCpuOffline.text();
            case CPU_TOO_SMALL -> errorText = GuiText.CraftErrorCpuTooSmall.text();
            case MISSING_INGREDIENT -> {
                if (details instanceof GenericStack) {
                    setMissingIngredientSlot((GenericStack) details);
                }
                errorText = GuiText.CraftErrorMissingIngredient.text();
            }
            default -> throw new IllegalArgumentException("Unsupported crafting submit error code: " + errorCode);
        }

        setTextContent("errorText", errorText);

        widgets.addButton("replan", GuiText.CraftErrorReplan.text(), () -> {
            returnToParent();
            container.replan();
        });
        widgets.addButton("retry", GuiText.CraftErrorRetry.text(), () -> {
            returnToParent();
            container.startJob();
        });
        widgets.addButton("cancel", GuiText.Cancel.text(), () -> {
            returnToParent();
            container.goBack();
        });
    }

    private static InventoryPlayer getPlayerInventory(GuiCraftConfirm parent) {
        return parent.getContainer().getPlayerInventory();
    }

    private ITextComponent getNoSuitableCpuErrorText(Object details) {
        ITextComponent text = GuiText.CraftErrorNoSuitableCpu.text();
        if (details instanceof UnsuitableCpus(int offline, int busy, int tooSmall, int excluded)) {
            ObjectList<ITextComponent> stats = new ObjectArrayList<>();
            if (offline > 0) {
                stats.add(GuiText.CraftErrorNoSuitableCpuOffline.text(offline));
            }
            if (busy > 0) {
                stats.add(GuiText.CraftErrorNoSuitableCpuBusy.text(busy));
            }
            if (tooSmall > 0) {
                stats.add(GuiText.CraftErrorNoSuitableCpuTooSmall.text(tooSmall));
            }
            if (excluded > 0) {
                stats.add(GuiText.CraftErrorNoSuitableCpuExcluded.text(excluded));
            }

            ITextComponent suffix = new TextComponentString("(");
            for (int i = 0; i < stats.size(); i++) {
                ITextComponent stat = stats.get(i);
                if (i != 0) {
                    suffix.appendText(", ");
                }
                suffix.appendSibling(stat);
            }
            suffix.appendText(")");
            text.appendText(" ").appendSibling(suffix);
        }
        return text;
    }

    private void setMissingIngredientSlot(GenericStack genericStack) {
        for (Slot slot : new ObjectArrayList<>(container.getSlots(SlotSemantics.MISSING_INGREDIENT))) {
            if (container.isClientSideSlot(slot)) {
                container.removeClientSideSlot(slot);
            }
        }

        container.addClientSideSlot(new ClientDisplaySlot(genericStack), SlotSemantics.MISSING_INGREDIENT);
        repositionSlots(SlotSemantics.MISSING_INGREDIENT);
    }

    private void returnToParent() {
        for (Slot slot : new ObjectArrayList<>(container.getSlots(SlotSemantics.MISSING_INGREDIENT))) {
            if (container.isClientSideSlot(slot)) {
                container.removeClientSideSlot(slot);
            }
        }
        container.clearError();
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }
}
