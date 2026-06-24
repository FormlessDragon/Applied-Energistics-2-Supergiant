package ae2.core.network.clientbound;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.client.gui.me.patternaccess.GuiPatternAccessTerm;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

public class PatternAccessTerminalPacket extends ClientboundPacket {
    private static final int MAX_SLOT_UPDATES = 4096;
    private static final int MAX_INVENTORY_SIZE = 4096;

    private boolean fullUpdate;
    private long inventoryId;
    private int inventorySize;
    private long sortBy;
    private boolean canEditTerminalName;
    private boolean canModifyTerminalVisibility;
    private PatternContainerGroup group;
    private Int2ObjectMap<ItemStack> slots = new Int2ObjectOpenHashMap<>();

    public PatternAccessTerminalPacket() {
    }

    private PatternAccessTerminalPacket(boolean fullUpdate, long inventoryId, int inventorySize, long sortBy,
                                        boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                        PatternContainerGroup group,
                                        Int2ObjectMap<ItemStack> slots) {
        this.fullUpdate = fullUpdate;
        this.inventoryId = inventoryId;
        this.inventorySize = inventorySize;
        this.sortBy = sortBy;
        this.canEditTerminalName = canEditTerminalName;
        this.canModifyTerminalVisibility = canModifyTerminalVisibility;
        this.group = group;
        this.slots = slots;
    }

    public static PatternAccessTerminalPacket fullUpdate(long inventoryId, int inventorySize, long sortBy,
                                                         boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                                         PatternContainerGroup group,
                                                         Int2ObjectMap<ItemStack> slots) {
        return new PatternAccessTerminalPacket(true, inventoryId, inventorySize, sortBy, canEditTerminalName,
            canModifyTerminalVisibility, group, slots);
    }

    public static PatternAccessTerminalPacket incrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots) {
        return new PatternAccessTerminalPacket(false, inventoryId, 0, 0, false, false, null, slots);
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.inventoryId = packetBuffer.readVarLong();
        this.fullUpdate = packetBuffer.readBoolean();
        if (this.fullUpdate) {
            this.inventorySize = packetBuffer.readVarInt();
            if (this.inventorySize < 0 || this.inventorySize > MAX_INVENTORY_SIZE) {
                throw new IllegalArgumentException("Invalid pattern access terminal inventory size: "
                    + this.inventorySize);
            }
            this.sortBy = packetBuffer.readVarLong();
            this.canEditTerminalName = packetBuffer.readBoolean();
            this.canModifyTerminalVisibility = packetBuffer.readBoolean();
            this.group = PatternContainerGroup.readFromPacket(packetBuffer);
        }

        var slotCount = packetBuffer.readVarInt();
        if (slotCount < 0 || slotCount > MAX_SLOT_UPDATES) {
            throw new IllegalArgumentException("Invalid pattern access terminal slot count: " + slotCount);
        }
        this.slots = new Int2ObjectOpenHashMap<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            var slot = packetBuffer.readVarInt();
            if (slot < 0 || (this.fullUpdate && slot >= this.inventorySize)) {
                throw new IllegalArgumentException("Invalid pattern access terminal slot index: " + slot);
            }
            try {
                this.slots.put(slot, packetBuffer.readItemStack());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read pattern access terminal slot", e);
            }
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarLong(this.inventoryId);
        packetBuffer.writeBoolean(this.fullUpdate);
        if (this.fullUpdate) {
            packetBuffer.writeVarInt(this.inventorySize);
            packetBuffer.writeVarLong(this.sortBy);
            packetBuffer.writeBoolean(this.canEditTerminalName);
            packetBuffer.writeBoolean(this.canModifyTerminalVisibility);
            this.group.writeToPacket(packetBuffer);
        }

        packetBuffer.writeVarInt(this.slots.size());
        for (var entry : this.slots.int2ObjectEntrySet()) {
            packetBuffer.writeVarInt(entry.getIntKey());
            packetBuffer.writeItemStack(entry.getValue());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof GuiPatternAccessTerm<?> patternAccessTerminal) {
            if (this.fullUpdate) {
                patternAccessTerminal.postFullUpdate(this.inventoryId, this.sortBy, this.canEditTerminalName,
                    this.canModifyTerminalVisibility,
                    this.group, this.inventorySize, this.slots);
            } else {
                patternAccessTerminal.postIncrementalUpdate(this.inventoryId, this.slots);
            }
        }
    }
}
