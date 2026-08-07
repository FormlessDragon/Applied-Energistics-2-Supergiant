package ae2.container.me.patternencode;

import ae2.client.gui.implementations.GuiProviderSelect;

/**
 * Client-screen boundary for revisioned provider-directory page responses.
 * <p>
 * The clientbound page packets use this interface so they can deliver pages to either Pattern Encoding screen without
 * depending on concrete GUI classes. Implementations own nonce/revision rejection and page caching after the packet
 * has verified the active container window.
 */
public interface IProviderSelectPageReceiver {

    GuiProviderSelect<?> getProviderSelectionOverlay();

    /**
     * Receives one provider-directory page for client-side revision and nonce validation.
     *
     * @param page immutable directory page decoded from the server response
     */
    default void receiveProviderDirectoryPage(ProviderDirectoryPage page) {
        this.getProviderSelectionOverlay().receiveProviderDirectoryPage(page);
    }

    /** Receives one mapping-management page after window, nonce and revision validation. */
    default void receiveProviderMappingPage(ProviderMappingPage page) {
        this.getProviderSelectionOverlay().receiveProviderMappingPage(page);
    }

}
