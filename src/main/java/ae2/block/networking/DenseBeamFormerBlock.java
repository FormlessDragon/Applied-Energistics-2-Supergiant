package ae2.block.networking;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.tile.networking.TileDenseBeamFormer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Locale;

public class DenseBeamFormerBlock extends AEBaseTileBlock<TileDenseBeamFormer> {

    public static final PropertyEnum<State> STATE = PropertyEnum.create("state", State.class);
    private static final AxisAlignedBB DOWN_AABB = new AxisAlignedBB(2.0 / 16.0, 6.0 / 16.0, 2.0 / 16.0,
        14.0 / 16.0, 1.0, 14.0 / 16.0);
    private static final AxisAlignedBB EAST_AABB = new AxisAlignedBB(0.0, 2.0 / 16.0, 2.0 / 16.0,
        10.0 / 16.0, 14.0 / 16.0, 14.0 / 16.0);
    private static final AxisAlignedBB NORTH_AABB = new AxisAlignedBB(2.0 / 16.0, 2.0 / 16.0, 6.0 / 16.0,
        14.0 / 16.0, 14.0 / 16.0, 1.0);
    private static final AxisAlignedBB SOUTH_AABB = new AxisAlignedBB(2.0 / 16.0, 2.0 / 16.0, 0.0,
        14.0 / 16.0, 14.0 / 16.0, 10.0 / 16.0);
    private static final AxisAlignedBB UP_AABB = new AxisAlignedBB(2.0 / 16.0, 0.0, 2.0 / 16.0,
        14.0 / 16.0, 10.0 / 16.0, 14.0 / 16.0);
    private static final AxisAlignedBB WEST_AABB = new AxisAlignedBB(6.0 / 16.0, 2.0 / 16.0, 2.0 / 16.0,
        1.0, 14.0 / 16.0, 14.0 / 16.0);

    public DenseBeamFormerBlock() {
        super(Material.IRON);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileDenseBeamFormer.class);
        this.setOpaque();
        this.setFullSize();
        this.setLightOpacity(0);
        this.setDefaultState(this.blockState.getBaseState().withProperty(STATE, State.OFF));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(STATE);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(STATE, State.OFF);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileDenseBeamFormer tileEntity) {
        return currentState.withProperty(STATE, tileEntity.isLinked() ? State.HAS_CHANNEL : State.OFF);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileDenseBeamFormer tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return super.getActualState(state, world, pos).withProperty(STATE, State.OFF);
        }
        return super.getActualState(state, world, pos).withProperty(STATE, tile.isLinked() ? State.HAS_CHANNEL : State.OFF);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (this.getOrientationStrategy().getFacing(state)) {
            case DOWN -> DOWN_AABB;
            case EAST -> EAST_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            default -> UP_AABB;
        };
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
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
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    public enum State implements IStringSerializable {
        OFF,
        HAS_CHANNEL;

        @Override
        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
