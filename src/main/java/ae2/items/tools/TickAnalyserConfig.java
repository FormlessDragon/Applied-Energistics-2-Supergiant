package ae2.items.tools;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public record TickAnalyserConfig(int duration, boolean op1, boolean op2, boolean op3, boolean op4) {
    public static final TickAnalyserConfig DEFAULT = new TickAnalyserConfig(60, true, true, true, true);

    public static TickAnalyserConfig read(ByteBuf buf) {
        return new TickAnalyserConfig(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean());
    }

    public void write(ByteBuf buf) {
        buf.writeInt(duration);
        buf.writeBoolean(op1);
        buf.writeBoolean(op2);
        buf.writeBoolean(op3);
        buf.writeBoolean(op4);
    }

    public static TickAnalyserConfig fromTag(NBTTagCompound tag) {
        if (tag == null) {
            return DEFAULT;
        }
        return new TickAnalyserConfig(Math.max(1, tag.getInteger("duration")),
            !tag.hasKey("op1") || tag.getBoolean("op1"),
            !tag.hasKey("op2") || tag.getBoolean("op2"),
            !tag.hasKey("op3") || tag.getBoolean("op3"),
            !tag.hasKey("op4") || tag.getBoolean("op4"));
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("duration", Math.max(1, duration));
        tag.setBoolean("op1", op1);
        tag.setBoolean("op2", op2);
        tag.setBoolean("op3", op3);
        tag.setBoolean("op4", op4);
        return tag;
    }
}
