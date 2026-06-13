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

package ae2.block.networking;

import ae2.api.parts.SelectedPart;
import ae2.api.util.AEColor;
import ae2.api.util.ICustomName;
import ae2.block.AEBaseTileBlock;
import ae2.client.render.cablebus.CableBusBakedModel;
import ae2.client.render.cablebus.CableBusBreakingParticle;
import ae2.core.DebugCreativeTab;
import ae2.helpers.ICustomCollision;
import ae2.helpers.cablebus.CableBusRenderState;
import ae2.tile.networking.TileCableBus;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CableBusBlock extends AEBaseTileBlock<TileCableBus> implements ICustomCollision {
    public static final IUnlistedProperty<CableBusRenderState> RENDER_STATE = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "render_state";
        }

        @Override
        public boolean isValid(CableBusRenderState value) {
            return true;
        }

        @Override
        public Class<CableBusRenderState> getType() {
            return CableBusRenderState.class;
        }

        @Override
        public String valueToString(CableBusRenderState value) {
            return String.valueOf(value);
        }
    };

    public CableBusBlock() {
        super(Material.GLASS);
        this.setHardness(0.2F);
        this.setResistance(1.0F);
        this.setTileEntity(TileCableBus.class);
        this.setOpaque();
        this.setFullSize();
        this.setLightOpacity(0);
        this.setSoundType(SoundType.GLASS);
    }

    @Nullable
    private static EnumFacing getChangedSide(BlockPos pos, BlockPos fromPos) {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (pos.offset(side).equals(fromPos)) {
                return side;
            }
        }
        return null;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, getOrientationStrategy().getProperties().toArray(new IProperty<?>[0]),
            new IUnlistedProperty<?>[]{RENDER_STATE});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileCableBus tile = getTileEntity(world, pos);
        if (tile == null) {
            return state;
        }

        CableBusRenderState renderState = tile.getRenderState();
        renderState.setWorld(world);
        renderState.setPos(pos);
        return ((IExtendedBlockState) state).withProperty(RENDER_STATE, renderState);
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addHitEffects(IBlockState state, World world, RayTraceResult target, ParticleManager manager) {
        if (world.rand.nextBoolean() || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return true;
        }

        BlockPos pos = target.getBlockPos();
        List<TextureAtlasSprite> textures = getParticleTextures(world, pos);
        if (textures.isEmpty()) {
            return true;
        }

        TextureAtlasSprite texture = textures.get(world.rand.nextInt(textures.size()));
        Particle particle = new CableBusBreakingParticle(world, target.hitVec.x, target.hitVec.y, target.hitVec.z,
            state, texture).scale(0.8F).setBlockPos(pos);
        manager.addEffect(particle);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addDestroyEffects(World world, BlockPos pos, ParticleManager manager) {
        List<TextureAtlasSprite> textures = getParticleTextures(world, pos);
        if (textures.isEmpty()) {
            return true;
        }

        IBlockState state = world.getBlockState(pos);
        for (int j = 0; j < 4; ++j) {
            for (int k = 0; k < 4; ++k) {
                for (int l = 0; l < 4; ++l) {
                    TextureAtlasSprite texture = textures.get(world.rand.nextInt(textures.size()));

                    double x = pos.getX() + (j + 0.5D) / 4.0D;
                    double y = pos.getY() + (k + 0.5D) / 4.0D;
                    double z = pos.getZ() + (l + 0.5D) / 4.0D;

                    Particle particle = new CableBusBreakingParticle(world, x, y, z, x - pos.getX() - 0.5D,
                        y - pos.getY() - 0.5D, z - pos.getZ() - 0.5D, state, texture).setBlockPos(pos);
                    manager.addEffect(particle);
                }
            }
        }

        return true;
    }

    @SideOnly(Side.CLIENT)
    private List<TextureAtlasSprite> getParticleTextures(World world, BlockPos pos) {
        TileCableBus tile = getTileEntity(world, pos);
        if (tile == null) {
            return Collections.emptyList();
        }

        IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(getDefaultState());
        if (!(model instanceof CableBusBakedModel cableBusModel)) {
            return Collections.emptyList();
        }
        return cableBusModel.getParticleTextures(tile.getRenderState());
    }

    @Override
    public void randomDisplayTick(IBlockState state, World worldIn, BlockPos pos, Random rand) {
        TileCableBus tile = getTileEntity(worldIn, pos);
        if (tile != null) {
            tile.getCableBus().randomDisplayTick(worldIn, pos, rand);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile == null ? 0 : tile.getCableBus().isProvidingWeakPower(side.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public void onEntityCollision(World worldIn, BlockPos pos, IBlockState state, Entity entityIn) {
        TileCableBus tile = getTileEntity(worldIn, pos);
        if (tile != null) {
            tile.getCableBus().onEntityCollision(entityIn);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile == null ? 0 : tile.getCableBus().isProvidingStrongPower(side.getOpposite());
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile == null ? super.getLightValue(state, world, pos) : tile.getCableBus().getLightValue();
    }

    @Override
    public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile != null && tile.getCableBus().isLadder(entity);
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile != null && tile.getCableBus().isEmpty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile != null && tile.getCableBus().isSolidOnSide(side);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player,
                                   boolean willHarvest) {
        if (player.capabilities.isCreativeMode) {
            TileCableBus tile = getTileEntity(world, pos);
            if (tile != null) {
                tile.disableDrops();
            }
        }

        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        TileCableBus tile = getTileEntity(worldIn, pos);
        if (tile != null && tile.shouldDropItems()) {
            List<ItemStack> drops = new ObjectArrayList<>();
            tile.addPartDrops(drops);
            Platform.spawnDrops(worldIn, pos, drops);
        }

        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }

        TileCableBus tile = getTileEntity(world, pos);
        return tile != null && tile.getCableBus().canConnectRedstone(side.getOpposite());
    }

    @Override
    public boolean recolorBlock(World world, BlockPos pos, EnumFacing side, EnumDyeColor color) {
        return this.recolorBlock(world, pos, side, color, null);
    }

    public boolean recolorBlock(World world, BlockPos pos, EnumFacing side, EnumDyeColor color,
                                @Nullable EntityPlayer who) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile != null && tile.recolourBlock(side, AEColor.fromDye(color), who);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos,
                                  EntityPlayer player) {
        TileCableBus tile = getTileEntity(world, pos);
        if (tile == null) {
            return ItemStack.EMPTY;
        }

        Vec3d localPos = target.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
        SelectedPart selectedPart = tile.getCableBus().selectPartLocal(localPos);
        if (selectedPart.part != null) {
            var i = new ItemStack(selectedPart.part.getPartItem().asItem());
            if (selectedPart.part instanceof ICustomName p && p.hasCustomName()) {
                i.setStackDisplayName(p.getCustomName());
            }
            return i;
        }
        if (selectedPart.facade != null) {
            return selectedPart.facade.getItemStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        TileCableBus tile = getTileEntity(world, pos);
        if (tile != null) {
            EnumFacing side = getChangedSide(pos, fromPos);
            if (side != null) {
                tile.onUpdateShape(side);
            }

            if (!world.isRemote) {
                tile.onNeighborChanged(world, pos, fromPos);
            }
        }
    }

    @Override
    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn) {
        if (!worldIn.isRemote) {
            return;
        }

        TileCableBus tile = getTileEntity(worldIn, pos);
        if (tile == null) {
            return;
        }

        RayTraceResult hit = playerIn.rayTrace(5.0D, 1.0F);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && pos.equals(hit.getBlockPos())) {
            Vec3d localPos = hit.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
            tile.onClicked(playerIn, localPos);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileCableBus tile = getTileEntity(world, pos);
        if (tile == null) {
            return false;
        }

        Vec3d localPos = new Vec3d(hitX, hitY, hitZ);
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty()) {
            return tile.onUseItemOn(heldItem, player, hand, localPos);
        }

        return tile.onUseWithoutItem(player, localPos);
    }

    @Override
    public void getSubBlocks(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        if (creativeTab == DebugCreativeTab.INSTANCE) {
            itemStacks.add(new ItemStack(this));
        }
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(World world, BlockPos pos, Entity entity,
                                                                    boolean hitFluids) {
        TileCableBus tile = getTileEntity(world, pos);
        return tile == null ? Collections.emptyList() : tile.getCableBus().getBoxes(true, entity, true);
    }

    @Override
    public void addCollidingBlockToList(World world, BlockPos pos, AxisAlignedBB bb, List<AxisAlignedBB> out,
                                        Entity entity) {
        TileCableBus tile = getTileEntity(world, pos);
        if (tile == null) {
            return;
        }

        for (AxisAlignedBB box : tile.getCableBus().getBoxes(true, entity, false)) {
            out.add(box);
        }
    }
}
