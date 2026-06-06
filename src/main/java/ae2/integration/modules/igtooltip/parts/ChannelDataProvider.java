package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.api.networking.pathing.ControllerState;
import ae2.core.localization.LocalizationEnum;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.me.service.PathingService;
import ae2.parts.networking.IUsedChannelProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

/**
 * Shows the used and maximum channel count for a part that implements {@link IUsedChannelProvider}.
 */
public final class ChannelDataProvider
    implements BodyProvider<IUsedChannelProvider>, ServerDataProvider<IUsedChannelProvider> {
    private static final String TAG_MAX_CHANNELS = "maxChannels";
    private static final String TAG_USED_CHANNELS = "usedChannels";
    private static final String TAG_ERROR = "channelError";

    @Override
    public void buildTooltip(IUsedChannelProvider object, TooltipContext context, TooltipBuilder tooltip) {
        var serverData = context.serverData();
        if (serverData.hasKey(TAG_ERROR, Constants.NBT.TAG_STRING)) {
            var error = ChannelError.valueOf(serverData.getString(TAG_ERROR));
            tooltip.addLine(error.text, TextFormatting.RED);
            return;
        }

        if (serverData.hasKey(TAG_MAX_CHANNELS, Constants.NBT.TAG_INT)) {
            var usedChannels = serverData.getInteger(TAG_USED_CHANNELS);
            var maxChannels = serverData.getInteger(TAG_MAX_CHANNELS);
            // Even in the maxChannels=0 case, we'll show as infinite
            if (maxChannels <= 0) {
                tooltip.addLabel(TopText.channels, Integer.toString(usedChannels));
            } else {
                tooltip.addLabel(TopText.channels_of, usedChannels + " / " + maxChannels);
            }
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, IUsedChannelProvider object, NBTTagCompound serverData) {
        var gridNode = object.getGridNode();
        if (gridNode != null) {
            var pathingService = (PathingService) gridNode.grid().getPathingService();
            if (pathingService.getControllerState() == ControllerState.NO_CONTROLLER) {
                var adHocError = pathingService.getAdHocNetworkError();
                if (adHocError != null) {
                    switch (adHocError) {
                        case NESTED_P2P_TUNNEL ->
                            serverData.setString(TAG_ERROR, ChannelError.AD_HOC_NESTED_P2P_TUNNEL.name());
                        case TOO_MANY_CHANNELS ->
                            serverData.setString(TAG_ERROR, ChannelError.AD_HOC_TOO_MANY_CHANNELS.name());
                    }
                    return;
                }
            } else if (pathingService.getControllerState() == ControllerState.CONTROLLER_CONFLICT) {
                serverData.setString(TAG_ERROR, ChannelError.CONTROLLER_CONFLICT.name());
            }
        }

        serverData.setInteger(TAG_USED_CHANNELS, object.getUsedChannelsInfo());
        serverData.setInteger(TAG_MAX_CHANNELS, object.getMaxChannelsInfo());
    }

    enum ChannelError {
        AD_HOC_NESTED_P2P_TUNNEL(TopText.nested_p2p_tunnel),
        AD_HOC_TOO_MANY_CHANNELS(TopText.error_too_many_channels),
        CONTROLLER_CONFLICT(TopText.error_controller_conflict);

        final LocalizationEnum text;

        ChannelError(LocalizationEnum text) {
            this.text = text;
        }
    }
}
