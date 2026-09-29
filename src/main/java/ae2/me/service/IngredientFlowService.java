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
import ae2.api.util.FlowRate;
import ae2.api.util.FlowSearchDTO;
import ae2.core.AEConfig;
import ae2.parts.AEBasePart;
import ae2.util.JsonStreamUtil;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IngredientFlowService implements IGridService, IGridServiceProvider {

    private static final long BUCKET_SIZE_MS = 200L;
    private static final String TRACKING_ENABLED_KEY = "IngredientFlowTrackingEnabled";
    private final ArrayDeque<Bucket> buckets = new ArrayDeque<>();
    private boolean trackingEnabled = false;

    @Override
    public void onServerEndTick() {
        final long windowMs = AEConfig.instance().getIngredientFlowTrackingWindowMinutes() * 60_000L;
        final long cutoff = System.currentTimeMillis() - windowMs;
        while (!this.buckets.isEmpty() && this.buckets.peekFirst().start + BUCKET_SIZE_MS <= cutoff) {
            this.buckets.pollFirst();
        }
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        if (savedData != null && savedData.hasKey(TRACKING_ENABLED_KEY)) {
            this.trackingEnabled = this.trackingEnabled || savedData.getBoolean(TRACKING_ENABLED_KEY);
        }
    }

    @Override
    public void saveNodeData(IGridNode gridNode, NBTTagCompound savedData) {
        savedData.setBoolean(TRACKING_ENABLED_KEY, this.trackingEnabled);
    }

    @Override
    public void debugDump(JsonWriter writer) throws IOException {
        final Map<String, Object> flowJson = new Object2ObjectOpenHashMap<>();
        for (final Map.Entry<AEKey, FlowRate> entry : getAllRecentFlow().entrySet()) {
            final FlowRate rate = entry.getValue();
            final List<Object> locations = new ArrayList<>();
            for (final FlowSearchDTO location : getRecentFlow(entry.getKey())) {
                locations.add(location.toJSON());
            }

            final Map<String, Object> itemJson = new Object2ObjectOpenHashMap<>();
            itemJson.put("in", rate.in());
            itemJson.put("out", rate.out());
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

    public boolean isTrackingEnabled() {
        return this.trackingEnabled;
    }

    public void setTrackingEnabled(final boolean enabled) {
        this.trackingEnabled = enabled;
        if (!enabled) {
            this.buckets.clear();

        }
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
            accumulator.out -= delta;
        } else {
            accumulator.in += delta;
        }

        DimensionalBlockPos location = resolveLocation(src);
        if (location != null) {
            final LocationFlow locationFlow = accumulator.byLocation
                .computeIfAbsent(location, _ -> new LocationFlow());
            if (delta < 0) {
                locationFlow.out -= delta;
            } else {
                locationFlow.in += delta;
            }
        }
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

        BlockPos blockPos = resolveBlockPos(owner);
        if (blockPos == null) {
            return null;
        }
        return new DimensionalBlockPos(node.getLevel(), blockPos);
    }

    private BlockPos resolveBlockPos(Object owner) {
        if (owner instanceof TileEntity tile) {
            return tile.getPos();
        }
        if (owner instanceof IPartHost partHost) {
            return partHost.getLocation().getPos();
        }
        if (owner instanceof IPart part && part instanceof AEBasePart basePart
            && basePart.getHost() != null) {
            return basePart.getHost().getLocation().getPos();
        }
        if (owner instanceof Entity entity) {
            return entity.getPosition();
        }
        return null;
    }

    public List<FlowSearchDTO> getRecentFlow(final AEKey queryKey) {
        final Map<DimensionalBlockPos, LocationFlow> byLocation = new Object2ObjectLinkedOpenHashMap<>();
        final List<FlowSearchDTO> result = new ArrayList<>();

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<AEKey, ItemFlowAccumulator> entry : bucket.byKey.entrySet()) {

                if (!entry.getKey().equals(queryKey)) {
                    continue;
                }

                for (final Map.Entry<DimensionalBlockPos, LocationFlow> locationEntry : entry.getValue().byLocation
                    .entrySet()) {
                    final LocationFlow total = byLocation.computeIfAbsent(locationEntry.getKey(),
                        _ -> new LocationFlow());
                    total.in += locationEntry.getValue().in;
                    total.out += locationEntry.getValue().out;
                }
            }
        }

        for (final Map.Entry<DimensionalBlockPos, LocationFlow> entry : byLocation.entrySet()) {
            final LocationFlow flow = entry.getValue();
            if (flow.in != 0 || flow.out != 0) {
                result.add(new FlowSearchDTO(entry.getKey(), flow.in, flow.out));
            }
        }

        return result;
    }


    public Map<AEKey, FlowRate> getAllRecentFlow() {
        final Object2LongLinkedOpenHashMap<AEKey> totalsIn = new Object2LongLinkedOpenHashMap<>();
        final Object2LongLinkedOpenHashMap<AEKey> totalsOut = new Object2LongLinkedOpenHashMap<>();
        final Map<AEKey, FlowRate> result = new Object2ObjectOpenHashMap<>();

        for(final Bucket bucket : this.buckets) {
            for(final Map.Entry<AEKey, ItemFlowAccumulator> entry : bucket.byKey.entrySet()) {
                totalsIn.addTo(entry.getKey(), entry.getValue().in);
                totalsOut.addTo(entry.getKey(), entry.getValue().out);
            }
        }

        for(final Object2LongMap.Entry<AEKey> entry : totalsIn.object2LongEntrySet()) {
            result.put(entry.getKey(), new FlowRate(entry.getLongValue(), totalsOut.getLong(entry.getKey())));
        }

        return result;
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
        public final Map<DimensionalBlockPos, LocationFlow> byLocation = new Object2ObjectLinkedOpenHashMap<>();

    }

    private static final class LocationFlow {

        public long in;
        public long out;

    }
}
