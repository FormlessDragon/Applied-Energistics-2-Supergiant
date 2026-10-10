package ae2.core.mixins;

import ae2.core.AELog;
import com.cleanroommc.discovery.CleanroomModDiscoverer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Decides which mixins are applied.
 * <p>
 * HEI is a fork of JEI that keeps its mod id, its packages and its plugin interface, so the mod id alone cannot tell
 * the two apart. What it adds are bookmark items and the {@code showRecipeOrUses} ingredient lookup, and those decide
 * which side a mixin belongs to: the ones that need them are only applied on HEI, every other mixin works with either.
 */
public class AE2MixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> HEI_ONLY_MIXINS = Set.of(
        "ae2.mixins.hei.AccessorBookmarkItem",
        "ae2.mixins.hei.MixinInputHandlerFocus");

    private static final Set<String> PLAIN_JEI_ONLY_MIXINS = Set.of(
        "ae2.mixins.hei.AccessorBookmarkOverlay",
        "ae2.mixins.hei.InvokerGhostIngredientDragManager",
        "ae2.mixins.hei.MixinBookmarkOverlay",
        "ae2.mixins.hei.MixinLeftAreaDispatcher");

    private final boolean JEI_PRESENT = CleanroomModDiscoverer.instance().isModPresent("jei");
    private final boolean HEI_PRESENT = isClassPresent("jei", "mezz.jei.gui.navigation.NavigationLayout");

    public boolean isClassPresent(String modid, String className) {
        for (File src : CleanroomModDiscoverer.instance().modSources(modid)) {
            try (JarFile jar = new JarFile(src)) {
                if (jar.getEntry(className.replace(".", "/") + ".class") != null) {
                    return true;
                }
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
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
        if (!JEI_PRESENT) {
            return false;
        }
        if (PLAIN_JEI_ONLY_MIXINS.contains(mixinClassName)) {
            return !HEI_PRESENT;
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
