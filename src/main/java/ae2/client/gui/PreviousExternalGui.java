package ae2.client.gui;

import ae2.core.AELog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@SideOnly(Side.CLIENT)
public final class PreviousExternalGui {
    @Nullable
    private static GuiScreen previousScreen;
    @Nullable
    private static Field guiContainerField;

    private PreviousExternalGui() {
    }

    public static void capture(@Nullable GuiScreen screen) {
        if (screen != null) {
            previousScreen = screen;
        }
    }

    public static void restore(Minecraft minecraft, int windowId) {
        GuiScreen screen = previousScreen;
        previousScreen = null;
        if (screen == null) {
            return;
        }

        if (minecraft.player != null && screen instanceof GuiContainer guiContainer) {
            Container container = getContainer(guiContainer);
            if (container != null) {
                container.windowId = windowId;
                minecraft.player.openContainer = container;
            }
        }

        minecraft.displayGuiScreen(screen);
    }

    @Nullable
    private static Container getContainer(GuiContainer guiContainer) {
        try {
            if (guiContainerField == null) {
                guiContainerField = GuiContainer.class.getDeclaredField("inventorySlots");
                guiContainerField.setAccessible(true);
            }
            return (Container) guiContainerField.get(guiContainer);
        } catch (ReflectiveOperationException e) {
            AELog.warn(e, "Failed to restore external GUI container");
            return null;
        }
    }
}
