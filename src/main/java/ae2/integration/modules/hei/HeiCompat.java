package ae2.integration.modules.hei;

/**
 * Tells HEI and plain JEI apart at runtime.
 * <p>
 * HEI is a fork of JEI that keeps its mod id, its packages and its plugin interface, so the mod id alone cannot decide
 * which one is installed. It adds bookmark items that carry an amount and can be grouped to a bookmark list both sides
 * have; code that needs them lives in its own class ({@code HeiBookmarkItems}), and this check picks the side it is
 * used on without touching that class on plain JEI.
 */
public final class HeiCompat {
    private static final boolean HEI = isClassPresent("mezz.jei.bookmarks.BookmarkItem");

    private HeiCompat() {
    }

    /**
     * True when the installed JEI is HEI rather than plain JEI.
     */
    public static boolean isHei() {
        return HEI;
    }

    /**
     * Checks whether a class is on the classpath, without loading it.
     */
    private static boolean isClassPresent(String className) {
        String classFilePath = className.replace('.', '/') + ".class";
        ClassLoader classLoader = HeiCompat.class.getClassLoader();
        return classLoader.getResource(classFilePath) != null;
    }
}
