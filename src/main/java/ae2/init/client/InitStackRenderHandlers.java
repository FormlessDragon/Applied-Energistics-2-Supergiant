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

package ae2.init.client;

import ae2.api.client.AEKeyRenderHandler;
import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.style.FluidBlitter;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.ItemDefinition;
import ae2.crafting.pattern.EncodedPatternItem;
import ae2.items.misc.WrappedGenericStack;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.Function;

public final class InitStackRenderHandlers {
    static final GuiRenderCleanupState GUI_RENDER_CLEANUP_STATE = new GuiRenderCleanupState(true, false, false);

    private InitStackRenderHandlers() {
    }

    public static void init() {
        AEKeyRendering.register(AEKeyType.items(), AEItemKey.class, new ItemKeyRenderHandler());
        AEKeyRendering.register(AEKeyType.fluids(), AEFluidKey.class, new FluidKeyRenderHandler());
        installDelegatingRenderer(AEItems.CRAFTING_PATTERN, InitStackRenderHandlers::resolveEncodedPatternOutput);
        installDelegatingRenderer(AEItems.PROCESSING_PATTERN, InitStackRenderHandlers::resolveEncodedPatternOutput);
        installDelegatingRenderer(AEItems.WRAPPED_GENERIC_STACK, InitStackRenderHandlers::resolveWrappedStackItem);
    }

    private static void installDelegatingRenderer(ItemDefinition<?> definition, Function<ItemStack, ItemStack> resolver) {
        var item = definition.asItem();
        if (item == null) {
            return;
        }
        item.setTileEntityItemStackRenderer(new DelegatingItemStackRenderer(resolver));
    }

    private static ItemStack resolveEncodedPatternOutput(ItemStack stack) {
        if (!(stack.getItem() instanceof EncodedPatternItem<?> encodedPattern)) {
            return ItemStack.EMPTY;
        }

        World level = Minecraft.getMinecraft().world;
        if (level == null) {
            return ItemStack.EMPTY;
        }

        return encodedPattern.getOutput(stack, level);
    }

    private static ItemStack resolveWrappedStackItem(ItemStack stack) {
        if (!(stack.getItem() instanceof WrappedGenericStack)) {
            return ItemStack.EMPTY;
        }

        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack == null || !(genericStack.what() instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }

        return itemKey.toStack();
    }

    static GuiRenderCleanupState getGuiRenderCleanupState() {
        return GUI_RENDER_CLEANUP_STATE;
    }

    private static void restoreGuiRenderState() {
        if (GUI_RENDER_CLEANUP_STATE.blendEnabled()) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        if (GUI_RENDER_CLEANUP_STATE.depthEnabled()) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        if (GUI_RENDER_CLEANUP_STATE.lightingEnabled()) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final class ItemKeyRenderHandler implements AEKeyRenderHandler<AEItemKey> {
        @Override
        public void drawInGui(Minecraft minecraft, int x, int y, AEItemKey stack) {
            ItemStack displayStack = stack.getReadOnlyStack();
            RenderItem itemRenderer = minecraft.getRenderItem();
            GlStateManager.pushMatrix();
            try {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                itemRenderer.renderItemAndEffectIntoGUI(displayStack, x, y);
                itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, displayStack, x, y, "");
            } finally {
                RenderHelper.disableStandardItemLighting();
                restoreGuiRenderState();
                GlStateManager.popMatrix();
            }
        }

        @Override
        public void drawOnBlockFace(AEItemKey what, float scale, int combinedLight, World level) {
            ItemStack displayStack = what.getReadOnlyStack();
            if (displayStack.isEmpty()) {
                return;
            }

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

            GlStateManager.pushMatrix();
            try {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableNormalize();
                GlStateManager.scale(scale / 16.0f, scale / 16.0f, 0.0001f);
                GlStateManager.translate(-8.0f, -8.0f, 0.0f);
                RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
                renderItem.renderItemAndEffectIntoGUI(displayStack, 0, 0);
            } finally {
                GlStateManager.disableNormalize();
                RenderHelper.disableStandardItemLighting();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.popMatrix();
            }
        }

        @Override
        public ITextComponent getDisplayName(AEItemKey stack) {
            return stack.getDisplayName();
        }

        @Override
        public List<ITextComponent> getTooltip(AEItemKey stack) {
            Minecraft minecraft = Minecraft.getMinecraft();
            List<String> lines = stack.getReadOnlyStack().getTooltip(
                minecraft.player,
                minecraft.gameSettings.advancedItemTooltips
                    ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED
                    : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            List<ITextComponent> result = new ObjectArrayList<>(lines.size());
            for (String line : lines) {
                result.add(new TextComponentString(line));
            }
            return result;
        }
    }

    private static final class FluidKeyRenderHandler implements AEKeyRenderHandler<AEFluidKey> {
        @Override
        public void drawInGui(Minecraft minecraft, int x, int y, AEFluidKey what) {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.disableLighting();
                FluidBlitter.create(what)
                            .dest(x, y, 16, 16)
                            .blit();
            } finally {
                restoreGuiRenderState();
                GlStateManager.popMatrix();
            }
        }

        @Override
        public void drawOnBlockFace(AEFluidKey what, float scale, int combinedLight, World level) {
            FluidStack fluidStack = what.toStack(1);
            Fluid fluid = what.getFluid();
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                                                 .getAtlasSprite(fluid.getStill(fluidStack).toString());
            int color = fluid.getColor(fluidStack);
            float r = (color >> 16 & 255) / 255.0f;
            float g = (color >> 8 & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            float a = (color >> 24 & 255) / 255.0f;
            if (a <= 0.0f) {
                a = 1.0f;
            }

            float x0 = -scale / 2.0f;
            float y0 = -scale / 2.0f;
            float x1 = scale / 2.0f;
            float y1 = scale / 2.0f;

            GlStateManager.pushMatrix();
            try {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                GlStateManager.disableAlpha();
                GlStateManager.disableLighting();
                GlStateManager.disableCull();
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

                Tessellator tessellator = Tessellator.getInstance();
                var buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                buffer.pos(x0, y0, 0.0001f).tex(sprite.getMinU(), sprite.getMinV()).color(r, g, b, a).endVertex();
                buffer.pos(x0, y1, 0.0001f).tex(sprite.getMinU(), sprite.getMaxV()).color(r, g, b, a).endVertex();
                buffer.pos(x1, y1, 0.0001f).tex(sprite.getMaxU(), sprite.getMaxV()).color(r, g, b, a).endVertex();
                buffer.pos(x1, y0, 0.0001f).tex(sprite.getMaxU(), sprite.getMinV()).color(r, g, b, a).endVertex();
                tessellator.draw();
            } finally {
                GlStateManager.enableCull();
                GlStateManager.enableLighting();
                GlStateManager.enableAlpha();
                GlStateManager.disableBlend();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.popMatrix();
            }
        }

        @Override
        public ITextComponent getDisplayName(AEFluidKey stack) {
            return stack.getDisplayName();
        }

        @Override
        public List<ITextComponent> getTooltip(AEFluidKey stack) {
            List<ITextComponent> tooltip = new ObjectArrayList<>();
            tooltip.add(stack.toStack(1).getLocalizedName().isEmpty()
                ? stack.getDisplayName()
                : new TextComponentString(stack.toStack(1).getLocalizedName()));
            tooltip.add(new TextComponentString(Platform.getModName(stack.getModId())));
            return tooltip;
        }
    }

    private static final class DelegatingItemStackRenderer extends TileEntityItemStackRenderer {
        private final Function<ItemStack, ItemStack> displayStackResolver;

        private DelegatingItemStackRenderer(Function<ItemStack, ItemStack> displayStackResolver) {
            this.displayStackResolver = displayStackResolver;
        }

        @Override
        public void renderByItem(ItemStack stack) {
            ItemStack displayStack = this.displayStackResolver.apply(stack);
            if (displayStack.isEmpty()) {
                return;
            }

            if (displayStack.getItem() == stack.getItem()) {
                return;
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            RenderItem renderItem = minecraft.getRenderItem();
            IBakedModel model = renderItem.getItemModelWithOverrides(displayStack, minecraft.world, minecraft.player);
            if (model.isBuiltInRenderer()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableRescaleNormal();
                displayStack.getItem().getTileEntityItemStackRenderer().renderByItem(displayStack);
                return;
            }
            renderItem.renderItem(displayStack, model);
        }
    }

    record GuiRenderCleanupState(boolean blendEnabled, boolean depthEnabled, boolean lightingEnabled) {
    }
}
