package ae2.parts.automation;

import ae2.api.behaviors.PlacementStrategy;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;

import java.util.Map;

class PlacementStrategyFacade implements PlacementStrategy {
    private final Map<AEKeyType, PlacementStrategy> strategies;

    public PlacementStrategyFacade(Map<AEKeyType, PlacementStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public void clearBlocked() {
        for (var strategy : strategies.values()) {
            strategy.clearBlocked();
        }
    }

    @Override
    public long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        var strategy = strategies.get(what.getType());
        return strategy != null ? strategy.placeInWorld(what, amount, type, placeAsEntity) : 0;
    }
}
