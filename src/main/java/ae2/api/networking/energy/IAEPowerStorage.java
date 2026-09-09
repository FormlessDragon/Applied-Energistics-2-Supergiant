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

package ae2.api.networking.energy;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.events.GridPowerStorageChanged;

/**
 * Used to access information about AE's various power accepting blocks for monitoring purposes.
 */
public interface IAEPowerStorage extends IEnergySource, IGridNodeService {

    /**
     * Inject amt, power into the device, it will store what it can, and return the amount unable to be stored.
     * Simulation must not change storage or emit events. The returned remainder must be finite and between zero
     * and amt. The default {@link #getReceivableAEPower(double)} uses simulation to measure receivable power.
     *
     * @param amt  to be injected amount
     * @param mode action mode
     * @return amount of power which was unable to be stored
     */
    double injectAEPower(double amt, Actionable mode);

    /**
     * Measures how much AE power one extraction can supply, without changing storage, limits or emitting events.
     * The overlay caches this value on first access and after changes, instead of simulating on every query.
     * Linear storage implementations should override this method with a direct calculation. Implementations with
     * transfer limits must report those limits; the default delegates to extraction simulation with no multiplier.
     * Called synchronously on the owning grid's server thread, under the same contract as energy operations.
     * Changes made outside those operations must emit {@link GridPowerStorageChanged.ChangeType#VALUES_CHANGED}.
     *
     * @param maximum finite, non-negative upper bound in AE, no greater than the current stored amount
     * @return finite, non-negative extractable amount, at most {@code maximum}; equal to simulating that extraction
     */
    default double getExtractableAEPower(double maximum) {
        return extractAEPower(maximum, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    /**
     * Measures how much AE power one insertion can accept, without changing storage, limits or emitting events.
     * This returns accepted power, whereas {@link #injectAEPower(double, Actionable)} returns the remainder.
     * Linear storage implementations should override this method with a direct calculation; limited storage must
     * include its transfer limits. The default measures insertion simulation. The overlay calls and caches this
     * under the same server-thread and value-event contract as {@link #getExtractableAEPower(double)}.
     * Public visibility and flow restrictions are checked by the overlay before either capability query.
     *
     * @param maximum finite, non-negative upper bound in AE, no greater than the unfilled capacity
     * @return finite, non-negative receivable amount, at most {@code maximum}; equal to simulating that insertion
     */
    default double getReceivableAEPower(double maximum) {
        double remainder = injectAEPower(maximum, Actionable.SIMULATE);
        if (!Double.isFinite(remainder) || remainder < 0 || remainder > maximum) {
            throw new IllegalStateException("Invalid simulated power remainder " + remainder + " for " + maximum);
        }
        return maximum - remainder;
    }

    /**
     * If this value changes while the storage is registered, post a
     * {@link GridPowerStorageChanged.ChangeType#ROUTING_CHANGED} event because maximum capacity controls service
     * grouping.
     *
     * @return the current maximum power
     */
    double getAEMaxPower();

    /**
     * Changes to current, extractable or receivable power must post a
     * {@link GridPowerStorageChanged.ChangeType#VALUES_CHANGED} event unless they were caused by an operation invoked
     * through the energy service, which reads this storage after the operation to correct its shared total.
     * Power amounts must be finite and non-negative. This getter and the routing getters must be side effect free.
     *
     * @return the current AE Power Level, this may exceed getMEMaxPower()
     */
    double getAECurrentPower();

    /**
     * Checked on network reset to see if your block can be used as a public power storage ( use getPowerFlow to control
     * the behavior )
     * <p>
     * If this value changes while registered, post a {@link GridPowerStorageChanged.ChangeType#ROUTING_CHANGED} event.
     *
     * @return true if it can be used as a public power storage
     */
    boolean isAEPublicPowerStorage();

    /**
     * Control the power flow by telling what the network can do, either add? or subtract? or both!
     * <p>
     * If this value changes while registered, post a {@link GridPowerStorageChanged.ChangeType#ROUTING_CHANGED} event.
     *
     * @return access restriction what the network can do
     */

    AccessRestriction getPowerFlow();

    /**
     * The priority to use this energy storage.
     * A higher Value means it is more likely to be extracted from first, and less likely to be inserted into first.
     * If the value changes after the storage is added to an {@link IGrid}, post a
     * {@link GridPowerStorageChanged.ChangeType#ROUTING_CHANGED} event so the service-local order is rebuilt lazily.
     * This should never use {@link Integer#MIN_VALUE} or {@link Integer#MAX_VALUE}.
     *
     * @return the priority for this storage
     */
    default int getPriority() {
        return 0;
    }
}
