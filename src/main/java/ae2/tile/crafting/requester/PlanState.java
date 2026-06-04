package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public record PlanState(Future<ICraftingPlan> future) implements StatusState {
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

    private static boolean isCpuTooSmall(ICraftingSubmitResult result) {
        if (result.errorCode() == CraftingSubmitErrorCode.CPU_TOO_SMALL) {
            return true;
        }
        if (result.errorCode() == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND
            && result.errorDetail() instanceof UnsuitableCpus unsuitableCpus) {
            return unsuitableCpus.tooSmall() > 0;
        }
        return false;
    }

    private static boolean isCpuError(ICraftingSubmitResult result) {
        return result.errorCode() == CraftingSubmitErrorCode.NO_CPU_FOUND
            || result.errorCode() == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND
            || result.errorCode() == CraftingSubmitErrorCode.CPU_BUSY
            || result.errorCode() == CraftingSubmitErrorCode.CPU_OFFLINE;
    }

    @Override
    public StatusState handle(TileRequester host, int slot) {
        if (!this.future.isDone()) {
            return this;
        }
        if (this.future.isCancelled()) {
            return IDLE;
        }

        try {
            ICraftingPlan plan = this.future.get();
            if (plan == null) {
                return IDLE;
            }

            if (isNoPatternPlan(plan)) {
                return host.disableRequestForNoPattern(slot);
            }

            if (!host.getRequests().get(slot).isForceStart() && !plan.missingItems().isEmpty()) {
                host.markMissingRetry(slot);
                return BlockedState.missing();
            }

            ICraftingSubmitResult result = host.submitPlan(plan, slot);
            ICraftingLink link = result.link();
            if (result.successful() && link != null) {
                host.getStorageTracker().setTotalAmount(slot, plan.finalOutput().what(), plan.finalOutput().amount());
                return new LinkState(link);
            }

            if (isCpuTooSmall(result)) {
                host.markCpuTooSmallRetry(slot);
                return BlockedState.cpu();
            }
            if (isCpuError(result)) {
                return BlockedState.cpu();
            }
            if (result.errorCode() == CraftingSubmitErrorCode.NO_CRAFTING_PATTERN) {
                return host.disableRequestForNoPattern(slot);
            }
            if (result.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT
                || result.errorCode() == CraftingSubmitErrorCode.INCOMPLETE_PLAN) {
                host.markMissingRetry(slot);
                return BlockedState.missing();
            }
            return IDLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IDLE;
        } catch (ExecutionException ignored) {
            return IDLE;
        }
    }
}
