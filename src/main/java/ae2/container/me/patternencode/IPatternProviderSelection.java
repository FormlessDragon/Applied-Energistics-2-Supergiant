package ae2.container.me.patternencode;

import org.jetbrains.annotations.Nullable;

/**
 * Server-side boundary for Pattern Encoding provider selection.
 * <p>
 * This interface exists so auxiliary implementations can depend on the pattern encode/provider-select behavior they
 * need without depending on a concrete Pattern Encoding terminal container. The first use case is the provider-select
 * overlay, which needs a stable way to read provider rows, upload a processing pattern, and maintain recipeUid
 * mappings while the concrete container remains free to evolve.
 */
public interface IPatternProviderSelection {

    /**
     * Returns the revision of the server-pinned provider directory snapshot.
     * <p>
     * Clients use this value to invalidate cached directory pages without synchronizing the full provider list through
     * GUI fields.
     *
     * @return monotonically increasing directory revision for this container window
     */
    long getProviderDirectoryRevision();

    /**
     * Requests one filtered page from the current server-pinned provider directory.
     *
     * @param nonce client request generation used to reject stale responses
     * @param query provider name, location, or recipe UID query
     * @param page  zero-based page index
     * @param focus provider identity to promote on the first page, or {@code null} for normal directory order
     */
    void requestProviderDirectoryPage(long nonce, String query, int page,
                                      @Nullable ProviderDirectoryPageRequest.Focus focus);

    /** Requests one mapping-management page for a provider in the current directory snapshot. */
    void requestProviderMappingPage(long nonce, long directoryRevision, long providerId, int page);

    /**
     * Uploads the encoded processing pattern to the selected provider.
     * <p>
     * Implementations must resolve {@code inventoryId} against the current server-side provider-select snapshot and
     * reject unknown or stale ids with the existing user-facing status path.
     *
     * @param inventoryId provider-select entry id selected by the client
     */
    void uploadProcessingPatternToProvider(long inventoryId);

    /**
     * Binds a recipeUid or recipe title hint to the selected provider.
     * <p>
     * Implementations are responsible for normalizing and validating {@code mappingText} before writing persistent
     * mapping data.
     *
     * @param inventoryId provider-select entry id selected by the client
     * @param mappingText recipeUid or title hint requested by the client
     */
    void bindProviderMapping(long inventoryId, String mappingText);

    /**
     * Atomically binds a recipe type UID and uploads the encoded processing pattern to the selected provider.
     * <p>
     * This operation exists because issuing independent bind and upload actions can persist a mapping even when the
     * upload is rejected. Implementations must validate both changes before mutating either side, commit both inventory
     * changes before binding, and restore both inventories when the commit fails.
     *
     * @param inventoryId   provider-select entry id selected by the client
     * @param recipeTypeUid recipe type UID requested by the client
     */
    void bindAndUploadProcessingPatternToProvider(long inventoryId, String recipeTypeUid);

    /**
     * Removes all recipeUid mappings for the selected provider.
     * <p>
     * The provider id must be resolved server-side from the active provider-select snapshot before mapping data is
     * mutated.
     *
     * @param inventoryId provider-select entry id selected by the client
     */
    void unbindProviderMapping(long inventoryId);

    /**
     * Removes one recipeUid mapping for the selected provider.
     * <p>
     * Passing {@code null} follows the same behavior as {@link #unbindProviderMapping(long)}. Blank or invalid UIDs are
     * rejected and do not remove mappings.
     *
     * @param inventoryId provider-select entry id selected by the client
     * @param recipeType  recipeUid to remove, or {@code null} to remove all mappings
     */
    void unbindProviderMapping(long inventoryId, @Nullable String recipeType);

    /**
     * Rebuilds mappings from every currently eligible provider in the active grid snapshot.
     * <p>
     * This constant-size action replaces transferring a potentially unbounded provider ID array from the client.
     */
    void reloadAllCurrentProviders();

    /**
     * Returns the current provider-select overlay open request nonce.
     * <p>
     * GUI code compares this value with the last seen nonce to decide whether it should open the overlay.
     *
     * @return non-zero nonce for the latest open request, or zero before any request is made
     */
    int getProviderSelectOverlayRequestNonce();

    /**
     * Returns the initial provider-select search text for the latest open request.
     *
     * @return normalized search text for the overlay
     */
    String getProviderSelectOverlaySearchText();

    /**
     * Returns the initial mapping text for the latest open request.
     *
     * @return normalized mapping text for the overlay
     */
    String getProviderSelectOverlayMappingText();
}
