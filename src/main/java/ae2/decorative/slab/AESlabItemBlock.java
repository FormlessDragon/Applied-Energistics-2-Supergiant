package ae2.decorative.slab;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;

public class AESlabItemBlock extends ItemSlab {

    private final Block singleSlabBlock;

    public AESlabItemBlock(Block block, BlockSlab singleSlab, BlockSlab doubleSlab) {
        super(block, singleSlab, doubleSlab);
        this.singleSlabBlock = singleSlab;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return this.singleSlabBlock.getTranslationKey();
    }
}
