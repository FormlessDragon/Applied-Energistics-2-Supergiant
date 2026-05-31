package ae2.integration.modules.hei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.Fluid;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

final class FluidBlockDrawable {
    private static final int SIZE = 16;
    private static final float ANGLE = 36.0F;
    private static final float ROTATION = 45.0F;

    private FluidBlockDrawable() {
    }

    static void draw(Fluid fluid, int x, int y) {
        var block = fluid.getBlock();
        if (block == null) {
            return;
        }
        var state = block.getDefaultState();
        var world = new FluidBlockAccess(state);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.pushMatrix();
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableLighting();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableCull();

            GlStateManager.translate(x + SIZE / 2.0F, y + SIZE / 2.0F, 0.0F);
            GlStateManager.scale(SIZE, SIZE, SIZE);
            GlStateManager.scale(1.0F, 1.0F, -1.0F);
            GlStateManager.rotate(-180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(ANGLE, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(ROTATION, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            new BlockFluidRenderer(Minecraft.getMinecraft().getBlockColors())
                .renderFluid(world, state, BlockPos.ORIGIN, buffer);
            tessellator.draw();
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }

    private record FluidBlockAccess(net.minecraft.block.state.IBlockState fluidState) implements IBlockAccess {
        @Nullable
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return 0xF000F0;
        }

        @Override
        public net.minecraft.block.state.IBlockState getBlockState(BlockPos pos) {
            return BlockPos.ORIGIN.equals(pos) ? fluidState : Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !BlockPos.ORIGIN.equals(pos);
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return Biomes.PLAINS;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            return false;
        }
    }
}
