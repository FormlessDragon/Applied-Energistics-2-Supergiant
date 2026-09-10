package ae2.integration.modules.ic2;

import ae2.api.config.Actionable;
import ae2.api.config.PowerUnit;
import ae2.core.AELog;
import ae2.integration.abstraction.IC2PowerSink;
import ae2.tile.powersink.IExternalPowerSink;
import ic2.api.energy.prefab.BasicSink;
import ic2.api.energy.tile.IEnergyEmitter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class IC2PowerSinkAdapter extends BasicSink implements IC2PowerSink {

    private static final Map<World, Map<BlockPos, WeakReference<IC2PowerSinkAdapter>>> ACTIVE_SINKS =
        new WeakHashMap<>();

    private final TileEntity tileEntity;
    private final IExternalPowerSink powerSink;
    private final Set<EnumFacing> validFaces = EnumSet.allOf(EnumFacing.class);
    private boolean invalidDemandReported;

    public IC2PowerSinkAdapter(TileEntity tileEntity, IExternalPowerSink powerSink) {
        super(tileEntity, 0, Integer.MAX_VALUE);
        this.tileEntity = tileEntity;
        this.powerSink = powerSink;
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unregister(this);
    }

    @Override
    public void onLoad() {
        register(this);
    }

    private static void register(IC2PowerSinkAdapter sink) {
        synchronized (ACTIVE_SINKS) {
            World world = sink.tileEntity.getWorld();
            if (world == null || world.isRemote || sink.tileEntity.isInvalid()
                || !world.isBlockLoaded(sink.tileEntity.getPos())
                || world.getTileEntity(sink.tileEntity.getPos()) != sink.tileEntity) {
                unregisterLocked(sink, world);
                return;
            }

            Map<BlockPos, WeakReference<IC2PowerSinkAdapter>> worldSinks =
                ACTIVE_SINKS.computeIfAbsent(world, ignored -> new Object2ObjectOpenHashMap<>());
            BlockPos pos = sink.tileEntity.getPos().toImmutable();
            WeakReference<IC2PowerSinkAdapter> previousReference = worldSinks.put(pos, new WeakReference<>(sink));
            IC2PowerSinkAdapter previous = previousReference == null ? null : previousReference.get();
            if (previous != null && previous != sink) {
                previous.unregisterFromEnergyNet();
            }
            sink.registerWithEnergyNet();
        }
    }

    private static void unregister(IC2PowerSinkAdapter sink) {
        synchronized (ACTIVE_SINKS) {
            unregisterLocked(sink, sink.tileEntity.getWorld());
        }
    }

    private static void unregisterLocked(IC2PowerSinkAdapter sink, World world) {
        if (world != null) {
            Map<BlockPos, WeakReference<IC2PowerSinkAdapter>> worldSinks = ACTIVE_SINKS.get(world);
            if (worldSinks != null) {
                WeakReference<IC2PowerSinkAdapter> registeredReference = worldSinks.get(sink.tileEntity.getPos());
                IC2PowerSinkAdapter registered = registeredReference == null ? null : registeredReference.get();
                if (registered == null || registered == sink) {
                    worldSinks.remove(sink.tileEntity.getPos());
                    if (worldSinks.isEmpty()) {
                        ACTIVE_SINKS.remove(world);
                    }
                }
            }
        }
        sink.unregisterFromEnergyNet();
    }

    private void registerWithEnergyNet() {
        super.onLoad();
    }

    private void unregisterFromEnergyNet() {
        super.onChunkUnload();
    }

    @Override
    public double getDemandedEnergy() {
        double demand = this.powerSink.getExternalPowerDemand(PowerUnit.EU, maximumFiniteEuDemandRequest());
        double validDemand = sanitizeDemand(demand);
        if (demand != validDemand) {
            if (!this.invalidDemandReported) {
                AELog.error("IC2 power sink %s returned invalid energy demand %s; using %s instead",
                    this.powerSink, demand, validDemand);
                this.invalidDemandReported = true;
            }
        } else {
            this.invalidDemandReported = false;
        }
        return validDemand;
    }

    static double maximumFiniteEuDemandRequest() {
        return PowerUnit.AE.convertTo(PowerUnit.EU, Double.MAX_VALUE);
    }

    static double sanitizeDemand(double demand) {
        if (!Double.isFinite(demand) || demand <= 0) {
            return 0;
        }
        return Math.min(demand, maximumFiniteEuDemandRequest());
    }

    @Override
    public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
        return this.powerSink.injectExternalPower(PowerUnit.EU, amount, Actionable.MODULATE);
    }

    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        return this.validFaces.contains(side);
    }

    @Override
    public void setValidFaces(Set<EnumFacing> faces) {
        this.validFaces.clear();
        this.validFaces.addAll(faces);
    }
}
