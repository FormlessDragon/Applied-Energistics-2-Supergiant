package ae2.helpers;

import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.util.ConfigInventory;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

public class WirelessTerminalMagnetHost {
    private final ItemStack stack;
    private final WirelessTerminalItem terminal;
    private final ConfigInventory pickupConfig;
    private final ConfigInventory insertConfig;

    private IPartitionList pickupFilter;
    private IPartitionList insertFilter;

    public WirelessTerminalMagnetHost(ItemStack stack, WirelessTerminalItem terminal) {
        this.stack = stack;
        this.terminal = terminal;
        this.pickupConfig = ConfigInventory.configTypes(27)
                                           .supportedType(AEKeyType.items())
                                           .changeListener(this::savePickupConfig)
                                           .build();
        this.insertConfig = ConfigInventory.configTypes(27)
                                           .supportedType(AEKeyType.items())
                                           .changeListener(this::saveInsertConfig)
                                           .build();

        NBTTagCompound tag = WirelessTerminals.getExistingTerminalData(stack, terminal);
        if (tag != null) {
            this.pickupConfig.readFromChildTag(tag, WirelessTerminals.TAG_MAGNET_PICKUP_CONFIG);
            this.insertConfig.readFromChildTag(tag, WirelessTerminals.TAG_MAGNET_INSERT_CONFIG);
        }
        this.pickupFilter = createFilter(this.pickupConfig);
        this.insertFilter = createFilter(this.insertConfig);
    }

    private static IPartitionList createFilter(ConfigInventory config) {
        IPartitionList.Builder builder = IPartitionList.builder();
        builder.fuzzyMode(FuzzyMode.IGNORE_ALL);
        for (int i = 0; i < config.size(); i++) {
            builder.add(config.getKey(i));
        }
        return builder.build();
    }

    private static boolean matches(IPartitionList filter, IncludeExclude mode, AEItemKey key) {
        if (filter.isEmpty() && mode == IncludeExclude.WHITELIST) {
            return false;
        }
        return filter.matchesFilter(key, mode);
    }

    private static IncludeExclude toggle(IncludeExclude mode) {
        return mode == IncludeExclude.WHITELIST ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
    }

    private static void copyConfig(ConfigInventory source, ConfigInventory target) {
        target.beginBatch();
        try {
            for (int i = 0; i < target.size(); i++) {
                @Nullable AEKey key = i < source.size() ? source.getKey(i) : null;
                target.setStack(i, key == null ? null : new GenericStack(key, 1));
            }
        } finally {
            target.endBatch();
        }
    }

    public ConfigInventory getPickupConfig() {
        return this.pickupConfig;
    }

    public ConfigInventory getInsertConfig() {
        return this.insertConfig;
    }

    public IncludeExclude getPickupMode() {
        return getMode(WirelessTerminals.TAG_MAGNET_PICKUP_MODE);
    }

    public void togglePickupMode() {
        setMode(WirelessTerminals.TAG_MAGNET_PICKUP_MODE, toggle(getPickupMode()));
    }

    public IncludeExclude getInsertMode() {
        return getMode(WirelessTerminals.TAG_MAGNET_INSERT_MODE);
    }

    public void toggleInsertMode() {
        setMode(WirelessTerminals.TAG_MAGNET_INSERT_MODE, toggle(getInsertMode()));
    }

    public boolean matchesPickup(AEItemKey key) {
        return matches(this.pickupFilter, getPickupMode(), key);
    }

    public boolean matchesInsert(AEItemKey key) {
        return matches(this.insertFilter, getInsertMode(), key);
    }

    public void copyInsertToPickup() {
        copyConfig(this.insertConfig, this.pickupConfig);
    }

    public void copyPickupToInsert() {
        copyConfig(this.pickupConfig, this.insertConfig);
    }

    public void swapConfigs() {
        ConfigInventory oldPickup = ConfigInventory.configTypes(27).supportedType(AEKeyType.items()).build();
        copyConfig(this.pickupConfig, oldPickup);
        copyConfig(this.insertConfig, this.pickupConfig);
        copyConfig(oldPickup, this.insertConfig);
    }

    private IncludeExclude getMode(String tagName) {
        NBTTagCompound tag = WirelessTerminals.getExistingTerminalData(this.stack, this.terminal);
        if (tag == null || !tag.hasKey(tagName)) {
            return IncludeExclude.BLACKLIST;
        }
        return tag.getBoolean(tagName) ? IncludeExclude.WHITELIST : IncludeExclude.BLACKLIST;
    }

    private void setMode(String tagName, IncludeExclude mode) {
        WirelessTerminals.getTerminalData(this.stack, this.terminal)
                         .setBoolean(tagName, mode == IncludeExclude.WHITELIST);
    }

    private void savePickupConfig() {
        NBTTagCompound tag = WirelessTerminals.getTerminalData(this.stack, this.terminal);
        this.pickupConfig.writeToChildTag(tag, WirelessTerminals.TAG_MAGNET_PICKUP_CONFIG);
        this.pickupFilter = createFilter(this.pickupConfig);
    }

    private void saveInsertConfig() {
        NBTTagCompound tag = WirelessTerminals.getTerminalData(this.stack, this.terminal);
        this.insertConfig.writeToChildTag(tag, WirelessTerminals.TAG_MAGNET_INSERT_CONFIG);
        this.insertFilter = createFilter(this.insertConfig);
    }
}
