package ae2.api.networking.ticking;

import ae2.api.networking.IGridNode;

import java.util.IdentityHashMap;

/**
 * Mutable network tick timing snapshot maintained by {@link ITickManager}.
 */
public final class TickSnapshot {
    private final IdentityHashMap<IGridNode, NodeTimes> nodeTimes = new IdentityHashMap<>();
    private long cpuAverage;
    private long cpuMax;
    private long storage;
    private long crafting;
    private long tick;
    private long misc;

    public void reset() {
        this.cpuAverage = 0;
        this.cpuMax = 0;
        this.storage = 0;
        this.crafting = 0;
        this.tick = 0;
        this.misc = 0;
        this.nodeTimes.clear();
    }

    public void update(IGridNode node, Category category, long averageTime, long maximumTime) {
        NodeTimes times = this.nodeTimes.computeIfAbsent(node, ignored -> new NodeTimes());
        long deltaAverage = averageTime - times.averageTime;
        this.cpuAverage += deltaAverage;

        if (times.category != null && times.category != category) {
            addCategoryAverage(times.category, -times.averageTime);
            addCategoryAverage(category, averageTime);
        } else {
            addCategoryAverage(category, deltaAverage);
        }

        times.category = category;
        times.averageTime = averageTime;
        times.maximumTime = maximumTime;
        this.cpuMax = Math.max(this.cpuMax, maximumTime);
    }

    public void remove(IGridNode node) {
        NodeTimes times = this.nodeTimes.remove(node);
        if (times == null) {
            return;
        }

        this.cpuAverage -= times.averageTime;
        if (times.category != null) {
            addCategoryAverage(times.category, -times.averageTime);
        }

        if (times.maximumTime >= this.cpuMax) {
            updateCpuMax();
        }
    }

    private void addCategoryAverage(Category category, long averageTime) {
        switch (category) {
            case STORAGE -> this.storage += averageTime;
            case CRAFTING -> this.crafting += averageTime;
            case TICK -> this.tick += averageTime;
            case MISC -> this.misc += averageTime;
        }
    }

    private void updateCpuMax() {
        long maximumTime = 0;
        for (var times : this.nodeTimes.values()) {
            maximumTime = Math.max(maximumTime, times.maximumTime);
        }
        this.cpuMax = maximumTime;
    }

    public long cpuAverage() {
        return this.cpuAverage;
    }

    public long cpuMax() {
        return this.cpuMax;
    }

    public long storage() {
        return this.storage;
    }

    public long crafting() {
        return this.crafting;
    }

    public long tick() {
        return this.tick;
    }

    public long misc() {
        return this.misc;
    }

    public enum Category {
        STORAGE,
        CRAFTING,
        TICK,
        MISC
    }

    private static final class NodeTimes {
        private Category category;
        private long averageTime;
        private long maximumTime;
    }
}
