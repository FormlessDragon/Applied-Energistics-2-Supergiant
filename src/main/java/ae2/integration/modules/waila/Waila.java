package ae2.integration.modules.waila;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;

public final class Waila {
    private static final String MOD_ID = "waila";
    private static final String MODULE_CLASS = "ae2.integration.modules.waila.WailaModule";

    private Waila() {
    }

    public static void enqueueIMC() {
        if (Loader.isModLoaded(MOD_ID)) {
            FMLInterModComms.sendMessage(MOD_ID, "register", MODULE_CLASS + ".register");
        }
    }
}
