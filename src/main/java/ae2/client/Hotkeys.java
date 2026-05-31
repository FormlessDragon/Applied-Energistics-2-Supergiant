package ae2.client;

import ae2.hotkeys.HotkeyActions;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.Map;

/**
 * client side component of {@link HotkeyActions}
 */
public final class Hotkeys {

    private static final Map<String, Hotkey> HOTKEYS = new Object2ObjectOpenHashMap<>();

    private Hotkeys() {
    }

    private static Hotkey createHotkey(String id) {
        return new Hotkey(id, new KeyBinding("key.ae2." + id, Keyboard.KEY_NONE, "key.ae2.category"));
    }

    public static synchronized void registerHotkey(String id) {
        if (HOTKEYS.containsKey(id)) {
            return;
        }

        Hotkey hotkey = createHotkey(id);
        HOTKEYS.put(id, hotkey);
        ClientRegistry.registerKeyBinding(hotkey.mapping());
    }

    public static void checkHotkeys() {
        HOTKEYS.values().forEach(Hotkey::check);
    }

    @SuppressWarnings("unused")
    @Nullable
    public static Hotkey getHotkeyMapping(@Nullable String id) {
        return HOTKEYS.get(id);
    }
}
