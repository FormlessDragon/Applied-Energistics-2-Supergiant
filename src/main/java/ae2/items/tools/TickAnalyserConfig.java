package ae2.items.tools;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public record TickAnalyserConfig(
    int duration,
    boolean showBelow5Micros,
    boolean show5To100Micros,
    boolean show100To500Micros,
    boolean showAbove500Micros) {
    public static final int MAX_DURATION_SECONDS = 600;
    public static final TickAnalyserConfig DEFAULT = new TickAnalyserConfig(60, true, true, true, true);

    public static TickAnalyserConfig read(ByteBuf buf) {
        return new TickAnalyserConfig(clampDurationSeconds(buf.readInt()), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static TickAnalyserConfig fromTag(NBTTagCompound tag) {
        if (tag == null) {
            return DEFAULT;
        }
        return new TickAnalyserConfig(clampDurationSeconds(tag.getInteger("duration")),
            !tag.hasKey("op1") || tag.getBoolean("op1"),
            !tag.hasKey("op2") || tag.getBoolean("op2"),
            !tag.hasKey("op3") || tag.getBoolean("op3"),
            !tag.hasKey("op4") || tag.getBoolean("op4"));
    }

    public static int clampDurationSeconds(int duration) {
        return Math.clamp(duration, 1, MAX_DURATION_SECONDS);
    }

    public void write(ByteBuf buf) {
        buf.writeInt(clampDurationSeconds(duration));
        buf.writeBoolean(showBelow5Micros);
        buf.writeBoolean(show5To100Micros);
        buf.writeBoolean(show100To500Micros);
        buf.writeBoolean(showAbove500Micros);
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("duration", clampDurationSeconds(duration));
        tag.setBoolean("op1", showBelow5Micros);
        tag.setBoolean("op2", show5To100Micros);
        tag.setBoolean("op3", show100To500Micros);
        tag.setBoolean("op4", showAbove500Micros);
        return tag;
    }
}
