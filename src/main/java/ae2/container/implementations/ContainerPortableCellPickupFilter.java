package ae2.container.implementations;

import ae2.api.config.IncludeExclude;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.storage.ISubGuiHost;
import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.SlotSemantics;
import ae2.container.slot.FakeSlot;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import ae2.util.ConfigInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerPortableCellPickupFilter extends AEBaseContainer implements ISubGui {
    private static final String ACTION_TOGGLE_MODE = "togglePickupMode";
    private static final String ACTION_CLEAR_CONFIG = "clearPickupConfig";
    private static final String ACTION_TOGGLE_MATCH_NBT = "togglePickupMatchNbt";
    private static final String ACTION_TOGGLE_MATCH_DAMAGE = "togglePickupMatchDamage";
    private static final String ACTION_TOGGLE_MATCH_ORE_DICTIONARY = "togglePickupMatchOreDictionary";

    private final ItemGuiHost<?> host;
    private final ISubGuiHost subGuiHost;
    private final ConfigInventory pickupConfig;

    public ContainerPortableCellPickupFilter(InventoryPlayer ip, ItemGuiHost<?> host) {
        super(ip, host);
        this.host = host;
        if (!(host instanceof ISubGuiHost resolvedSubGuiHost)) {
            throw new IllegalArgumentException("Portable cell pickup filter host must support sub-guis");
        }
        this.subGuiHost = resolvedSubGuiHost;

        ItemStack stack = host.getItemStack();
        if (!PortableItemCellAutoPickup.isSupported(stack)) {
            throw new IllegalArgumentException("Portable cell pickup filter requires a supported portable cell");
        }

        this.pickupConfig = PortableItemCellAutoPickup.getPickupConfig(stack);
        addPickupConfigSlots();
        addPlayerInventorySlots(8, 169);

        registerClientAction(ACTION_TOGGLE_MODE, this::togglePickupMode);
        registerClientAction(ACTION_CLEAR_CONFIG, this::clearPickupConfig);
        registerClientAction(ACTION_TOGGLE_MATCH_NBT, this::toggleMatchNbt);
        registerClientAction(ACTION_TOGGLE_MATCH_DAMAGE, this::toggleMatchDamage);
        registerClientAction(ACTION_TOGGLE_MATCH_ORE_DICTIONARY, this::toggleMatchOreDictionary);
    }

    private void addPickupConfigSlots() {
        var inv = this.pickupConfig.createGuiWrapper();
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                int slot = y * 9 + x;
                addSlot(new FakeSlot(inv, slot, 8 + x * 18, 29 + y * 18), SlotSemantics.PORTABLE_CELL_PICKUP_FILTER);
            }
        }
    }

    @Override
    public void setFilter(int slotIndex, ItemStack item, boolean preferEmptying) {
        if (isServerSide() && !canConfigurePickupFilter()) {
            return;
        }
        super.setFilter(slotIndex, item, preferEmptying);
    }

    public IncludeExclude getPickupMode() {
        return PortableItemCellAutoPickup.getPickupMode(this.host.getItemStack());
    }

    public void togglePickupMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_MODE);
            return;
        }
        if (!canConfigurePickupFilter()) {
            return;
        }
        PortableItemCellAutoPickup.togglePickupMode(this.host.getItemStack());
    }

    public boolean isMatchNbt() {
        return PortableItemCellAutoPickup.isMatchNbt(this.host.getItemStack());
    }

    public boolean isMatchDamage() {
        return PortableItemCellAutoPickup.isMatchDamage(this.host.getItemStack());
    }

    public boolean isMatchOreDictionary() {
        return PortableItemCellAutoPickup.isMatchOreDictionary(this.host.getItemStack());
    }

    public void clearPickupConfig() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_CONFIG);
            return;
        }
        if (!canConfigurePickupFilter()) {
            return;
        }
        PortableItemCellAutoPickup.clearPickupConfig(this.host.getItemStack());
    }

    public void toggleMatchNbt() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_MATCH_NBT);
            return;
        }
        if (!canConfigurePickupFilter()) {
            return;
        }
        PortableItemCellAutoPickup.toggleMatchNbt(this.host.getItemStack());
    }

    public void toggleMatchDamage() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_MATCH_DAMAGE);
            return;
        }
        if (!canConfigurePickupFilter()) {
            return;
        }
        PortableItemCellAutoPickup.toggleMatchDamage(this.host.getItemStack());
    }

    public void toggleMatchOreDictionary() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_MATCH_ORE_DICTIONARY);
            return;
        }
        if (!canConfigurePickupFilter()) {
            return;
        }
        PortableItemCellAutoPickup.toggleMatchOreDictionary(this.host.getItemStack());
    }

    private boolean canConfigurePickupFilter() {
        ItemStack stack = this.host.getItemStack();
        return !stack.isEmpty()
            && PortableItemCellAutoPickup.isSupported(stack);
    }

    @Override
    public ISubGuiHost getHost() {
        return this.subGuiHost;
    }
}
