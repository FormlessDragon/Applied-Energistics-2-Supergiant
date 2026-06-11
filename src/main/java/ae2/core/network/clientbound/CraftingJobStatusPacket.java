package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.client.gui.me.common.PendingCraftingJobs;
import ae2.client.gui.me.common.PinnedKeys;
import ae2.core.AEConfig;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * Confirms to the player that a crafting job has started.
 */
public class CraftingJobStatusPacket extends ClientboundPacket {
    private UUID jobId;
    private AEKey what;
    private long requestedAmount;
    private long remainingAmount;
    private Status status;

    public CraftingJobStatusPacket() {
    }

    public CraftingJobStatusPacket(UUID jobId, AEKey what, long requestedAmount, long remainingAmount,
                                   Status status) {
        this.jobId = jobId;
        this.what = what;
        this.requestedAmount = requestedAmount;
        this.remainingAmount = remainingAmount;
        this.status = status;
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
            if (this.requestedAmount < 0 || this.remainingAmount < 0) {
                throw new IllegalArgumentException("Crafting job status contains negative amounts");
            }
        } catch (RuntimeException e) {
            this.jobId = null;
            this.what = null;
            this.requestedAmount = 0;
            this.remainingAmount = 0;
            this.status = Status.CANCELLED;
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
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.what == null) {
            return;
        }

        if (this.status == Status.STARTED) {
            if (AEConfig.instance().isPinAutoCraftedItems()) {
                PinnedKeys.pinKey(this.what, PinnedKeys.PinReason.CRAFTING);
            }
        }

        PendingCraftingJobs.jobStatus(this.jobId, this.what, this.requestedAmount, this.remainingAmount, this.status);
    }

    public enum Status {
        STARTED,
        CANCELLED,
        FINISHED
    }
}
