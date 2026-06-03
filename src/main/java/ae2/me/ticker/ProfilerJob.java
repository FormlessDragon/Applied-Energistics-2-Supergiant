package ae2.me.ticker;

import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.util.math.BlockPos;

public final class ProfilerJob {
    private final long waitingNanoseconds;
    private final long startedAt = System.nanoTime();
    private final Object2ReferenceMap<SamplePos, GridTickProfiler> results = new Object2ReferenceOpenHashMap<>();

    public ProfilerJob(long waitingNanoseconds) {
        this.waitingNanoseconds = waitingNanoseconds;
    }

    public void tick(SamplePos pos, long nanoseconds, long tick) {
        GridTickProfiler profiler = this.results.get(pos);
        if (profiler == null) {
            profiler = new GridTickProfiler();
            profiler.start();
            this.results.put(pos, profiler);
        }
        profiler.update(tick, nanoseconds);
    }

    public boolean isFinished() {
        return this.waitingNanoseconds <= System.nanoTime() - this.startedAt;
    }

    public ProfileData generateData() {
        return new ProfileData(this.results.entrySet()
            .stream()
            .map(e -> {
                double rate = e.getValue().rate() / 1000.0D;
                SamplePos pos = e.getKey();
                return new ProfileData.ATick(pos.dimension(), pos.pos(), rate, ProfileData.getColor(rate));
            })
            .toArray(ProfileData.ATick[]::new));
    }

    public record SamplePos(int dimension, BlockPos pos) {
    }
}
