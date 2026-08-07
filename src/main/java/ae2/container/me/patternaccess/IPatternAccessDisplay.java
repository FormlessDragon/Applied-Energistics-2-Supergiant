package ae2.container.me.patternaccess;

import ae2.api.config.ShowPatternProviders;
import ae2.api.storage.ILinkStatus;

/**
 * Server-side boundary for Pattern Access provider display actions.
 * <p>
 * This interface exists so Pattern Access GUI code and auxiliary implementations can depend on provider display
 * behavior without depending on a concrete PAT or PEAT container. It intentionally covers only Pattern Access display
 * and provider operations; encoding upload and recipeUid mapping belong to the pattern encode provider-selection
 * boundary.
 */
public interface IPatternAccessDisplay {

    /**
     * Exposes the current provider visibility filter.
     * <p>
     * Display code uses this value to keep provider lists and provider-specific actions aligned with server state.
     *
     * @return the provider visibility mode currently shown by this container
     */
    ShowPatternProviders getShownProviders();

    /**
     * Exposes the current terminal link state.
     * <p>
     * Pattern Access display code uses this status before provider operations so disconnected terminals present a
     * consistent server-side state.
     *
     * @return the current link status for this container
     */
    ILinkStatus getLinkStatus();

    /**
     * Opens the provider inventory represented by the given inventory id.
     * <p>
     * Implementations must resolve the id against the current Pattern Access provider cache.
     *
     * @param inventoryId provider inventory id selected by the client
     */
    void openPatternProvider(long inventoryId);

    /**
     * Toggles whether the selected provider is visible in the Pattern Access list.
     * <p>
     * Implementations must enforce provider permissions before mutating visibility.
     *
     * @param inventoryId provider inventory id selected by the client
     */
    void togglePatternProviderVisibility(long inventoryId);

    /**
     * Renames a single pattern provider.
     * <p>
     * Implementations must validate the new name and provider permissions before mutating the provider.
     *
     * @param inventoryId provider inventory id selected by the client
     * @param name requested provider display name
     */
    void renamePatternProvider(long inventoryId, String name);

    /**
     * Renames the group represented by the provided provider inventory ids.
     * <p>
     * Implementations resolve the ids against the current Pattern Access cache and apply the rename only to editable
     * providers.
     *
     * @param inventoryIds provider inventory ids that identify the group
     * @param name requested group display name
     */
    void renamePatternGroup(long[] inventoryIds, String name);
}
