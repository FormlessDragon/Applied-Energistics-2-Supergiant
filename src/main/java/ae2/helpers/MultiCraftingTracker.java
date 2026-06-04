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

package ae2.helpers;

import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingForceStartRequester;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.storage.StorageHelper;
import ae2.hooks.ticking.TickHandler;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MultiCraftingTracker {

    private static final int INITIAL_FAILURE_COOLDOWN_TICKS = 40;
    private static final int MAX_FAILURE_COOLDOWN_TICKS = 200;

    private final int size;
    private final ICraftingRequester owner;

    private Future<ICraftingPlan>[] jobs = null;
    private ICraftingLink[] links = null;
    private long[] retryAfterTick = null;
    private int[] retryFailures = null;
    private AEKey[] retryKeys = null;
    private long[] retryAmounts = null;

    public MultiCraftingTracker(ICraftingRequester owner, int size) {
        this.owner = owner;
        this.size = size;
    }

    public void readFromNBT(NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final NBTTagCompound link = extra.getCompoundTag("links-" + x);
            if (!link.isEmpty()) {
                this.setLink(x, StorageHelper.loadCraftingLink(link, this.owner));
            }
        }
    }

    public void writeToNBT(NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final ICraftingLink link = this.getLink(x);
            if (link != null) {
                final NBTTagCompound serializedLink = new NBTTagCompound();
                link.writeToNBT(serializedLink);
                extra.setTag("links-" + x, serializedLink);
            }
        }
    }

    public boolean handleCrafting(int x, AEKey what, long amount, World level, ICraftingService cg,
                                  IActionSource mySrc) {
        var craftingJob = this.getJob(x);
        if (this.getLink(x) != null) {
            return false;
        }

        if (this.isRetryCoolingDown(x, what, amount)) {
            return false;
        }

        if (craftingJob != null) {
            try {
                ICraftingPlan job = null;
                if (craftingJob.isDone()) {
                    job = craftingJob.get();
                }

                if (job != null) {
                    boolean forceStart = this.owner instanceof ICraftingForceStartRequester forceRequester
                        && forceRequester.canForceStartCrafting(job);
                    var result = cg.submitJob(job, this.owner, null, false, mySrc, forceStart);
                    this.setJob(x, null);

                    if (result.successful()) {
                        this.clearRetry(x);
                        this.setLink(x, result.link());
                        return true;
                    }
                    this.markFailure(x, what, amount);
                } else if (craftingJob.isDone()) {
                    this.setJob(x, null);
                    this.markFailure(x, what, amount);
                }
            } catch (InterruptedException | ExecutionException | RuntimeException ignored) {
                this.markFailure(x, what, amount);
                this.setJob(x, null);
            }
        } else if (this.getLink(x) == null) {
            this.setJob(x, cg.beginCraftingCalculation(level, () -> mySrc, what, amount, CalculationStrategy.CRAFT_LESS));
        }
        return false;
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        if (this.links == null) {
            return ImmutableSet.of();
        }

        return ImmutableSet.copyOf(new NonNullArrayIterator<>(this.links));
    }

    public void jobStateChange(ICraftingLink link) {
        if (this.links != null) {
            for (int x = 0; x < this.links.length; x++) {
                if (this.links[x] == link) {
                    this.setLink(x, null);
                    this.clearRetry(x);
                    return;
                }
            }
        }
    }

    int getSlot(ICraftingLink link) {
        if (this.links != null) {
            for (int x = 0; x < this.links.length; x++) {
                if (this.links[x] == link) {
                    return x;
                }
            }
        }

        return -1;
    }

    void cancel() {
        if (this.links != null) {
            for (ICraftingLink link : this.links) {
                if (link != null) {
                    link.cancel();
                }
            }

            this.links = null;
        }

        if (this.jobs != null) {
            for (Future<ICraftingPlan> job : this.jobs) {
                if (job != null) {
                    job.cancel(true);
                }
            }

            this.jobs = null;
        }

        this.clearRetries();
    }

    boolean isBusy(int slot) {
        return this.getLink(slot) != null || this.getJob(slot) != null;
    }

    private @Nullable ICraftingLink getLink(int slot) {
        if (this.links == null) {
            return null;
        }

        return this.links[slot];
    }

    private void setLink(int slot, ICraftingLink link) {
        if (this.links == null) {
            this.links = new ICraftingLink[this.size];
        }

        this.links[slot] = link;

        boolean hasStuff = false;
        for (int x = 0; x < this.links.length; x++) {
            final ICraftingLink current = this.links[x];
            if (current == null || current.isCanceled() || current.isDone()) {
                this.links[x] = null;
            } else {
                hasStuff = true;
            }
        }

        if (!hasStuff) {
            this.links = null;
        }
    }

    private boolean isRetryCoolingDown(int slot, AEKey what, long amount) {
        if (this.retryAfterTick == null || this.retryAfterTick[slot] <= 0) {
            return false;
        }

        if (this.retryKeys[slot] == null || !this.retryKeys[slot].equals(what) || this.retryAmounts[slot] != amount) {
            this.clearRetry(slot);
            return false;
        }

        if (TickHandler.instance().getCurrentTick() < this.retryAfterTick[slot]) {
            return true;
        }

        this.retryAfterTick[slot] = 0;
        return false;
    }

    private void markFailure(int slot, AEKey what, long amount) {
        this.ensureRetryArrays();

        int failures = this.retryFailures[slot] + 1;
        this.retryFailures[slot] = failures;
        this.retryKeys[slot] = what;
        this.retryAmounts[slot] = amount;
        this.retryAfterTick[slot] = TickHandler.instance().getCurrentTick()
            + Math.min(MAX_FAILURE_COOLDOWN_TICKS, INITIAL_FAILURE_COOLDOWN_TICKS * failures);
    }

    private void clearRetry(int slot) {
        if (this.retryAfterTick == null) {
            return;
        }

        this.retryAfterTick[slot] = 0;
        this.retryFailures[slot] = 0;
        this.retryKeys[slot] = null;
        this.retryAmounts[slot] = 0;
    }

    private void clearRetries() {
        this.retryAfterTick = null;
        this.retryFailures = null;
        this.retryKeys = null;
        this.retryAmounts = null;
    }

    private void ensureRetryArrays() {
        if (this.retryAfterTick == null) {
            this.retryAfterTick = new long[this.size];
            this.retryFailures = new int[this.size];
            this.retryKeys = new AEKey[this.size];
            this.retryAmounts = new long[this.size];
        }
    }

    private @Nullable Future<ICraftingPlan> getJob(int slot) {
        if (this.jobs == null) {
            return null;
        }

        return this.jobs[slot];
    }

    private void setJob(int slot, Future<ICraftingPlan> job) {
        if (this.jobs == null) {
            @SuppressWarnings("unchecked")
            Future<ICraftingPlan>[] jobs = (Future<ICraftingPlan>[]) new Future<?>[this.size];
            this.jobs = jobs;
        }

        this.jobs[slot] = job;

        boolean hasStuff = false;
        for (Future<ICraftingPlan> current : this.jobs) {
            if (current != null) {
                hasStuff = true;
                break;
            }
        }

        if (!hasStuff) {
            this.jobs = null;
        }
    }
}
