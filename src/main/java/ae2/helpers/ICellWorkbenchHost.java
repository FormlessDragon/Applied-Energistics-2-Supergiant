package ae2.helpers;

import ae2.api.inventories.InternalInventory;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.IConfigurableObject;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface ICellWorkbenchHost extends IUpgradeableObject, IConfigurableObject, InternalInventoryHost, ISubGuiHost {

    @Nullable
    ICellWorkbenchItem getCell();

    GenericStackInv getConfig();

    @Nullable
    InternalInventory getSubInventory(ResourceLocation id);

    void saveChanges();
}
