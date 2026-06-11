package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.integration.modules.theoneprobe.TopTooltipFormatter;
import ae2.parts.p2p.MEP2PTunnelPart;
import ae2.parts.p2p.P2PTunnelPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

@SuppressWarnings("rawtypes")
public final class P2PStateDataProvider implements BodyProvider<P2PTunnelPart>, ServerDataProvider<P2PTunnelPart> {
    public static final String TAG_P2P_STATE = "p2pState";
    public static final String TAG_P2P_OUTPUTS = "p2pOutputs";
    public static final String TAG_P2P_INPUTS = "p2pInputs";
    public static final String TAG_P2P_FREQUENCY = "p2pFrequency";
    public static final String TAG_P2P_FREQUENCY_NAME = "p2pFrequencyName";
    public static final String TAG_P2P_ME_CARRIED_CHANNELS = "p2pCarriedChannels";
    private static final byte STATE_UNLINKED = 0;
    private static final byte STATE_OUTPUT = 1;
    private static final byte STATE_INPUT = 2;

    @Override
    public void buildTooltip(P2PTunnelPart object, TooltipContext context, TooltipBuilder tooltip) {
        NBTTagCompound serverData = context.serverData();

        if (serverData.hasKey(TAG_P2P_STATE, 1)) {
            byte state = serverData.getByte(TAG_P2P_STATE);
            int outputs = serverData.getInteger(TAG_P2P_OUTPUTS);

            switch (state) {
                case STATE_UNLINKED -> tooltip.addLine(TopText.p2p_unlinked);
                case STATE_OUTPUT -> {
                    if (serverData.hasKey(TAG_P2P_INPUTS, 3)) {
                        tooltip.addLabel(TopText.p2p_output_many_inputs,
                            Integer.toString(serverData.getInteger(TAG_P2P_INPUTS)));
                    } else {
                        tooltip.addLine(TopText.p2p_output);
                    }
                }
                case STATE_INPUT -> {
                    if (outputs <= 1) {
                        tooltip.addLine(TopText.p2p_input_one_output);
                    } else {
                        tooltip.addLabel(TopText.p2p_input_many_outputs, Integer.toString(outputs));
                    }
                }
                default -> {
                }
            }

            short freq = serverData.getShort(TAG_P2P_FREQUENCY);
            String freqTooltip = TopTooltipFormatter.p2pFrequency(freq);
            if (serverData.hasKey(TAG_P2P_FREQUENCY_NAME, 8)) {
                String freqName = serverData.getString(TAG_P2P_FREQUENCY_NAME);
                freqTooltip = TextFormatting.GREEN + freqName + TextFormatting.WHITE + " (" + freqTooltip
                    + TextFormatting.WHITE + ")";
            }

            tooltip.addLine(tooltip.localize(TopText.p2p_frequency) + ": " + freqTooltip);

            if (serverData.hasKey(TAG_P2P_ME_CARRIED_CHANNELS, 3)) {
                int carriedChannels = serverData.getInteger(TAG_P2P_ME_CARRIED_CHANNELS);
                tooltip.addLabel(TopText.p2p_me_carried_channels, Integer.toString(carriedChannels));
            }
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, P2PTunnelPart part, NBTTagCompound serverData) {
        if (!part.isPowered()) {
            return;
        }

        serverData.setShort(TAG_P2P_FREQUENCY, part.getFrequency());

        byte state = STATE_UNLINKED;
        if (!part.isOutput()) {
            int outputCount = part.getOutputs().size();
            if (outputCount > 0) {
                state = STATE_INPUT;
                serverData.setInteger(TAG_P2P_OUTPUTS, outputCount);
            }

            if (part.getCustomName() != null) {
                serverData.setString(TAG_P2P_FREQUENCY_NAME, part.getCustomName());
            }
        } else {
            var input = part.getInput();
            if (input != null) {
                state = STATE_OUTPUT;
                if (part.supportsMultipleInputs()) {
                    serverData.setInteger(TAG_P2P_INPUTS, part.getInputs().size());
                }
                if (input.getCustomName() != null) {
                    serverData.setString(TAG_P2P_FREQUENCY_NAME, input.getCustomName());
                }
            }
        }

        serverData.setByte(TAG_P2P_STATE, state);

        if (part instanceof MEP2PTunnelPart meTunnel) {
            var externalNode = meTunnel.getExternalFacingNode();
            if (externalNode != null) {
                serverData.setInteger(TAG_P2P_ME_CARRIED_CHANNELS, externalNode.getUsedChannels());
            }
        }
    }
}
