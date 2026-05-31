package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.api.parts.IPart;
import ae2.integration.modules.igtooltip.GridNodeState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

/**
 * Provide info about the grid connection status of a part.
 */
public final class GridNodeStateProvider implements BodyProvider<IPart>, ServerDataProvider<IPart> {
    private static final String TAG_STATE = "gridNodeState";

    @Override
    public void buildTooltip(IPart object, TooltipContext context, TooltipBuilder tooltip) {
        var serverData = context.serverData();
        if (serverData.hasKey(TAG_STATE, Constants.NBT.TAG_BYTE)) {
            var state = GridNodeState.values()[serverData.getByte(TAG_STATE)];
            tooltip.addLine(state.textComponent().setStyle(new Style().setColor(TextFormatting.GRAY)));
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, IPart part, NBTTagCompound serverData) {
        var state = GridNodeState.fromNode(part.getGridNode());
        serverData.setByte(TAG_STATE, (byte) state.ordinal());
    }
}
