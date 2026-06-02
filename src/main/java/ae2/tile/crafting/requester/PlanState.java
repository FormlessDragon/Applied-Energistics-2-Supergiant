package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public record PlanState(Future<ICraftingPlan> future) implements StatusState {
    private static final Set<CraftingSubmitErrorCode> CPU_ERROR_CODES = Set.of(
        CraftingSubmitErrorCode.NO_CPU_FOUND,
        CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND,
        CraftingSubmitErrorCode.CPU_BUSY,
        CraftingSubmitErrorCode.CPU_OFFLINE,
        CraftingSubmitErrorCode.CPU_TOO_SMALL
    );

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

            if (!host.getRequests().get(slot).isForceStart() && !plan.missingItems().isEmpty()) {
                return BlockedState.missing();
            }

            ICraftingSubmitResult result = host.submitPlan(plan, slot);
            ICraftingLink link = result.link();
            if (result.successful() && link != null) {
                host.getStorageTracker().setTotalAmount(slot, plan.finalOutput().what(), plan.finalOutput().amount());
                return new LinkState(link);
            }

            if (result.errorCode() != null && CPU_ERROR_CODES.contains(result.errorCode())) {
                return BlockedState.cpu();
            }
            if (result.errorCode() == CraftingSubmitErrorCode.NO_CRAFTING_PATTERN) {
                return BlockedState.noPattern();
            }
            if (result.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT
                || result.errorCode() == CraftingSubmitErrorCode.INCOMPLETE_PLAN) {
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

    @Override
    public RequestStatus type() {
        return RequestStatus.PLANNING;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return this.future.isDone() && !this.future.isCancelled() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }
}
