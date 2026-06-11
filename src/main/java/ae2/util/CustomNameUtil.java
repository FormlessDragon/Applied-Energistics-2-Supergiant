package ae2.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

public final class CustomNameUtil {
    public static final String CUSTOM_NAME_TAG = "customName";
    private static final String DISPLAY_TAG = "display";
    private static final String DISPLAY_NAME_TAG = "Name";
    private static final int MAX_SYNC_CUSTOM_NAME_LENGTH = 256;

    private CustomNameUtil() {
    }

    public static @Nullable String normalize(@Nullable String name) {
        return name == null || name.isEmpty() ? null : name;
    }

    public static @Nullable String getDisplayName(ItemStack stack) {
        if (!stack.hasDisplayName()) {
            return null;
        }

        NBTTagCompound display = stack.getSubCompound(DISPLAY_TAG);
        if (display == null || !display.hasKey(DISPLAY_NAME_TAG, Constants.NBT.TAG_STRING)) {
            return null;
        }

        return normalize(display.getString(DISPLAY_NAME_TAG));
    }

    public static void writeNullableString(PacketBuffer data, @Nullable String value) {
        data.writeBoolean(value != null);
        if (value != null) {
            data.writeString(clampSyncCustomName(value));
        }
    }

    public static @Nullable String readNullableString(PacketBuffer data) {
        if (!data.readBoolean()) {
            return null;
        }
        return normalize(data.readString(MAX_SYNC_CUSTOM_NAME_LENGTH));
    }

    public static void writeNullableString(ByteBuf data, @Nullable String value) {
        writeNullableString(new PacketBuffer(data), value);
    }

    public static @Nullable String readNullableString(ByteBuf data) {
        return readNullableString(new PacketBuffer(data));
    }

    private static String clampSyncCustomName(String value) {
        return value.length() <= MAX_SYNC_CUSTOM_NAME_LENGTH ? value : value.substring(0, MAX_SYNC_CUSTOM_NAME_LENGTH);
    }
}
