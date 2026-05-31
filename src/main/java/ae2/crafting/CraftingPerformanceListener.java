package ae2.crafting;

import ae2.api.stacks.AEKey;

interface CraftingPerformanceListener {
    CraftingPerformanceListener NOOP = new CraftingPerformanceListener() {
    };

    default boolean isEnabled() {
        return false;
    }

    default void start(AEKey output, long amount) {
    }

    default void stage(String name, long nanos) {
    }

    default void selfStage(String name, long nanos) {
    }

    default void count(String name, long amount) {
    }

    default void finish(long nanos, CraftingCalculation calculation) {
    }
}
