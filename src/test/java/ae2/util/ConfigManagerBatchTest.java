package ae2.util;

import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerListener;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerBatchTest {
    @Test
    void NbtLoadAndImportNotifyOnceAfterAllSettingsAreApplied() {
        var calls = new AtomicInteger();
        var seen = new AtomicReference<>();
        var listener = new IConfigManagerListener() {
            @Override
            public void onSettingChanged(IConfigManager loaded, Setting<?> setting) {
                calls.incrementAndGet();
                seen.set(setting);
            }

            @Override
            public void onSettingsChanged(IConfigManager loaded, Set<Setting<?>> settings) {
                calls.incrementAndGet();
                seen.set(settings.iterator().next());
                assertEquals(2, settings.size());
            }
        };
        IConfigManager manager = new ConfigManager(listener);
        var concrete = (ConfigManager) manager;
        concrete.registerSetting(Settings.CRAFT_ONLY, YesNo.YES);
        concrete.registerSetting(Settings.CRAFT_VIA_REDSTONE, YesNo.YES);
        var data = new NBTTagCompound();
        data.setString(Settings.CRAFT_ONLY.getName(), YesNo.NO.name());
        data.setString(Settings.CRAFT_VIA_REDSTONE.getName(), YesNo.NO.name());
        manager.readFromNBT(data);
        assertEquals(1, calls.get());
        assertTrue(seen.get() == Settings.CRAFT_ONLY || seen.get() == Settings.CRAFT_VIA_REDSTONE);
        assertEquals(YesNo.NO, manager.getSetting(Settings.CRAFT_ONLY));
        assertEquals(YesNo.NO, manager.getSetting(Settings.CRAFT_VIA_REDSTONE));
        manager.importSettings(Map.of(Settings.CRAFT_ONLY.getName(), YesNo.YES.name(),
            Settings.CRAFT_VIA_REDSTONE.getName(), YesNo.YES.name()));
        assertEquals(2, calls.get());
        assertEquals(YesNo.YES, manager.getSetting(Settings.CRAFT_ONLY));
        assertEquals(YesNo.YES, manager.getSetting(Settings.CRAFT_VIA_REDSTONE));
    }

    @Test
    void InvalidValuesDoNotNotifyAndAListenerCannotRecursivelyLoadTheManager() {
        var calls = new AtomicInteger();
        var recursive = new AtomicReference<ConfigManager>();
        var recursiveFailure = new AtomicReference<Throwable>();
        var manager = new ConfigManager(new IConfigManagerListener() {
            @Override
            public void onSettingChanged(IConfigManager loaded, Setting<?> setting) {
                calls.incrementAndGet();
            }

            @Override
            public void onSettingsChanged(IConfigManager loaded, Set<Setting<?>> settings) {
                calls.incrementAndGet();
                try {
                    var nested = new NBTTagCompound();
                    nested.setString(Settings.CRAFT_ONLY.getName(), YesNo.YES.name());
                    recursive.get().readFromNBT(nested);
                } catch (Throwable failure) {
                    recursiveFailure.set(failure);
                }
            }
        });
        recursive.set(manager);
        manager.registerSetting(Settings.CRAFT_ONLY, YesNo.YES);
        var invalid = new NBTTagCompound();
        invalid.setString(Settings.CRAFT_ONLY.getName(), "invalid");
        manager.readFromNBT(invalid);
        assertEquals(0, calls.get());
        var valid = new NBTTagCompound();
        valid.setString(Settings.CRAFT_ONLY.getName(), YesNo.NO.name());
        manager.readFromNBT(valid);
        assertEquals(1, calls.get());
        assertInstanceOf(IllegalStateException.class, recursiveFailure.get());
    }
}
