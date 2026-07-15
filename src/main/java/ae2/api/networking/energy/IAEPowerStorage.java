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

import java.util.Objects;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.events.GridPowerStorageChanged;

/**
 * Used to access information about AE's various power accepting blocks for monitoring purposes.
 */
public interface IAEPowerStorage extends IEnergySource, IGridNodeService {

    /**
     * Inject amt, power into the device, it will store what it can, and return the amount unable to be stored.
     *
     * @param amt  to be injected amount
     * @param mode action mode
     * @return amount of power which was unable to be stored
     */
    double injectAEPower(double amt, Actionable mode);

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
     * through the energy service, which performs an immediate absolute snapshot correction.
     *
     * @return the current AE Power Level, this may exceed getMEMaxPower()
     */
    double getAECurrentPower();

    /**
     * Writes a complete snapshot used by the overlay energy cache into the supplied reusable builder.
     * <p>
     * This method must be side-effect free, must call
     * {@link PowerStorageSnapshotBuilder#setValues(double, double, double, double, AccessRestriction, int, boolean)}
     * exactly once and must not retain the builder. Implementations with transfer limits or other dynamic availability
     * rules should override this method so extractable and receivable power describe what one operation can perform at
     * the time of the call.
     * <p>
     * The default implementation derives the complete snapshot from {@link #getAECurrentPower()},
     * {@link #getAEMaxPower()}, {@link #getPowerFlow()}, {@link #getPriority()} and
     * {@link #isAEPublicPowerStorage()}.
     *
     * @param builder reusable destination owned by the energy service
     */
    default void getPowerSnapshot(PowerStorageSnapshotBuilder builder) {
        Objects.requireNonNull(builder, "builder");

        double currentPower = getAECurrentPower();
        double maximumPower = getAEMaxPower();
        double receivablePower = Math.max(0, maximumPower - currentPower);
        AccessRestriction powerFlow = getPowerFlow();
        int priority = getPriority();
        boolean publicStorage = isAEPublicPowerStorage();
        builder.setValues(currentPower, maximumPower, currentPower, receivablePower, powerFlow, priority,
            publicStorage);
    }

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
