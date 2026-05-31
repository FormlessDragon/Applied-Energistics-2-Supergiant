package ae2.block.misc;

import ae2.block.AEBaseTileBlock;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.server.services.compass.ServerCompassService;
import ae2.tile.misc.TileMysteriousCube;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

public class MysteriousCubeBlock extends AEBaseTileBlock<TileMysteriousCube> {
    public MysteriousCubeBlock() {
        super(Material.IRON);
        this.setHardness(10.0F);
        this.setResistance(1000.0F);
        this.setTileEntity(TileMysteriousCube.class);
        this.setOpaque();
        this.setFullSize();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (world instanceof WorldServer && !world.isRemote) {
            ServerCompassService.notifyBlockChange((WorldServer) world, pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        if (world instanceof WorldServer && !world.isRemote) {
            ServerCompassService.notifyBlockChange((WorldServer) world, pos);
        }
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, net.minecraft.entity.player.EntityPlayer player) {
        return true;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(Item.getItemFromBlock(this));
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(AEItems.CALCULATION_PROCESSOR_PRESS.stack());
        drops.add(AEItems.ENGINEERING_PROCESSOR_PRESS.stack());
        drops.add(AEItems.LOGIC_PROCESSOR_PRESS.stack());
        drops.add(AEItems.SILICON_PRESS.stack());
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add(GuiText.MysteriousQuote.getLocal());
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }
}
