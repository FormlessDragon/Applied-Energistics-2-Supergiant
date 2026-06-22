package ae2.tile.networking;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.core.definitions.AEBlocks;
import ae2.helpers.beamformer.BeamFormerEndpoint;
import ae2.helpers.beamformer.BeamFormerLinkLogic;
import ae2.helpers.beamformer.BeamFormerRenderGeometry;
import ae2.helpers.beamformer.BeamFormerType;
import ae2.tile.grid.AENetworkedTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class TileDenseBeamFormer extends AENetworkedTile implements BeamFormerEndpoint, IGridTickable, IPowerChannelState {

    private static final String TAG_BEAM_LENGTH = "beamLength";
    private static final String TAG_BEAM_COLOR = "beamColor";
    private static final String TAG_BEAM_VISIBLE = "beamVisible";
    private static final String TAG_BEAM_LINKED = "beamLinked";
    private static final int POWERED_FLAG = 1;
    private static final int LINKED_FLAG = 2;
    private static final int BEAM_VISIBLE_FLAG = 4;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 9216.0;
    private static final double DENSE_BEAM_ORIGIN_FACE_OFFSET = 2.0D / 16.0D;
    private static final AEColor[] COLORS = AEColor.values();

    @Nullable
    private IGridConnection connection;
    @Nullable
    private BeamFormerEndpoint linkedEndpoint;
    @Nullable
    private AxisAlignedBB cachedRenderBoundingBox;
    private double beamLength;
    private AEColor beamColor = AEColor.TRANSPARENT;
    private int clientFlags;
    private boolean beamVisible = true;
    private boolean beamLinked;

    public TileDenseBeamFormer() {
        this.getMainNode()
            .setFlags(GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(this.getIdlePowerUsage())
            .addService(IGridTickable.class, this);
        this.onGridConnectableSidesChanged();
    }

    private static AEColor readColor(int ordinal) {
        return ordinal >= 0 && ordinal < COLORS.length ? COLORS[ordinal] : AEColor.TRANSPARENT;
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.DENSE_BEAM_FORMER.stack();
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.markForUpdate();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(10, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.connection = BeamFormerLinkLogic.updateLink(this, this.connection);
        return this.connection == null ? TickRateModulation.SLOWER : TickRateModulation.FASTER;
    }

    @Override
    public void onChunkUnloaded() {
        BeamFormerLinkLogic.disconnect(this, this.connection);
        this.connection = null;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        BeamFormerLinkLogic.disconnect(this, this.connection);
        this.connection = null;
        super.setRemoved();
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.beamLength = data.getDouble(TAG_BEAM_LENGTH);
        this.beamColor = readColor(data.getInteger(TAG_BEAM_COLOR));
        this.beamVisible = !data.hasKey(TAG_BEAM_VISIBLE) || data.getBoolean(TAG_BEAM_VISIBLE);
        this.beamLinked = data.getBoolean(TAG_BEAM_LINKED);
        this.invalidateRenderBoundingBox();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setDouble(TAG_BEAM_LENGTH, this.beamLength);
        data.setInteger(TAG_BEAM_COLOR, this.beamColor.ordinal());
        data.setBoolean(TAG_BEAM_VISIBLE, this.beamVisible);
        data.setBoolean(TAG_BEAM_LINKED, this.beamLinked);
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        this.clientFlags = getCurrentClientFlags();
        data.writeByte(this.clientFlags);
        data.writeDouble(this.beamLength);
        data.writeByte(this.beamColor.ordinal());
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        int oldFlags = this.clientFlags;
        double oldLength = this.beamLength;
        AEColor oldColor = this.beamColor;
        this.clientFlags = data.readUnsignedByte();
        this.beamLength = data.readDouble();
        this.beamColor = readColor(data.readUnsignedByte());
        if (changed || oldFlags != this.clientFlags || oldLength != this.beamLength || oldColor != this.beamColor) {
            this.invalidateRenderBoundingBox();
        }
        return changed || oldFlags != this.clientFlags || oldLength != this.beamLength || oldColor != this.beamColor;
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setInteger("clientFlags", getCurrentClientFlags());
        data.setDouble(TAG_BEAM_LENGTH, this.beamLength);
        data.setInteger(TAG_BEAM_COLOR, this.beamColor.ordinal());
        data.setBoolean(TAG_BEAM_VISIBLE, this.beamVisible);
        data.setBoolean(TAG_BEAM_LINKED, this.beamLinked);
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        this.clientFlags = data.getInteger("clientFlags");
        this.beamLength = data.getDouble(TAG_BEAM_LENGTH);
        this.beamColor = readColor(data.getInteger(TAG_BEAM_COLOR));
        this.beamVisible = !data.hasKey(TAG_BEAM_VISIBLE) || data.getBoolean(TAG_BEAM_VISIBLE);
        this.beamLinked = data.getBoolean(TAG_BEAM_LINKED);
        this.invalidateRenderBoundingBox();
    }

    @Override
    public World getBeamWorld() {
        return getWorld();
    }

    @Override
    public BlockPos getBeamPos() {
        return getPos();
    }

    @Override
    public EnumFacing getBeamDirection() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    @Override
    public IGridNode getBeamGridNode() {
        return getGridNode();
    }

    @Override
    public IGridConnection getBeamConnection() {
        return this.connection;
    }

    @Override
    public void setBeamConnection(IGridConnection connection) {
        this.connection = connection;
    }

    @Override
    public BeamFormerType getBeamType() {
        return BeamFormerType.DENSE_BLOCK;
    }

    @Override
    public double getBeamOriginOffsetX() {
        return getBeamDirection().getXOffset() * DENSE_BEAM_ORIGIN_FACE_OFFSET;
    }

    @Override
    public double getBeamOriginOffsetY() {
        return getBeamDirection().getYOffset() * DENSE_BEAM_ORIGIN_FACE_OFFSET;
    }

    @Override
    public double getBeamOriginOffsetZ() {
        return getBeamDirection().getZOffset() * DENSE_BEAM_ORIGIN_FACE_OFFSET;
    }

    @Override
    public AEColor getBeamColor() {
        if (getWorld() != null && !getWorld().isRemote) {
            return getConnectedCableColor();
        }
        return this.beamColor;
    }

    @Override
    public void setBeamColor(AEColor color) {
        if (this.beamColor != color) {
            this.beamColor = color;
            this.onBeamChanged();
        }
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGridColor() != color) {
            this.getMainNode().setGridColor(color);
        }
    }

    @Override
    public double getBeamLength() {
        return this.beamLength;
    }

    @Override
    public void setBeamLength(double length) {
        if (this.beamLength != length) {
            this.beamLength = length;
            this.invalidateRenderBoundingBox();
        }
    }

    @Override
    public @Nullable BeamFormerEndpoint getLinkedEndpoint() {
        return this.linkedEndpoint;
    }

    @Override
    public void setLinkedEndpoint(@Nullable BeamFormerEndpoint endpoint) {
        this.linkedEndpoint = endpoint;
    }

    @Override
    public void clearLinkedEndpoint(BeamFormerEndpoint endpoint) {
        if (endpoint == null || this.linkedEndpoint == endpoint) {
            this.linkedEndpoint = null;
        }
    }

    @Override
    public boolean isBeamVisible() {
        if (getWorld() != null && getWorld().isRemote) {
            return (this.clientFlags & BEAM_VISIBLE_FLAG) != 0;
        }
        return this.beamVisible;
    }

    @Override
    public void setBeamVisible(boolean beamVisible) {
        if (this.beamVisible != beamVisible) {
            this.beamVisible = beamVisible;
            this.invalidateRenderBoundingBox();
            if (this.linkedEndpoint != null && this.linkedEndpoint.isBeamVisible() != beamVisible) {
                this.linkedEndpoint.setBeamVisible(beamVisible);
            }
            this.onBeamChanged();
        }
    }

    @Override
    public boolean isBeamLinked() {
        if (getWorld() != null && getWorld().isRemote) {
            return (this.clientFlags & LINKED_FLAG) != 0;
        }
        return this.beamLinked;
    }

    @Override
    public void setBeamLinked(boolean linked) {
        if (this.beamLinked != linked) {
            this.beamLinked = linked;
            this.invalidateRenderBoundingBox();
        }
    }

    @Override
    public double getIdlePowerUsage() {
        return 40;
    }

    @Override
    public void onBeamChanged() {
        this.invalidateRenderBoundingBox();
        this.markForUpdate();
        this.saveChanges();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (this.cachedRenderBoundingBox == null) {
            this.cachedRenderBoundingBox = BeamFormerRenderGeometry.computeRenderBoundingBox(this);
        }
        return this.cachedRenderBoundingBox;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return MAX_RENDER_DISTANCE_SQUARED;
    }

    public boolean isPowered() {
        if (getWorld() != null && getWorld().isRemote) {
            return (this.clientFlags & POWERED_FLAG) != 0;
        }
        IGridNode node = getGridNode();
        return node != null && node.isPowered();
    }

    public boolean isActive() {
        if (getWorld() != null && getWorld().isRemote) {
            return this.isPowered();
        }
        IGridNode node = getGridNode();
        return node != null && node.isActive();
    }

    public boolean isLinked() {
        return this.isBeamLinked();
    }

    private void invalidateRenderBoundingBox() {
        this.cachedRenderBoundingBox = null;
    }

    private int getCurrentClientFlags() {
        int flags = 0;
        if (this.isPowered()) {
            flags |= POWERED_FLAG;
        }
        if (this.beamLinked) {
            flags |= LINKED_FLAG;
        }
        if (this.beamVisible) {
            flags |= BEAM_VISIBLE_FLAG;
        }
        return flags;
    }

    private AEColor getConnectedCableColor() {
        EnumFacing back = getOrientation().getSide(RelativeSide.BACK);
        BlockPos cablePos = getPos().offset(back);
        if (getWorld() == null || !getWorld().isBlockLoaded(cablePos)) {
            return AEColor.TRANSPARENT;
        }

        IInWorldGridNodeHost host = GridHelper.getNodeHost(getWorld(), cablePos);
        if (host instanceof TileCableBus cableBus) {
            return cableBus.getColor();
        }

        IGridNode node = GridHelper.getExposedNode(getWorld(), cablePos, back.getOpposite());
        return node == null ? AEColor.TRANSPARENT : node.getGridColor();
    }
}
