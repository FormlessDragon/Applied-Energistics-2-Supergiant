package ae2.parts.automation;

import ae2.api.behaviors.PlacementStrategy;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FluidPlacementStrategy implements PlacementStrategy {
    private final WorldServer level;
    private final BlockPos pos;
    private final ReferenceSet<Fluid> blocked = new ReferenceOpenHashSet<>();
    private long lastEffect;

    public FluidPlacementStrategy(WorldServer level, BlockPos pos, EnumFacing ignoredSide, TileEntity ignoredHost,
                                  @Nullable UUID ignoredOwningEntityPlayerId) {
        this.level = level;
        this.pos = pos;
    }

    @Override
    public void clearBlocked() {
        this.blocked.clear();
    }

    @Override
    public long placeInWorld(AEKey key, long amount, Actionable type, boolean placeAsEntity) {
        if (placeAsEntity || !(key instanceof AEFluidKey fluidKey)) {
            return 0;
        }

        if (amount < AEFluidKey.AMOUNT_BLOCK || fluidKey.hasTagCompound()) {
            return 0;
        }

        Fluid fluid = fluidKey.getFluid();
        if (this.blocked.contains(fluid)) {
            return 0;
        }

        IBlockState state = level.getBlockState(pos);
        if (!this.canPlace(level, state, pos, fluid)) {
            this.blocked.add(fluid);
            return 0;
        }

        if (type == Actionable.MODULATE) {
            FluidStack stack = fluidKey.toStack(AEFluidKey.AMOUNT_BLOCK);
            if (!FluidUtil.tryPlaceFluid(null, level, pos, new FluidTank(stack, stack.amount), stack)) {
                return 0;
            }
            this.playEffect(level, pos, fluid);
        }

        return AEFluidKey.AMOUNT_BLOCK;
    }

    private void playEffect(World level, BlockPos pos, Fluid fluid) {
        if (throttleEffect()) {
            return;
        }

        if (fluid == FluidRegistry.LAVA) {
            level.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else {
            level.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean canPlace(WorldServer level, IBlockState state, BlockPos pos, Fluid ignoredFluid) {
        Block block = state.getBlock();
        if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
            return false;
        }

        return block.isReplaceable(level, pos) && !state.getMaterial().isLiquid();
    }

    protected final boolean throttleEffect() {
        long now = System.currentTimeMillis();
        if (now < this.lastEffect + 250) {
            return true;
        }
        this.lastEffect = now;
        return false;
    }
}
