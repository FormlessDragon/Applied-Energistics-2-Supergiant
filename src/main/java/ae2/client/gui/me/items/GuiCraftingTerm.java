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
import ae2.api.stacks.AEItemKey;
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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

public class GuiCraftingTerm extends GuiMEStorage<ContainerCraftingTerm> {
    /**
     * How opaque the preview of a material that is still being crafted is drawn.
     */
    private static final float GHOST_ALPHA = 0.45F;

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
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        renderPendingGridFills();
    }

    /**
     * Shows the materials a batch order is still crafting in the crafting grid slots they will be put into, so the
     * player sees what the terminal is waiting for. They are drawn semi-transparently because they are not there yet,
     * and their slot gets the same flowing border a pinned slot has. Once the material arrived it is put into the slot
     * and both stop.
     */
    private void renderPendingGridFills() {
        for (var fill : this.container.getPendingGridFills()) {
            if (fill.slot() < 0 || fill.slot() >= 9 || !(fill.what() instanceof AEItemKey itemKey)) {
                continue;
            }

            var slot = this.container.getCraftingSlot(fill.slot());
            if (!slot.getStack().isEmpty()) {
                // The player put something into this slot themselves.
                continue;
            }

            renderGhostItem(itemKey.getReadOnlyStack(), slot.xPos, slot.yPos);
            drawSpinner(slot.xPos + 8, slot.yPos + 8);
        }
    }

    /**
     * Draws an item in the slot it will arrive in, translucent but otherwise exactly like the real item, including the
     * shading of block models.
     * <p>
     * The vanilla item renderer cannot be used for this: it writes a fully opaque alpha into every single vertex, so
     * the preview would look exactly like an item that is already in the slot. Its model is used directly instead, with
     * the same GUI transform the renderer would apply and the same quad data, only with a translucent alpha. The
     * model's display transform is applied inside the pushed matrix, like the vanilla renderer does, because it is
     * multiplied straight into the current matrix and would otherwise stay applied to everything drawn afterwards.
     */
    private void renderGhostItem(ItemStack stack, int x, int y) {
        var model = this.itemRender.getItemModelWithOverrides(stack, null, null);

        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        // Where the vanilla renderer would put this model in a GUI.
        GlStateManager.translate(x, y, 100.0F + this.itemRender.zLevel);
        GlStateManager.translate(8.0F, 8.0F, 0.0F);
        GlStateManager.scale(1.0F, -1.0F, 1.0F);
        GlStateManager.scale(16.0F, 16.0F, 16.0F);
        boolean gui3d = model.isGui3d();
        model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
        if (gui3d) {
            // The foreground layer runs with standard item lighting disabled, but block models are shaded by it.
            RenderHelper.enableGUIStandardItemLighting();
        } else {
            GlStateManager.disableLighting();
        }
        // An item model spans -0.5 to 0.5, the renderer shifts it into the slot before drawing it.
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, GHOST_ALPHA);

        emitGhostQuads(model, stack);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /**
     * Emits the quads of an item model with a translucent alpha. The quads use the vertex format of the item renderer,
     * whose alpha cannot be changed, so the vertex data is copied into a format that can carry one.
     */
    private static void emitGhostQuads(IBakedModel model, ItemStack stack) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        // The very format the item renderer bakes its quads in, which carries a per-vertex color the alpha belongs to.
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        for (var facing : EnumFacing.values()) {
            emitGhostQuads(buffer, model.getQuads(null, facing, 0L), stack);
        }
        emitGhostQuads(buffer, model.getQuads(null, null, 0L), stack);
        tessellator.draw();
    }

    private static void emitGhostQuads(BufferBuilder buffer, List<BakedQuad> quads, ItemStack stack) {
        var itemColors = Minecraft.getMinecraft().getItemColors();
        for (var quad : quads) {
            int[] data = quad.getVertexData();
            // Position, color, texture coordinates and normal, the layout the item renderer bakes its quads in.
            int stride = data.length / 4;
            if (stride != 7) {
                continue;
            }

            // Tinted quads, such as the grass on a block of grass, carry the stack's color for that tint index.
            int tintIndex = quad.getTintIndex();
            int tint = tintIndex == -1 ? -1 : itemColors.colorMultiplier(stack, tintIndex);
            float red = tint == -1 ? 1.0F : (tint >> 16 & 0xFF) / 255.0F;
            float green = tint == -1 ? 1.0F : (tint >> 8 & 0xFF) / 255.0F;
            float blue = tint == -1 ? 1.0F : (tint & 0xFF) / 255.0F;

            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                int normal = data[offset + 6];
                buffer.pos(
                        Float.intBitsToFloat(data[offset]),
                        Float.intBitsToFloat(data[offset + 1]),
                        Float.intBitsToFloat(data[offset + 2]))
                    .color(red, green, blue, GHOST_ALPHA)
                    .tex(
                        Float.intBitsToFloat(data[offset + 4]),
                        Float.intBitsToFloat(data[offset + 5]))
                    .normal(
                        (byte) (normal & 0xFF) / 127.0F,
                        (byte) (normal >> 8 & 0xFF) / 127.0F,
                        (byte) (normal >> 16 & 0xFF) / 127.0F)
                    .endVertex();
            }
        }
    }

    private void drawSpinner(int x, int y) {
        long millis = System.currentTimeMillis();
        float angle = (millis % 1000) / 1000f * 360f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);

        int dots = 6;
        int radius = 5;
        int color = 0xFFFFFF;

        for (int i = 0; i < dots; i++) {
            int alpha = 255 - (i * (200 / dots));
            int dotColor = (alpha << 24) | color;

            double rad = Math.toRadians((360f / dots) * i);
            int dx = (int) (Math.cos(rad) * radius);
            int dy = (int) (Math.sin(rad) * radius);

            Gui.drawRect(dx - 1, dy - 1, dx + 1, dy + 1, dotColor);
        }

        GlStateManager.popMatrix();
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
