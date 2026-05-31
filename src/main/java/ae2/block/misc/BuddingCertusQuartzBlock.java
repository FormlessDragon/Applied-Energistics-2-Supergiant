package ae2.block.misc;

import ae2.block.AEBaseBlock;
import ae2.core.definitions.AEBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class BuddingCertusQuartzBlock extends AEBaseBlock {
    public static final int GROWTH_CHANCE = 5;
    public static final int DECAY_CHANCE = 12;
    private static final EnumFacing[] DIRECTIONS = EnumFacing.values();

    public BuddingCertusQuartzBlock() {
        super(Material.ROCK);
        this.setHardness(1.5F);
        this.setResistance(6.0F);
        this.setTickRandomly(true);
    }

    public static boolean canClusterGrowAtState(IBlockState state) {
        return state.getMaterial() == Material.AIR
            || state.getBlock() == Blocks.WATER && state.getValue(BlockLiquid.LEVEL) == 0;
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (rand.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }

        EnumFacing direction = DIRECTIONS[rand.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.offset(direction);
        IBlockState targetState = world.getBlockState(targetPos);
        Block newCluster = null;
        if (canClusterGrowAtState(targetState)) {
            newCluster = AEBlocks.SMALL_QUARTZ_BUD.block();
        } else if (targetState.getBlock() == AEBlocks.SMALL_QUARTZ_BUD.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.MEDIUM_QUARTZ_BUD.block();
        } else if (targetState.getBlock() == AEBlocks.MEDIUM_QUARTZ_BUD.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.LARGE_QUARTZ_BUD.block();
        } else if (targetState.getBlock() == AEBlocks.LARGE_QUARTZ_BUD.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.QUARTZ_CLUSTER.block();
        }

        if (newCluster == null) {
            return;
        }

        world.setBlockState(targetPos, newCluster.getDefaultState().withProperty(BlockDirectional.FACING, direction), 3);

        if (this == AEBlocks.FLAWLESS_BUDDING_QUARTZ.block() || rand.nextInt(DECAY_CHANCE) != 0) {
            return;
        }

        Block newBlock;
        if (this == AEBlocks.FLAWED_BUDDING_QUARTZ.block()) {
            newBlock = AEBlocks.CHIPPED_BUDDING_QUARTZ.block();
        } else if (this == AEBlocks.CHIPPED_BUDDING_QUARTZ.block()) {
            newBlock = AEBlocks.DAMAGED_BUDDING_QUARTZ.block();
        } else if (this == AEBlocks.DAMAGED_BUDDING_QUARTZ.block()) {
            newBlock = AEBlocks.QUARTZ_BLOCK.block();
        } else {
            return;
        }

        world.setBlockState(pos, newBlock.getDefaultState(), 3);
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state,
                                  net.minecraft.entity.player.EntityPlayer player) {
        return this != AEBlocks.FLAWLESS_BUDDING_QUARTZ.block();
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(Item.getItemFromBlock(this));
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(Item.getItemFromBlock(getDroppedBlock())));
    }

    @SuppressWarnings("deprecation")
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    private Block getDroppedBlock() {
        if (this == AEBlocks.FLAWLESS_BUDDING_QUARTZ.block()) {
            return AEBlocks.FLAWED_BUDDING_QUARTZ.block();
        } else if (this == AEBlocks.FLAWED_BUDDING_QUARTZ.block()) {
            return AEBlocks.CHIPPED_BUDDING_QUARTZ.block();
        } else if (this == AEBlocks.CHIPPED_BUDDING_QUARTZ.block()) {
            return AEBlocks.DAMAGED_BUDDING_QUARTZ.block();
        } else if (this == AEBlocks.DAMAGED_BUDDING_QUARTZ.block()) {
            return AEBlocks.QUARTZ_BLOCK.block();
        }

        return this;
    }
}

