package ae2.container.implementations;

import ae2.api.config.IncludeExclude;
import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.SlotSemantic;
import ae2.container.SlotSemantics;
import ae2.container.slot.FakeSlot;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.helpers.WirelessTerminalMagnetHost;
import ae2.util.ConfigInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerWirelessMagnet extends AEBaseContainer implements ISubGui {
    private static final String ACTION_TOGGLE_PICKUP_MODE = "togglePickupMode";
    private static final String ACTION_TOGGLE_INSERT_MODE = "toggleInsertMode";
    private static final String ACTION_COPY_UP = "copyUp";
    private static final String ACTION_COPY_DOWN = "copyDown";
    private static final String ACTION_SWAP = "swap";

    private final WirelessTerminalGuiHost<?> host;
    private final WirelessTerminalMagnetHost magnetHost;

    public ContainerWirelessMagnet(InventoryPlayer ip, WirelessTerminalGuiHost<?> host) {
        super(ip, host);
        this.host = host;
        this.magnetHost = host.getMagnetHost();

        addConfigSlots(this.magnetHost.getPickupConfig(), SlotSemantics.MAGNET_PICKUP_CONFIG);
        addConfigSlots(this.magnetHost.getInsertConfig(), SlotSemantics.MAGNET_INSERT_CONFIG);
        addPlayerInventorySlots(8, 169);

        registerClientAction(ACTION_TOGGLE_PICKUP_MODE, this::togglePickupMode);
        registerClientAction(ACTION_TOGGLE_INSERT_MODE, this::toggleInsertMode);
        registerClientAction(ACTION_COPY_UP, this::copyInsertToPickup);
        registerClientAction(ACTION_COPY_DOWN, this::copyPickupToInsert);
        registerClientAction(ACTION_SWAP, this::swapConfigs);
    }

    private void addConfigSlots(ConfigInventory config, SlotSemantic semantic) {
        var inv = config.createGuiWrapper();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                int slot = y * 9 + x;
                addSlot(new FakeSlot(inv, slot, 8 + x * 18, 31 + y * 18), semantic);
            }
        }
    }

    @Override
    public GuiHostLocator getLocator() {
        return super.getLocator();
    }

    @Override
    public WirelessTerminalGuiHost<?> getHost() {
        return this.host;
    }

    public IncludeExclude getPickupMode() {
        return this.magnetHost.getPickupMode();
    }

    public IncludeExclude getInsertMode() {
        return this.magnetHost.getInsertMode();
    }

    @Override
    public void setFilter(int slotIndex, ItemStack item, boolean preferEmptying) {
        if (isServerSide() && !hasMagnetCard()) {
            return;
        }
        super.setFilter(slotIndex, item, preferEmptying);
    }

    public void togglePickupMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PICKUP_MODE);
        } else if (!hasMagnetCard()) {
            return;
        }
        this.magnetHost.togglePickupMode();
    }

    public void toggleInsertMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_INSERT_MODE);
        } else if (!hasMagnetCard()) {
            return;
        }
        this.magnetHost.toggleInsertMode();
    }

    public void copyInsertToPickup() {
        if (isClientSide()) {
            sendClientAction(ACTION_COPY_UP);
        } else if (!hasMagnetCard()) {
            return;
        }
        this.magnetHost.copyInsertToPickup();
    }

    public void copyPickupToInsert() {
        if (isClientSide()) {
            sendClientAction(ACTION_COPY_DOWN);
        } else if (!hasMagnetCard()) {
            return;
        }
        this.magnetHost.copyPickupToInsert();
    }

    public void swapConfigs() {
        if (isClientSide()) {
            sendClientAction(ACTION_SWAP);
        } else if (!hasMagnetCard()) {
            return;
        }
        this.magnetHost.swapConfigs();
    }

    private boolean hasMagnetCard() {
        return this.host.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
    }
}
