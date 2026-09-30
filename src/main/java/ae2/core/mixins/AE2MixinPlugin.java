package ae2.core.mixins;

import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Decides which mixins are applied.
 * <p>
 * HEI is a fork of JEI that keeps its mod id, its packages and its plugin interface, so the mod id alone cannot tell
 * the two apart. What it adds are bookmark items and the {@code showRecipeOrUses} ingredient lookup, and those decide
 * which side a mixin belongs to: the ones that need them are only applied on HEI, every other mixin works with either.
 */
public class AE2MixinPlugin implements IMixinConfigPlugin {
    /**
     * Mixins that only work on HEI.
     */
    private static final Set<String> HEI_ONLY_MIXINS = Set.of(
        "ae2.mixins.hei.AccessorBookmarkItem",
        "ae2.mixins.hei.MixinInputHandlerFocus");

    /**
     * Whether HEI rather than plain JEI is installed. It is the only one of the two that has bookmark items.
     */
    private static final boolean HEI_PRESENT = isClassPresent("mezz.jei.bookmarks.BookmarkItem");

    /**
     * Checks whether a class is on the classpath, without loading it. Mixins are applied long before mod classes may
     * be touched, so a lookup of the class file is all that can be done here.
     */
    public static boolean isClassPresent(String className) {
        String classFilePath = className.replace('.', '/') + ".class";
        ClassLoader classLoader = AE2MixinPlugin.class.getClassLoader();
        return classLoader.getResource(classFilePath) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Nullable
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith("ae2.mixins.hei.")) {
            return true;
        }
        if (!Loader.isModLoaded("jei")) {
            return false;
        }
        return !HEI_ONLY_MIXINS.contains(mixinClassName) || HEI_PRESENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Nullable
    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
