package ae2.block.misc;

import ae2.block.AEBaseBlock;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class EntroBuddingBlock extends AEBaseBlock {
    private static final int GROWTH_CHANCE = 5;
    private static final int DECAY_CHANCE = 12;
    private static final EnumFacing[] DIRECTIONS = EnumFacing.values();

    private final Stage stage;

    public EntroBuddingBlock(Stage stage) {
        super(Material.ROCK);
        this.stage = stage;
        this.setHardness(1.5F);
        this.setResistance(6.0F);
        this.setTickRandomly(true);
    }

    public static boolean canClusterGrowAtState(IBlockState state) {
        return state.getMaterial() == Material.AIR
            || state.getBlock() == net.minecraft.init.Blocks.WATER && state.getValue(BlockLiquid.LEVEL) == 0;
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
            newCluster = AEBlocks.ENTRO_CLUSTER_SMALL.block();
        } else if (targetState.getBlock() == AEBlocks.ENTRO_CLUSTER_SMALL.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.ENTRO_CLUSTER_MEDIUM.block();
        } else if (targetState.getBlock() == AEBlocks.ENTRO_CLUSTER_MEDIUM.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.ENTRO_CLUSTER_LARGE.block();
        } else if (targetState.getBlock() == AEBlocks.ENTRO_CLUSTER_LARGE.block()
            && targetState.getValue(BlockDirectional.FACING) == direction) {
            newCluster = AEBlocks.ENTRO_CLUSTER.block();
        }

        if (newCluster == null) {
            return;
        }

        world.setBlockState(targetPos, newCluster.getDefaultState().withProperty(BlockDirectional.FACING, direction), 3);

        if (rand.nextInt(DECAY_CHANCE) == 0) {
            world.setBlockState(pos, this.stage.getDecayedBlock().getDefaultState(), 3);
        }
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state,
                                  net.minecraft.entity.player.EntityPlayer player) {
        return true;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state,
                         int fortune) {
        drops.add(AEItems.ENTRO_DUST.stack());
    }

    @SuppressWarnings("deprecation")
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    public enum Stage {
        FULLY {
            @Override
            Block getDecayedBlock() {
                return AEBlocks.ENTRO_BUDDING_MOSTLY.block();
            }
        },
        MOSTLY {
            @Override
            Block getDecayedBlock() {
                return AEBlocks.ENTRO_BUDDING_HALF.block();
            }
        },
        HALF {
            @Override
            Block getDecayedBlock() {
                return AEBlocks.ENTRO_BUDDING_HARDLY.block();
            }
        },
        HARDLY {
            @Override
            Block getDecayedBlock() {
                return AEBlocks.QUARTZ_BLOCK.block();
            }
        };

        abstract Block getDecayedBlock();
    }
}
