package ae2.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public record ColorData(float alpha, float red, float green, float blue) {

    public ColorData(float red, float green, float blue) {
        this(1.0f, red, green, blue);
    }

    public ColorData(int argb) {
        this(((argb >>> 24) & 0xFF) / 255.0f,
            ((argb >>> 16) & 0xFF) / 255.0f,
            ((argb >>> 8) & 0xFF) / 255.0f,
            (argb & 0xFF) / 255.0f);
    }

    public int toARGB() {
        int a = Math.clamp(Math.round(alpha * 255.0f), 0, 255);
        int r = Math.clamp(Math.round(red * 255.0f), 0, 255);
        int g = Math.clamp(Math.round(green * 255.0f), 0, 255);
        int b = Math.clamp(Math.round(blue * 255.0f), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static ColorData read(ByteBuf buf) {
        return new ColorData(buf.readInt());
    }

    public void write(ByteBuf buf) {
        buf.writeInt(toARGB());
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("argb", toARGB());
        return tag;
    }

    public static ColorData fromTag(NBTTagCompound tag, ColorData fallback) {
        return tag.hasKey("argb") ? new ColorData(tag.getInteger("argb")) : fallback;
    }
}
