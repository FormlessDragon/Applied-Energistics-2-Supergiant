package ae2.block.misc;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.tile.misc.TileCrystalAssembler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class CrystalAssemblerBlock extends AEBaseTileBlock<TileCrystalAssembler> {
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    public CrystalAssemblerBlock() {
        super(Material.IRON);
        setOpaque();
        setFullSize();
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileCrystalAssembler.class);
        setDefaultState(this.blockState.getBaseState().withProperty(POWERED, Boolean.FALSE));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(POWERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state) | (state.getValue(POWERED) ? 8 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(POWERED, (meta & 8) == 8);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileCrystalAssembler tileEntity) {
        return currentState.withProperty(POWERED, tileEntity.isPowered());
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

        TileCrystalAssembler tile = getTileEntity(world, pos);
        if (tile != null) {
            if (tile.onPlayerUse(player, hand)) {
                return true;
            }
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.CRYSTAL_ASSEMBLER, tile);
            }
            return true;
        }
        return false;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        TileCrystalAssembler tile = getTileEntity(world, pos);
        if (tile != null) {
            tile.updateNeighbors();
        }
    }
}
