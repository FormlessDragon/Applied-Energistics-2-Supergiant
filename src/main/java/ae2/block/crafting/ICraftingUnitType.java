package ae2.block.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import net.minecraft.item.Item;

/**
 * Legacy crafting unit type bridge.
 */
public interface ICraftingUnitType extends ICraftingUnitDefinition {

    long getStorageBytes();

    int getAcceleratorThreads();

    Item getItemFromType();

    @Override
    default long storageBytes() {
        return getStorageBytes();
    }

    @Override
    default int acceleratorThreads() {
        return getAcceleratorThreads();
    }

    @Override
    default Item getItemRepresentation() {
        return getItemFromType();
    }
}

