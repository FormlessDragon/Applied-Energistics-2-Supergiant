package ae2.core.registries;

import ae2.api.stacks.AEKeyTypesInternal;

/**
 *
 */
public final class AppEngRegistries {

    private static boolean initialized;

    private AppEngRegistries() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AEKeyTypesInternal.getAllTypes();
        CraftingUnitRegistry.getInstance().initBuiltins();
    }
}
