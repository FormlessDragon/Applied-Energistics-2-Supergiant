package ae2.parts.reporting;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import java.util.LinkedList;

final class ThroughputCache {
    private static final int MAX_SIZE = 600;
    private final LinkedList<CacheEntry> cache = new LinkedList<>();

    public int size() {
        return cache.size();
    }

    public void push(long amount, long timestamp) {
        if (timestamp == 0) {
            return;
        }
        if (!cache.isEmpty() && cache.getFirst().timestamp == timestamp) {
            return;
        }
        cache.addFirst(new CacheEntry(amount, timestamp));
        if (cache.size() > MAX_SIZE) {
            cache.removeLast();
        }
    }

    public void clear() {
        cache.clear();
    }

    public double averagePerTick(long now, int timeLimitSeconds) {
        long timeLimit = now - timeLimitSeconds * 20L;
        long lastAmount = -1;
        long lastTimestamp = -1;
        var averages = new DoubleArrayList();

        for (var entry : cache) {
            if (entry.timestamp < timeLimit) {
                break;
            }
            if (lastTimestamp != -1) {
                long timestampDelta = lastTimestamp - entry.timestamp;
                long amountDelta = lastAmount - entry.amount;
                if (timestampDelta != 0) {
                    averages.add(amountDelta / (double) timestampDelta);
                }
            }
            lastAmount = entry.amount;
            lastTimestamp = entry.timestamp;
        }

        if (averages.isEmpty()) {
            return 0;
        }
        double average = 0;
        for (double value : averages) {
            average += value / averages.size();
        }
        return average;
    }

    private record CacheEntry(long amount, long timestamp) {
    }
}
