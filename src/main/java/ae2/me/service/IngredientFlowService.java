package ae2.me.service;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.stacks.AEKey;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.FlowSearchDTO;
import ae2.core.AEConfig;
import ae2.me.InWorldGridNode;
import ae2.parts.AEBasePart;
import ae2.util.JsonStreamUtil;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.LongLongMutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IngredientFlowService implements IGridService, IGridServiceProvider {

    private static final long BUCKET_SIZE_MS = 200L;
    private static final String TRACKING_ENABLED_KEY = "IngredientFlowTrackingEnabled";
    private final ArrayDeque<Bucket> buckets = new ArrayDeque<>();
    private boolean trackingEnabled = false;
    private long flowRevision;
    private long cachedFlowRevision = Long.MIN_VALUE;
    private Map<AEKey, LongLongPair> cachedAllRecentFlow = Map.of();

    private static long positiveMagnitude(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : -value;
    }

    private static long saturatingAdd(long current, long delta) {
        if (delta <= 0) {
            return current;
        }
        if (current >= Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }

    @Override
    public void saveNodeData(IGridNode gridNode, NBTTagCompound savedData) {
        savedData.setBoolean(TRACKING_ENABLED_KEY, this.trackingEnabled);
    }

    @Override
    public void onServerEndTick() {
        final long windowMs = AEConfig.instance().getIngredientFlowTrackingWindowMinutes() * 60_000L;
        final long cutoff = System.currentTimeMillis() - windowMs;
        boolean expired = false;
        while (!this.buckets.isEmpty() && this.buckets.peekFirst().start + BUCKET_SIZE_MS <= cutoff) {
            this.buckets.pollFirst();
            expired = true;
        }
        if (expired) {
            this.flowRevision++;
        }
    }

    public boolean isTrackingEnabled() {
        return this.trackingEnabled;
    }

    public void setTrackingEnabled(final boolean enabled) {
        if (this.trackingEnabled != enabled) {
            this.flowRevision++;
        }
        this.trackingEnabled = enabled;
        if (!enabled) {
            if (!this.buckets.isEmpty()) {
                this.buckets.clear();
                this.flowRevision++;
            }
        }
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        if (savedData != null && savedData.hasKey(TRACKING_ENABLED_KEY)) {
            boolean enabled = this.trackingEnabled || savedData.getBoolean(TRACKING_ENABLED_KEY);
            if (enabled != this.trackingEnabled) {
                this.trackingEnabled = enabled;
                this.flowRevision++;
            }
        }
    }

    @Override
    public void debugDump(JsonWriter writer) throws IOException {
        final Map<String, Object> flowJson = new Object2ObjectOpenHashMap<>();
        for (final Map.Entry<AEKey, LongLongPair> entry : getAllRecentFlow().entrySet()) {
            final LongLongPair rate = entry.getValue();
            final List<Object> locations = new ArrayList<>();
            for (final FlowSearchDTO location : getRecentFlow(entry.getKey())) {
                locations.add(location.toJSON());
            }

            final Map<String, Object> itemJson = new Object2ObjectOpenHashMap<>();
            itemJson.put("in", rate.leftLong());
            itemJson.put("out", rate.rightLong());
            itemJson.put("locations", locations);
            flowJson.put(String.valueOf(entry.getKey()), itemJson);
        }

        final Map<String, Object> root = new Object2ObjectOpenHashMap<>();
        root.put("trackingEnabled", this.trackingEnabled);
        root.put("windowMinutes", AEConfig.instance().getIngredientFlowTrackingWindowMinutes());
        root.put("buckets", this.buckets.size());
        root.put("flow", flowJson);

        JsonStreamUtil.writeProperties(root, writer);
    }

    public void recordFlow(final AEKey what, long delta, final IActionSource src) {
        if (!AEConfig.instance().isIngredientFlowTrackingEnabled() || !this.trackingEnabled) {
            return;
        }

        final long now = System.currentTimeMillis();
        Bucket bucket = this.buckets.peekLast();

        if (bucket == null || now - bucket.start >= BUCKET_SIZE_MS) {
            bucket = new Bucket(now);
            this.buckets.addLast(bucket);
        }

        final ItemFlowAccumulator accumulator = bucket.byKey.computeIfAbsent(what, _ -> new ItemFlowAccumulator());
        if (delta < 0) {
            accumulator.out = saturatingAdd(accumulator.out, positiveMagnitude(delta));
        } else {
            accumulator.in = saturatingAdd(accumulator.in, delta);
        }

        DimensionalBlockPos location = resolveLocation(src);
        if (location != null) {
            final LocationFlow locationFlow = accumulator.byLocation
                .computeIfAbsent(location, _ -> new LocationFlow());
            if (delta < 0) {
                locationFlow.out = saturatingAdd(locationFlow.out, positiveMagnitude(delta));
            } else {
                locationFlow.in = saturatingAdd(locationFlow.in, delta);
            }
        }
        this.flowRevision++;
    }

    @Nullable
    private static DimensionalBlockPos resolveHostLocation(@Nullable Object owner) {
        if (owner instanceof TileEntity tile) {
            return new DimensionalBlockPos(tile);
        }
        if (owner instanceof IPartHost partHost) {
            return partHost.getLocation();
        }
        if (owner instanceof IPart part && part instanceof AEBasePart basePart
            && basePart.getHost() != null) {
            return basePart.getHost().getLocation();
        }
        if (owner instanceof Entity entity) {
            return new DimensionalBlockPos(entity.world, entity.getPosition());
        }
        return null;
    }

    private DimensionalBlockPos resolveLocation(IActionSource src) {
        if (src.machine().isEmpty()) {
            return null;
        }

        final IActionHost owner = src.machine().get();
        final IGridNode node = owner.getActionableNode();
        if (node == null) {
            return null;
        }

        DimensionalBlockPos location = resolveHostLocation(owner);
        if (location != null) {
            return location;
        }

        // Hosts such as ME interfaces (mainNode::getNode) or wireless terminals are not placed in the world
        // themselves, so fall back to whatever owns the node they act through.
        location = resolveHostLocation(node.getOwner());
        if (location != null) {
            return location;
        }
        if (node instanceof InWorldGridNode inWorldNode && inWorldNode.getLocation() != null) {
            return new DimensionalBlockPos(node.getLevel(), inWorldNode.getLocation());
        }
        return null;
    }

    public List<FlowSearchDTO> getRecentFlow(final AEKey queryKey) {
        final Map<DimensionalBlockPos, FlowSearchDTO> byLocation = new Object2ObjectOpenHashMap<>();
        final List<FlowSearchDTO> result = new ArrayList<>();

        for (final Bucket bucket : this.buckets) {
            final ItemFlowAccumulator accumulator = bucket.byKey.get(queryKey);
            if (accumulator == null) {
                continue;
            }

            for (final Map.Entry<DimensionalBlockPos, LocationFlow> locationEntry : accumulator.byLocation.entrySet()) {
                final LocationFlow flow = locationEntry.getValue();
                final DimensionalBlockPos location = locationEntry.getKey();
                FlowSearchDTO total = byLocation.computeIfAbsent(location, l -> new FlowSearchDTO(l, 0, 0));
                final boolean wasZero = total.in == 0 && total.out == 0;
                total.in = saturatingAdd(total.in, flow.in);
                total.out = saturatingAdd(total.out, flow.out);
                if (wasZero && (total.in != 0 || total.out != 0)) {
                    result.add(total);
                }
            }
        }

        return result;
    }


    /**
     * Returns cached flow totals: left is inflow, right is outflow.
     * The map and its pairs are borrowed for reading; callers must not mutate the pairs.
     * Each rebuild owns new pairs so previously returned snapshots remain unchanged.
     */
    public Map<AEKey, LongLongPair> getAllRecentFlow() {
        if (this.cachedFlowRevision == this.flowRevision) {
            return this.cachedAllRecentFlow;
        }

        final Map<AEKey, LongLongPair> result = new Object2ObjectOpenHashMap<>(this.cachedAllRecentFlow.size());

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<AEKey, ItemFlowAccumulator> entry : bucket.byKey.entrySet()) {
                final ItemFlowAccumulator flow = entry.getValue();
                final LongLongPair total = result.computeIfAbsent(entry.getKey(), _ -> new LongLongMutablePair(0, 0));
                total.left(saturatingAdd(total.leftLong(), flow.in));
                total.right(saturatingAdd(total.rightLong(), flow.out));
            }
        }

        this.cachedAllRecentFlow = Collections.unmodifiableMap(result);
        this.cachedFlowRevision = this.flowRevision;
        return this.cachedAllRecentFlow;
    }

    private static final class Bucket {

        public final long start;
        public final Map<AEKey, ItemFlowAccumulator> byKey = new Object2ObjectOpenHashMap<>();

        public Bucket(final long start) {
            this.start = start;
        }
    }

    private static final class ItemFlowAccumulator {

        public long in;
        public long out;
        public final Map<DimensionalBlockPos, LocationFlow> byLocation = new Object2ObjectOpenHashMap<>();

    }

    private static final class LocationFlow {

        public long in;
        public long out;

    }
}
