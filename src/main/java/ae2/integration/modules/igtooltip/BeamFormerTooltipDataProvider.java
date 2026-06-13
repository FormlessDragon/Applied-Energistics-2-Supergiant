package ae2.integration.modules.igtooltip;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.helpers.beamformer.BeamFormerEndpoint;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.parts.networking.BeamFormerPart;
import ae2.tile.networking.TileDenseBeamFormer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public final class BeamFormerTooltipDataProvider {

    private static final String TAG_LINKED = "beamLinked";
    private static final String TAG_TARGET_X = "beamTargetX";
    private static final String TAG_TARGET_Y = "beamTargetY";
    private static final String TAG_TARGET_Z = "beamTargetZ";
    private static final String TAG_BEAM_VISIBLE = "beamVisible";

    private BeamFormerTooltipDataProvider() {
    }

    public static void buildPartTooltip(BeamFormerPart part, TooltipContext context, TooltipBuilder tooltip) {
        buildTooltip(context, tooltip);
    }

    public static void providePartData(EntityPlayer player, BeamFormerPart part, NBTTagCompound serverData) {
        provideData(part, serverData);
    }

    public static void buildTileTooltip(TileDenseBeamFormer tile, TooltipContext context, TooltipBuilder tooltip) {
        buildTooltip(context, tooltip);
    }

    public static void provideTileData(EntityPlayer player, TileDenseBeamFormer tile, NBTTagCompound serverData) {
        provideData(tile, serverData);
    }

    private static void buildTooltip(TooltipContext context, TooltipBuilder tooltip) {
        NBTTagCompound serverData = context.serverData();
        if (serverData.getBoolean(TAG_LINKED)) {
            tooltip.addLine(TopText.beam_former_linked);
            tooltip.addLabel(TopText.beam_former_target, formatTarget(serverData));
        } else {
            tooltip.addLine(TopText.beam_former_unlinked);
        }

        if (serverData.hasKey(TAG_BEAM_VISIBLE) && !serverData.getBoolean(TAG_BEAM_VISIBLE)) {
            tooltip.addLine(TopText.beam_former_beam_hidden);
        }
    }

    private static void provideData(BeamFormerEndpoint endpoint, NBTTagCompound serverData) {
        BeamFormerEndpoint linkedEndpoint = endpoint.getLinkedEndpoint();
        serverData.setBoolean(TAG_LINKED, linkedEndpoint != null);
        serverData.setBoolean(TAG_BEAM_VISIBLE, endpoint.isBeamVisible());
        if (linkedEndpoint != null) {
            BlockPos targetPos = linkedEndpoint.getBeamPos();
            serverData.setInteger(TAG_TARGET_X, targetPos.getX());
            serverData.setInteger(TAG_TARGET_Y, targetPos.getY());
            serverData.setInteger(TAG_TARGET_Z, targetPos.getZ());
        }
    }

    private static String formatTarget(NBTTagCompound serverData) {
        return serverData.getInteger(TAG_TARGET_X) + ", "
            + serverData.getInteger(TAG_TARGET_Y) + ", "
            + serverData.getInteger(TAG_TARGET_Z);
    }
}
