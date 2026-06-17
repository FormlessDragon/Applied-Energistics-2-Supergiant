package ae2.items.tools.advancedmemorycard;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public record AdvancedMemoryCardP2PEntry(
    int entryId,
    ResourceLocation tunnelType,
    String displayNameKey,
    @Nullable String customName,
    boolean input,
    short frequency,
    boolean missingChannel,
    boolean error,
    int dimension,
    BlockPos pos,
    @Nullable EnumFacing side) {

    private static final int MAX_DISPLAY_NAME_KEY_LENGTH = 128;
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;

    public static AdvancedMemoryCardP2PEntry read(PacketBuffer data) {
        return new AdvancedMemoryCardP2PEntry(
            data.readVarInt(),
            data.readResourceLocation(),
            data.readString(MAX_DISPLAY_NAME_KEY_LENGTH),
            readCustomName(data),
            data.readBoolean(),
            data.readShort(),
            data.readBoolean(),
            data.readBoolean(),
            data.readVarInt(),
            BlockPos.fromLong(data.readLong()),
            readSide(data));
    }

    private static @Nullable String readCustomName(PacketBuffer data) {
        String name = data.readString(MAX_CUSTOM_NAME_LENGTH);
        return name.isEmpty() ? null : name;
    }

    private static @Nullable EnumFacing readSide(PacketBuffer data) {
        int side = data.readByte();
        return side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : null;
    }

    private static String clamp(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public void write(PacketBuffer data) {
        data.writeVarInt(entryId);
        data.writeResourceLocation(tunnelType);
        data.writeString(clamp(displayNameKey, MAX_DISPLAY_NAME_KEY_LENGTH));
        data.writeString(customName == null ? "" : clamp(customName, MAX_CUSTOM_NAME_LENGTH));
        data.writeBoolean(input);
        data.writeShort(frequency);
        data.writeBoolean(missingChannel);
        data.writeBoolean(error);
        data.writeVarInt(dimension);
        data.writeLong(pos.toLong());
        data.writeByte(side == null ? -1 : side.ordinal());
    }

    public String customNameOrEmpty() {
        return customName == null ? "" : customName;
    }
}
