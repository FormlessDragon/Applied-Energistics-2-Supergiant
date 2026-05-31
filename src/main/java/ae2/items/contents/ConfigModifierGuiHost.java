package ae2.items.contents;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.ConfigModifierItem;
import net.minecraft.entity.player.EntityPlayer;

public class ConfigModifierGuiHost extends ItemGuiHost<ConfigModifierItem> {

    public ConfigModifierGuiHost(ConfigModifierItem item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
    }

    public ConfigModifierItem.Settings getSettings() {
        return getItem().getSettings(getItemStack());
    }

    public void setMode(ConfigModifierItem.Mode mode) {
        getItem().setSettings(getItemStack(), new ConfigModifierItem.Settings(mode, getSettings().data()));
    }

    public void setData(long data) {
        getItem().setSettings(getItemStack(), new ConfigModifierItem.Settings(getSettings().mode(), data));
    }
}
