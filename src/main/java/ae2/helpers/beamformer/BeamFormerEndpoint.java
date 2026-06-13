package ae2.helpers.beamformer;

import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.util.AEColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public interface BeamFormerEndpoint {

    int MAX_BEAM_LENGTH = 32;
    double BEAM_ORIGIN_FACE_OFFSET = 7.0D / 16.0D;

    World getBeamWorld();

    BlockPos getBeamPos();

    EnumFacing getBeamDirection();

    IGridNode getBeamGridNode();

    IGridConnection getBeamConnection();

    void setBeamConnection(IGridConnection connection);

    BeamFormerType getBeamType();

    AEColor getBeamColor();

    void setBeamColor(AEColor color);

    double getBeamLength();

    void setBeamLength(double length);

    BeamFormerEndpoint getLinkedEndpoint();

    void setLinkedEndpoint(BeamFormerEndpoint endpoint);

    void clearLinkedEndpoint(BeamFormerEndpoint endpoint);

    boolean isBeamVisible();

    void setBeamVisible(boolean beamVisible);

    boolean isBeamLinked();

    void setBeamLinked(boolean linked);

    default double getBeamOriginOffsetX() {
        return getBeamDirection().getXOffset() * BEAM_ORIGIN_FACE_OFFSET;
    }

    default double getBeamOriginOffsetY() {
        return getBeamDirection().getYOffset() * BEAM_ORIGIN_FACE_OFFSET;
    }

    default double getBeamOriginOffsetZ() {
        return getBeamDirection().getZOffset() * BEAM_ORIGIN_FACE_OFFSET;
    }

    default Vec3d getBeamOriginOffset() {
        return new Vec3d(getBeamOriginOffsetX(), getBeamOriginOffsetY(), getBeamOriginOffsetZ());
    }

    default double getBeamRadius() {
        return getBeamType() == BeamFormerType.DENSE_BLOCK ? 4.0 / 32.0 : 2.0 / 32.0;
    }

    default double getIdlePowerUsage() {
        return getBeamType() == BeamFormerType.DENSE_BLOCK ? 40.0D : 10.0D;
    }

    default boolean isBeamOnline() {
        IGridNode node = getBeamGridNode();
        return node != null && node.isActive();
    }

    void onBeamChanged();
}
