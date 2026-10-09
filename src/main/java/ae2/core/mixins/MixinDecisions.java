package ae2.core.mixins;

import com.cleanroommc.discovery.CleanroomModDiscoverer;

import java.util.Set;

public final class MixinDecisions {

    private static final Set<String> HEI_ONLY_MIXINS = Set.of(
        "ae2.mixins.hei.AccessorBookmarkItem",
        "ae2.mixins.hei.MixinInputHandlerFocus");

    private static final Set<String> PLAIN_JEI_ONLY_MIXINS = Set.of(
        "ae2.mixins.hei.AccessorBookmarkOverlay",
        "ae2.mixins.hei.InvokerGhostIngredientDragManager",
        "ae2.mixins.hei.MixinBookmarkOverlay",
        "ae2.mixins.hei.MixinLeftAreaDispatcher");

    public static final boolean JEI_PRESENT = CleanroomModDiscoverer.instance().isModPresent("jei");
    public static final boolean HEI_PRESENT = isClassPresent("mezz.jei.gui.navigation.NavigationLayout");

    /**
     * Checks whether a class is on the classpath, without loading it. Mixins are applied long before mod classes may
     * be touched, so a lookup of the class file is all that can be done here.
     */
    public static boolean isClassPresent(String className) {
        String classFilePath = className.replace('.', '/') + ".class";
        ClassLoader classLoader = MixinDecisions.class.getClassLoader();
        return classLoader.getResource(classFilePath) != null;
    }

    public static boolean shouldApply(String mixinClassName) {
        if (!JEI_PRESENT) {
            return false;
        }
        if (PLAIN_JEI_ONLY_MIXINS.contains(mixinClassName)) {
            return !HEI_PRESENT;
        }
        return !HEI_ONLY_MIXINS.contains(mixinClassName) || HEI_PRESENT;
    }
}
