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

package ae2.api.networking.ticking;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeService;

/**
 * Implement on IGridHosts which want to use AE's Network Ticking Feature.
 * <p/>
 * <strong>Important note regarding IGridTickables with more than one node:</strong>
 * <p/>
 * If your IGridHost hosts multiple nodes, it may be on multiple grids, or its node may be present on the same grid
 * multiple times. This is as designed. However, if you choose to use the grid to tick these hosts, they should
 * probably pick a single node to tick for instead of ticking for each node.
 */
public interface IGridTickable extends IGridNodeService {

    /**
     * Return a valid TickingRequest to tell AE a guide for which type of responsiveness your device wants.
     * <p>
     * This will be called for your tile entity any time your tile entity changes grids. This can happen at any time,
     * so if you're using the sleep feature you may wish to preserve your sleep in the result of this method, or you can
     * simply reset it.
     *
     * @return a valid new TickingRequest
     */
    TickingRequest getTickingRequest(IGridNode node);

    /**
     * AE lets you adjust your tick rate based on the results of your tick. If your tile has accomplished work, you may
     * wish to increase the ticking speed. If your tile is idle, you may wish to slow it down.
     * <p>
     * It's up to you.
     * <p>
     * Note: this is never called if you return null from getTickingRequest.
     *
     * @param ticksSinceLastCall the number of world ticks that were skipped since your last tick, you can use this to
     *                           adjust speed of processing or adjust your tick rate.
     * @return tick rate adjustment.
     */

    TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall);
}
