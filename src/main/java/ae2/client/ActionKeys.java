package ae2.client;

import net.minecraftforge.fml.client.registry.ClientRegistry;

/**
 * Registers every {@link ActionKey} with Forge so that they show up in the controls screen.
 */
public final class ActionKeys {

    private static boolean registered;

    private ActionKeys() {
    }

    public static void init() {
        if (registered) {
            return;
        }

        registered = true;
        for (ActionKey key : ActionKey.values()) {
            ClientRegistry.registerKeyBinding(key.getBinding());
        }
    }
}
