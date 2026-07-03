package ae2.container.implementations;

import ae2.api.config.ShowPatternProviders;
import ae2.api.storage.ILinkStatus;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;

/**
 * Shared container capabilities for Pattern Access terminals.
 * <p>
 * This interface exists so Pattern Access GUIs and packets can route provider actions to any container that exposes
 * pattern provider targets, without depending on a concrete terminal implementation.
 */
public interface IPatternAccess {

    /**
     * Exposes the current provider visibility filter.
     * <p>
     * GUI code needs this value to keep provider lists and provider-specific actions aligned with the server-side
     * container state.
     *
     * @return the provider visibility mode currently shown by this container
     */
    ShowPatternProviders getShownProviders();

    /**
     * Exposes the current terminal link state.
     * <p>
     * GUI code uses this status before provider operations so disconnected terminals can present the same state across
     * Pattern Access containers.
     *
     * @return the current link status for this container
     */
    ILinkStatus getLinkStatus();

    /**
     * Opens the provider inventory represented by the given inventory id.
     * <p>
     * Provider list entries need a common action that works for both standalone Pattern Access terminals and pattern
     * encoding terminals with Pattern Access support.
     *
     * @param inventoryId the provider inventory id selected by the client
     */
    void openPatternProvider(long inventoryId);

    /**
     * Toggles whether the given provider is visible in the Pattern Access list.
     * <p>
     * Provider visibility is part of the shared Pattern Access UI contract and must be routed through the active
     * container.
     *
     * @param inventoryId the provider inventory id whose visibility should be toggled
     */
    void togglePatternProviderVisibility(long inventoryId);

    /**
     * Renames a single pattern provider.
     * <p>
     * The provider rename action is exposed here so common Pattern Access UI code can address the active container
     * without knowing its concrete terminal type.
     *
     * @param inventoryId the provider inventory id to rename
     * @param name the new provider name requested by the client
     */
    void renamePatternProvider(long inventoryId, String name);

    /**
     * Renames the group represented by the provided provider inventory ids.
     * <p>
     * Group rename is shared by Pattern Access containers because the GUI groups providers independently of the concrete
     * terminal implementation.
     *
     * @param inventoryIds the provider inventory ids that identify the group
     * @param name the new group name requested by the client
     */
    void renamePatternGroup(long[] inventoryIds, String name);

    boolean isPatternModifierPanelAvailable();

    PatternModifierPanel getPatternModifierPanel();

    void updatePatternModifierPanelVisibleSlots(boolean visible);

    /**
     * Moves the encoded pattern from the clicked source slot into one of the currently visible provider slots.
     * <p>
     * Quick-move packets use this method to keep encoded-pattern transfer behavior common across Pattern Access
     * containers while still letting each container validate its own player-side slots.
     *
     * @param player the server-side player that requested the move
     * @param sourceSlot the already resolved source slot from the open container
     * @param visiblePatternContainerIds provider inventory ids currently visible to the client
     * @param visiblePatternSlots provider slot indexes paired with {@code visiblePatternContainerIds}
     */
    void quickMovePattern(EntityPlayerMP player, Slot sourceSlot, LongList visiblePatternContainerIds,
                          LongList visiblePatternSlots);
}
