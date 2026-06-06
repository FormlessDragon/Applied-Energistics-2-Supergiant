package ae2.integration.modules.igtooltip;

import ae2.api.networking.IGridNode;
import ae2.core.localization.LocalizationEnum;
import ae2.integration.modules.theoneprobe.TopText;
import org.jetbrains.annotations.Nullable;

public enum GridNodeState {
    OFFLINE(TopText.device_offline),
    NETWORK_BOOTING(TopText.network_booting),
    MISSING_CHANNEL(TopText.device_missing_channel),
    ONLINE(TopText.device_online);

    private final LocalizationEnum text;

    GridNodeState(LocalizationEnum text) {
        this.text = text;
    }

    public static GridNodeState fromNode(@Nullable IGridNode gridNode) {
        var state = GridNodeState.OFFLINE;
        if (gridNode != null && gridNode.isPowered()) {
            if (!gridNode.hasGridBooted()) {
                state = GridNodeState.NETWORK_BOOTING;
            } else if (!gridNode.meetsChannelRequirements()) {
                state = GridNodeState.MISSING_CHANNEL;
            } else {
                state = GridNodeState.ONLINE;
            }
        }
        return state;
    }

    public LocalizationEnum text() {
        return text;
    }

}
