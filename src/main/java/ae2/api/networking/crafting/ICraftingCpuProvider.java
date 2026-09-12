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

package ae2.api.networking.crafting;

import ae2.api.networking.IGridNode;
import ae2.api.networking.events.GridCraftingCpuChange;
import ae2.me.cluster.implementations.CraftingCPUCluster;

import java.util.Collection;

/**
 * Implemented by the owner of an {@link IGridNode} to contribute a set of crafting CPUs to the grid's crafting
 * service.
 * <p/>
 * This differs from {@code ICraftingCPUTileEntity}, which models "one block entity is exactly one CPU". This
 * interface allows one block entity to expose an arbitrary number of CPUs, and that number may change at runtime.
 * It is intended for machines that internally manage several concurrent crafting jobs backed by a shared pool of
 * storage.
 * <p/>
 * A node whose owner implements this interface is tracked by the crafting service just like a regular crafting CPU
 * block entity. Whenever the returned collection would change, the implementation must post
 * {@link GridCraftingCpuChange} on its grid so the service re-expands the providers. The crafting service only
 * re-reads providers when that event marks its CPU list dirty; it does not poll every tick.
 * <p/>
 * Implementations must treat {@link #getProvidedCraftingCpus()} as a pure query. It is called while the crafting
 * service rebuilds its CPU list, so it must not mutate grid state, post events, or recycle CPUs.
 * <p/>
 * For a multi-block machine where every block owns its own grid node, it is fine for all of those nodes to return
 * the same CPUs: the crafting service de-duplicates by identity.
 * <p/>
 * <b>Stability:</b> this is an internal-facing extension point. Its contract type is the implementation class
 * {@link CraftingCPUCluster} rather than {@link ICraftingCPU}, because the crafting service relies on behavior that
 * is not part of the {@link ICraftingCPU} interface. Provided CPUs must therefore extend {@link CraftingCPUCluster}.
 */
public interface ICraftingCpuProvider {

    /**
     * @return the CPUs this node owner currently contributes to the grid. May be empty, but never null. The returned
     * collection is only read during the call and is not retained.
     */
    Collection<? extends CraftingCPUCluster> getProvidedCraftingCpus();
}
