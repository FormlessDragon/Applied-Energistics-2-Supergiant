package ae2.api.client.terminalsettings;

import ae2.client.gui.me.common.TerminalSettingsPageRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Client-only registry for terminal settings pages.
 * <p>
 * Add-ons use this entry point to add pages to AE terminal settings screens without controlling the host GUI toolbar.
 */
@SideOnly(Side.CLIENT)
public final class TerminalSettingsPages {
    private TerminalSettingsPages() {
    }

    /**
     * Registers a terminal settings page provider.
     *
     * @param provider The page provider to register.
     */
    public static void register(TerminalSettingsPageProvider provider) {
        TerminalSettingsPageRegistry.register(provider);
    }

    /**
     * Returns all registered page providers in toolbar order.
     *
     * @return Immutable snapshot of registered providers.
     */
    public static List<TerminalSettingsPageProvider> getRegistered() {
        return TerminalSettingsPageRegistry.getRegistered();
    }
}
