package ae2.helpers.beamformer;

import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.util.AEColor;
import ae2.parts.networking.BeamFormerPart;
import ae2.tile.networking.TileCableBus;
import ae2.tile.networking.TileDenseBeamFormer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class BeamFormerLinkLogic {

    private BeamFormerLinkLogic() {
    }

    public static IGridConnection updateLink(BeamFormerEndpoint endpoint, IGridConnection currentConnection) {
        if (endpoint.getBeamWorld() == null || endpoint.getBeamWorld().isRemote) {
            return currentConnection;
        }

        currentConnection = endpoint.getBeamConnection();
        endpoint.setBeamColor(endpoint.getBeamColor());
        boolean online = endpoint.isBeamOnline();
        BeamFormerEndpoint previousTarget = endpoint.getLinkedEndpoint();
        if (!online) {
            if (previousTarget != null && !previousTarget.isBeamOnline()) {
                destroyConnection(currentConnection);
                if (clearLink(endpoint)) {
                    endpoint.onBeamChanged();
                }
                return null;
            }
            return currentConnection;
        }

        BeamFormerEndpoint target = tryReuseExistingTarget(endpoint, previousTarget, currentConnection);
        if (target == null) {
            target = scan(endpoint);
        }
        if (target == null) {
            destroyConnection(currentConnection);
            if (clearLink(endpoint)) {
                endpoint.onBeamChanged();
            }
            return null;
        }

        target.setBeamColor(target.getBeamColor());
        if (target.getBeamDirection() != endpoint.getBeamDirection().getOpposite()) {
            destroyConnection(currentConnection);
            if (clearLink(endpoint)) {
                endpoint.onBeamChanged();
            }
            return null;
        }

        if (!hasCompatibleColor(endpoint, target)) {
            destroyConnection(currentConnection);
            if (clearLink(endpoint)) {
                endpoint.onBeamChanged();
            }
            return null;
        }

        IGridNode ourNode = endpoint.getBeamGridNode();
        IGridNode theirNode = target.getBeamGridNode();
        if (ourNode == null || theirNode == null) {
            destroyConnection(currentConnection);
            if (clearLink(endpoint)) {
                endpoint.onBeamChanged();
            }
            return null;
        }

        double renderLength = BeamFormerRenderGeometry.computeBeamLength(endpoint, target);
        if (previousTarget == target && currentConnection != null && target.getBeamConnection() == currentConnection) {
            if (applyLinkState(endpoint, target, currentConnection, renderLength)) {
                endpoint.onBeamChanged();
            }
            if (applyLinkState(target, endpoint, currentConnection, renderLength)) {
                target.onBeamChanged();
            }
            return currentConnection;
        }

        if (previousTarget != null) {
            destroyConnection(currentConnection);
            if (clearLink(endpoint)) {
                endpoint.onBeamChanged();
            }
            currentConnection = null;
        }
        BeamFormerEndpoint targetOwner = target.getLinkedEndpoint();
        if (targetOwner != null && targetOwner != endpoint) {
            if (!canReplaceTargetOwner(target, targetOwner, renderLength)) {
                destroyConnection(currentConnection);
                if (clearLink(endpoint)) {
                    endpoint.onBeamChanged();
                }
                return null;
            }
            destroyConnection(target.getBeamConnection());
            if (clearLink(target)) {
                target.onBeamChanged();
            }
        }

        destroyConnection(currentConnection);
        destroyConnection(target.getBeamConnection());

        IGridConnection newConnection;
        try {
            newConnection = GridHelper.createConnection(ourNode, theirNode);
        } catch (RuntimeException ignored) {
            if (clearEndpointState(endpoint, target)) {
                endpoint.onBeamChanged();
            }
            if (clearEndpointState(target, endpoint)) {
                target.onBeamChanged();
            }
            return null;
        }

        applyLinkState(endpoint, target, newConnection, renderLength);
        applyLinkState(target, endpoint, newConnection, renderLength);
        endpoint.onBeamChanged();
        target.onBeamChanged();
        return newConnection;
    }

    public static void disconnect(BeamFormerEndpoint endpoint, IGridConnection connection) {
        IGridConnection activeConnection = endpoint.getBeamConnection();
        destroyConnection(activeConnection == null ? connection : activeConnection);
        clearLink(endpoint);
        endpoint.setBeamConnection(null);
        endpoint.setBeamLinked(false);
        endpoint.setBeamLength(0.0);
        endpoint.onBeamChanged();
    }

    public static void clearRemoteLink(BeamFormerEndpoint endpoint) {
        BeamFormerEndpoint other = endpoint.getLinkedEndpoint();
        if (other != null) {
            other.clearLinkedEndpoint(endpoint);
            other.setBeamConnection(null);
            other.setBeamLinked(false);
            other.setBeamLength(0.0);
            other.onBeamChanged();
        }
        endpoint.setBeamConnection(null);
        endpoint.setBeamLinked(false);
        endpoint.setBeamLength(0.0);
        endpoint.clearLinkedEndpoint(other);
        endpoint.onBeamChanged();
    }

    private static void destroyConnection(IGridConnection connection) {
        if (connection != null) {
            connection.destroy();
        }
    }

    private static boolean clearLink(BeamFormerEndpoint endpoint) {
        BeamFormerEndpoint other = endpoint.getLinkedEndpoint();
        if (other != null) {
            if (clearEndpointState(other, endpoint)) {
                other.onBeamChanged();
            }
        }
        return clearEndpointState(endpoint, other);
    }

    private static boolean hasCompatibleColor(BeamFormerEndpoint a, BeamFormerEndpoint b) {
        AEColor aColor = a.getBeamColor();
        AEColor bColor = b.getBeamColor();
        return aColor == AEColor.TRANSPARENT || bColor == AEColor.TRANSPARENT || aColor == bColor;
    }

    private static boolean canReplaceTargetOwner(BeamFormerEndpoint target, BeamFormerEndpoint currentOwner,
                                                 double candidateLength) {
        double currentLength = target.getBeamLength();
        if (currentLength <= 0.0D) {
            currentLength = BeamFormerRenderGeometry.computeBeamLength(target, currentOwner);
        }
        return candidateLength < currentLength;
    }

    private static boolean applyLinkState(BeamFormerEndpoint endpoint, BeamFormerEndpoint target,
                                          IGridConnection connection, double length) {
        boolean changed = endpoint.getBeamConnection() != connection
            || endpoint.getLinkedEndpoint() != target
            || !endpoint.isBeamLinked()
            || endpoint.getBeamLength() != length;
        endpoint.setBeamConnection(connection);
        endpoint.setLinkedEndpoint(target);
        endpoint.setBeamLinked(true);
        endpoint.setBeamLength(length);
        return changed;
    }

    private static boolean clearEndpointState(BeamFormerEndpoint endpoint, BeamFormerEndpoint target) {
        boolean changed = endpoint.getBeamConnection() != null
            || endpoint.getLinkedEndpoint() != null
            || endpoint.isBeamLinked()
            || endpoint.getBeamLength() != 0.0;
        endpoint.setBeamConnection(null);
        endpoint.clearLinkedEndpoint(target);
        endpoint.setBeamLinked(false);
        endpoint.setBeamLength(0.0);
        return changed;
    }

    private static @Nullable BeamFormerEndpoint tryReuseExistingTarget(BeamFormerEndpoint endpoint,
                                                                       @Nullable BeamFormerEndpoint previousTarget,
                                                                       @Nullable IGridConnection currentConnection) {
        if (previousTarget == null || currentConnection == null) {
            return null;
        }
        if (previousTarget.getBeamConnection() != currentConnection || !previousTarget.isBeamLinked()) {
            return null;
        }
        if (previousTarget.getBeamWorld() != endpoint.getBeamWorld() || !previousTarget.isBeamOnline()) {
            return null;
        }
        if (previousTarget.getBeamDirection() != endpoint.getBeamDirection().getOpposite()) {
            return null;
        }
        if (!hasCompatibleColor(endpoint, previousTarget)) {
            return null;
        }

        BlockPos sourcePos = endpoint.getBeamPos();
        BlockPos targetPos = previousTarget.getBeamPos();
        EnumFacing direction = endpoint.getBeamDirection();
        int distance = axisDistance(direction, targetPos.getX() - sourcePos.getX(), targetPos.getY() - sourcePos.getY(),
            targetPos.getZ() - sourcePos.getZ());
        if (distance <= 0 || distance > BeamFormerEndpoint.MAX_BEAM_LENGTH) {
            return null;
        }
        return hasClearPath(endpoint.getBeamWorld(), sourcePos, direction, distance) ? previousTarget : null;
    }

    private static @Nullable BeamFormerEndpoint scan(BeamFormerEndpoint endpoint) {
        World world = endpoint.getBeamWorld();
        EnumFacing direction = endpoint.getBeamDirection();
        MutableBlockPos cursor = new MutableBlockPos(endpoint.getBeamPos());

        for (int distance = 1; distance <= BeamFormerEndpoint.MAX_BEAM_LENGTH; distance++) {
            cursor.setPos(cursor.getX() + direction.getXOffset(), cursor.getY() + direction.getYOffset(),
                cursor.getZ() + direction.getZOffset());
            if (!world.isBlockLoaded(cursor)) {
                return null;
            }

            BeamFormerEndpoint target = getEndpoint(world, cursor, endpoint);
            if (target != null) {
                return target;
            }

            IBlockState state = world.getBlockState(cursor);
            if (state.isOpaqueCube()) {
                return null;
            }
        }

        return null;
    }

    private static boolean hasClearPath(World world, BlockPos origin, EnumFacing direction, int distance) {
        MutableBlockPos cursor = new MutableBlockPos(origin);
        for (int step = 1; step < distance; step++) {
            cursor.setPos(cursor.getX() + direction.getXOffset(), cursor.getY() + direction.getYOffset(),
                cursor.getZ() + direction.getZOffset());
            if (!world.isBlockLoaded(cursor) || world.getBlockState(cursor).isOpaqueCube()) {
                return false;
            }
        }
        return true;
    }

    private static int axisDistance(EnumFacing direction, int offsetX, int offsetY, int offsetZ) {
        return switch (direction) {
            case DOWN -> offsetY < 0 && offsetX == 0 && offsetZ == 0 ? -offsetY : -1;
            case UP -> offsetY > 0 && offsetX == 0 && offsetZ == 0 ? offsetY : -1;
            case NORTH -> offsetZ < 0 && offsetX == 0 && offsetY == 0 ? -offsetZ : -1;
            case SOUTH -> offsetZ > 0 && offsetX == 0 && offsetY == 0 ? offsetZ : -1;
            case WEST -> offsetX < 0 && offsetY == 0 && offsetZ == 0 ? -offsetX : -1;
            case EAST -> offsetX > 0 && offsetY == 0 && offsetZ == 0 ? offsetX : -1;
        };
    }

    private static BeamFormerEndpoint getEndpoint(World world, BlockPos pos, BeamFormerEndpoint endpoint) {
        TileEntity tile = world.getTileEntity(pos);
        if (endpoint.getBeamType() == BeamFormerType.DENSE_BLOCK && tile instanceof TileDenseBeamFormer beamFormer
            && beamFormer.getBeamDirection() == endpoint.getBeamDirection().getOpposite()) {
            return beamFormer;
        }
        if (endpoint.getBeamType() == BeamFormerType.PART && tile instanceof TileCableBus cableBus) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (cableBus.getPart(facing) instanceof BeamFormerPart beamFormer
                    && beamFormer.getBeamDirection() == endpoint.getBeamDirection().getOpposite()) {
                    return beamFormer;
                }
            }
        }
        return null;
    }

}
