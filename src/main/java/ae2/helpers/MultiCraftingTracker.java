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

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingForceStartRequester;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.StorageHelper;
import ae2.core.AELog;
import ae2.crafting.graph.MaterialClosure;
import ae2.hooks.ticking.TickHandler;
import ae2.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MultiCraftingTracker {

    private static final int INITIAL_FAILURE_COOLDOWN_TICKS = 40;
    private static final int MAX_FAILURE_COOLDOWN_TICKS = 200;

    private final int size;
    private final ICraftingRequester owner;
    private final CalculationContext[] calculations;
    private final Submission[] submissions;
    private final boolean[] handling;
    private final boolean[] cancelRequested;
    private final boolean[] cancelingLinks;
    private int submissionCount;
    private int cancelDepth;
    private boolean suspended;
    private long lifecycleGeneration;
    private long calculationGeneration;

    private Future<ICraftingPlan>[] jobs = null;
    private ICraftingLink[] links = null;
    private long[] retryAfterTick = null;
    private int[] retryFailures = null;
    private AEKey[] retryKeys = null;
    private long[] retryAmounts = null;

    public MultiCraftingTracker(ICraftingRequester owner, int size) {
        this.owner = owner;
        this.size = size;
        this.calculations = new CalculationContext[size];
        this.submissions = new Submission[size];
        this.handling = new boolean[size];
        this.cancelRequested = new boolean[size];
        this.cancelingLinks = new boolean[size];
    }

    public void readFromNBT(NBTTagCompound extra) {
        this.readConfiguration(() -> {
            long generation = this.calculationGeneration;
            var originalLinks = this.links;
            var loaded = new ICraftingLink[this.size];
            var loadedCancellation = new boolean[this.size];
            var retainedCancellation = new boolean[this.size];
            var pending = new Submission[this.size];
            boolean hasLinks = false;
            for (int x = 0; x < this.size; x++) {
                final NBTTagCompound link = extra.getCompoundTag("links-" + x);
                if (!link.isEmpty()) {
                    if (link.hasKey("trackerCancelRequested") && !link.hasKey("trackerCancelRequested", 1)) {
                        throw new IllegalStateException("Invalid crafting cancellation flag");
                    }
                    var restored = StorageHelper.loadCraftingLink(link, this.owner);
                    if (!restored.isCanceled() && !restored.isDone()) {
                        loaded[x] = restored;
                        loadedCancellation[x] = link.getBoolean("trackerCancelRequested");
                        hasLinks = true;
                    }
                }
                var canceling = this.getLink(x);
                if (canceling != null && this.cancelRequested[x]) {
                    if (!link.isEmpty() && (!link.hasUniqueId("craftId")
                        || !Objects.requireNonNull(link.getUniqueId("craftId")).equals(canceling.getCraftingID()))) {
                        throw new IllegalStateException("Cannot replace a crafting link with unconfirmed cancellation");
                    }
                    loaded[x] = canceling;
                    loadedCancellation[x] = true;
                    retainedCancellation[x] = true;
                    hasLinks = true;
                }
                String pendingKey = "unconfirmed-submission-" + x;
                if (extra.hasKey(pendingKey)) {
                    pending[x] = new Submission(extra.getTag(pendingKey), this.owner);
                    if (pending[x].terminal) {
                        pending[x] = null;
                    }
                }
                var current = this.submissions[x];
                if (current != null) {
                    if (pending[x] != null && !current.sameSubmission(pending[x])) {
                        throw new IllegalStateException("Cannot replace an unconfirmed crafting submission");
                    }
                    pending[x] = current;
                }
            }
            if (generation != this.calculationGeneration || this.links != originalLinks) {
                throw new IllegalStateException("Crafting ownership changed during link restoration");
            }
            for (int x = 0; x < this.size; x++) {
                if (retainedCancellation[x] && (this.getLink(x) != loaded[x] || !this.cancelRequested[x])) {
                    throw new IllegalStateException("Crafting cancellation settled during link restoration");
                }
            }
            this.links = hasLinks ? loaded : null;
            System.arraycopy(loadedCancellation, 0, this.cancelRequested, 0, this.size);
            for (int x = 0; x < this.size; x++) {
                if (this.submissions[x] != null) {
                    this.submissions[x].revoked = true;
                }
                this.setSubmission(x, pending[x]);
            }
        });
    }

    /**
     * Run a host configuration read with planning disabled, without changing lifecycle suspension.
     */
    public void readConfiguration(Runnable reader) {
        this.cancelDepth++;
        try {
            this.cancelCalculations();
            reader.run();
        } finally {
            this.cancelDepth--;
        }
    }

    public void writeToNBT(NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final ICraftingLink link = this.getLink(x);
            if (link != null) {
                boolean cancellationPending = this.cancelRequested[x];
                final NBTTagCompound serializedLink = new NBTTagCompound();
                link.writeToNBT(serializedLink);
                if (cancellationPending || this.getLink(x) != link || this.cancelRequested[x]) {
                    serializedLink.setBoolean("trackerCancelRequested", true);
                }
                extra.setTag("links-" + x, serializedLink);
            } else {
                extra.removeTag("links-" + x);
            }
            var submission = this.submissions[x];
            if (submission == null) {
                extra.removeTag("unconfirmed-submission-" + x);
            } else {
                extra.setTag("unconfirmed-submission-" + x, submission.writeToNBT());
            }
        }
    }

    public boolean handleCrafting(int x, AEKey what, long amount, World level, ICraftingService cg,
                                  IActionSource mySrc) {
        if (x < 0 || x >= this.size || this.suspended || this.cancelDepth > 0 || this.handling[x]
            || this.submissions[x] != null) {
            return false;
        }
        this.handling[x] = true;
        try {
            return this.handleCalculation(x, what, amount, level, cg, mySrc);
        } finally {
            this.handling[x] = false;
        }
    }

    private boolean handleCalculation(int x, AEKey what, long amount, World level, ICraftingService cg,
                                      IActionSource mySrc) {
        long generation = this.calculationGeneration;
        var context = this.calculations[x];
        boolean matches = context == null || context.matches(what, amount, level, cg, mySrc, this.owner);
        if (generation != this.calculationGeneration || this.suspended || this.calculations[x] != context) {
            return false;
        }
        if (!matches) {
            this.cancelCalculation(x);
            this.clearRetry(x);
        }
        if (what == null || amount <= 0 || level == null || cg == null || mySrc == null) {
            return false;
        }

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
                    if (!this.ownsCalculation(x, craftingJob)) {
                        return false;
                    }
                    job = craftingJob.get();
                }

                if (!this.ownsCalculation(x, craftingJob)) {
                    return false;
                }

                if (job != null) {
                    boolean forceStart = this.owner instanceof ICraftingForceStartRequester forceRequester
                        && forceRequester.canForceStartCrafting(job);
                    var submission = new Submission(what, amount, this.calculationGeneration,
                        this.lifecycleGeneration);
                    if (!this.ownsCalculation(x, craftingJob)) {
                        return false;
                    }
                    this.setSubmission(x, submission);
                    this.setJob(x, null);
                    return this.submitCalculation(x, submission, job, cg, mySrc, forceStart);
                } else if (craftingJob.isDone() && this.ownsCalculation(x, craftingJob)) {
                    this.setJob(x, null);
                    this.markFailure(x, what, amount);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (this.getJob(x) == craftingJob) {
                    this.setJob(x, null);
                }
                return false;
            } catch (ExecutionException | RuntimeException ignored) {
                if (this.getJob(x) == craftingJob) {
                    this.markFailure(x, what, amount);
                    this.setJob(x, null);
                }
            }
        } else if (this.getLink(x) == null) {
            var node = this.owner.getActionableNode();
            var started = new CalculationContext(what, amount, level, cg, mySrc, node,
                node == null ? null : node.grid());
            if (generation != this.calculationGeneration || this.suspended || this.cancelDepth > 0) {
                return false;
            }
            this.calculations[x] = started;
            Future<ICraftingPlan> future = null;
            try {
                var check = materialClosureCheck(cg, what, amount);
                if (!this.ownsContext(x, started)) {
                    return false;
                }
                if (check != null && check.guaranteedMissing()) {
                    // The structure proves the request can only report missing items.
                    this.markFailure(x, what, amount);
                    this.calculations[x] = null;
                } else {
                    future = cg.beginCraftingCalculation(level,
                        new CardSimulationRequester(mySrc, cg, check == null ? null : check.subset()), what, amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                    if (this.ownsContext(x, started)) {
                        this.setJob(x, future);
                        future = null;
                    }
                }
            } finally {
                if (future != null) {
                    this.cancelFuture(future);
                }
                if (this.calculations[x] == started && this.getJob(x) == null) {
                    this.calculations[x] = null;
                }
            }
        }
        return false;
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        if (this.submissionCount == 0) {
            return this.links == null ? ImmutableSet.of()
                : ImmutableSet.copyOf(new NonNullArrayIterator<>(this.links));
        }
        ImmutableSet.Builder<ICraftingLink> result = null;
        for (int x = 0; x < this.size; x++) {
            var link = this.getLink(x);
            if (link != null) {
                if (result == null) {
                    result = ImmutableSet.builder();
                }
                result.add(link);
            }
            var submission = this.submissions[x];
            if (submission != null && submission.link != null) {
                if (result == null) {
                    result = ImmutableSet.builder();
                }
                result.add(submission.link);
            }
        }
        return result == null ? ImmutableSet.of() : result.build();
    }

    private boolean submitCalculation(int slot, Submission submission, ICraftingPlan plan, ICraftingService crafting,
                                      IActionSource source, boolean forceStart) {
        long generation = this.calculationGeneration;
        try {
            var result = crafting.submitJob(plan, this.owner, null, false, source, forceStart);
            // Own a returned link before any further receipt callback can invalidate the request.
            submission.link = result.link();
            boolean successful = result.successful();
            if (submission.terminal) {
                this.setSubmission(slot, null);
                return successful;
            }
            if (!successful && submission.link == null) {
                this.setSubmission(slot, null);
                if (generation == this.calculationGeneration && !submission.revoked) {
                    this.markFailure(slot, submission.key, submission.amount);
                }
                return false;
            }
            if (submission.link == null) {
                AELog.warn("Successful crafting submission returned no requester link; automatic retry stopped");
                return false;
            }
            if (!successful || submission.revoked) {
                submission.revoked = true;
                this.cancelSubmission(slot, submission);
                if (!successful && this.submissions[slot] == null && generation == this.calculationGeneration) {
                    this.markFailure(slot, submission.key, submission.amount);
                }
                return successful;
            }
            this.setSubmission(slot, null);
            this.clearRetry(slot);
            this.setLink(slot, submission.link);
            return true;
        } catch (RuntimeException failure) {
            AELog.error(failure, "Crafting submission outcome is unconfirmed; automatic retry stopped");
            return false;
        } finally {
            submission.inFlight = false;
        }
    }

    private void cancelSubmission(int slot, Submission submission) {
        if (submission.link == null || submission.canceling) {
            return;
        }
        submission.canceling = true;
        this.cancelDepth++;
        try {
            submission.link.cancel();
            if ((submission.terminal || submission.link.isCanceled() || submission.link.isDone())
                && this.submissions[slot] == submission) {
                this.setSubmission(slot, null);
            }
        } catch (RuntimeException failure) {
            AELog.error(failure, "Crafting cancellation is unconfirmed; retaining the returned link");
        } finally {
            this.cancelDepth--;
            submission.canceling = false;
        }
    }

    public void jobStateChange(ICraftingLink link) {
        for (int x = 0; x < this.size; x++) {
            if (this.getLink(x) == link) {
                this.setLink(x, null);
                this.clearRetry(x);
                return;
            }
            var submission = this.submissions[x];
            if (submission != null && submission.link == link) {
                submission.terminal = true;
                if (!submission.inFlight && !submission.canceling) {
                    this.setSubmission(x, null);
                }
                return;
            }
        }
    }

    /**
     * The confirmed production slot, or -1 for a foreign or unconfirmed link.
     */
    public int getSlot(ICraftingLink link) {
        if (this.links != null) {
            for (int x = 0; x < this.links.length; x++) {
                if (this.links[x] == link && !this.cancelRequested[x]) {
                    return x;
                }
            }
        }

        return -1;
    }

    void cancel() {
        this.calculationGeneration++;
        var oldLinks = this.links;
        var oldJobs = this.jobs;
        var oldSubmissions = this.submissionCount == 0 ? null : this.submissions.clone();
        if (oldSubmissions != null) {
            for (var submission : oldSubmissions) {
                if (submission != null) {
                    submission.revoked = true;
                }
            }
        }
        if (oldLinks != null) {
            for (int x = 0; x < oldLinks.length; x++) {
                if (oldLinks[x] != null) {
                    this.cancelRequested[x] = true;
                }
            }
        }
        this.jobs = null;
        Arrays.fill(this.calculations, null);
        this.clearRetries();
        this.cancelDepth++;
        try {
            if (oldLinks != null) {
                for (int x = 0; x < oldLinks.length; x++) {
                    var link = oldLinks[x];
                    if (link != null && this.getLink(x) == link) {
                        this.cancelConfirmedLink(x, link);
                    }
                }
            }
            if (oldJobs != null) {
                for (Future<ICraftingPlan> job : oldJobs) {
                    if (job != null) {
                        this.cancelFuture(job);
                    }
                }
            }
            if (oldSubmissions != null) {
                for (int x = 0; x < oldSubmissions.length; x++) {
                    var submission = oldSubmissions[x];
                    if (submission != null && !submission.inFlight) {
                        this.cancelSubmission(x, submission);
                    }
                }
            }
        } finally {
            this.cancelDepth--;
        }
    }

    private void cancelConfirmedLink(int slot, ICraftingLink link) {
        if (this.cancelingLinks[slot]) {
            return;
        }
        this.cancelingLinks[slot] = true;
        try {
            link.cancel();
            if (this.getLink(slot) != link) {
                return;
            }
            boolean terminal = link.isCanceled();
            if (this.getLink(slot) != link) {
                return;
            }
            if (!terminal) {
                terminal = link.isDone();
            }
            if (terminal && this.getLink(slot) == link) {
                this.setLink(slot, null);
            }
        } catch (RuntimeException failure) {
            AELog.error(failure, "Crafting link cancellation is unconfirmed; retaining ownership");
        } finally {
            this.cancelingLinks[slot] = false;
        }
    }

    /**
     * Revoke unsubmitted calculations after configuration or source changes, retaining production links.
     */
    public void cancelCalculations() {
        this.calculationGeneration++;
        var oldJobs = this.jobs;
        this.jobs = null;
        Arrays.fill(this.calculations, null);
        this.clearRetries();
        this.cancelDepth++;
        try {
            if (oldJobs != null) {
                for (var job : oldJobs) {
                    if (job != null) {
                        this.cancelFuture(job);
                    }
                }
            }
        } finally {
            this.cancelDepth--;
        }
    }

    /**
     * Close admission before destroying the host node; submitted links remain owned for save/reload.
     */
    public long suspendCalculations() {
        this.suspended = true;
        long generation = ++this.lifecycleGeneration;
        this.cancelCalculations();
        return generation;
    }

    /**
     * Reopen only if no newer lifecycle close occurred during node initialization.
     */
    public void resumeCalculations(long generation) {
        if (generation == this.lifecycleGeneration) {
            this.suspended = false;
        }
    }

    boolean isBusy(int slot) {
        return this.submissions[slot] != null || this.getLink(slot) != null || this.getJob(slot) != null;
    }

    private void setSubmission(int slot, @Nullable Submission submission) {
        if (this.submissions[slot] == null && submission != null) {
            this.submissionCount++;
        } else if (this.submissions[slot] != null && submission == null) {
            this.submissionCount--;
        }
        this.submissions[slot] = submission;
    }

    private boolean ownsContext(int slot, CalculationContext context) {
        return !this.suspended && this.calculations[slot] == context && this.cancelDepth == 0
            && context.isCurrent(this.owner)
            && !this.suspended && this.cancelDepth == 0 && this.calculations[slot] == context;
    }

    private boolean ownsCalculation(int slot, Future<ICraftingPlan> job) {
        var context = this.calculations[slot];
        if (this.getJob(slot) != job || context == null) {
            return false;
        }
        if (!this.ownsContext(slot, context)) {
            if (this.getJob(slot) == job && this.calculations[slot] == context) {
                this.cancelCalculation(slot);
            }
            return false;
        }
        return true;
    }

    private void cancelCalculation(int slot) {
        var job = this.getJob(slot);
        this.setJob(slot, null);
        if (job != null) {
            this.cancelFuture(job);
        }
    }

    private void cancelFuture(Future<ICraftingPlan> job) {
        this.cancelDepth++;
        try {
            job.cancel(true);
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to cancel tracked crafting calculation");
        } finally {
            this.cancelDepth--;
        }
    }

    private void setLink(int slot, ICraftingLink link) {
        if (this.links == null) {
            this.links = new ICraftingLink[this.size];
        }

        this.links[slot] = link;
        this.cancelRequested[slot] = false;
        var published = this.links;
        boolean hasStuff = false;
        for (int x = 0; x < published.length; x++) {
            if (this.links != published) {
                return;
            }
            final ICraftingLink current = published[x];
            if (current != null) {
                boolean terminal = false;
                try {
                    terminal = current.isCanceled();
                    if (this.links != published || published[x] != current) {
                        return;
                    }
                    if (!terminal) {
                        terminal = current.isDone();
                    }
                } catch (RuntimeException failure) {
                    // A committed link remains owned when an extension cannot report its state.
                    AELog.error(failure, "Failed to inspect tracked crafting link");
                }
                if (this.links != published || published[x] != current) {
                    return;
                }
                if (terminal) {
                    published[x] = null;
                    this.cancelRequested[x] = false;
                } else {
                    hasStuff = true;
                }
            }
        }

        // Callbacks can change earlier slots in the same array as well.
        if (!hasStuff && this.links == published
            && Arrays.stream(published).allMatch(Objects::isNull)) {
            this.links = null;
        }
    }

    private void setJob(int slot, Future<ICraftingPlan> job) {
        if (job == null) {
            this.calculations[slot] = null;
        }
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

    private @Nullable ICraftingLink getLink(int slot) {
        if (this.links == null) {
            return null;
        }

        return this.links[slot];
    }

    private static final class Submission {
        private final NBTBase original;
        @Nullable
        private final AEKey key;
        private final long amount;
        @Nullable
        private ICraftingLink link;
        private boolean revoked;
        private boolean inFlight;
        private boolean canceling;
        private boolean terminal;

        Submission(@Nullable AEKey key, long amount, long sourceGeneration, long lifecycleGeneration) {
            this.key = key;
            this.amount = amount;
            var tag = new NBTTagCompound();
            tag.setInteger("schema", 1);
            tag.setUniqueId("submissionId", UUID.randomUUID());
            tag.setLong("sourceGeneration", sourceGeneration);
            tag.setLong("lifecycleGeneration", lifecycleGeneration);
            tag.setTag("request", GenericStack.writeTag(new GenericStack(key, amount)));
            this.original = tag;
            this.inFlight = true;
        }

        Submission(NBTBase original, ICraftingRequester owner) {
            this.original = original.copy();
            this.key = null;
            this.amount = 0;
            if (original instanceof NBTTagCompound tag && tag.getInteger("schema") == 1) {
                this.revoked = tag.getBoolean("revoked");
                if (tag.hasKey("link", 10)) {
                    try {
                        this.link = StorageHelper.loadCraftingLink(tag.getCompoundTag("link"), owner);
                        this.terminal = this.link.isCanceled() || this.link.isDone();
                    } catch (RuntimeException failure) {
                        AELog.error(failure, "Retaining unreadable unconfirmed crafting submission");
                    }
                }
            }
        }

        boolean sameSubmission(Submission other) {
            if (this.original.equals(other.original)) {
                return true;
            }
            return this.original instanceof NBTTagCompound first && other.original instanceof NBTTagCompound second
                && first.getInteger("schema") == 1 && second.getInteger("schema") == 1
                && first.hasUniqueId("submissionId") && second.hasUniqueId("submissionId")
                && Objects.requireNonNull(first.getUniqueId("submissionId")).equals(second.getUniqueId("submissionId"))
                && first.getLong("sourceGeneration") == second.getLong("sourceGeneration")
                && first.getLong("lifecycleGeneration") == second.getLong("lifecycleGeneration")
                && first.getCompoundTag("request").equals(second.getCompoundTag("request"))
                && Objects.equals(first.getTag("link"), second.getTag("link"));
        }

        NBTBase writeToNBT() {
            var result = this.original.copy();
            if (result instanceof NBTTagCompound tag && tag.getInteger("schema") == 1) {
                tag.setBoolean("revoked", this.revoked);
                if (this.link != null) {
                    var linkTag = new NBTTagCompound();
                    this.link.writeToNBT(linkTag);
                    tag.setTag("link", linkTag);
                }
            }
            return result;
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

    private record CalculationContext(AEKey key, long amount, World world, ICraftingService crafting,
                                      IActionSource source, @Nullable IGridNode node, @Nullable IGrid grid) {
        boolean matches(AEKey key, long amount, World world, ICraftingService crafting, IActionSource source,
                        ICraftingRequester owner) {
            return this.key.equals(key) && this.amount == amount && this.world == world
                && this.crafting == crafting && this.source == source && this.isCurrent(owner);
        }

        boolean isCurrent(ICraftingRequester owner) {
            return this.node == owner.getActionableNode() && (this.node == null || this.grid == this.node.grid());
        }
    }

    /**
     * Judges the request top-down against the inventory using the cached material closure, or null when no closure is
     * known or the owner may force-start plans: the walk only settles requests that can exclusively miss.
     */
    @Nullable
    private MaterialClosure.CheckResult materialClosureCheck(ICraftingService cg, AEKey what, long amount) {
        if (this.owner instanceof ICraftingForceStartRequester || !(cg instanceof CraftingService service)) {
            return null;
        }
        var closure = service.peekSimulationClosure(what);
        return closure == null
            ? null
            : closure.check(amount, service.getGrid().getStorageService().getCachedInventory());
    }

    /**
     * Simulation requester of crafting-card driven jobs (interface, export bus). It carries no cache of its own and
     * answers with the material closure walked by {@link #materialClosureCheck}, or null to let the calculation
     * derive the subset itself.
     */
    private record CardSimulationRequester(IActionSource source, ICraftingService crafting,
                                           @Nullable Collection<AEKey> subset)
        implements ICraftingSimulationRequester {

        @Override
        public IActionSource getActionSource() {
            return this.source;
        }

        @Nullable
        @Override
        public Collection<AEKey> getSimulationSubset(AEKey output) {
            return this.subset;
        }
    }
}
