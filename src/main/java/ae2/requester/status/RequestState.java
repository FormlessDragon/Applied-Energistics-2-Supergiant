package ae2.requester.status;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.requester.Request;
import ae2.tile.crafting.TileRequester;

import java.util.concurrent.Future;

public final class RequestState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        Request request = host.getRequestManager().get(slot);
        AEKey key = request.getKey();
        if (!request.isEnabled() || request.isEmpty() || key == null) {
            return IDLE;
        }

        long amountToCraft = host.getStorageManager().computeAmountToCraft(slot, request);
        if (amountToCraft <= 0) {
            return IDLE;
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
