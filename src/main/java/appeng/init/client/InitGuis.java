package appeng.init.client;

import appeng.client.gui.style.GuiStyleManager;
import appeng.client.gui.style.IconAtlas;
import net.minecraft.client.Minecraft;

public final class InitGuis {
    private static boolean initialized;

    private InitGuis() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        var resourceManager = Minecraft.getMinecraft().getResourceManager();
        GuiStyleManager.initialize(resourceManager);
        IconAtlas.initialize(resourceManager);
    }
}
