package ae2.items.tools.powered;

import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.api.inventories.InternalInventory;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.core.definitions.AEItems;
import ae2.util.Platform;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;
import java.util.PrimitiveIterator;

final class WirelessUniversalTerminalUpgradeInventory extends AppEngInternalInventory
    implements InternalInventoryHost, IUpgradeInventory {
    private static final String TAG_UPGRADES = "upgrades";

    private final WirelessUniversalTerminalItem item;
    private final ItemStack stack;
    private Reference2IntMap<Item> installed;

    WirelessUniversalTerminalUpgradeInventory(WirelessUniversalTerminalItem item, ItemStack stack) {
        super(null, getSlotCount(item, stack), 1);
        this.item = item;
        this.stack = stack;
        this.setHost(this);
        this.setFilter(new UpgradeFilter());

        if (stack.hasTagCompound()) {
            readFromNBT(stack.getTagCompound(), TAG_UPGRADES);
        }
    }

    private static int getSlotCount(WirelessUniversalTerminalItem item, ItemStack stack) {
        int slots = 0;
        for (String id : item.getInstalledTerminalIds(stack)) {
            WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionOfId(id);
            if (definition != null) {
                slots += definition.upgradeSlots();
            }
        }
        return slots;
    }

    static int combineMaxInstalled(Item upgradeCard, PrimitiveIterator.OfInt terminalMaximums) {
        int max = 0;
        while (terminalMaximums.hasNext()) {
            max += terminalMaximums.nextInt();
        }

        if (isUniqueWirelessUpgrade(upgradeCard)) {
            return Math.min(1, max);
        }

        return max;
    }

    private static boolean isUniqueWirelessUpgrade(Item upgradeCard) {
        return upgradeCard == AEItems.QUANTUM_BRIDGE_CARD.item()
            || upgradeCard == AEItems.MAGNET_CARD.item();
    }

    @Override
    public Item getUpgradableItem() {
        return stack.getItem();
    }

    @Override
    public int getInstalledUpgrades(Item upgradeCard) {
        if (installed == null) {
            installed = new Reference2IntArrayMap<>(size());
            for (var card : this) {
                int maxInstalled = getMaxInstalled(card.getItem());
                if (maxInstalled > 0) {
                    installed.merge(card.getItem(), 1, (a, b) -> Math.min(maxInstalled, a + b));
                }
            }
        }
        return installed.getOrDefault(upgradeCard, 0);
    }

    @Override
    public int getMaxInstalled(Item upgradeCard) {
        int[] perTerminalMax = new int[item.getInstalledTerminalIds(stack).size()];
        int count = 0;
        for (String id : item.getInstalledTerminalIds(stack)) {
            WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionOfId(id);
            if (definition != null) {
                perTerminalMax[count++] = Upgrades.getMaxInstallable(upgradeCard, definition.item());
            }
        }
        return combineMaxInstalled(upgradeCard, Arrays.stream(perTerminalMax, 0, count).iterator());
    }

    @Override
    public void readFromNBT(NBTTagCompound data, String subtag) {
        super.readFromNBT(data, subtag);
        this.installed = null;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
        }
        writeToNBT(tag, TAG_UPGRADES);
        stack.setTagCompound(Platform.isNbtEmpty(tag) ? null : tag);
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.installed = null;
        item.onUpgradesChanged(stack, this);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    private class UpgradeFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return IAEItemFilter.super.allowExtract(inv, slot, amount);
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            Item cardItem = stack.getItem();
            return Upgrades.isUpgradeCardItem(stack) && getInstalledUpgrades(cardItem) < getMaxInstalled(cardItem);
        }
    }
}
