package ae2.helpers.externalstorage;

import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEKeyType;
import com.google.common.primitives.Ints;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.jetbrains.annotations.Nullable;

public class GenericStackFluidStorage implements IFluidHandler {
    private static final IFluidTankProperties[] EMPTY_TANKS = new IFluidTankProperties[0];

    private final GenericInternalInventory inv;

    public GenericStackFluidStorage(GenericInternalInventory inv) {
        this.inv = inv;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        if (!this.inv.isSupportedType(AEKeyType.fluids())) {
            return EMPTY_TANKS;
        }

        IFluidTankProperties[] properties = new IFluidTankProperties[this.inv.size()];
        int capacity = Ints.saturatedCast(this.inv.getCapacity(AEKeyType.fluids()));
        for (int i = 0; i < this.inv.size(); i++) {
            FluidStack stack = null;
            boolean canDrain = false;
            boolean canFill = this.inv.canInsert();
            if (this.inv.getKey(i) instanceof AEFluidKey what) {
                stack = what.toStack(Ints.saturatedCast(this.inv.getAmount(i)));
                canDrain = this.inv.canExtract();
            }
            properties[i] = new FluidTankProperties(stack, capacity, canFill, canDrain);
        }
        return properties;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        var what = AEFluidKey.of(resource);
        if (what == null) {
            return 0;
        }

        int inserted = 0;
        for (int i = 0; i < this.inv.size() && inserted < resource.amount; i++) {
            inserted += Ints.saturatedCast(this.inv.insert(i, what, resource.amount - inserted,
                Actionable.ofSimulate(!doFill)));
        }
        return inserted;
    }

    @Override
    @Nullable
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        var what = AEFluidKey.of(resource);
        if (what == null) {
            return null;
        }

        return extract(what, resource.amount, doDrain);
    }

    @Override
    @Nullable
    public FluidStack drain(int maxDrain, boolean doDrain) {
        for (int i = 0; i < this.inv.size(); i++) {
            if (this.inv.getKey(i) instanceof AEFluidKey what) {
                return extract(what, maxDrain, doDrain);
            }
        }
        return null;
    }

    @Nullable
    private FluidStack extract(AEFluidKey what, int amount, boolean doDrain) {
        int extracted = 0;
        for (int i = 0; i < this.inv.size() && extracted < amount; i++) {
            extracted += Ints.saturatedCast(this.inv.extract(i, what, amount - extracted,
                Actionable.ofSimulate(!doDrain)));
        }

        return extracted > 0 ? what.toStack(extracted) : null;
    }
}
