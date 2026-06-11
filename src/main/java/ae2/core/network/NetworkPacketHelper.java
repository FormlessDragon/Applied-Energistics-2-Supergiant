package ae2.core.network;

import ae2.core.AELog;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NetworkPacketHelper {
    private static final long LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PACKET_WARNING_KEYS = 256;
    private static final Map<String, Long> LAST_PACKET_WARNING = new PacketWarningMap();

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
            Long previous = LAST_PACKET_WARNING.get(key);
            if (previous != null && now - previous < LOG_INTERVAL_NANOS) {
                return;
            }
            LAST_PACKET_WARNING.put(key, now);
        }
        AELog.warn(exception, message, params);
    }

    private static final class PacketWarningMap extends LinkedHashMap<String, Long> {
        private PacketWarningMap() {
            super(MAX_PACKET_WARNING_KEYS, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_PACKET_WARNING_KEYS;
        }

        @Override
        public Object clone() {
            throw new AssertionError();
        }
    }
}
