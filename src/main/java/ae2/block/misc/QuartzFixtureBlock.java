package ae2.block.misc;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.orientation.RelativeSide;
import ae2.block.AEBaseBlock;
import ae2.client.EffectType;
import ae2.core.AEConfig;
import ae2.core.AppEngBase;
import ae2.util.EmptyArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@SuppressWarnings("deprecation")
public class QuartzFixtureBlock extends AEBaseBlock {
    public static final PropertyBool ODD = PropertyBool.create("odd");

    private static final AxisAlignedBB DOWN_AABB = createAabb(EnumFacing.DOWN);
    private static final AxisAlignedBB UP_AABB = createAabb(EnumFacing.UP);
    private static final AxisAlignedBB NORTH_AABB = createAabb(EnumFacing.NORTH);
    private static final AxisAlignedBB SOUTH_AABB = createAabb(EnumFacing.SOUTH);
    private static final AxisAlignedBB WEST_AABB = createAabb(EnumFacing.WEST);
    private static final AxisAlignedBB EAST_AABB = createAabb(EnumFacing.EAST);

    public QuartzFixtureBlock() {
        super(Material.GLASS);
        this.setOpaque();
        this.setFullSize();
        this.setHardness(0.3F);
        this.setResistance(1.5F);
        this.setLightLevel(1.0F);
        this.setDefaultState(this.blockState.getBaseState()
                                            .withProperty(BlockDirectional.FACING, EnumFacing.UP)
                                            .withProperty(ODD, false));
    }

    private static AxisAlignedBB createAabb(EnumFacing facing) {
        double xOff = -0.3D * facing.getXOffset();
        double yOff = -0.3D * facing.getYOffset();
        double zOff = -0.3D * facing.getZOffset();
        return new AxisAlignedBB(xOff + 0.3D, yOff + 0.3D, zOff + 0.3D, xOff + 0.7D, yOff + 0.7D, zOff + 0.7D);
    }

    private static EnumFacing[] getNearestLookingDirections(EntityLivingBase placer) {
        if (placer == null) {
            return EnumFacing.values();
        }

        Vec3d look = placer.getLookVec();
        List<EnumFacing> ordered = new ObjectArrayList<>(6);
        List<AxisFacing> axisFacings = Arrays.asList(
            new AxisFacing(Math.abs(look.x), look.x >= 0.0D ? EnumFacing.EAST : EnumFacing.WEST),
            new AxisFacing(Math.abs(look.y), look.y >= 0.0D ? EnumFacing.UP : EnumFacing.DOWN),
            new AxisFacing(Math.abs(look.z), look.z >= 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH));
        axisFacings.sort(Comparator.comparingDouble(AxisFacing::magnitude).reversed());

        for (AxisFacing axisFacing : axisFacings) {
            ordered.add(axisFacing.facing);
        }
        for (AxisFacing axisFacing : axisFacings) {
            ordered.add(axisFacing.facing.getOpposite());
        }

        return ordered.toArray(EmptyArrays.EMPTY_FACING_ARRAY);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facingNoPlayerRotation();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return this.createBlockState(ODD);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockDirectional.FACING).getIndex() | (state.getValue(ODD) ? 8 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int facingIndex = meta & 7;
        if (facingIndex > 5) {
            facingIndex = 0;
        }
        return this.getDefaultState()
                   .withProperty(BlockDirectional.FACING, EnumFacing.byIndex(facingIndex))
                   .withProperty(ODD, (meta & 8) != 0);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        IBlockState state = this.getDefaultState().withProperty(ODD, ((pos.getX() + pos.getY() + pos.getZ()) & 1) != 0);

        for (EnumFacing lookDirection : getNearestLookingDirections(placer)) {
            IBlockState placedState = state.withProperty(BlockDirectional.FACING, lookDirection.getOpposite());
            if (this.canBlockStay(world, pos, placedState)) {
                return placedState;
            }
        }

        for (EnumFacing direction : EnumFacing.values()) {
            IBlockState placedState = state.withProperty(BlockDirectional.FACING, direction);
            if (this.canBlockStay(world, pos, placedState)) {
                return placedState;
            }
        }

        return state.withProperty(BlockDirectional.FACING, facing);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (state.getValue(BlockDirectional.FACING)) {
            case DOWN -> DOWN_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
            default -> UP_AABB;
        };
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (this.canBlockStay(world, pos, this.getDefaultState().withProperty(BlockDirectional.FACING, facing))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return this.canBlockStay(world, pos, this.getDefaultState().withProperty(BlockDirectional.FACING, side));
    }

    public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
        EnumFacing facing = state.getValue(BlockDirectional.FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(supportPos);
        return supportState.getBlock().isSideSolid(supportState, world, supportPos, facing);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!this.canBlockStay(world, pos, state) && !world.isRemote) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (!AEConfig.instance().isEnableEffects()) {
            return;
        }

        if (random.nextFloat() < 0.98F) {
            return;
        }

        EnumFacing top = this.getOrientation(state).getSide(RelativeSide.TOP);
        double xOff = -0.3D * top.getXOffset();
        double yOff = -0.3D * top.getYOffset();
        double zOff = -0.3D * top.getZOffset();
        for (int bolts = 0; bolts < 3; bolts++) {
            if (AppEngBase.runtime().shouldAddParticles(random)) {
                AppEngBase.runtime().spawnEffect(EffectType.Lightning, world,
                    xOff + 0.5D + pos.getX(),
                    yOff + 0.5D + pos.getY(),
                    zOff + 0.5D + pos.getZ(),
                    null);
            }
        }
    }

    private record AxisFacing(double magnitude, EnumFacing facing) {
    }
}
