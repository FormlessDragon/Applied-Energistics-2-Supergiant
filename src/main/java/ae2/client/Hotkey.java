package ae2.client;

import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.HotkeyPacket;
import net.minecraft.client.settings.KeyBinding;

public record Hotkey(String name, KeyBinding mapping) {

    public void check() {
        while (mapping().isPressed()) {
            InitNetwork.sendToServer(new HotkeyPacket(this));
        }
    }
}
