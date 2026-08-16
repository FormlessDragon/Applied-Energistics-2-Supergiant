package ae2.core.network.serverbound;

import ae2.container.me.patternaccess.IPatternAccess;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.network.PacketBuffer;

public class QuickMovePatternPacket extends ServerboundPacket {
    // The client sends at most visibleRows * 9 targets; 1024 covers extreme GUI sizes while bounding packet work.
    private static final int MAX_TARGETS = 1024;

    private int windowId;
    private int clickedSlot;
    private boolean invalidTargetCount;
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
        if (targetCount < 0 || targetCount > MAX_TARGETS) {
            this.invalidTargetCount = true;
            this.allowedPatternContainerIds = LongLists.emptyList();
            this.allowedPatternSlots = LongLists.emptyList();
            invalidateMalformed(buf, new IllegalArgumentException(
                "Quick move pattern target count out of bounds: " + targetCount));
            return;
        }
        var containerIds = new LongArrayList(targetCount);
        var slots = new LongArrayList(targetCount);
        for (int i = 0; i < targetCount; i++) {
            containerIds.add(packetBuffer.readVarLong());
            slots.add(packetBuffer.readVarInt());
        }
        this.allowedPatternContainerIds = containerIds;
        this.allowedPatternSlots = slots;
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing quick move pattern payload bytes: "
                + packetBuffer.readableBytes());
        }
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
        if (this.invalidTargetCount) {
            return;
        }
        Container openContainer = player.openContainer;
        if (!(openContainer instanceof IPatternAccess container)) {
            return;
        }
        if (openContainer.windowId != this.windowId) {
            return;
        }
        if (this.clickedSlot < 0 || this.clickedSlot >= openContainer.inventorySlots.size()) {
            return;
        }
        Slot sourceSlot = openContainer.getSlot(this.clickedSlot);
        container.quickMovePattern(
            player,
            sourceSlot,
            this.allowedPatternContainerIds,
            this.allowedPatternSlots);
    }
}
