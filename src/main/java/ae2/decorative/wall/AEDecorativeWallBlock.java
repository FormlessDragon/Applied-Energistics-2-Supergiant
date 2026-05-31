package ae2.decorative.wall;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWall;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class AEDecorativeWallBlock extends BlockWall {

    public AEDecorativeWallBlock(Block modelBlock, float hardness, float resistance) {
        super(modelBlock);
        this.setHardness(hardness);
        this.setResistance(resistance);
        this.useNeighborBrightness = true;
    }

    @Override
    public void getSubBlocks(CreativeTabs itemIn, NonNullList<ItemStack> items) {
        items.add(new ItemStack(this));
    }
}
