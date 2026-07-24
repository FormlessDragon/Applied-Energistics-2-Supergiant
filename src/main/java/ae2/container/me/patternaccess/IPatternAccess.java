package ae2.container.me.patternaccess;

import ae2.container.implementations.PatternModifierPanel;
import ae2.container.me.patternaccess.IPatternAccessDisplay;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;

/**
 * Shared container capabilities for Pattern Access terminals.
 * <p>
 * This interface exists so Pattern Access GUIs and packets can route provider actions to any container that exposes
 * pattern provider targets, without depending on a concrete terminal implementation.
 */
public interface IPatternAccess extends IPatternAccessDisplay {

    /**
     * Reports whether this Pattern Access container can expose the optional Pattern Modifier panel.
     * <p>
     * The method exists because PAT and PEAT share the same access display GUI, but only containers with a detected
     * Pattern Modifier item should render and enable the modifier slots.
     *
     * @return {@code true} when the modifier panel has a backing inventory available for this open container
     */
    boolean isPatternModifierPanelAvailable();

    /**
     * Returns the server/client panel coordinator used by shared Pattern Access screens.
     * <p>
     * GUI code uses this object to render modifier slots and route panel commands without depending on whether the
     * concrete container is PAT or PEAT.
     *
     * @return the Pattern Modifier panel owned by this container
     */
    PatternModifierPanel getPatternModifierPanel();

    /**
     * Applies the current Pattern Modifier panel visibility to its slots.
     * <p>
     * The first need for this method came from sharing one access GUI path across PAT and PEAT: the GUI decides whether
     * the panel is expanded, while each container owns the actual slot activation and availability checks.
     *
     * @param visible whether the GUI wants the modifier panel slots visible
     */
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
