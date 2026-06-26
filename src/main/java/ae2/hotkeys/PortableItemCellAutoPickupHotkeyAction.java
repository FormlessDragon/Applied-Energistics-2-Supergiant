package ae2.hotkeys;

import ae2.api.features.HotkeyAction;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import net.minecraft.entity.player.EntityPlayerMP;

public final class PortableItemCellAutoPickupHotkeyAction implements HotkeyAction {
    @Override
    public boolean run(EntityPlayerMP player) {
        return PortableItemCellAutoPickup.toggleFirstAvailable(player);
    }
}
