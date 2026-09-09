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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ae2.api.networking.events;

import ae2.api.networking.IGrid;
import ae2.api.networking.energy.IAEPowerStorage;

import java.util.Objects;

/**
 * Informs an {@link IGrid} that a registered {@link IAEPowerStorage} changed after it was added to the grid.
 * <p>
 * Value changes mark only the changed source for a lazy read and update of the shared totals. Routing changes discard
 * the storage-routing cache so maximum capacity, public visibility, access restrictions and priority are evaluated
 * lazily on the next energy operation. Adding or removing a grid node already supplies the corresponding structural
 * notification and does not require this event.
 */
public final class GridPowerStorageChanged extends GridEvent {
    /** The storage whose cached state changed. Identity, rather than {@link Object#equals(Object)}, is significant. */
    public final IAEPowerStorage storage;
    /** The class of cached state that changed. */
    public final ChangeType type;

    /**
     * Creates a storage change event.
     *
     * @param storage storage that is already registered with the grid receiving the event
     * @param type    whether only energy amounts or storage routing changed
     */
    public GridPowerStorageChanged(IAEPowerStorage storage, ChangeType type) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Identifies the minimum cache scope invalidated by a storage change.
     */
    public enum ChangeType {
        /**
         * Current power, extractable power or receivable power changed without changing maximum capacity, public
         * visibility, access restrictions or priority. The energy service lazily reads only this source and replaces
         * its primitive contributions to the shared totals. Repeated events before a read are coalesced. An event
         * also permits retrying a source previously isolated for invalid values. Events emitted by the storage
         * currently being operated on are accounted for by that operation instead. Callbacks must run synchronously
         * on the server thread.
         */
        VALUES_CHANGED,

        /**
         * Maximum capacity, public visibility, access restrictions or priority changed. Maximum capacity participates
         * in service grouping, so the energy service lazily rebuilds and reorders the complete storage-routing cache
         * while retaining the Quartz Fiber topology cache.
         */
        ROUTING_CHANGED
    }
}
