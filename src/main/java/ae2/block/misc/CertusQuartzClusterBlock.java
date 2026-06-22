package ae2.block.misc;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseBlock;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

@SuppressWarnings("deprecation")
public class CertusQuartzClusterBlock extends AEBaseBlock {
    private final AxisAlignedBB northAabb;
    private final AxisAlignedBB southAabb;
    private final AxisAlignedBB eastAabb;
    private final AxisAlignedBB westAabb;
    private final AxisAlignedBB upAabb;
    private final AxisAlignedBB downAabb;

    public CertusQuartzClusterBlock(int width, int height, float lightLevel) {
        super(Material.GLASS);
        this.setOpaque();
        this.setFullSize();
        this.setHardness(1.5F);
        this.setResistance(1.0F);
        this.setLightLevel(lightLevel);
        this.setDefaultState(this.blockState.getBaseState().withProperty(BlockDirectional.FACING, EnumFacing.UP));

        double min = width / 16.0D;
        double max = 1.0D - min;
        double tall = height / 16.0D;
        this.upAabb = new AxisAlignedBB(min, 0.0D, min, max, tall, max);
        this.downAabb = new AxisAlignedBB(min, 1.0D - tall, min, max, 1.0D, max);
        this.northAabb = new AxisAlignedBB(min, min, 1.0D - tall, max, max, 1.0D);
        this.southAabb = new AxisAlignedBB(min, min, 0.0D, max, max, tall);
        this.eastAabb = new AxisAlignedBB(0.0D, min, min, tall, max, max);
        this.westAabb = new AxisAlignedBB(1.0D - tall, min, min, 1.0D, max, max);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facingNoPlayerRotation();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockDirectional.FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (state.getValue(BlockDirectional.FACING)) {
            case NORTH -> northAabb;
            case SOUTH -> southAabb;
            case EAST -> eastAabb;
            case WEST -> westAabb;
            case DOWN -> downAabb;
            default -> upAabb;
        };
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (canStay(world, pos, facing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return canStay(world, pos, side);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn,
                                BlockPos fromPos) {
        EnumFacing facing = state.getValue(BlockDirectional.FACING);
        if (!canStay(world, pos, facing)) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return true;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(Item.getItemFromBlock(this));
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return this == AEBlocks.QUARTZ_CLUSTER.block()
            ? AEItems.CERTUS_QUARTZ_CRYSTAL.item()
            : AEItems.CERTUS_QUARTZ_DUST.item();
    }

    @Override
    public int quantityDropped(Random random) {
        return this == AEBlocks.QUARTZ_CLUSTER.block() ? 4 : 1;
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        if (this != AEBlocks.QUARTZ_CLUSTER.block()) {
            return 1;
        }
        return 4 + (fortune > 0 ? random.nextInt(fortune + 1) : 0);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(this.getItemDropped(state, RANDOM, fortune), this.quantityDroppedWithBonus(fortune, RANDOM)));
    }

    private boolean canStay(World world, BlockPos pos, EnumFacing facing) {
        BlockPos supportPos = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(supportPos);
        return supportState.getBlock().isSideSolid(supportState, world, supportPos, facing);
    }
}

