package ae2.api.ids;

import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;

/**
 * IDs of the AE2 creative tabs.
 */
public final class AECreativeTabIds {
    public static final ResourceLocation MAIN = create("main");
    public static final ResourceLocation FACADES = create("facades");
    public static final ResourceLocation DEBUG = create("debug");

    private AECreativeTabIds() {
    }

    private static ResourceLocation create(String path) {
        return AppEng.makeId(path);
    }
}
