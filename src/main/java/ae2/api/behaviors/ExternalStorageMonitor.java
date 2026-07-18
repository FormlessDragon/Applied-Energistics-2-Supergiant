/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 TeamAppliedEnergistics
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

package ae2.api.behaviors;

import ae2.api.config.StorageFilter;
import ae2.api.storage.MEStorageChangeListener;

/**
 * Optional event source for an external item or fluid handler exposed to an AE2 storage bus.
 * <p>
 * The object implementing the platform storage capability should implement this interface as well. AE2 can then scan
 * it once for initialization and consume exact change callbacks instead of periodically enumerating all slots or tanks.
 */
public interface ExternalStorageMonitor {

    /**
     * Registers a listener for one storage-filter view.
     * <p>
     * Changes must use the signed-delta contract of
     * {@link MEStorageChangeListener#onStackChange(ae2.api.stacks.AEKey, long)} and must be delivered synchronously on
     * the registering server thread. A listener instance may only be registered once with this monitor, but the
     * monitor must support multiple distinct listeners observing the same capability view.
     *
     * @param storageFilter     whether the view includes all resources or only extractable resources
     * @param listener          listener that receives exact content changes
     * @param verificationToken opaque registration token used to reject stale callbacks
     * @throws IllegalStateException if the listener is already registered
     */
    void addListener(StorageFilter storageFilter, MEStorageChangeListener listener, Object verificationToken);

    /**
     * Removes a previously registered listener. Calling this for an absent listener has no effect. No callback may be
     * made to the listener after this method returns.
     *
     * @param listener listener to remove
     */
    void removeListener(MEStorageChangeListener listener);

}
