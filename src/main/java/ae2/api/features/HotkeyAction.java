package ae2.api.features;

import ae2.core.network.serverbound.HotkeyPacket;
import ae2.hotkeys.HotkeyActions;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Hotkey actions are server-side actions, that are triggered by customizable hotkeys on the client.
 * <p/>
 * Actions are transferred via {@link HotkeyPacket} to the server.
 */
public interface HotkeyAction {

    String WIRELESS_TERMINAL = "wireless_terminal";
    String PORTABLE_ITEM_CELL = "portable_item_cell";
    String PORTABLE_FLUID_CELL = "portable_fluid_cell";
    String PATTERN_MODIFIER = "pattern_modifier";

    /**
     * register a new {@link HotkeyAction} under an id
     * <p/>
     * {@link HotkeyAction}s which are added later will be called first
     * <p/>
     * a Keybinding will be created automatically for every id
     */
    static void register(HotkeyAction hotkeyAction, String id) {
        HotkeyActions.register(hotkeyAction, id);
    }

    /**
     * Handles the hotkey action on the server-side. Return true to indicate the action was triggered, false to allow
     * other handlers for the hotkey to process the event.
     */
    boolean run(EntityPlayerMP player);
}
