package ae2.tile.crafting;

import ae2.api.inventories.InternalInventory;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

final class MolecularAssemblerUpgradeInventory extends AppEngInternalInventory implements IUpgradeInventory,
    InternalInventoryHost {
    private final TileMolecularAssembler host;
    @Nullable
    private Reference2IntMap<Item> installed;

    MolecularAssemblerUpgradeInventory(TileMolecularAssembler host) {
        super(null, 5 + AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit(), 1);
        this.host = host;
        this.setHost(this);
        this.setFilter(new UpgradeFilter());
    }

    @Override
    public Item getUpgradableItem() {
        return AEBlocks.MOLECULAR_ASSEMBLER.item();
    }

    @Override
    public int getInstalledUpgrades(Item upgradeCard) {
        if (this.installed == null) {
            this.updateUpgradeInfo();
        }
        return this.installed.getOrDefault(upgradeCard, 0);
    }

    @Override
    public int getMaxInstalled(Item upgradeCard) {
        return Upgrades.getMaxInstallable(upgradeCard, getUpgradableItem());
    }

    @Override
    public void readFromNBT(NBTTagCompound data, String subtag) {
        super.readFromNBT(data, subtag);
        this.updateUpgradeInfo();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.host.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.installed = null;
        this.host.onUpgradesChanged();
    }

    @Override
    public boolean isClientSide() {
        return this.host.isClientSide();
    }

    private void updateUpgradeInfo() {
        this.installed = new Reference2IntArrayMap<>(size());
        for (ItemStack stack : this) {
            if (stack.isEmpty()) {
                continue;
            }
            int maxInstalled = getMaxInstalled(stack.getItem());
            if (maxInstalled > 0) {
                this.installed.merge(stack.getItem(), 1, (a, b) -> Math.min(maxInstalled, a + b));
            }
        }
    }

    private boolean isPatternExpansionCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == AEItems.PATTERN_EXPANSION_CARD.item();
    }

    private final class UpgradeFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!isPatternExpansionCard(stack)) {
                return true;
            }

            int removedCards = Math.min(amount, stack.getCount());
            return PatternProviderCapacity.canRemoveCapacityCards(
                getInstalledUpgrades(AEItems.PATTERN_EXPANSION_CARD.item()),
                removedCards,
                AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit(),
                host::isPatternSlotOccupied);
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack itemstack) {
            Item cardItem = itemstack.getItem();
            return getInstalledUpgrades(cardItem) < getMaxInstalled(cardItem);
        }
    }
}
