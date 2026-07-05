package ae2.api.client.terminalsettings;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

/**
 * Lifecycle hooks for one terminal settings page instance.
 * <p>
 * The host calls these hooks while it owns layout, toolbar buttons, and page selection.
 */
@SideOnly(Side.CLIENT)
public interface TerminalSettingsPage {

    /**
     * Called once after the page has been created.
     *
     * @param context Current terminal settings context.
     */
    default void init(TerminalSettingsPageContext context) {
    }

    /**
     * Called every GUI update before rendering.
     *
     * @param context  Current terminal settings context.
     * @param selected True when this page is currently selected.
     */
    default void update(TerminalSettingsPageContext context, boolean selected) {
    }

    /**
     * Called while the selected page background is being drawn.
     *
     * @param context      Current terminal settings context.
     * @param offsetX      GUI left screen coordinate.
     * @param offsetY      GUI top screen coordinate.
     * @param mouseX       Mouse X screen coordinate.
     * @param mouseY       Mouse Y screen coordinate.
     * @param partialTicks Render partial ticks.
     */
    default void drawBackground(TerminalSettingsPageContext context, int offsetX, int offsetY, int mouseX, int mouseY,
                                float partialTicks) {
    }

    /**
     * Called while the selected page foreground is being drawn.
     *
     * @param context Current terminal settings context.
     * @param offsetX GUI left screen coordinate.
     * @param offsetY GUI top screen coordinate.
     * @param mouseX  Mouse X screen coordinate.
     * @param mouseY  Mouse Y screen coordinate.
     */
    default void drawForeground(TerminalSettingsPageContext context, int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    /**
     * Called before the host processes key input.
     *
     * @param typedChar Typed character.
     * @param keyCode   Keyboard key code.
     * @return True when the page consumed the input.
     */
    default boolean keyTyped(char typedChar, int keyCode) throws IOException {
        return false;
    }

    /**
     * Called when the terminal settings GUI is closing.
     */
    default void onClosed() {
    }

    /**
     * Called before the terminal settings GUI returns to its parent screen.
     */
    default void onReturnToParent() {
    }
}
