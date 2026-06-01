package ae2.block.crafting;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.tile.crafting.TileRequester;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RequesterBlock extends AEBaseTileBlock<TileRequester> {

    public static final PropertyBool ACTIVE = PropertyBool.create("active");

    public RequesterBlock() {
        super(Material.IRON);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileRequester.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(ACTIVE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ACTIVE) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(ACTIVE, meta != 0);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileRequester tileEntity) {
        return currentState.withProperty(ACTIVE, tileEntity.isActive());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileRequester tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return false;
        }

        if (!world.isRemote) {
            GuiOpener.openGui(player, GuiIds.GuiKey.REQUESTER, tile);
        }
        return true;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

}
