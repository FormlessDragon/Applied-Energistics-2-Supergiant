package ae2.requester.status;

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class PlanState implements StatusState {
    private static final Set<CraftingSubmitErrorCode> CPU_ERROR_CODES = Set.of(
        CraftingSubmitErrorCode.NO_CPU_FOUND,
        CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND,
        CraftingSubmitErrorCode.CPU_BUSY,
        CraftingSubmitErrorCode.CPU_OFFLINE,
        CraftingSubmitErrorCode.CPU_TOO_SMALL
    );

    private final Future<ICraftingPlan> future;

    public PlanState(Future<ICraftingPlan> future) {
        this.future = future;
    }

    public Future<ICraftingPlan> future() {
        return future;
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

            if (!host.getRequestManager().get(slot).isForceStart() && !plan.missingItems().isEmpty()) {
                return new MissingState();
            }

            ICraftingSubmitResult result = host.submitPlan(plan, slot);
            ICraftingLink link = result.link();
            if (result.successful() && link != null) {
                host.getStorageManager().setTotalAmount(slot, plan.finalOutput().what(), plan.finalOutput().amount());
                return new LinkState(link);
            }

            if (result.errorCode() != null && CPU_ERROR_CODES.contains(result.errorCode())) {
                return new CpuState();
            }
            if (result.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT) {
                return new MissingState();
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
