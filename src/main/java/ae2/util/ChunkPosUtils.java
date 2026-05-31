package ae2.util;

public class ChunkPosUtils {

    public static long asLong(int x, int z) {
        return (long)x & 4294967295L | ((long)z & 4294967295L) << 32;
    }

    public static int getX(long key) {
        return (int)(key & 4294967295L);
    }

    public static int getZ(long key) {
        return (int)(key >>> 32 & 4294967295L);
    }
}
