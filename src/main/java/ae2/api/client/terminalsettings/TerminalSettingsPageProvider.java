package ae2.api.client.terminalsettings;

import ae2.client.gui.Icon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Describes one page that can appear in the terminal settings GUI.
 * <p>
 * The host owns toolbar button creation. Providers expose only a stable id, one icon, a title, visibility, and the
 * page lifecycle implementation.
 */
@SideOnly(Side.CLIENT)
public interface TerminalSettingsPageProvider {

    /**
     * Stable unique page id.
     *
     * @return The page id.
     */
    ResourceLocation id();

    /**
     * Toolbar icon used by the host-created page button.
     *
     * @return The page icon.
     */
    Icon icon();

    /**
     * Page title and toolbar tooltip text.
     *
     * @param context Current terminal settings context.
     * @return Localized page title.
     */
    ITextComponent title(TerminalSettingsPageContext context);

    /**
     * Whether this page should appear for the current terminal settings context.
     *
     * @param context Current terminal settings context.
     * @return True when the page should be listed.
     */
    boolean isVisible(TerminalSettingsPageContext context);

    /**
     * Creates the lifecycle object for one settings GUI instance.
     *
     * @param context Current terminal settings context.
     * @return Page lifecycle object.
     */
    TerminalSettingsPage create(TerminalSettingsPageContext context);
}
