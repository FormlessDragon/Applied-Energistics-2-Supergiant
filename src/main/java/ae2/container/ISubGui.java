package ae2.container;

import ae2.api.storage.ISubGuiHost;
import ae2.core.gui.locator.GuiHostLocator;

/**
 * A container that is usually opened from another container, and that offers a way to return to that main container.
 */
public interface ISubGui {
    /**
     * @return The locator used to open this sub-container.
     */
    GuiHostLocator getLocator();

    /**
     * @return The host used to open this sub-container. Can be used to return to the main container.
     */
    ISubGuiHost getHost();
}
