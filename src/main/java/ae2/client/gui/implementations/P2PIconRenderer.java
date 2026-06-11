package ae2.client.gui.implementations;

import ae2.api.parts.IPartItem;
import ae2.parts.p2p.P2PModels;
import ae2.parts.p2p.P2PTunnelPart;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Map;

final class P2PIconRenderer {
    private static final int ICON_SIZE = 16;

    private final Map<ResourceLocation, P2PModels.IconTextures> textureCache = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceLocation, ItemStack> fallbackStackCache = new Object2ObjectOpenHashMap<>();

    void render(ResourceLocation tunnelType, int x, int y) {
        P2PModels.IconTextures textures = getP2PIconTextures(tunnelType);
        if (textures != null) {
            renderTextures(textures, x, y);
            return;
        }

        renderFallbackStack(tunnelType, x, y);
    }

    private P2PModels.IconTextures getP2PIconTextures(ResourceLocation tunnelType) {
        if (this.textureCache.containsKey(tunnelType)) {
            return this.textureCache.get(tunnelType);
        }

        IPartItem<?> partItem = IPartItem.byId(tunnelType);
        if (partItem == null || !P2PTunnelPart.class.isAssignableFrom(partItem.getPartClass())) {
            this.textureCache.put(tunnelType, null);
            return null;
        }

        P2PModels.IconTextures textures = P2PModels.findIconTextures(partItem.getPartClass()).orElse(null);
        this.textureCache.put(tunnelType, textures);
        return textures;
    }

    private void renderTextures(P2PModels.IconTextures textures, int x, int y) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, 1);

        renderTexture(textures.front(), x, y);
        renderTexture(textures.type(), x, y);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderTexture(ResourceLocation texture, int x, int y) {
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString());
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, y + ICON_SIZE, 0).tex(sprite.getMinU(), sprite.getMaxV()).color(255, 255, 255, 255).endVertex();
        buffer.pos(x + ICON_SIZE, y + ICON_SIZE, 0).tex(sprite.getMaxU(), sprite.getMaxV()).color(255, 255, 255, 255)
              .endVertex();
        buffer.pos(x + ICON_SIZE, y, 0).tex(sprite.getMaxU(), sprite.getMinV()).color(255, 255, 255, 255).endVertex();
        buffer.pos(x, y, 0).tex(sprite.getMinU(), sprite.getMinV()).color(255, 255, 255, 255).endVertex();
        tessellator.draw();
    }

    private void renderFallbackStack(ResourceLocation tunnelType, int x, int y) {
        ItemStack stack = getFallbackStack(tunnelType);
        if (stack.isEmpty()) {
            return;
        }

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        minecraft.getRenderItem().renderItemOverlayIntoGUI(minecraft.fontRenderer, stack, x, y, null);
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
    }

    private ItemStack getFallbackStack(ResourceLocation tunnelType) {
        if (this.fallbackStackCache.containsKey(tunnelType)) {
            return this.fallbackStackCache.get(tunnelType);
        }

        Item item = Item.REGISTRY.getObject(tunnelType);
        ItemStack stack = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        this.fallbackStackCache.put(tunnelType, stack);
        return stack;
    }
}
