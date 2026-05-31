package ae2.block.misc;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.tile.misc.TileCrystalFixer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class CrystalFixerBlock extends AEBaseTileBlock<TileCrystalFixer> {

    public CrystalFixerBlock() {
        super(Material.IRON);
        setOpaque();
        setFullSize();
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileCrystalFixer.class);
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 2;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }
        if (player.isSneaking()) {
            return false;
        }

        TileCrystalFixer tile = getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                tile.refuel(player, hand);
            }
            return true;
        }
        return false;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        TileCrystalFixer tile = getTileEntity(world, pos);
        if (tile != null) {
            tile.onFacingBlockChanged();
        }
    }
}
