/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking;

import appeng.api.networking.pathing.IPathingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This interface is intended for the host that created this node. It is used to configure the node's properties.
 */
public interface IManagedGridNode {

    /**
     * By destroying your node, you destroy any connections, and its existence in the grid, use in invalidate, or
     * onChunkUnload. After calling this method, {@link #isReady()} will return false. The node cannot be reused.
     */
    void destroy();

    /**
     * Finish creation of the node, which means it'll try to make a connection to adjacent nodes if it's exposed on the
     * host, and it'll be available for connections from other nodes.
     * <p>
     * This should only be called when the node is in a ticking chunk, for example from a callback registered to
     * {@link GridHelper#onFirstTick}.
     */
    void create(World level, @Nullable BlockPos blockPos);

    /**
     * This should be called for each node you create. If you have a nodeData compound to load from, you can store all
     * your nodes in a single compound using a name.
     * <p>
     * Important: You must call this before {@link #create(World, BlockPos)}.
     *
     * @param nodeData to be loaded data
     */
    void loadFromNBT(NBTTagCompound nodeData);

    /**
     * This should be called for each node you maintain. You can save all your nodes to the same tag with different
     * names. If you fail to complete the load / save procedure, network state may be lost between game loads and saves.
     *
     * @param nodeData to be saved data
     */
    void saveToNBT(NBTTagCompound nodeData);

    /**
     * Call the given function on the grid this node is connected to. Will do nothing if the grid node isn't initialized
     * yet or has been destroyed.
     */
    default void ifPresent(Consumer<IGrid> action) {
        var node = getNode();
        if (node == null) {
            return;
        }
        action.accept(node.grid());
    }

    default void ifPresent(BiConsumer<IGrid, IGridNode> action) {
        var node = getNode();
        if (node == null) {
            return;
        }
        action.accept(node.grid(), node);
    }

    /**
     * Get the grid this managed grid node is currently connected to.
     *
     * @return The grid if {@link #isReady()} is true, null otherwise.
     */
    @Nullable
    default IGrid getGrid() {
        var node = getNode();
        if (node == null) {
            return null;
        }
        return node.grid();
    }

    IManagedGridNode setFlags(GridFlags... flags);

    /**
     * Changes the sides of the node's host this node is exposed on.
     */
    IManagedGridNode setExposedOnSides(Set<EnumFacing> directions);

    /**
     * @param usagePerTick The power in AE/t that will be drained by this node.
     */
    IManagedGridNode setIdlePowerUsage(double usagePerTick);

    /**
     * Sets an itemstack that will only be used to represent this grid node in user interfaces. Can be set to
     * <code>null</code> to hide the node from UIs.
     */
    IManagedGridNode setVisualRepresentation(@Nullable AEItemKey visualRepresentation);

    /**
     * Shortcut for {@link #setVisualRepresentation(AEItemKey)} based on an {@link ItemStack}.
     */
    default IManagedGridNode setVisualRepresentation(ItemStack visualRepresentation) {
        return setVisualRepresentation(AEItemKey.of(visualRepresentation));
    }

    /**
     * Shortcut for {@link #setVisualRepresentation(AEItemKey)} based on an {@link Item}.
     */
    default IManagedGridNode setVisualRepresentation(Item visualRepresentation) {
        return setVisualRepresentation(AEItemKey.of(visualRepresentation));
    }

    /**
     * Changes whether this node can be discovered by other nodes in-world via the position of its owner and the sides
     * exposed via {@link #setExposedOnSides(Set)}. By default, all sides are exposed.
     */
    IManagedGridNode setInWorldNode(boolean accessible);

    /**
     * Changes the name of the NBT subtag in the host's NBT data that this node's data will be stored as.
     */
    IManagedGridNode setTagName(String tagName);

    /**
     * Colors can be used to prevent adjacent grid nodes from connecting. {@link AEColor#TRANSPARENT} indicates that the
     * node will connect to nodes of any color.
     */
    @SuppressWarnings("UnusedReturnValue")
    IManagedGridNode setGridColor(AEColor gridColor);

    <T extends IGridNodeService> IManagedGridNode addService(Class<T> serviceClass, T service);

    /**
     * @return True if the node and its grid are available. This will never be the case on the client-side. Server-side,
     * it'll be true after {@link #create(World, BlockPos)} and before {@link #destroy()} are called.
     */
    boolean isReady();

    boolean isActive();

    boolean isOnline();

    boolean isPowered();

    /**
     * @return True if the node is connected to a grid, and that grid has fully booted.
     * @see IPathingService#isNetworkBooting()
     */
    boolean hasGridBooted();

    /**
     * Tell the node who was responsible for placing it. Failure to do this may result in incompatibility with the
     * security system. Called instead of loadFromNBT when initially placed, once set never required again, the value is
     * saved with the node NBT.
     *
     * @param ownerPlayerId ME player id of the owner. See {@link appeng.api.features.IPlayerRegistry}.
     */
    void setOwningPlayerId(int ownerPlayerId);

    /**
     * Same as {@link #setOwningPlayerId(int)}, but resolves the numeric player ID automatically.
     *
     * @param ownerPlayer The owning player.
     */
    void setOwningPlayer(EntityPlayer ownerPlayer);

    /**
     * @return The node that was created by the managed node. Will be non-null when {@link #isReady()} is true.
     */
    @Nullable
    IGridNode getNode();
}
