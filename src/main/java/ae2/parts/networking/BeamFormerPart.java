package ae2.parts.networking;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.BusSupport;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.client.render.BeamFormerRenderer;
import ae2.core.AppEng;
import ae2.helpers.beamformer.BeamFormerEndpoint;
import ae2.helpers.beamformer.BeamFormerLinkLogic;
import ae2.helpers.beamformer.BeamFormerRenderGeometry;
import ae2.helpers.beamformer.BeamFormerType;
import ae2.items.parts.PartModels;
import ae2.parts.AEBasePart;
import ae2.parts.PartModel;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

public class BeamFormerPart extends AEBasePart implements BeamFormerEndpoint, IGridTickable {

    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/beam_former_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/beam_former_on");
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_ON);
    private static final String TAG_BEAM_LENGTH = "beamLength";
    private static final String TAG_BEAM_COLOR = "beamColor";
    private static final String TAG_BEAM_VISIBLE = "beamVisible";
    private static final String TAG_BEAM_LINKED = "beamLinked";
    @Nullable
    private IGridConnection connection;
    @Nullable
    private BeamFormerEndpoint linkedEndpoint;
    private double beamLength;
    private AEColor clientBeamColor = AEColor.TRANSPARENT;
    private boolean beamVisible = true;
    private boolean beamLinked;

    public BeamFormerPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
            .setFlags(GridFlags.PREFERRED)
            .setIdlePowerUsage(this.getIdlePowerUsage())
            .addService(IGridTickable.class, this);
    }

    private static AEColor readColor(int ordinal) {
        AEColor[] colors = AEColor.values();
        return ordinal >= 0 && ordinal < colors.length ? colors[ordinal] : AEColor.TRANSPARENT;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(5, 5, 12, 11, 11, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public boolean canBePlacedOn(BusSupport what) {
        return what == BusSupport.CABLE || what == BusSupport.DENSE_CABLE;
    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
        if (!BeamFormerRenderGeometry.shouldRender(this)) {
            return;
        }
        BeamFormerRenderer.render(this, x, y, z);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.beamLinked ? MODELS_ON : MODELS_OFF;
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
    public void removeFromWorld() {
        BeamFormerLinkLogic.disconnect(this, this.connection);
        this.connection = null;
        super.removeFromWorld();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setDouble(TAG_BEAM_LENGTH, this.beamLength);
        data.setInteger(TAG_BEAM_COLOR, this.getBeamColor().ordinal());
        data.setBoolean(TAG_BEAM_VISIBLE, this.beamVisible);
        data.setBoolean(TAG_BEAM_LINKED, this.beamLinked);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.beamLength = data.getDouble(TAG_BEAM_LENGTH);
        this.clientBeamColor = readColor(data.getInteger(TAG_BEAM_COLOR));
        this.beamVisible = !data.hasKey(TAG_BEAM_VISIBLE) || data.getBoolean(TAG_BEAM_VISIBLE);
        this.beamLinked = data.getBoolean(TAG_BEAM_LINKED);
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeDouble(this.beamLength);
        data.writeByte(this.getBeamColor().ordinal());
        data.writeBoolean(this.beamLinked);
        data.writeBoolean(this.beamVisible);
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean changed = super.readFromStream(data);
        double oldLength = this.beamLength;
        AEColor oldColor = this.clientBeamColor;
        boolean oldBeamLinked = this.beamLinked;
        boolean oldBeamVisible = this.beamVisible;
        this.beamLength = data.readDouble();
        this.clientBeamColor = readColor(data.readUnsignedByte());
        this.beamLinked = data.readBoolean();
        this.beamVisible = data.readBoolean();
        return changed || oldLength != this.beamLength || oldColor != this.clientBeamColor
            || oldBeamLinked != this.beamLinked || oldBeamVisible != this.beamVisible;
    }

    @Override
    public World getBeamWorld() {
        return getLevel();
    }

    @Override
    public BlockPos getBeamPos() {
        return getTileEntity().getPos();
    }

    @Override
    public EnumFacing getBeamDirection() {
        EnumFacing side = getSide();
        return side == null ? EnumFacing.NORTH : side;
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
        return BeamFormerType.PART;
    }

    @Override
    public AEColor getBeamColor() {
        if (isClientSide()) {
            return this.clientBeamColor;
        }
        return getColor();
    }

    @Override
    public void setBeamColor(AEColor color) {
        AEColor oldColor = this.clientBeamColor;
        this.clientBeamColor = color;
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGridColor() != color) {
            this.getMainNode().setGridColor(color);
        }
        if (oldColor != color) {
            this.onBeamChanged();
        }
    }

    @Override
    public double getBeamLength() {
        return this.beamLength;
    }

    @Override
    public void setBeamLength(double length) {
        this.beamLength = length;
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
        return this.beamVisible;
    }

    @Override
    public void setBeamVisible(boolean beamVisible) {
        if (this.beamVisible != beamVisible) {
            this.beamVisible = beamVisible;
            if (this.linkedEndpoint != null && this.linkedEndpoint.isBeamVisible() != beamVisible) {
                this.linkedEndpoint.setBeamVisible(beamVisible);
            }
            this.onBeamChanged();
        }
    }

    @Override
    public boolean isBeamLinked() {
        return this.beamLinked;
    }

    @Override
    public void setBeamLinked(boolean linked) {
        this.beamLinked = linked;
    }

    @Override
    public double getIdlePowerUsage() {
        return 10;
    }

    @Override
    public void onBeamChanged() {
        if (getHost() != null) {
            getHost().markForUpdate();
            getHost().markForSave();
        }
    }
}
