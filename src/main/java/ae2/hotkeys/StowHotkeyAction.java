package ae2.hotkeys;

import ae2.api.features.HotkeyAction;
import ae2.helpers.WirelessTerminalActions;
import net.minecraft.entity.player.EntityPlayerMP;

public class StowHotkeyAction implements HotkeyAction {
    @Override
    public boolean run(EntityPlayerMP player) {
        return WirelessTerminalActions.stowInventory(player);
    }
}
