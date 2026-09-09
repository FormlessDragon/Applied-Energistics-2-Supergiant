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

package ae2.client.gui.me.items;

import ae2.api.config.ActionItems;
import ae2.client.gui.Icon;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.RecipeSelectionButton;
import ae2.client.gui.widgets.SimpleIconButton;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.core.AEConfig;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.Tooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class GuiCraftingTerm extends GuiMEStorage<ContainerCraftingTerm> {
    private final RecipeSelectionButton recipeSelectionButton;

    public GuiCraftingTerm(ContainerCraftingTerm container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                           GuiStyle style) {
        super(container, playerInventory, resolveTitle(container, title), style);

        ActionButton clearBtn = new ActionButton(ActionItems.S_STASH, container::clearCraftingGrid);
        clearBtn.setHalfSize(true);
        clearBtn.setDisableBackground(true);
        widgets.add("clearCraftingGrid", clearBtn);

        ActionButton clearToPlayerInvBtn = new ActionButton(ActionItems.S_STASH_TO_PLAYER_INV,
            container::clearToPlayerInventory);
        clearToPlayerInvBtn.setHalfSize(true);
        clearToPlayerInvBtn.setDisableBackground(true);
        widgets.add("clearToPlayerInv", clearToPlayerInvBtn);

        this.recipeSelectionButton = new RecipeSelectionButton(this, container::getRecipeCandidates,
            container::getSelectedRecipeId, container::selectRecipe);
        widgets.add("recipeConflictSelection", this.recipeSelectionButton.button());

        var rotateGridButton = new CompactCraftingIconButton(Icon.CRAFTING_GRID_ROTATE,
            ButtonToolTips.RotateCraftingGrid.text(), rotateGridTooltip(),
            () -> this.container.rotateGrid(isShiftDown()));
        widgets.add("rotateCraftingGrid", rotateGridButton);

        var balanceGridButton = new CompactCraftingIconButton(Icon.CRAFTING_GRID_BALANCE,
            ButtonToolTips.BalanceCraftingGrid.text(), balanceGridTooltip(),
            () -> this.container.balanceGrid(isShiftDown()));
        widgets.add("balanceCraftingGrid", balanceGridButton);
    }

    private static ITextComponent resolveTitle(ContainerCraftingTerm container, @Nullable ITextComponent title) {
        if (title != null) {
            return title;
        }
        if (container.getGuiTitle() != null) {
            return container.getGuiTitle();
        }
        return new TextComponentString("");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.container.setClearGridOnClose(AEConfig.instance().isClearGridOnClose());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.recipeSelectionButton.update(true);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_TAB && !isSearchFieldFocused()) {
            this.container.restoreLastRecipe(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static List<ITextComponent> rotateGridTooltip() {
        return List.of(
            ButtonToolTips.RotateCraftingGrid.text(),
            Tooltips.muted(ButtonToolTips.RotateCraftingGridDesc.text(Tooltips.getMouseButtonText(0))),
            Tooltips.muted(ButtonToolTips.RotateCraftingGridShiftDesc.text(Tooltips.getMouseButtonText(0))));
    }

    private static List<ITextComponent> balanceGridTooltip() {
        return List.of(
            ButtonToolTips.BalanceCraftingGrid.text(),
            Tooltips.muted(ButtonToolTips.BalanceCraftingGridDesc.text(Tooltips.getMouseButtonText(0))),
            Tooltips.muted(ButtonToolTips.BalanceCraftingGridShiftDesc.text(Tooltips.getMouseButtonText(0))));
    }

    private static final class CompactCraftingIconButton extends SimpleIconButton {
        private CompactCraftingIconButton(Icon icon, ITextComponent message, List<ITextComponent> tooltip,
                                          Runnable onPress) {
            super(icon, message, tooltip, onPress);
            this.width = 12;
            this.height = 12;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int yOffset = this.hovered ? 1 : 0;
            Icon background = this.hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
            background.getBlitter()
                      .dest(this.x - 1, this.y + yOffset, this.width + 2, this.height + 4)
                      .zOffset(2)
                      .blit();

            Icon icon = getIcon();
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.x, this.y + yOffset, 20.0F);
            GlStateManager.scale(0.75F, 0.75F, 1.0F);
            icon.getBlitter().dest(0, 0).blit();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

}
