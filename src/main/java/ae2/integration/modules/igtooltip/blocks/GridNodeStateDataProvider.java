package ae2.integration.modules.igtooltip.blocks;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.integration.modules.igtooltip.GridNodeState;
import ae2.me.helpers.IGridConnectedTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

/**
 * Provide info about the grid connection status of a machine.
 */
public final class GridNodeStateDataProvider implements BodyProvider<TileEntity>, ServerDataProvider<TileEntity> {
    private static final String TAG_STATE = "gridNodeState";

    @Override
    public void buildTooltip(TileEntity object, TooltipContext context, TooltipBuilder tooltip) {
        var tag = context.serverData();
        if (tag.hasKey(TAG_STATE, Constants.NBT.TAG_BYTE)) {
            var state = GridNodeState.fromOrdinal(tag.getByte(TAG_STATE));
            if (state != null) {
                tooltip.addLine(state.text(), TextFormatting.GRAY);
            }
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, TileEntity object, NBTTagCompound serverData) {
        // Some devices can be powered both externally and through the grid.
        // If they are powered externally, they might still be active when the grid itself is down
        if (object instanceof IPowerChannelState powerChannelState && powerChannelState.isActive()) {
            serverData.setByte(TAG_STATE, (byte) GridNodeState.ONLINE.ordinal());
            return;
        }

        if (object instanceof IGridConnectedTile gridConnectedTile) {
            var state = GridNodeState.fromNode(gridConnectedTile.getActionableNode());
            serverData.setByte(TAG_STATE, (byte) state.ordinal());
        }
    }
}
