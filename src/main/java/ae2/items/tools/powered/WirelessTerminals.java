package ae2.items.tools.powered;

import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.util.ConfigManager;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public final class WirelessTerminals {
    public static final String TAG_LINK = "wireless_link";
    public static final String TAG_LINK_DIM = "dim";
    public static final String TAG_LINK_X = "x";
    public static final String TAG_LINK_Y = "y";
    public static final String TAG_LINK_Z = "z";
    public static final String TAG_VIEW_CELLS = "view_cells";
    public static final String TAG_CRAFTING_GRID = "crafting_grid";
    public static final String TAG_PATTERN_ENCODING = "pattern_encoding";
    public static final String TAG_SINGULARITY = "quantum_singularity";
    public static final String TAG_INSTALLED_TERMINALS = "installed_terminals";
    public static final String TAG_CURRENT_TERMINAL = "current_terminal";
    public static final String TAG_TERMINAL_DATA = "terminal_data";
    public static final String TAG_PICK_BLOCK = "pick_block";
    public static final String TAG_CRAFT_IF_MISSING = "craft_if_missing";
    public static final String TAG_RESTOCK = "restock";
    public static final String TAG_MAGNET_MODE = "magnet_mode";
    public static final String TAG_MAGNET_PICKUP_CONFIG = "magnet_pickup_config";
    public static final String TAG_MAGNET_INSERT_CONFIG = "magnet_insert_config";
    public static final String TAG_MAGNET_PICKUP_MODE = "magnet_pickup_mode";
    public static final String TAG_MAGNET_INSERT_MODE = "magnet_insert_mode";

    private WirelessTerminals() {
    }

    public static NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    public static NBTTagCompound getTerminalData(ItemStack stack, WirelessTerminalItem terminal) {
        if (stack.getItem() instanceof WirelessUniversalTerminalItem) {
            NBTTagCompound tag = getOrCreateTag(stack);
            migrateUniversalSharedLink(tag, terminal);
            NBTTagCompound allData = tag.getCompoundTag(TAG_TERMINAL_DATA);
            NBTTagCompound terminalData = allData.getCompoundTag(terminal.getTerminalId());
            allData.setTag(terminal.getTerminalId(), terminalData);
            tag.setTag(TAG_TERMINAL_DATA, allData);
            return terminalData;
        }
        return getOrCreateTag(stack);
    }

    public static IConfigManagerBuilder configBuilder(Supplier<ItemStack> stack, WirelessTerminalItem terminal) {
        var manager = new ConfigManager((mgr, ignoredSetting) -> {
            NBTTagCompound settings = new NBTTagCompound();
            for (var entry : mgr.exportSettings().entrySet()) {
                settings.setString(entry.getKey(), entry.getValue());
            }

            NBTTagCompound terminalData = getTerminalData(stack.get(), terminal);
            if (Platform.isNbtEmpty(settings)) {
                terminalData.removeTag(IConfigManager.EXPORTED_SETTINGS);
            } else {
                terminalData.setTag(IConfigManager.EXPORTED_SETTINGS, settings.copy());
            }
        });

        return new IConfigManagerBuilder() {
            @Override
            public <T extends Enum<T>> IConfigManagerBuilder registerSetting(ae2.api.config.Setting<T> setting,
                                                                             T defaultValue) {
                manager.registerSetting(setting, defaultValue);
                return this;
            }

            @Override
            public IConfigManager build() {
                NBTTagCompound tag = getExistingTerminalData(stack.get(), terminal);
                if (tag != null && tag.hasKey(IConfigManager.EXPORTED_SETTINGS, 10)) {
                    NBTTagCompound settingsTag = tag.getCompoundTag(IConfigManager.EXPORTED_SETTINGS);
                    Object2ObjectMap<String, String> settings = new Object2ObjectOpenHashMap<>();
                    for (String key : settingsTag.getKeySet()) {
                        settings.put(key, settingsTag.getString(key));
                    }
                    manager.importSettings(settings);
                }
                return manager;
            }
        };
    }

    public static boolean isPickBlockEnabled(ItemStack stack, WirelessTerminalItem terminal) {
        return getBoolean(stack, terminal, TAG_PICK_BLOCK);
    }

    public static void setPickBlockEnabled(ItemStack stack, WirelessTerminalItem terminal, boolean enabled) {
        setBoolean(stack, terminal, TAG_PICK_BLOCK, enabled);
    }

    public static boolean isCraftIfMissingEnabled(ItemStack stack, WirelessTerminalItem terminal) {
        return getBoolean(stack, terminal, TAG_CRAFT_IF_MISSING);
    }

    public static void setCraftIfMissingEnabled(ItemStack stack, WirelessTerminalItem terminal, boolean enabled) {
        setBoolean(stack, terminal, TAG_CRAFT_IF_MISSING, enabled);
    }

    public static boolean isRestockEnabled(ItemStack stack, WirelessTerminalItem terminal) {
        return getBoolean(stack, terminal, TAG_RESTOCK);
    }

    public static void setRestockEnabled(ItemStack stack, WirelessTerminalItem terminal, boolean enabled) {
        setBoolean(stack, terminal, TAG_RESTOCK, enabled);
    }

    public static WirelessTerminalMagnetMode getMagnetMode(ItemStack stack, WirelessTerminalItem terminal) {
        NBTTagCompound tag = getExistingTerminalData(stack, terminal);
        if (tag == null || !tag.hasKey(TAG_MAGNET_MODE)) {
            return WirelessTerminalMagnetMode.OFF;
        }
        return WirelessTerminalMagnetMode.fromId(tag.getByte(TAG_MAGNET_MODE));
    }

    public static void setMagnetMode(ItemStack stack, WirelessTerminalItem terminal,
                                     WirelessTerminalMagnetMode mode) {
        getTerminalData(stack, terminal).setByte(TAG_MAGNET_MODE, mode.id());
    }

    private static boolean getBoolean(ItemStack stack, WirelessTerminalItem terminal, String tagName) {
        NBTTagCompound tag = getExistingTerminalData(stack, terminal);
        return tag != null && tag.getBoolean(tagName);
    }

    private static void setBoolean(ItemStack stack, WirelessTerminalItem terminal, String tagName, boolean enabled) {
        getTerminalData(stack, terminal).setBoolean(tagName, enabled);
    }

    @Nullable
    public static NBTTagCompound getExistingTerminalData(ItemStack stack, WirelessTerminalItem terminal) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        if (stack.getItem() instanceof WirelessUniversalTerminalItem) {
            migrateUniversalSharedLink(tag, terminal);
            NBTTagCompound allData = tag.getCompoundTag(TAG_TERMINAL_DATA);
            if (!allData.hasKey(terminal.getTerminalId(), 10)) {
                return null;
            }
            return allData.getCompoundTag(terminal.getTerminalId());
        }
        return tag;
    }

    @Nullable
    public static NBTTagCompound getUniversalSharedData(ItemStack stack) {
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem)) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }

        migrateUniversalSharedLink(tag, null);
        return tag;
    }

    public static NBTTagCompound getOrCreateUniversalSharedData(ItemStack stack) {
        NBTTagCompound tag = getOrCreateTag(stack);
        migrateUniversalSharedLink(tag, null);
        return tag;
    }

    private static void migrateUniversalSharedLink(NBTTagCompound root, @Nullable WirelessTerminalItem terminal) {
        if (root.hasKey(TAG_LINK, 10)) {
            removeLegacyUniversalLink(root, terminal);
            return;
        }

        NBTTagCompound allData = root.getCompoundTag(TAG_TERMINAL_DATA);
        if (terminal != null && allData.hasKey(terminal.getTerminalId(), 10)) {
            NBTTagCompound terminalData = allData.getCompoundTag(terminal.getTerminalId());
            if (terminalData.hasKey(TAG_LINK, 10)) {
                root.setTag(TAG_LINK, terminalData.getCompoundTag(TAG_LINK).copy());
            }
        }

        if (!root.hasKey(TAG_LINK, 10)) {
            for (String key : List.copyOf(allData.getKeySet())) {
                NBTTagCompound terminalData = allData.getCompoundTag(key);
                if (terminalData.hasKey(TAG_LINK, 10)) {
                    root.setTag(TAG_LINK, terminalData.getCompoundTag(TAG_LINK).copy());
                    break;
                }
            }
        }

        removeLegacyUniversalLink(root, terminal);
    }

    private static void removeLegacyUniversalLink(NBTTagCompound root, @Nullable WirelessTerminalItem terminal) {
        NBTTagCompound allData = root.getCompoundTag(TAG_TERMINAL_DATA);
        if (terminal != null && allData.hasKey(terminal.getTerminalId(), 10)) {
            NBTTagCompound terminalData = allData.getCompoundTag(terminal.getTerminalId());
            terminalData.removeTag(TAG_LINK);
            if (terminalData.isEmpty()) {
                allData.removeTag(terminal.getTerminalId());
            } else {
                allData.setTag(terminal.getTerminalId(), terminalData);
            }
        } else {
            for (String key : List.copyOf(allData.getKeySet())) {
                NBTTagCompound terminalData = allData.getCompoundTag(key);
                terminalData.removeTag(TAG_LINK);
                if (terminalData.isEmpty()) {
                    allData.removeTag(key);
                } else {
                    allData.setTag(key, terminalData);
                }
            }
        }
        root.setTag(TAG_TERMINAL_DATA, allData);
    }
}
