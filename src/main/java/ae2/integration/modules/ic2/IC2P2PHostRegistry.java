package ae2.integration.modules.ic2;

import ae2.integration.abstraction.IC2P2PTunnel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

final class IC2P2PHostRegistry {
    private final Map<Key, Entry> entries = new Object2ObjectOpenHashMap<>();

    void attachOrUpdate(World world, BlockPos pos, IC2P2PTunnel tunnel) {
        Key key = new Key(world, pos);
        Entry entry = this.entries.get(key);
        if (entry == null) {
            entry = new Entry(world, pos);
            this.entries.put(key, entry);
            entry.host.onLoad();
        }

        entry.tunnels.put(tunnel.getIc2Facing(), tunnel);
        entry.host.update();
    }

    void detach(World world, BlockPos pos, IC2P2PTunnel tunnel) {
        Key key = new Key(world, pos);
        Entry entry = this.entries.get(key);
        if (entry == null) {
            return;
        }

        entry.tunnels.remove(tunnel.getIc2Facing(), tunnel);
        if (entry.tunnels.isEmpty()) {
            entry.host.invalidate();
            this.entries.remove(key);
        } else {
            entry.host.update();
        }
    }

    private static final class Entry {
        final EnumMap<EnumFacing, IC2P2PTunnel> tunnels = new EnumMap<>(EnumFacing.class);
        final IC2CableBusP2PHostAdapter host;

        Entry(World world, BlockPos pos) {
            this.host = new IC2CableBusP2PHostAdapter(world, pos, this.tunnels);
        }
    }

    private record Key(World world, BlockPos pos) {
    }
}
