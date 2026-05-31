/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.util;

import ae2.api.config.Setting;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerListener;
import ae2.api.util.UnsupportedSettingException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public final class ConfigManager implements IConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigManager.class);

    private final Map<Setting<?>, Enum<?>> settings = new Reference2ObjectOpenHashMap<>();
    @Nullable
    private final IConfigManagerListener listener;

    public ConfigManager(@Nullable IConfigManagerListener listener) {
        this.listener = listener;
    }

    public ConfigManager(Runnable changeListener) {
        this.listener = (ignoredManager, ignoredSetting) -> changeListener.run();
    }

    @Override
    public Set<Setting<?>> getSettings() {
        return this.settings.keySet();
    }

    public <T extends Enum<T>> void registerSetting(Setting<T> setting, T defaultValue) {
        this.settings.put(setting, defaultValue);
    }

    @Override
    public <T extends Enum<T>> T getSetting(Setting<T> setting) {
        var oldValue = this.settings.get(setting);
        if (oldValue == null) {
            throw new UnsupportedSettingException("Setting " + setting.getName() + " is not supported.");
        }
        return setting.getEnumClass().cast(oldValue);
    }

    @Override
    public <T extends Enum<T>> void putSetting(Setting<T> setting, T newValue) {
        if (!settings.containsKey(setting)) {
            throw new UnsupportedSettingException("Setting " + setting.getName() + " is not supported.");
        }
        this.settings.put(setting, newValue);
        if (this.listener != null) {
            this.listener.onSettingChanged(this, setting);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound destination) {
        for (var entry : this.settings.entrySet()) {
            destination.setString(entry.getKey().getName(), entry.getValue().name());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound src) {
        for (var setting : this.settings.keySet()) {
            if (src.hasKey(setting.getName(), 8)) {
                String value = src.getString(setting.getName());
                try {
                    setting.setFromString(this, value);
                } catch (IllegalArgumentException e) {
                    LOG.warn("Failed to load setting {} from value '{}': {}", setting, value, e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean importSettings(Map<String, String> settings) {
        boolean anythingRead = false;
        for (var setting : this.settings.keySet()) {
            String value = settings.get(setting.getName());
            if (value != null) {
                try {
                    setting.setFromString(this, value);
                    anythingRead = true;
                } catch (IllegalArgumentException e) {
                    LOG.warn("Failed to load setting {} from value '{}': {}", setting, value, e.getMessage());
                }
            }
        }
        return anythingRead;
    }

    @Override
    public Map<String, String> exportSettings() {
        Map<String, String> result = new Object2ObjectOpenHashMap<>();
        for (var entry : this.settings.entrySet()) {
            result.put(entry.getKey().getName(), entry.getValue().name());
        }
        return Map.copyOf(result);
    }
}
