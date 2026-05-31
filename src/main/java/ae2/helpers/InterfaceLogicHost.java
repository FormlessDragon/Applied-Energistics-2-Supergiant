package ae2.helpers;

import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.parts.AEBasePart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

public interface InterfaceLogicHost extends IConfigurableObject, IUpgradeableObject, IPriorityHost, IConfigInvHost {
    TileEntity getTileEntity();

    void saveChanges();

    InterfaceLogic getInterfaceLogic();

    @Override
    default IConfigManager getConfigManager() {
        return getInterfaceLogic().getConfigManager();
    }

    @Override
    default IUpgradeInventory getUpgrades() {
        return getInterfaceLogic().getUpgrades();
    }

    @Override
    default int getPriority() {
        return getInterfaceLogic().getPriority();
    }

    @Override
    default void setPriority(int newValue) {
        getInterfaceLogic().setPriority(newValue);
    }

    @Override
    default GenericStackInv getConfig() {
        return getInterfaceLogic().getConfig();
    }

    default GenericStackInv getStorage() {
        return getInterfaceLogic().getStorage();
    }

    default void openGui(EntityPlayer player, GuiHostLocator locator) {
        openGui(player, locator, false);
    }

    default void openGui(EntityPlayer player, GuiHostLocator ignoredLocator, boolean returnedFromSubScreen) {
        if (this instanceof AEBasePart part) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.INTERFACE, part, returnedFromSubScreen);
        } else {
            GuiOpener.openGui(player, GuiIds.GuiKey.INTERFACE, getTileEntity(), returnedFromSubScreen);
        }
    }

    @Override
    default void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        openGui(player, subGui.getLocator(), true);
    }
}
