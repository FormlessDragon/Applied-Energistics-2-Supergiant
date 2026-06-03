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
        AnalyserMode mode = AnalyserMode.byIndex(buf.readByte());
        float nodeSize = buf.readFloat();
        var colors = new Reference2ObjectOpenHashMap<Enum<?>, ColorData>();
        int size = buf.readUnsignedByte();
        for (int i = 0; i < size; i++) {
            FlagType type = FlagType.byIndex(buf.readByte());
            int ordinal = buf.readByte();
            ColorData color = ColorData.read(buf);
            switch (type) {
                case LINK -> colors.put(LinkFlag.byIndex(ordinal), color);
                case NODE -> colors.put(NodeFlag.byIndex(ordinal), color);
            }
        }
        return new NetworkAnalyserConfig(mode, nodeSize, colors);
    }

    public void write(ByteBuf buf) {
        buf.writeByte(mode.ordinal());
        buf.writeFloat(nodeSize);
        buf.writeByte(colors.size());
        for (var entry : colors.entrySet()) {
            Enum<?> type = entry.getKey();
            if (type instanceof LinkFlag) {
                buf.writeByte(FlagType.LINK.ordinal());
            } else {
                buf.writeByte(FlagType.NODE.ordinal());
            }
            buf.writeByte(type.ordinal());
            entry.getValue().write(buf);
        }
    }

    public static NetworkAnalyserConfig fromTag(NBTTagCompound tag) {
        if (tag == null) {
            return DEFAULT;
        }
        AnalyserMode mode = tag.hasKey(TAG_MODE) ? AnalyserMode.byIndex(tag.getInteger(TAG_MODE)) : DEFAULT.mode;
        float nodeSize = tag.hasKey(TAG_NODE_SIZE) ? tag.getFloat(TAG_NODE_SIZE) : DEFAULT.nodeSize;
        var colors = new Reference2ObjectOpenHashMap<Enum<?>, ColorData>();
        colors.putAll(DEFAULT_COLORS);
        if (tag.hasKey(TAG_COLORS)) {
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

    private static void readColor(NBTTagCompound tag, Reference2ObjectMap<Enum<?>, ColorData> colors, Enum<?> key,
                                  String name) {
        if (tag.hasKey(name)) {
            colors.put(key, ColorData.fromTag(tag.getCompoundTag(name), colors.get(key)));
        }
    }

    private void writeColor(NBTTagCompound tag, Enum<?> key, String name) {
        ColorData color = colors.get(key);
        if (color != null) {
            tag.setTag(name, color.toTag());
        }
    }
}
