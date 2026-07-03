package ae2.client.gui.me.patternaccess;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side boundary for screens that display pattern provider inventories.
 * <p>
 * This interface exists so provider packets can update any compatible screen without being coupled to a
 * concrete terminal GUI class. It deliberately contains only packet delivery methods and no provider row,
 * cache, scrolling or sorting state.
 */
public interface IPatternProviderDisplay {

    /**
     * Clears all provider data that was previously delivered by provider packets.
     */
    void clear();

    /**
     * Applies world-location metadata for a provider inventory.
     *
     * @param inventoryId provider inventory id used by later provider updates
     * @param dimensionId dimension containing the provider
     * @param pos provider block position
     * @param face provider side when the provider exposes a sided location
     */
    void postProviderInfo(long inventoryId, int dimensionId, BlockPos pos, @Nullable EnumFacing face);

    /**
     * Replaces or creates a provider inventory entry from a full packet update.
     *
     * @param inventoryId provider inventory id
     * @param sortBy server-side sort key for stable provider ordering
     * @param canEditTerminalName whether the terminal allows editing this provider name
     * @param canModifyTerminalVisibility whether the terminal allows toggling this provider visibility
     * @param group provider group metadata
     * @param inventorySize number of slots in the provider inventory
     * @param slots full slot payload keyed by provider slot index
     */
    void postFullUpdate(long inventoryId, long sortBy, boolean canEditTerminalName,
                        boolean canModifyTerminalVisibility, PatternContainerGroup group, int inventorySize,
                        Int2ObjectMap<ItemStack> slots);

    /**
     * Applies changed provider slots to an existing provider inventory entry.
     *
     * @param inventoryId provider inventory id that must already be known to the display
     * @param slots changed slot payload keyed by provider slot index
     */
    void postIncrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots);
}
