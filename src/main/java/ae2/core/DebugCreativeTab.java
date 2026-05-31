package ae2.core;

import ae2.api.ids.AECreativeTabIds;
import ae2.core.definitions.AEItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public final class DebugCreativeTab extends CreativeTabs {

    public static final DebugCreativeTab INSTANCE = new DebugCreativeTab();

    private DebugCreativeTab() {
        super(AECreativeTabIds.DEBUG.toString());
    }

    @Override
    public ItemStack createIcon() {
        return AEItems.DEBUG_CARD.stack();
    }
}
