package ae2.me.ticker;

public final class GridTickProfiler {
    private long nanoseconds;
    private long ticks;

    public void start() {
        this.nanoseconds = 0L;
        this.ticks = 0L;
    }

    public double rate() {
        if (this.ticks == 0L) {
            return 0.0D;
        }
        return (double) this.nanoseconds / this.ticks;
    }

    public void update(long tick, long nanosecond) {
        this.ticks += tick;
        this.nanoseconds += nanosecond;
    }
}
