package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.core.AELog;
import ae2.tile.crafting.TileRequester;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public record PlanState(Future<ICraftingPlan> future, @Nullable TileRequester.CalculationSource source)
    implements StatusState {
    /**
     * Unbound external callers retain entry-time validation; internal planning always supplies its original source.
     */
    public PlanState(Future<ICraftingPlan> future) {
        this(future, null);
    }

    @Override
    public void cancel() {
        this.future.cancel(true);
    }

    private static boolean isNoPatternPlan(ICraftingPlan plan) {
        return !plan.missingItems().isEmpty() && plan.patternTimes().isEmpty() && plan.emittedItems().isEmpty();
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.PLANNING;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return this.future.isDone() && !this.future.isCancelled() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public StatusState handle(TileRequester host, int slot) {
        var owner = this.source == null ? host.captureCalculationSource(slot) : this.source;
        if (!host.isCalculationSourceCurrent(owner)) {
            try {
                this.cancel();
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to cancel requester calculation from an expired source");
            }
            return IDLE;
        }
        if (!this.future.isDone()) {
            return this;
        }
        if (this.future.isCancelled() || !host.isCalculationSourceCurrent(owner)) {
            return IDLE;
        }

        try {
            ICraftingPlan plan = this.future.get();
            if (plan == null || !host.isCalculationSourceCurrent(owner)) {
                return IDLE;
            }

            boolean noPattern = isNoPatternPlan(plan);
            if (!host.isCalculationSourceCurrent(owner)) {
                return IDLE;
            }
            if (noPattern) {
                return host.disableRequestForNoPattern(slot);
            }

            boolean missing = !host.getRequests().get(slot).isForceStart() && !plan.missingItems().isEmpty();
            var output = plan.finalOutput();
            if (!host.isCalculationSourceCurrent(owner)) {
                return IDLE;
            }
            if (missing) {
                host.markMissingRetry(slot);
                return BlockedState.missing();
            }

            return host.submitPlan(plan, slot, owner, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IDLE;
        } catch (ExecutionException | CancellationException ignored) {
            return IDLE;
        }
    }
}
