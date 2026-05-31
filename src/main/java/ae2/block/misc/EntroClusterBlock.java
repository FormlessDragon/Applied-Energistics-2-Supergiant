package ae2.block.misc;

import ae2.core.definitions.AEItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Random;

public class EntroClusterBlock extends CertusQuartzClusterBlock {
    private final boolean fullyGrown;

    public EntroClusterBlock(int width, int height, float lightLevel, boolean fullyGrown) {
        super(width, height, lightLevel);
        this.fullyGrown = fullyGrown;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return this.fullyGrown ? AEItems.ENTRO_CRYSTAL.item() : AEItems.ENTRO_SHARD.item();
    }

    @Override
    public int quantityDropped(Random random) {
        return this.fullyGrown ? 4 : 1;
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        if (!this.fullyGrown) {
            return 1;
        }
        return 4 + (fortune > 0 ? random.nextInt(fortune + 1) : 0);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        drops.add(new ItemStack(this.getItemDropped(state, RANDOM, fortune),
            this.quantityDroppedWithBonus(fortune, RANDOM)));
    }
}
