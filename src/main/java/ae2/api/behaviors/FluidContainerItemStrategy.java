package ae2.api.behaviors;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.GenericStack;
import ae2.util.GenericContainerHelper;
import ae2.util.fluid.FluidSoundHelper;
import com.google.common.primitives.Ints;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

class FluidContainerItemStrategy
    implements ContainerItemStrategy<AEFluidKey, FluidContainerItemStrategy.Context> {
    @Override
    public @Nullable GenericStack getContainedStack(ItemStack stack) {
        return GenericContainerHelper.getContainedFluidStack(stack);
    }

    @Override
    public @Nullable Context findCarriedContext(EntityPlayer player, Container container) {
        if (player.inventory.getItemStack().hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return new CarriedContext(player);
        }
        return null;
    }

    @Override
    public @Nullable Context findPlayerSlotContext(EntityPlayer player, int slot) {
        if (player.inventory.getStackInSlot(slot).hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return new PlayerInvContext(player, slot);
        }

        return null;
    }

    @Override
    public long extract(Context context, AEFluidKey what, long amount, Actionable mode) {
        ItemStack stack = context.getStack();
        ItemStack copy = stack.copy();
        copy.setCount(1);
        IFluidHandlerItem fluidHandler = copy.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (fluidHandler == null) {
            return 0;
        }

        FluidStack drained = fluidHandler.drain(what.toStack(Ints.saturatedCast(amount)), mode.getFluidAction());
        int extracted = drained != null ? drained.amount : 0;
        if (extracted > 0 && mode == Actionable.MODULATE) {
            stack.shrink(1);
            context.addOverflow(fluidHandler.getContainer());
        }
        return extracted;
    }

    @Override
    public long insert(Context context, AEFluidKey what, long amount, Actionable mode) {
        ItemStack stack = context.getStack();
        ItemStack copy = stack.copy();
        copy.setCount(1);
        IFluidHandlerItem fluidHandler = copy.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (fluidHandler == null) {
            return 0;
        }

        int filled = fluidHandler.fill(what.toStack(Ints.saturatedCast(amount)), mode.getFluidAction());
        if (filled > 0 && mode == Actionable.MODULATE) {
            stack.shrink(1);
            context.addOverflow(fluidHandler.getContainer());
        }
        return filled;
    }

    @Override
    public void playFillSound(EntityPlayer player, AEFluidKey what) {
        FluidSoundHelper.playFillSound(player, what);
    }

    @Override
    public void playEmptySound(EntityPlayer player, AEFluidKey what) {
        FluidSoundHelper.playEmptySound(player, what);
    }

    @Override
    public @Nullable GenericStack getExtractableContent(Context context) {
        return getContainedStack(context.getStack());
    }

    interface Context {
        ItemStack getStack();

        void setStack(ItemStack stack);

        void addOverflow(ItemStack stack);
    }

    private record CarriedContext(EntityPlayer player) implements Context {

        @Override
        public ItemStack getStack() {
            return this.player.inventory.getItemStack();
        }

        @Override
        public void setStack(ItemStack stack) {
            this.player.inventory.setItemStack(stack);
        }

        @Override
        public void addOverflow(ItemStack stack) {
            if (this.player.inventory.getItemStack().isEmpty()) {
                this.player.inventory.setItemStack(stack);
            } else {
                this.player.inventory.addItemStackToInventory(stack);
            }
        }
    }

    private record PlayerInvContext(EntityPlayer player, int slot) implements Context {

        @Override
        public ItemStack getStack() {
            return this.player.inventory.getStackInSlot(this.slot);
        }

        @Override
        public void setStack(ItemStack stack) {
            this.player.inventory.setInventorySlotContents(this.slot, stack);
        }

        @Override
        public void addOverflow(ItemStack stack) {
            this.player.inventory.addItemStackToInventory(stack);
        }
    }
}
