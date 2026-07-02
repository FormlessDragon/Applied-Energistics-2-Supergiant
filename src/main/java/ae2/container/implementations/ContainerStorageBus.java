package ae2.container.implementations;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Settings;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.MEStorage;
import ae2.api.util.IConfigManager;
import ae2.container.guisync.GuiSync;
import ae2.core.definitions.AEItems;
import ae2.parts.storagebus.StorageBusPart;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

public class ContainerStorageBus extends UpgradeableContainer<StorageBusPart> {

    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_PARTITION = "partition";

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo filterOnExtract = YesNo.YES;

    @GuiSync(8)
    @Nullable
    public ITextComponent connectedTo;

    public ContainerStorageBus(InventoryPlayer ip, StorageBusPart te) {
        super(ip, te);

        registerClientAction(ACTION_CLEAR, this::clear);
        registerClientAction(ACTION_PARTITION, this::partition);

        this.connectedTo = te.getConnectedToDescription();
    }

    @Override
    protected void setupConfig() {
        addExpandableConfigSlots(getHost().getConfig());
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.connectedTo = getHost().getConnectedToDescription();
        }

        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        this.setReadWriteMode(cm.getSetting(Settings.ACCESS));
        this.setStorageFilter(cm.getSetting(Settings.STORAGE_FILTER));
        this.setFilterOnExtract(cm.getSetting(Settings.FILTER_ON_EXTRACT));
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        final int upgrades = getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD.item());
        return upgrades > idx;
    }

    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }

        getHost().getConfig().clear();
        this.broadcastChanges();
    }

    public void partition() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }

        ConfigInventory inv = getHost().getConfig();
        MEStorage cellInv = getHost().getInternalHandler();

        Iterator<Object2LongMap.Entry<AEKey>> i = Collections.emptyIterator();
        if (cellInv != null) {
            i = cellInv.getAvailableStacks().iterator();
        }

        inv.beginBatch();
        try {
            for (int x = 0; x < inv.size(); x++) {
                if (i.hasNext() && this.isSlotEnabled(x / 9 - 2)) {
                    Object2LongMap.Entry<AEKey> entry = i.next();
                    inv.setStack(x, new GenericStack(entry.getKey(), getPartitionAmount(entry)));
                } else {
                    inv.setStack(x, null);
                }
            }
        } finally {
            inv.endBatch();
        }

        this.broadcastChanges();
    }

    private long getPartitionAmount(Object2LongMap.Entry<AEKey> entry) {
        return Math.max(1, entry.getLongValue());
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    private void setReadWriteMode(AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    private void setStorageFilter(StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }

    public YesNo getFilterOnExtract() {
        return this.filterOnExtract;
    }

    public void setFilterOnExtract(YesNo filterOnExtract) {
        this.filterOnExtract = filterOnExtract;
    }

    public boolean supportsFuzzySearch() {
        return hasUpgrade(AEItems.FUZZY_CARD.item());
    }

    @Nullable
    public ITextComponent getConnectedTo() {
        return this.connectedTo;
    }
}
