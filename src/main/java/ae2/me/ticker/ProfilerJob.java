package ae2.me.ticker;

import ae2.util.EmptyArrays;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.math.BlockPos;

public final class ProfilerJob {
    private final long waitingNanoseconds;
    private final long startedAt = System.nanoTime();
    private final Object2LongOpenHashMap<SamplePos> nanoseconds = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<SamplePos> ticks = new Object2LongOpenHashMap<>();

    public ProfilerJob(long waitingNanoseconds) {
        this.waitingNanoseconds = waitingNanoseconds;
    }

    public void tick(SamplePos pos, long nanoseconds, long tick) {
        this.nanoseconds.addTo(pos, nanoseconds);
        this.ticks.addTo(pos, tick);
    }

    public boolean isFinished() {
        return this.waitingNanoseconds <= System.nanoTime() - this.startedAt;
    }

    public ProfileData generateData() {
        var ticks = new ObjectArrayList<ProfileData.ATick>(this.nanoseconds.size());
        for (var entry : this.nanoseconds.object2LongEntrySet()) {
            if (ticks.size() >= ProfileData.MAX_PROFILE_TICKS) {
                break;
            }
            SamplePos pos = entry.getKey();
            long tickCount = this.ticks.getLong(pos);
            double rate = tickCount == 0L ? 0.0D : (double) entry.getLongValue() / tickCount / 1000.0D;
            ticks.add(new ProfileData.ATick(pos.dimension(), pos.pos(), rate, ProfileData.getColor(rate)));
        }
        return new ProfileData(ticks.toArray(EmptyArrays.EMPTY_PROFILE_DATA_ATICK_ARRAY));
    }

    public record SamplePos(int dimension, BlockPos pos) {
    }
}
