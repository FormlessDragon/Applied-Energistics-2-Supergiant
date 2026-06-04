package ae2.tile.crafting.requester;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public final class BlockedState implements StatusState {
    private final RequestStatus status;
    private PlanState simulatedPlanState;

    private BlockedState(RequestStatus status) {
        this.status = status;
    }

    public static BlockedState missing() {
        return new BlockedState(RequestStatus.MISSING);
    }

    public static BlockedState cpu() {
        return new BlockedState(RequestStatus.CPU);
    }

    @Override
    public StatusState handle(TileRequester host, int slot) {
        if (host.shouldDelayRetry(slot, this.status)) {
            return this;
        }

        if (this.simulatedPlanState != null) {
            StatusState planSim = this.simulatedPlanState.handle(host, slot);
            if (planSim == this.simulatedPlanState) {
                return this;
            }

            this.simulatedPlanState = null;
            return planSim;
        }

        StatusState idleSim = IDLE.handle(host, slot);
        if (idleSim == IDLE || idleSim == EXPORT) {
            return IDLE;
        }

        StatusState requestSim = REQUEST.handle(host, slot);
        if (requestSim == IDLE) {
            return IDLE;
        }

        if (!(requestSim instanceof PlanState planState)) {
            return requestSim;
        }

        this.simulatedPlanState = planState;
        return this;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.IDLE;
    }

    @Override
    public RequestStatus type() {
        return this.status;
    }
}
