package ae2.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

@SideOnly(Side.CLIENT)
public final class PreviousExternalGui {
    @Nullable
    private static GuiScreen previousScreen;

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
            Container container = guiContainer.inventorySlots;
            container.windowId = windowId;
            minecraft.player.openContainer = container;
        }

        minecraft.displayGuiScreen(screen);
    }
}
