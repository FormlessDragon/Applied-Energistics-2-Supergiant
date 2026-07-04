package ae2.items.contents;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.PriorityTunerItem;
import net.minecraft.entity.player.EntityPlayer;

public class PriorityTunerGuiHost extends ItemGuiHost<PriorityTunerItem> {

    public PriorityTunerGuiHost(PriorityTunerItem item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
    }

    public PriorityTunerItem.Settings getSettings() {
        return getItem().getSettings(getItemStack());
    }

    public void setMode(PriorityTunerItem.Mode mode) {
        getItem().setSettings(getItemStack(), new PriorityTunerItem.Settings(mode, getSettings().priority()));
    }

    public void setPriority(int priority) {
        getItem().setSettings(getItemStack(), new PriorityTunerItem.Settings(getSettings().mode(), priority));
    }
}
