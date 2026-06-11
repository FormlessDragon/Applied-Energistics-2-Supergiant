package ae2.items.tools;

import ae2.me.AnalyserMode;
import ae2.me.netdata.FlagType;
import ae2.me.netdata.LinkFlag;
import ae2.me.netdata.NodeFlag;
import ae2.util.ColorData;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

public record NetworkAnalyserConfig(AnalyserMode mode, float nodeSize, Map<Enum<?>, ColorData> colors) {
    private static final String TAG_MODE = "mode";
    private static final String TAG_NODE_SIZE = "nodeSize";
    private static final String TAG_COLORS = "colors";

    public static final Reference2ObjectMap<Enum<?>, ColorData> DEFAULT_COLORS = new Reference2ObjectOpenHashMap<>();
    public static final NetworkAnalyserConfig DEFAULT;

    static {
        DEFAULT_COLORS.put(NodeFlag.NORMAL, new ColorData(0.8f, 0f, 0f, 1f));
        DEFAULT_COLORS.put(NodeFlag.DENSE, new ColorData(0.8f, 1f, 1f, 0f));
        DEFAULT_COLORS.put(NodeFlag.MISSING, new ColorData(0.8f, 1f, 0f, 0f));
        DEFAULT_COLORS.put(LinkFlag.NORMAL, new ColorData(0.8f, 0f, 0f, 1f));
        DEFAULT_COLORS.put(LinkFlag.DENSE, new ColorData(0.8f, 1f, 1f, 0f));
        DEFAULT_COLORS.put(LinkFlag.COMPRESSED, new ColorData(0.8f, 1f, 0f, 1f));
        DEFAULT = new NetworkAnalyserConfig(AnalyserMode.FULL, 0.4f, DEFAULT_COLORS);
    }

    public static NetworkAnalyserConfig read(ByteBuf buf) {
        if (buf.readableBytes() < 6) {
            return null;
        }
        int modeIndex = buf.readByte();
        AnalyserMode mode = getEnumByIndex(AnalyserMode.values(), modeIndex);
        if (mode == null) {
            return null;
        }
        float nodeSize = buf.readFloat();
        if (!Float.isFinite(nodeSize) || nodeSize < 0.1f || nodeSize > 0.9f) {
            return null;
        }
        var colors = new Reference2ObjectOpenHashMap<Enum<?>, ColorData>();
        int size = buf.readUnsignedByte();
        if (size > DEFAULT_COLORS.size()) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            if (buf.readableBytes() < 6) {
                return null;
            }
            FlagType type = getEnumByIndex(FlagType.values(), buf.readByte());
            int ordinal = buf.readByte();
            if (type == null) {
                return null;
            }
            ColorData color = ColorData.read(buf);
            switch (type) {
                case LINK -> {
                    LinkFlag flag = getEnumByIndex(LinkFlag.values(), ordinal);
                    if (flag == null) {
                        return null;
                    }
                    if (!DEFAULT_COLORS.containsKey(flag) || colors.containsKey(flag)) {
                        return null;
                    }
                    colors.put(flag, color);
                }
                case NODE -> {
                    NodeFlag flag = getEnumByIndex(NodeFlag.values(), ordinal);
                    if (flag == null) {
                        return null;
                    }
                    if (!DEFAULT_COLORS.containsKey(flag) || colors.containsKey(flag)) {
                        return null;
                    }
                    colors.put(flag, color);
                }
            }
        }
        return new NetworkAnalyserConfig(mode, nodeSize, colors);
    }

    private static <T extends Enum<T>> T getEnumByIndex(T[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : null;
    }

    public static NetworkAnalyserConfig fromTag(NBTTagCompound tag) {
        if (tag == null) {
            return DEFAULT;
        }
        AnalyserMode mode = DEFAULT.mode;
        if (tag.hasKey(TAG_MODE, 99)) {
            AnalyserMode tagMode = getEnumByIndex(AnalyserMode.values(), tag.getInteger(TAG_MODE));
            if (tagMode != null) {
                mode = tagMode;
            }
        }
        float nodeSize = tag.hasKey(TAG_NODE_SIZE) ? tag.getFloat(TAG_NODE_SIZE) : DEFAULT.nodeSize;
        if (!Float.isFinite(nodeSize)) {
            nodeSize = DEFAULT.nodeSize;
        }
        var colors = new Reference2ObjectOpenHashMap<Enum<?>, ColorData>();
        colors.putAll(DEFAULT_COLORS);
        if (tag.hasKey(TAG_COLORS, 10)) {
            NBTTagCompound colorTag = tag.getCompoundTag(TAG_COLORS);
            readColor(colorTag, colors, LinkFlag.NORMAL, "link_normal");
            readColor(colorTag, colors, LinkFlag.DENSE, "link_dense");
            readColor(colorTag, colors, LinkFlag.COMPRESSED, "link_compressed");
            readColor(colorTag, colors, NodeFlag.NORMAL, "node_normal");
            readColor(colorTag, colors, NodeFlag.DENSE, "node_dense");
            readColor(colorTag, colors, NodeFlag.MISSING, "node_missing");
        }
        return new NetworkAnalyserConfig(mode, Math.clamp(nodeSize, 0.1f, 0.9f), colors);
    }

    private static void readColor(NBTTagCompound tag, Reference2ObjectMap<Enum<?>, ColorData> colors, Enum<?> key,
                                  String name) {
        if (tag.hasKey(name, 10)) {
            colors.put(key, ColorData.fromTag(tag.getCompoundTag(name), colors.get(key)));
        }
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_MODE, mode.ordinal());
        tag.setFloat(TAG_NODE_SIZE, nodeSize);
        NBTTagCompound colorTag = new NBTTagCompound();
        writeColor(colorTag, LinkFlag.NORMAL, "link_normal");
        writeColor(colorTag, LinkFlag.DENSE, "link_dense");
        writeColor(colorTag, LinkFlag.COMPRESSED, "link_compressed");
        writeColor(colorTag, NodeFlag.NORMAL, "node_normal");
        writeColor(colorTag, NodeFlag.DENSE, "node_dense");
        writeColor(colorTag, NodeFlag.MISSING, "node_missing");
        tag.setTag(TAG_COLORS, colorTag);
        return tag;
    }

    public void write(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        buf.writeFloat(nodeSize);
        buf.writeByte(DEFAULT_COLORS.size());
        for (Enum<?> type : DEFAULT_COLORS.keySet()) {
            if (type instanceof LinkFlag) {
                buf.writeByte(FlagType.LINK.ordinal());
            } else {
                buf.writeByte(FlagType.NODE.ordinal());
            }
            buf.writeByte(type.ordinal());
            ColorData color = colors.get(type);
            if (color == null) {
                color = DEFAULT_COLORS.get(type);
            }
            color.write(buf);
        }
    }

    private void writeColor(NBTTagCompound tag, Enum<?> key, String name) {
        ColorData color = colors.get(key);
        if (color != null) {
            tag.setTag(name, color.toTag());
        }
    }
}
