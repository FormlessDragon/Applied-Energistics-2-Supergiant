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

package ae2.api.networking.energy;

import org.jetbrains.annotations.ApiStatus;

import ae2.api.config.AccessRestriction;

/**
 * Reusable, allocation-free snapshot of an {@link IAEPowerStorage}.
 * <p>
 * Implementations of {@link IAEPowerStorage#getPowerSnapshot(PowerStorageSnapshotBuilder)} must call
 * {@link #setValues(double, double, double, double, AccessRestriction, int, boolean)} exactly once and must not retain
 * this object. The energy cache owns the builder lifecycle and reuses one instance for cache construction, events and
 * post-operation correction.
 */
public final class PowerStorageSnapshotBuilder {
    private boolean complete;
    private double currentPower;
    private double maximumPower;
    private double extractablePower;
    private double receivablePower;
    private AccessRestriction powerFlow;
    private int priority;
    private boolean publicStorage;

    /**
     * Sets every value that contributes to the overlay energy cache.
     * <p>
     * All power values must be finite and non-negative. Extractable power must not exceed current power. Receivable
     * power must not exceed the currently unused part of maximum capacity.
     */
    public void setValues(double currentPower, double maximumPower, double extractablePower, double receivablePower,
                          AccessRestriction powerFlow, int priority, boolean publicStorage) {
        if (this.complete) {
            throw new IllegalStateException("Power storage snapshot values were supplied more than once");
        }

        requirePowerValue("currentPower", currentPower);
        requirePowerValue("maximumPower", maximumPower);
        requirePowerValue("extractablePower", extractablePower);
        requirePowerValue("receivablePower", receivablePower);
        if (extractablePower > currentPower) {
            throw new IllegalArgumentException("extractablePower must not exceed currentPower");
        }
        if (receivablePower > Math.max(0, maximumPower - currentPower)) {
            throw new IllegalArgumentException("receivablePower must not exceed unused maximum capacity");
        }
        if (powerFlow == null) {
            throw new IllegalArgumentException("powerFlow must not be null");
        }

        this.currentPower = currentPower;
        this.maximumPower = maximumPower;
        this.extractablePower = extractablePower;
        this.receivablePower = receivablePower;
        this.powerFlow = powerFlow;
        this.priority = priority;
        this.publicStorage = publicStorage;
        this.complete = true;
    }

    @ApiStatus.Internal
    public void reset() {
        this.complete = false;
        this.currentPower = 0;
        this.maximumPower = 0;
        this.extractablePower = 0;
        this.receivablePower = 0;
        this.powerFlow = null;
        this.priority = 0;
        this.publicStorage = false;
    }

    @ApiStatus.Internal
    public boolean isComplete() {
        return this.complete;
    }

    @ApiStatus.Internal
    public double getCurrentPower() {
        ensureComplete();
        return this.currentPower;
    }

    @ApiStatus.Internal
    public double getMaximumPower() {
        ensureComplete();
        return this.maximumPower;
    }

    @ApiStatus.Internal
    public double getExtractablePower() {
        ensureComplete();
        return this.extractablePower;
    }

    @ApiStatus.Internal
    public double getReceivablePower() {
        ensureComplete();
        return this.receivablePower;
    }

    @ApiStatus.Internal
    public AccessRestriction getPowerFlow() {
        ensureComplete();
        return this.powerFlow;
    }

    @ApiStatus.Internal
    public int getPriority() {
        ensureComplete();
        return this.priority;
    }

    @ApiStatus.Internal
    public boolean isPublicStorage() {
        ensureComplete();
        return this.publicStorage;
    }

    private static void requirePowerValue(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private void ensureComplete() {
        if (!this.complete) {
            throw new IllegalStateException("Power storage snapshot is incomplete");
        }
    }
}
