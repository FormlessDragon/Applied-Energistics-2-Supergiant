package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.client.gui.me.common.PendingCraftingJobs;
import ae2.client.gui.me.common.PinnedKeys;
import ae2.core.AEConfig;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.UUID;

/**
 * Confirms to the player that a crafting job has started.
 */
public class CraftingJobStatusPacket extends ClientboundPacket {
    /**
     * Upper bound for the items a single started job may make the terminal track.
     */
    private static final int MAX_TRACKED_KEYS = 32;

    private UUID jobId;
    private AEKey what;
    private long requestedAmount;
    private long remainingAmount;
    private Status status;
    private boolean showFinishedToast;
    /**
     * The items this job makes instead of {@link #what}. The terminal pins them in its own row on top of the item list
     * and treats them as in progress until the job finishes. A batch order of the crafting terminal orders the
     * materials a recipe was missing, so those are the items it crafts, while {@link #what} is only a placeholder
     * output that nothing is crafted into.
     */
    private List<AEKey> trackedKeys = List.of();

    public CraftingJobStatusPacket() {
    }

    public CraftingJobStatusPacket(UUID jobId, AEKey what, long requestedAmount, long remainingAmount,
                                   Status status, boolean showFinishedToast) {
        this(jobId, what, requestedAmount, remainingAmount, status, showFinishedToast, List.of());
    }

    public CraftingJobStatusPacket(UUID jobId, AEKey what, long requestedAmount, long remainingAmount,
                                   Status status, boolean showFinishedToast, List<AEKey> trackedKeys) {
        this.jobId = jobId;
        this.what = what;
        this.requestedAmount = requestedAmount;
        this.remainingAmount = remainingAmount;
        this.status = status;
        this.showFinishedToast = showFinishedToast;
        this.trackedKeys = List.copyOf(trackedKeys);
    }

    @Override
    protected void read(ByteBuf buf) {
        try {
            var data = new PacketBuffer(buf);
            this.jobId = data.readUniqueId();
            this.status = NetworkPacketHelper.readEnumOrNull(data, Status.class);
            if (this.status == null) {
                this.status = Status.CANCELLED;
            }
            this.what = AEKey.readKey(data);
            this.requestedAmount = data.readLong();
            this.remainingAmount = data.readLong();
            this.showFinishedToast = data.readBoolean();
            int trackedCount = data.readVarInt();
            if (trackedCount < 0 || trackedCount > MAX_TRACKED_KEYS) {
                throw new IllegalArgumentException("Invalid pinned key count: " + trackedCount);
            }
            var trackedKeys = new ObjectArrayList<AEKey>(trackedCount);
            for (int i = 0; i < trackedCount; i++) {
                var key = AEKey.readKey(data);
                if (key != null) {
                    trackedKeys.add(key);
                }
            }
            this.trackedKeys = trackedKeys;
            if (this.requestedAmount < 0 || this.remainingAmount < 0) {
                throw new IllegalArgumentException("Crafting job status contains negative amounts");
            }
        } catch (RuntimeException e) {
            this.jobId = null;
            this.what = null;
            this.requestedAmount = 0;
            this.remainingAmount = 0;
            this.status = Status.CANCELLED;
            this.showFinishedToast = false;
            this.trackedKeys = List.of();
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeUniqueId(this.jobId);
        data.writeEnumValue(this.status);
        AEKey.writeKey(data, this.what);
        data.writeLong(this.requestedAmount);
        data.writeLong(this.remainingAmount);
        data.writeBoolean(this.showFinishedToast);
        data.writeVarInt(this.trackedKeys.size());
        for (var pinnedKey : this.trackedKeys) {
            AEKey.writeKey(data, pinnedKey);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.what == null) {
            return;
        }

        if (this.status == Status.STARTED && AEConfig.instance().isPinAutoCraftedItems()) {
            if (this.trackedKeys.isEmpty()) {
                PinnedKeys.pinKey(this.what, PinnedKeys.PinReason.CRAFTING);
            } else {
                for (var trackedKey : this.trackedKeys) {
                    PinnedKeys.pinKey(trackedKey, PinnedKeys.PinReason.CRAFTING);
                }
            }
        }

        PendingCraftingJobs.jobStatus(this.jobId, this.what, this.requestedAmount, this.remainingAmount, this.status,
            this.showFinishedToast, this.trackedKeys);
    }

    public enum Status {
        STARTED,
        CANCELLED,
        FINISHED
    }
}
