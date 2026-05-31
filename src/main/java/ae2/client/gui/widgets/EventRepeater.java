/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.widgets;

import java.time.Duration;

public class EventRepeater {

    private final long eventDelay;
    private final long eventInterval;
    private long nextEventTime = -1;
    private EventCallback eventCallback = null;

    public EventRepeater(Duration delay, Duration interval) {
        this.eventDelay = delay.toNanos();
        this.eventInterval = interval.toNanos();
    }

    public void tick() {
        if (this.eventCallback == null) {
            return;
        }

        long nanoTime = System.nanoTime();
        if (nanoTime < this.nextEventTime) {
            return;
        }

        this.nextEventTime = nanoTime + this.eventInterval;
        this.eventCallback.trigger();
    }

    public void repeat(EventCallback callback) {
        long time = System.nanoTime();
        this.eventCallback = callback;
        this.nextEventTime = time + eventDelay;
    }

    public boolean isRepeating() {
        return this.eventCallback != null;
    }

    public void stop() {
        this.eventCallback = null;
    }

    @FunctionalInterface
    public interface EventCallback {
        void trigger();
    }

}

