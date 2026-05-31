package ae2.block.crafting;

import net.minecraft.item.Item;

public interface ICraftingUnitType {

    long getStorageBytes();

    int getAcceleratorThreads();

    Item getItemFromType();
}

