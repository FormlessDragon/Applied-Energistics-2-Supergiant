/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.util;

import ae2.api.config.Setting;
import ae2.util.ConfigManager;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Used to adjust settings on an object,
 * <p>
 * Obtained via {@link IConfigurableObject}
 */
public interface IConfigManager {
    String EXPORTED_SETTINGS = "exported_settings";

    /**
     * Get a builder for a configuration manager that stores its settings in an item stack.
     */
    static IConfigManagerBuilder builder(ItemStack stack) {
        return builder(() -> stack);
    }

    /**
     * Get a builder for a configuration manager that stores its settings in an item stack.
     */
    static IConfigManagerBuilder builder(Supplier<ItemStack> stack) {
        var manager = new ConfigManager((mgr, settingName) -> {
            NBTTagCompound settings = new NBTTagCompound();
            for (var entry : mgr.exportSettings().entrySet()) {
                settings.setString(entry.getKey(), entry.getValue());
            }
            NBTTagCompound tag = Platform.openNbtData(stack.get());
            if (Platform.isNbtEmpty(settings)) {
                tag.removeTag(EXPORTED_SETTINGS);
            } else {
                tag.setTag(EXPORTED_SETTINGS, settings.copy());
            }
        });

        return new IConfigManagerBuilder() {
            @Override
            public <T extends Enum<T>> IConfigManagerBuilder registerSetting(Setting<T> setting, T defaultValue) {
                manager.registerSetting(setting, defaultValue);
                return this;
            }

            @Override
            public IConfigManager build() {
                NBTTagCompound tag = stack.get().getTagCompound();
                if (tag != null && tag.hasKey(EXPORTED_SETTINGS, 10)) {
                    NBTTagCompound settingsTag = tag.getCompoundTag(EXPORTED_SETTINGS);
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

    static IConfigManagerBuilder builder(Runnable changeListener) {
        return builder((manager, setting) -> changeListener.run());
    }

    static IConfigManagerBuilder builder(IConfigManagerListener changeListener) {
        var manager = new ConfigManager(changeListener);
        return new IConfigManagerBuilder() {
            @Override
            public <T extends Enum<T>> IConfigManagerBuilder registerSetting(Setting<T> setting, T defaultValue) {
                manager.registerSetting(setting, defaultValue);
                return this;
            }

            @Override
            public IConfigManager build() {
                return manager;
            }
        };
    }

    /**
     * get a list of different settings
     *
     * @return enum set of settings
     */
    Set<Setting<?>> getSettings();

    /**
     * Checks if this config manager supports the given setting.
     */
    default boolean hasSetting(Setting<?> setting) {
        return getSettings().contains(setting);
    }

    /**
     * Get Value of a particular setting
     *
     * @param setting the setting
     * @return value of setting
     * @throws UnsupportedSettingException if setting has not been registered before
     */
    <T extends Enum<T>> T getSetting(Setting<T> setting);

    /**
     * Change setting
     *
     * @param setting  to be changed setting
     * @param newValue new value for setting
     * @throws UnsupportedSettingException if setting has not been registered before
     */
    <T extends Enum<T>> void putSetting(Setting<T> setting, T newValue);

    /**
     * write all settings to the NBT Tag so they can be read later.
     *
     * @param destination to be written nbt tag
     */
    void writeToNBT(NBTTagCompound destination);

    /**
     * Only works after settings have been registered
     *
     * @param src to be read nbt tag
     */
    void readFromNBT(NBTTagCompound src);

    /**
     * Import settings that were previously exported from {@link #exportSettings()}. Unparsable or unknown settings are
     * ignored.
     *
     * @return true if any of the settings were successfully imported
     */
    boolean importSettings(Map<String, String> settings);

    /**
     * Exports all settings.
     */
    Map<String, String> exportSettings();
}
