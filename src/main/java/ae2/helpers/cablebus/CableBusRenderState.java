package ae2.helpers.cablebus;

import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.util.collections.Enum2IntMap;
import ae2.util.collections.Enum2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class CableBusRenderState {

    private final EnumMap<EnumFacing, IPartModel> attachments = new EnumMap<>(EnumFacing.class);
    private final Enum2IntMap<EnumFacing> attachmentConnections = new Enum2IntMap<>(EnumFacing.class);
    private final EnumMap<EnumFacing, FacadeRenderState> facades = new EnumMap<>(EnumFacing.class);
    private final List<AxisAlignedBB> boundingBoxes = new ObjectArrayList<>();
    private final EnumMap<EnumFacing, Object> partModelData = new EnumMap<>(EnumFacing.class);
    private final Enum2IntMap<EnumFacing> attachmentSpins = new Enum2IntMap<>(EnumFacing.class);
    private final Enum2LongMap<EnumFacing> partFlags = new Enum2LongMap<>(EnumFacing.class);
    private final Enum2IntMap<EnumFacing> channelsOnSide = new Enum2IntMap<>(EnumFacing.class);
    private AECableType cableType = AECableType.NONE;
    private CableCoreType coreType;
    private AEColor cableColor = AEColor.TRANSPARENT;
    private EnumMap<EnumFacing, AECableType> connectionTypes = new EnumMap<>(EnumFacing.class);
    private EnumSet<EnumFacing> cableBusAdjacent = EnumSet.noneOf(EnumFacing.class);
    private WeakReference<IBlockAccess> world;
    private BlockPos pos;

    public AECableType getCableType() {
        return this.cableType;
    }

    public void setCableType(AECableType cableType) {
        this.cableType = cableType;
        if (this.coreType == null || cableType == AECableType.NONE) {
            this.coreType = CableCoreType.fromCableType(cableType);
        }
    }

    public CableCoreType getCoreType() {
        return this.coreType;
    }

    public void setCoreType(CableCoreType coreType) {
        this.coreType = coreType;
    }

    public AEColor getCableColor() {
        return this.cableColor;
    }

    public void setCableColor(AEColor cableColor) {
        this.cableColor = cableColor;
    }

    public EnumMap<EnumFacing, AECableType> getConnectionTypes() {
        return this.connectionTypes;
    }

    public void setConnectionTypes(EnumMap<EnumFacing, AECableType> connectionTypes) {
        this.connectionTypes = connectionTypes;
    }

    public EnumSet<EnumFacing> getCableBusAdjacent() {
        return this.cableBusAdjacent;
    }

    public void setCableBusAdjacent(EnumSet<EnumFacing> cableBusAdjacent) {
        this.cableBusAdjacent = cableBusAdjacent;
    }

    public Reference2IntMap<EnumFacing> getChannelsOnSide() {
        return this.channelsOnSide;
    }

    public void setChannelsOnSide(Map<EnumFacing, Integer> channelsOnSide) {
        this.channelsOnSide.clear();
        this.channelsOnSide.putAll(channelsOnSide);
    }

    public EnumMap<EnumFacing, IPartModel> getAttachments() {
        return this.attachments;
    }

    public Reference2IntMap<EnumFacing> getAttachmentConnections() {
        return this.attachmentConnections;
    }

    public EnumMap<EnumFacing, FacadeRenderState> getFacades() {
        return this.facades;
    }

    @Nullable
    public IBlockAccess getWorld() {
        return this.world != null ? this.world.get() : null;
    }

    public void setWorld(IBlockAccess world) {
        this.world = new WeakReference<>(world);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public List<AxisAlignedBB> getBoundingBoxes() {
        return this.boundingBoxes;
    }

    public EnumMap<EnumFacing, Object> getPartModelData() {
        return this.partModelData;
    }

    public Reference2IntMap<EnumFacing> getAttachmentSpins() {
        return this.attachmentSpins;
    }

    public Reference2LongMap<EnumFacing> getPartFlags() {
        return this.partFlags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.attachments, this.attachmentConnections, this.cableBusAdjacent, this.cableColor, this.cableType,
            this.channelsOnSide, this.connectionTypes, this.coreType, this.partModelData);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CableBusRenderState other)) {
            return false;
        }
        return this.cableType == other.cableType
            && this.coreType == other.coreType
            && this.cableColor == other.cableColor
            && Objects.equals(this.connectionTypes, other.connectionTypes)
            && Objects.equals(this.cableBusAdjacent, other.cableBusAdjacent)
            && Objects.equals(this.attachments, other.attachments)
            && Objects.equals(this.channelsOnSide, other.channelsOnSide)
            && Objects.equals(this.attachmentConnections, other.attachmentConnections)
            && Objects.equals(this.partModelData, other.partModelData);
    }
}
