package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class QuickMovePatternPacket extends ServerboundPacket {
    private int windowId;
    private int clickedSlot;
    private LongList allowedPatternContainerIds = LongLists.emptyList();
    private LongList allowedPatternSlots = LongLists.emptyList();

    public QuickMovePatternPacket() {
    }

    public QuickMovePatternPacket(int windowId, int clickedSlot, LongList allowedPatternContainerIds,
                                  LongList allowedPatternSlots) {
        this.windowId = windowId;
        this.clickedSlot = clickedSlot;
        this.allowedPatternContainerIds = new LongArrayList(allowedPatternContainerIds);
        this.allowedPatternSlots = new LongArrayList(allowedPatternSlots);
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.clickedSlot = packetBuffer.readVarInt();
        int targetCount = packetBuffer.readVarInt();
        var containerIds = new LongArrayList(targetCount);
        var slots = new LongArrayList(targetCount);
        for (int i = 0; i < targetCount; i++) {
            containerIds.add(packetBuffer.readVarLong());
            slots.add(packetBuffer.readVarInt());
        }
        this.allowedPatternContainerIds = containerIds;
        this.allowedPatternSlots = slots;
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeVarInt(this.clickedSlot);
        int targets = Math.min(this.allowedPatternContainerIds.size(), this.allowedPatternSlots.size());
        packetBuffer.writeVarInt(targets);
        for (int i = 0; i < targets; i++) {
            packetBuffer.writeVarLong(this.allowedPatternContainerIds.getLong(i));
            packetBuffer.writeVarInt((int) this.allowedPatternSlots.getLong(i));
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer.windowId == this.windowId
            && player.openContainer instanceof ContainerPatternAccessTerm container) {
            container.quickMovePattern(
                player,
                this.clickedSlot,
                this.allowedPatternContainerIds,
                this.allowedPatternSlots);
        }
    }
}
