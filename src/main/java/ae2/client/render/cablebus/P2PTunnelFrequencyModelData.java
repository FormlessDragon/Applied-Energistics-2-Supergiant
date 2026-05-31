package ae2.client.render.cablebus;

public final class P2PTunnelFrequencyModelData {
    public static final long FREQUENCY_MASK = 0xffffL;
    public static final long ACTIVE_MASK = 0x10000L;

    private P2PTunnelFrequencyModelData() {
    }

    public static long of(short frequency, boolean active) {
        long flags = Short.toUnsignedLong(frequency) & FREQUENCY_MASK;
        if (active) {
            flags |= ACTIVE_MASK;
        }
        return flags;
    }

    public static short getFrequency(long flags) {
        return (short) (flags & FREQUENCY_MASK);
    }

    public static boolean isActive(long flags) {
        return (flags & ACTIVE_MASK) != 0;
    }
}
