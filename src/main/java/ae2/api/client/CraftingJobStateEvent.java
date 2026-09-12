/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.api.client;

import ae2.api.stacks.AEKey;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;
import java.util.UUID;

/**
 * Posted on the Forge event bus when the client is told that a crafting job started by the local player changed
 * state.
 * <p>
 * Add-ons can use this to react to crafting job progress without depending on AE2's networking internals. The event
 * is posted before AE2 applies its own handling, such as the finished-job toast.
 * <p>
 * Only jobs whose owner is the local player are reported, because the server only sends these updates to the
 * requesting player. The event is never posted on a dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class CraftingJobStateEvent extends Event {

    private final UUID jobId;
    private final AEKey what;
    private final long requestedAmount;
    private final long remainingAmount;
    private final CraftingJobState state;

    public CraftingJobStateEvent(UUID jobId,
                                 AEKey what,
                                 long requestedAmount,
                                 long remainingAmount,
                                 CraftingJobState state) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.what = Objects.requireNonNull(what, "what");
        this.requestedAmount = requestedAmount;
        this.remainingAmount = remainingAmount;
        this.state = Objects.requireNonNull(state, "state");
    }

    /**
     * @return the id identifying this job across its state changes
     */
    public UUID getJobId() {
        return this.jobId;
    }

    /**
     * @return what the job produces
     */
    public AEKey getWhat() {
        return this.what;
    }

    /**
     * @return the amount originally requested
     */
    public long getRequestedAmount() {
        return this.requestedAmount;
    }

    /**
     * @return the amount still outstanding at the time of this update
     */
    public long getRemainingAmount() {
        return this.remainingAmount;
    }

    /**
     * @return the state the job entered
     */
    public CraftingJobState getState() {
        return this.state;
    }
}
