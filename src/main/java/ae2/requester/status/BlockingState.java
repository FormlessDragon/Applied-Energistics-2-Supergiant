package ae2.requester.status;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public abstract class BlockingState implements StatusState {
    private PlanState simulatedPlanState;

    @Override
    public StatusState handle(TileRequester host, int slot) {
        if (this.simulatedPlanState != null) {
            StatusState planSim = this.simulatedPlanState.handle(host, slot);
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

        this.simulatedPlanState = (PlanState) requestSim;
        return this;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.IDLE;
    }
}
