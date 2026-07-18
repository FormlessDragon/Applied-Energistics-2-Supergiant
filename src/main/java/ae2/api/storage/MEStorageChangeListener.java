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

import ae2.api.stacks.AEKey;

/**
 * Receives authoritative content changes from an {@link MEStorageMonitor}.
 * <p>
 * The listener is intended for storage aggregation without repeatedly enumerating the monitored storage. Callbacks
 * are synchronous and must run on the same server thread that registered the listener.
 */
public interface MEStorageChangeListener {

    /**
     * Verifies that this listener still belongs to the registration represented by {@code verificationToken}.
     * Monitors must call this before dispatching a change and must remove registrations that are no longer valid.
     *
     * @param verificationToken opaque token supplied when the listener was registered
     * @return {@code true} while callbacks for this registration are still accepted
     */
    boolean isValid(Object verificationToken);

    /**
     * Reports a signed change to one resource in the monitored storage view.
     * <p>
     * Positive values add resources and negative values remove resources. Zero has no effect. Implementations must
     * report every content change exactly once and must not replace ordinary changes with {@link #onListUpdate()}.
     *
     * @param what  resource whose amount changed
     * @param delta signed amount added to or removed from the monitored storage
     */
    void onStackChange(AEKey what, long delta);

    /**
     * Reports that a structural change cannot be represented by exact deltas. The listener remains registered and
     * will enumerate the monitor once again when its cache is next required. This must not be used for ordinary
     * content changes.
     */
    void onListUpdate();
}
