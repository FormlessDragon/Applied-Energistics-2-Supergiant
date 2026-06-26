package ae2.items.contents;

import ae2.api.config.CopyMode;
import ae2.api.config.Settings;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.IConfigManager;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.helpers.ICellWorkbenchHost;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.items.tools.PortableCellWorkbenchItem;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import ae2.tile.misc.TileCellWorkbench;
import ae2.util.ConfigInventory;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class PortableCellWorkbenchGuiHost extends ItemGuiHost<PortableCellWorkbenchItem>
    implements ICellWorkbenchHost {

    private static final String CELL_TAG = "cell";
    private static final String CONFIG_TAG = "config";

    private final AppEngInternalInventory cell = new AppEngInternalInventory(this, 1);
    private final IConfigManager manager;
    @Nullable
    private IUpgradeInventory cacheUpgrades;
    @Nullable
    private ConfigInventory cacheConfig;
    private ItemStack lastCellStack = ItemStack.EMPTY;
    private boolean locked;
    private ConfigInventory config = createConfigInventory(63);

    public PortableCellWorkbenchGuiHost(PortableCellWorkbenchItem item, EntityPlayer player,
                                        ItemGuiHostLocator locator) {
        super(item, player, locator);
        this.cell.setEnableClientEvents(true);
        this.manager = IConfigManager.builder(this::getItemStack)
                                     .registerSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE)
                                     .build();
        readFromStack();
    }

    private ConfigInventory createConfigInventory(int size) {
        return ConfigInventory.configTypes(Math.max(0, size))
                              .changeListener(this::configChanged)
                              .build();
    }

    @Nullable
    @Override
    public ICellWorkbenchItem getCell() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return !stack.isEmpty() && stack.getItem() instanceof ICellWorkbenchItem
            ? (ICellWorkbenchItem) stack.getItem()
            : null;
    }

    @Override
    public GenericStackInv getConfig() {
        ensureConfigSizeForCurrentCell();
        return this.config;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.CELLS.equals(id)) {
            return this.cell;
        }
        return null;
    }

    @Override
    public boolean isClientSide() {
        return getPlayer().world.isRemote;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (inv == this.cell) {
            PortableItemCellAutoPickup.invalidateCachedState(this.cell.getStackInSlot(0));
        }
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.cell && !this.locked) {
            PortableItemCellAutoPickup.invalidateCachedState(this.lastCellStack);
            this.lastCellStack = this.cell.getStackInSlot(0);
            PortableItemCellAutoPickup.invalidateCachedState(this.lastCellStack);
            this.locked = true;
            try {
                this.cacheUpgrades = null;
                this.cacheConfig = null;

                ConfigInventory configInventory = this.getCellConfigInventory();
                this.resizeConfig(configInventory != null ? configInventory.size() : 63);
                if (configInventory != null) {
                    if (!configInventory.isEmpty()) {
                        TileCellWorkbench.copy(configInventory, this.config);
                    } else {
                        TileCellWorkbench.copy(this.config, configInventory);
                        TileCellWorkbench.copy(configInventory, this.config);
                    }
                } else if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.CLEAR_ON_REMOVE) {
                    this.config.clear();
                    saveChanges();
                }
            } finally {
                this.locked = false;
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (this.cacheUpgrades == null) {
            ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return UpgradeInventories.empty();
            }

            ItemStack stack = this.cell.getStackInSlot(0);
            if (stack.isEmpty()) {
                return UpgradeInventories.empty();
            }

            IUpgradeInventory upgrades = cell.getUpgrades(stack);
            if (upgrades == null) {
                return UpgradeInventories.empty();
            }

            this.cacheUpgrades = upgrades;
        }
        return this.cacheUpgrades;
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openItemGui(player, GuiIds.GuiKey.PORTABLE_CELL_WORKBENCH, getLocator(), true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEItems.PORTABLE_CELL_WORKBENCH.stack();
    }

    @Override
    public void saveChanges() {
        ItemStack stack = getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        this.cell.writeToNBT(tag, CELL_TAG);
        this.config.writeToChildTag(tag, CONFIG_TAG);
    }

    private void readFromStack() {
        NBTTagCompound tag = getItemStack().getTagCompound();
        if (tag == null) {
            return;
        }

        this.cell.readFromNBT(tag, CELL_TAG);
        this.lastCellStack = this.cell.getStackInSlot(0);
        ensureConfigSizeForCurrentCell();
        this.config.readFromChildTag(tag, CONFIG_TAG);
    }

    private void configChanged() {
        if (locked) {
            return;
        }

        this.locked = true;
        try {
            ConfigInventory c = this.getCellConfigInventory();
            if (c != null) {
                TileCellWorkbench.copy(this.config, c);
                TileCellWorkbench.copy(c, this.config);
            }
            saveChanges();
        } finally {
            this.locked = false;
        }
    }

    private void ensureConfigSizeForCurrentCell() {
        ConfigInventory configInventory = this.getCellConfigInventory();
        resizeConfig(configInventory != null ? configInventory.size() : 63);
    }

    private void resizeConfig(int size) {
        size = Math.max(0, size);
        if (this.config.size() == size) {
            return;
        }

        ConfigInventory oldConfig = this.config;
        this.config = createConfigInventory(size);
        boolean wasLocked = this.locked;
        this.locked = true;
        try {
            TileCellWorkbench.copy(oldConfig, this.config);
        } finally {
            this.locked = wasLocked;
        }
    }

    @Nullable
    private ConfigInventory getCellConfigInventory() {
        if (this.cacheConfig == null) {
            ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            ItemStack stack = this.cell.getStackInSlot(0);
            if (stack.isEmpty()) {
                return null;
            }

            ConfigInventory inv = cell.getConfigInventory(stack);
            if (inv == null) {
                return null;
            }

            this.cacheConfig = inv;
        }
        return this.cacheConfig;
    }
}
