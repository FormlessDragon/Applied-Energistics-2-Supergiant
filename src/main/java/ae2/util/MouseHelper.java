package ae2.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class MouseHelper {

    public static final MouseHelper INSTANCE = new MouseHelper();
    private ScaledResolution scaledresolution;
    private int displayWidth;
    private int displayHeight;

    public static int getMouseX() {
        return INSTANCE.getX();
    }

    public static int getMouseY() {
        return INSTANCE.getY();
    }

    public int getX() {
        int i = scaledresolution.getScaledWidth();
        return Mouse.getX() * i / displayWidth;
    }

    public int getY() {
        int j = scaledresolution.getScaledHeight();
        return j - Mouse.getY() * j / displayHeight - 1;
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        displayWidth = minecraft.displayWidth;
        displayHeight = minecraft.displayHeight;
        scaledresolution = new ScaledResolution(minecraft);
    }

}
