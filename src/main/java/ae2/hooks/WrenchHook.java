package ae2.hooks;

import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.DimensionalBlockPos;
import ae2.block.AEBaseTileBlock;
import ae2.block.crafting.PatternProviderBlock;
import ae2.block.networking.CableBusBlock;
import ae2.tile.AEBaseTile;
import ae2.tile.networking.TileCableBus;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jspecify.annotations.Nullable;

public final class WrenchHook {
    public WrenchHook() {
    }

    public static EnumActionResult onPlayerUseBlock(EntityPlayer player, World world, net.minecraft.util.EnumHand hand,
                                                    BlockPos pos, EnumFacing clickedFace, Vec3d localHit) {
        if (player == null || world == null || pos == null || clickedFace == null || hand != net.minecraft.util.EnumHand.MAIN_HAND) {
            return EnumActionResult.PASS;
        }

        ItemStack itemStack = player.getHeldItem(hand);
        if (InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchDisassemble(itemStack)) {
            EnumActionResult cableBusResult = disassembleCableBusPart(player, world, pos, localHit);
            if (cableBusResult != EnumActionResult.PASS) {
                return cableBusResult;
            }
            return disassemble(player, world, pos);
        }

        if (!InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchRotate(itemStack)) {
            return rotate(player, world, pos, clickedFace);
        }

        return EnumActionResult.PASS;
    }

    private static @Nullable Vec3d normalizeLocalHit(BlockPos pos, Vec3d hitVec) {
        if (hitVec == null) {
            return null;
        }
        if (hitVec.x >= 0 && hitVec.x <= 1 && hitVec.y >= 0 && hitVec.y <= 1 && hitVec.z >= 0 && hitVec.z <= 1) {
            return hitVec;
        }
        return hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    private static EnumActionResult disassembleCableBusPart(EntityPlayer player, World world, BlockPos pos,
                                                            Vec3d localHit) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof CableBusBlock)) {
            return EnumActionResult.PASS;
        }

        if (localHit == null) {
            return EnumActionResult.SUCCESS;
        }

        if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            return EnumActionResult.FAIL;
        }

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileCableBus cableBus)) {
            return EnumActionResult.SUCCESS;
        }

        if (!world.isRemote && cableBus.onWrenched(player, localHit)) {
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.7F, 1.0F);
        }
        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult disassemble(EntityPlayer player, World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof AEBaseTileBlock<?>)) {
            return EnumActionResult.PASS;
        }

        if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            NonNullList<ItemStack> drops = NonNullList.create();
            block.getDrops(drops, world, pos, state, 0);
            if (drops.isEmpty()) {
                drops.add(new ItemStack(block));
            }

            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof AEBaseTile baseTile) {
                baseTile.addAdditionalDrops(drops);
                baseTile.clearContent();
            }
            if (tile != null) {
                world.removeTileEntity(pos);
            }
            world.setBlockToAir(pos);
            Platform.spawnDrops(world, pos, drops);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.7F, 1.0F);
        }

        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult rotate(EntityPlayer player, World world, BlockPos pos, EnumFacing clickedFace) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PatternProviderBlock) {
            return EnumActionResult.PASS;
        }

        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            return EnumActionResult.PASS;
        }

        if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            return EnumActionResult.FAIL;
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof AEBaseTile baseTile) {
            BlockOrientation orientation = baseTile.getOrientation().rotateClockwiseAround(clickedFace);
            if (!world.isRemote) {
                baseTile.setOrientation(orientation.getSide(RelativeSide.FRONT), orientation.getSide(RelativeSide.TOP));
            }
            return EnumActionResult.SUCCESS;
        }

        BlockOrientation orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(clickedFace);
        IBlockState newState = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT),
            orientation.getSpin());
        if (newState == state) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            world.setBlockState(pos, newState, 3);
        }
        return EnumActionResult.SUCCESS;
    }

    @SubscribeEvent
    public void onPlayerUseBlockEvent(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }

        Vec3d localHit = normalizeLocalHit(event.getPos(), event.getHitVec());
        EnumActionResult result = onPlayerUseBlock(event.getEntityPlayer(), event.getWorld(), event.getHand(),
            event.getPos(), event.getFace(), localHit);
        if (result != EnumActionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
