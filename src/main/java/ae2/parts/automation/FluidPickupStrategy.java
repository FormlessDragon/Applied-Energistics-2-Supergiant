package ae2.parts.automation;

import ae2.api.behaviors.PickupSink;
import ae2.api.behaviors.PickupStrategy;
import ae2.api.config.Actionable;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.stacks.AEFluidKey;
import ae2.core.AppEng;
import ae2.core.network.clientbound.BlockTransitionEffectPacket;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FluidPickupStrategy implements PickupStrategy {
    private final WorldServer level;
    private final BlockPos pos;
    private final EnumFacing side;

    /**
     * {@link System#currentTimeMillis()} of when the last sound/visual effect was played by this plane.
     */
    private long lastEffect;

    public FluidPickupStrategy(WorldServer level, BlockPos pos, EnumFacing side, TileEntity host,
                               Object2IntMap<Enchantment> enchantments, @Nullable UUID owningEntityPlayerId) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean canPickUpEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity) {
        return false;
    }

    @Override
    public Result tryPickup(IEnergySource energySource, PickupSink sink) {
        IBlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
            IFluidHandler handler = FluidUtil.getFluidHandler(level, pos, null);
            if (handler == null) {
                return Result.CANT_PICKUP;
            }

            var simulated = handler.drain(Integer.MAX_VALUE, false);
            var what = AEFluidKey.of(simulated);
            if (what != null && this.storeFluid(sink, what, simulated.amount, false)) {
                var drained = handler.drain(Integer.MAX_VALUE, true);
                var actual = AEFluidKey.of(drained);
                if (actual != null) {
                    this.storeFluid(sink, actual, drained.amount, true);
                }

                if (!throttleEffect()) {
                    AppEng.instance().sendToAllNearExcept(null, pos.getX(), pos.getY(), pos.getZ(), 64, level,
                        new BlockTransitionEffectPacket(pos, blockState, side,
                            BlockTransitionEffectPacket.SoundMode.FLUID));
                }

                return Result.PICKED_UP;
            }
            return Result.CANT_STORE;
        }

        // nothing to do here :)
        return Result.CANT_PICKUP;
    }

    private boolean storeFluid(PickupSink sink, AEFluidKey what, long amount, boolean modulate) {
        return sink.insert(what, amount, modulate ? Actionable.MODULATE : Actionable.SIMULATE) >= amount;
    }

    /**
     * Only play the effect every 250ms.
     */
    private boolean throttleEffect() {
        var now = System.currentTimeMillis();
        if (now < lastEffect + 250) {
            return true;
        }
        lastEffect = now;
        return false;
    }

}
