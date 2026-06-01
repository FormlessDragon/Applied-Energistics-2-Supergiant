package ae2.tile.misc;

import ae2.api.AECapabilities;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.tile.AEBaseTile;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TileIngredientBuffer extends AEBaseTile {
    private final GenericStackInv buffer = new GenericStackInv(this::saveChanges, 36);

    public GenericStackInv getBuffer() {
        return this.buffer;
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.buffer.writeToChildTag(data, "buffer");
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.buffer.readFromChildTag(data, "buffer");
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        if (this.world == null) {
            return;
        }
        for (int index = 0; index < this.buffer.size(); index++) {
            var stack = this.buffer.getStack(index);
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, this.world, this.pos);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.buffer.clear();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return (T) this.buffer;
        }
        return super.getCapability(capability, facing);
    }

}
