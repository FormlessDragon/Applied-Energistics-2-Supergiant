package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.tile.crafting.TileRequester;

import java.util.concurrent.Future;

public final class RequestState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        Request request = host.getRequests().get(slot);
        AEKey key = request.getKey();
        if (!request.isEnabled() || request.isEmpty() || key == null) {
            return IDLE;
        }

        long amountToCraft = host.getStorageTracker().computeAmountToCraft(slot, request);
        if (amountToCraft <= 0) {
            return IDLE;
        }

        if (!host.hasIdleCpu()) {
            return BlockedState.cpu();
        }

        Future<ICraftingPlan> future = host.beginPlan(key, amountToCraft);
        return new PlanState(future);
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
