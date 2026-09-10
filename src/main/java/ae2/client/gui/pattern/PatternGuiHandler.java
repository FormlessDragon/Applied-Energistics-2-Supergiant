package ae2.client.gui.pattern;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.container.pattern.ContainerCraftingPattern;
import ae2.container.pattern.ContainerProcessingPattern;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;

@SideOnly(Side.CLIENT)
public final class PatternGuiHandler {

    private static final Map<Class<?>, GuiScreenGetter> PATTERN_GUI_MAP = new Reference2ObjectOpenHashMap<>();
    private static final Minecraft minecraft = Minecraft.getMinecraft();

    static {
        register(AECraftingPattern.class, (pattern)->{
            ContainerCraftingPattern container = new ContainerCraftingPattern(minecraft.player.inventory, pattern);
            return new GuiCraftingPattern(container, minecraft.player.inventory);
        });
        register(AEProcessingPattern.class, (pattern)->{
            ContainerProcessingPattern container = new ContainerProcessingPattern(minecraft.player.inventory, pattern);
            return new GuiProcessingPattern(container, minecraft.player.inventory);
        });
    }

    public static void register(Class<?> clazz, GuiScreenGetter getter) {
        PATTERN_GUI_MAP.put(clazz, getter);
    }

    public static boolean open(ItemStack pattern) {
        if (minecraft.player == null || minecraft.world == null || pattern.isEmpty()) {
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, minecraft.world);

        if (details == null) {
            return false;
        }

        GuiScreenGetter getter = PATTERN_GUI_MAP.get(details.getClass());

        if (getter == null) {
            return false;
        }
        GuiScreen previousScreen = minecraft.currentScreen;
        GuiScreen patternScreen = getter.getGuiScreen(pattern);
        if (patternScreen instanceof GuiPattern<?> guiPattern) {
            guiPattern.setPreviousScreen(previousScreen);
        }
        minecraft.displayGuiScreen(patternScreen);
        return true;
    }

    @FunctionalInterface
    public interface GuiScreenGetter {
        GuiScreen getGuiScreen(ItemStack pattern);
    }
}
