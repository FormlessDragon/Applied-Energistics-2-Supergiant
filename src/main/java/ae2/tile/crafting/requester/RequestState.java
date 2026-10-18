package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.tile.crafting.TileRequester;

import java.util.concurrent.Future;

public final class RequestState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        if (!host.canProcessRequests()) {
            return IDLE;
        }
        var source = host.captureCalculationSource(slot);
        Request request = host.getRequests().get(slot);
        AEKey key = request.getKey();
        if (!request.isEnabled() || request.isEmpty() || key == null) {
            return IDLE;
        }

        long amountToCraft = host.getStorageTracker().computeAmountToCraft(slot, request);
        if (amountToCraft <= 0) {
            return IDLE;
        }

        boolean missing = !request.isForceStart() && host.planMaterialSubset(key, amountToCraft);
        if (!host.isCalculationSourceCurrent(source)) {
            return IDLE;
        }
        if (missing) {
            // The structure proves the request can only report missing items: settle without a calculation.
            host.markMissingRetry(slot);
            return BlockedState.missing();
        }

        boolean idleCpu = host.hasIdleCpu();
        if (!host.isCalculationSourceCurrent(source)) {
            return IDLE;
        }
        if (!idleCpu) {
            return BlockedState.cpu();
        }

        Future<ICraftingPlan> future = host.beginPlan(key, amountToCraft);
        return new PlanState(future, source);
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.REQUESTING;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.SLOWER;
    }
}
