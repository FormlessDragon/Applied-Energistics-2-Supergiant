/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
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

package ae2.api.networking;

import ae2.api.util.AECableType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

/**
 * Implement to create a networked {@link TileEntity}. Must be implemented for a tile entity to be available for
 * in-world connection attempts by adjacent grid nodes.
 * <p>
 * Must be provided via the {@link ae2.api.AECapabilities#IN_WORLD_GRID_NODE_HOST} capability.
 */
public interface IInWorldGridNodeHost {
    /**
     * get the grid node for a particular side of a block, you can return null, by returning a valid node later and
     * calling updateState, you can join the Grid when your block is ready.
     *
     * @param dir feel free to ignore this, most blocks will use the same node for every side.
     * @return a IGridNode, create these with IAppEngApi.instance().createGridNode( MyIGridBlock )
     */
    @Nullable
    IGridNode getGridNode(EnumFacing dir);

    /**
     * Determines how cables render when they connect to this block. Priority is Smart &gt; Covered &gt; Glass
     *
     * @param dir direction
     */

    default AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.GLASS;
    }

}
