package ae2.me;

import ae2.core.AppEngBase;
import ae2.me.netdata.LinkFlag;
import ae2.me.netdata.NodeFlag;
import ae2.me.netdata.State;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NetworkData {
    public static final int MAX_NETWORK_NODES = 262_144;
    public static final int MAX_NETWORK_LINKS = 524_288;
    public static final NetworkData EMPTY = new NetworkData(new ANode[0], new ALink[0]);

    public ANode[] nodes;
    public ALink[] links;
    private boolean corrupt;
    private final Object2IntMap<ANode> nodeMap = new Object2IntOpenHashMap<>();

    public NetworkData(ANode[] nodes, ALink[] links) {
        this.nodes = nodes;
        this.links = links;
        for (int i = 0; i < nodes.length; i++) {
            this.nodeMap.put(nodes[i], i);
        }
    }

    private NetworkData() {
    }

    public int countNode(NodeFlag type) {
        if (corrupt || nodes == null) {
            return 0;
        }
        int count = 0;
        for (ANode node : nodes) {
            if (node.state().get() == type) {
                count++;
            }
        }
        return count;
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    public static NetworkData read(ByteBuf buf) {
        NetworkData data = new NetworkData();
        try (var stream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteBufInputStream(buf))))) {
            int nodeCount = stream.readInt();
            if (nodeCount < 0 || nodeCount > MAX_NETWORK_NODES) {
                throw new IOException("Invalid network node count: " + nodeCount);
            }
            data.nodes = new ANode[nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                data.nodes[i] = new ANode(BlockPos.fromLong(stream.readLong()),
                    new State<>(NodeFlag.byIndex(stream.readByte())));
            }
            int linkCount = stream.readInt();
            if (linkCount < 0 || linkCount > MAX_NETWORK_LINKS) {
                throw new IOException("Invalid network link count: " + linkCount);
            }
            data.links = new ALink[linkCount];
            for (int i = 0; i < linkCount; i++) {
                int a = stream.readInt();
                int b = stream.readInt();
                if (a < 0 || a >= nodeCount || b < 0 || b >= nodeCount) {
                    throw new IOException("Invalid network link node index: " + a + " -> " + b);
                }
                data.links[i] = new ALink(data.nodes[a], data.nodes[b], stream.readShort(),
                    new State<>(LinkFlag.byIndex(stream.readByte())));
            }
        } catch (IOException | RuntimeException e) {
            AppEngBase.LOGGER.error("Fail to analyse the network. The packet is corrupted!", e);
            data.corrupt = true;
            data.nodes = new ANode[0];
            data.links = new ALink[0];
        }
        return data;
    }

    public void write(ByteBuf buf) {
        try (var stream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new ByteBufOutputStream(buf))))) {
            stream.writeInt(nodes.length);
            for (ANode node : nodes) {
                stream.writeLong(node.pos().toLong());
                stream.writeByte(node.state().get().ordinal());
            }
            stream.writeInt(links.length);
            for (ALink link : links) {
                stream.writeInt(nodeMap.getInt(link.a()));
                stream.writeInt(nodeMap.getInt(link.b()));
                stream.writeShort(link.channel());
                stream.writeByte(link.state().get().ordinal());
            }
        } catch (IOException | RuntimeException e) {
            AppEngBase.LOGGER.error("Fail to write network analyser data.", e);
            corrupt = true;
        }
    }

    public record ANode(BlockPos pos, State<NodeFlag> state) {
    }

    public record ALink(ANode a, ANode b, short channel, State<LinkFlag> state) {
    }
}
