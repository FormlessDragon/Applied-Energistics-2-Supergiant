package ae2.core.network;

import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

public final class NetworkPacketHelper {
    private static final long LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PACKET_WARNING_KEYS = 256;
    private static final PacketWarningLimiter PACKET_WARNING_LIMITER =
        new PacketWarningLimiter(LOG_INTERVAL_NANOS, MAX_PACKET_WARNING_KEYS);

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
        if (!PACKET_WARNING_LIMITER.shouldLog(key, System.nanoTime())) {
            return;
        }
        AELog.warn(exception, message, params);
    }

    static final class PacketWarningLimiter {
        private final long intervalNanos;
        private final int maxKeys;
        private final Object2LongLinkedOpenHashMap<String> lastWarnings;

        PacketWarningLimiter(long intervalNanos, int maxKeys) {
            if (intervalNanos <= 0) {
                throw new IllegalArgumentException("intervalNanos must be positive");
            }
            if (maxKeys <= 0) {
                throw new IllegalArgumentException("maxKeys must be positive");
            }
            this.intervalNanos = intervalNanos;
            this.maxKeys = maxKeys;
            this.lastWarnings = new Object2LongLinkedOpenHashMap<>(maxKeys, 0.75F);
        }

        synchronized boolean shouldLog(String key, long now) {
            if (this.lastWarnings.containsKey(key)) {
                long elapsed = now - this.lastWarnings.getLong(key);
                if (elapsed >= 0 && elapsed < this.intervalNanos) {
                    return false;
                }
            }

            this.lastWarnings.putAndMoveToLast(key, now);
            while (this.lastWarnings.size() > this.maxKeys) {
                this.lastWarnings.removeFirstLong();
            }
            return true;
        }

        synchronized int trackedKeyCount() {
            return this.lastWarnings.size();
        }
    }
}
