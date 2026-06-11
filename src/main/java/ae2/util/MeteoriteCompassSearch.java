package ae2.util;

public final class MeteoriteCompassSearch {
    // Meteorite generation and compass search region size. Client prefetch covers a 5x5 window of these regions.
    public static final int REGION_SIZE_CHUNKS = 24;
    public static final int PREFETCH_RADIUS = 2;
    public static final int MAX_PREFETCH_REQUESTS = (PREFETCH_RADIUS * 2 + 1) * (PREFETCH_RADIUS * 2 + 1);

    private MeteoriteCompassSearch() {
    }

    public static int getRegion(int chunkCoord) {
        return Math.floorDiv(chunkCoord, REGION_SIZE_CHUNKS);
    }

    public static int getRegionMinChunk(int region) {
        return region * REGION_SIZE_CHUNKS;
    }

    public static int getRegionMaxChunk(int region) {
        return getRegionMinChunk(region) + REGION_SIZE_CHUNKS - 1;
    }
}
