package ae2.core.network.clientbound;

import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.me.patternencode.ProviderMappingPage;

/**
 * Client-screen boundary for revisioned provider-directory page responses.
 * <p>
 * The clientbound page packets use this interface so they can deliver pages to either Pattern Encoding screen without
 * depending on concrete GUI classes. Implementations own nonce/revision rejection and page caching after the packet
 * has verified the active container window.
 */
public interface IProviderSelectPageReceiver {

    /**
     * Receives one provider-directory page for client-side revision and nonce validation.
     *
     * @param page immutable directory page decoded from the server response
     */
    void receiveProviderDirectoryPage(ProviderDirectoryPage page);

    /** Receives one mapping-management page after window, nonce and revision validation. */
    void receiveProviderMappingPage(ProviderMappingPage page);

}
