package ae2.core.network;

import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

public final class NetworkPacketHelper {
    private static final long LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PACKET_WARNING_KEYS = 256;
    private static final Object2LongMap<String> LAST_PACKET_WARNING = new PacketWarningMap();

    private NetworkPacketHelper() {
    }

    @Nullable
    public static <T extends Enum<T>> T readEnumOrNull(PacketBuffer buffer, Class<T> enumClass) {
        int ordinal = buffer.readVarInt();
        T[] values = enumClass.getEnumConstants();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static void warnMalformedPacket(Throwable exception, String key, String message, Object... params) {
        warnRateLimited(exception, "malformed:" + key, message, params);
    }

    public static void warnFailedPacket(Throwable exception, String key, String message, Object... params) {
        warnRateLimited(exception, "failed:" + key, message, params);
    }

    private static void warnRateLimited(Throwable exception, String key, String message, Object... params) {
        long now = System.nanoTime();
        synchronized (LAST_PACKET_WARNING) {
            long previous = LAST_PACKET_WARNING.getLong(key);
            if (now - previous < LOG_INTERVAL_NANOS) {
                return;
            }
            LAST_PACKET_WARNING.put(key, now);
        }
        AELog.warn(exception, message, params);
    }

    private static final class PacketWarningMap extends Object2LongLinkedOpenHashMap<String> {
        private PacketWarningMap() {
            super(MAX_PACKET_WARNING_KEYS, 0.75F);
        }

        @Override
        public long put(final String k, final long v) {
            final long oldValue = super.put(k, v);
            while (size() > MAX_PACKET_WARNING_KEYS) {
                removeFirstLong();
            }
            return oldValue;
        }

        @Override
        public Object2LongLinkedOpenHashMap<String> clone() {
            throw new AssertionError();
        }
    }
}
