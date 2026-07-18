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

package ae2.api.storage;

/**
 * An {@link MEStorage} that can publish exact content changes after its initial enumeration.
 * <p>
 * Consumers register before enumerating the storage once. Implementations must synchronously publish every later
 * content change as a signed delta, allowing consumers to update their aggregate cache without rescanning this
 * storage. If a structural change cannot be represented by exact deltas, the monitor must request one new enumeration
 * through {@link MEStorageChangeListener#onListUpdate()}.
 */
public interface MEStorageMonitor extends MEStorage {

    /**
     * Registers a listener for this storage view.
     * <p>
     * The monitor must retain the token and call {@link MEStorageChangeListener#isValid(Object)} before every callback.
     * A listener instance may only be registered once with a monitor.
     *
     * @param listener           listener that receives synchronous signed changes
     * @param verificationToken opaque registration token used to reject stale callbacks
     * @throws IllegalStateException if the listener is already registered
     */
    void addListener(MEStorageChangeListener listener, Object verificationToken);

    /**
     * Removes a previously registered listener. Calling this for an absent listener has no effect. No callback may be
     * made to the listener after this method returns.
     *
     * @param listener listener to remove
     */
    void removeListener(MEStorageChangeListener listener);
}
