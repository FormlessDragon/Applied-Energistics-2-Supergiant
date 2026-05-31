package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public final class PinnedKeys {
    // One rows worth of keys
    public static final int MAX_PINNED = 9;

    // Compares by time the entry was pinned in ascending order
    private static final Comparator<Map.Entry<AEKey, PinInfo>> TIME_COMPARATOR = Comparator
        .comparing(e -> e.getValue().since);

    private static final Map<AEKey, PinInfo> pinned = new Object2ObjectOpenHashMap<>(MAX_PINNED);

    private PinnedKeys() {
    }

    public static boolean isEmpty() {
        return pinned.isEmpty();
    }

    public static Set<AEKey> getPinnedKeys() {
        return ImmutableSet.copyOf(pinned.keySet());
    }

    @Nullable
    public static PinInfo getPinInfo(AEKey key) {
        return pinned.get(key);
    }

    public static void clearPinnedKeys() {
        pinned.clear();
    }

    public static void pinKey(AEKey key, PinReason reason) {
        // Refresh timer for existing pinned keys if they're re-pinned
        PinInfo info = pinned.get(key);
        if (info != null) {
            info.since = Instant.now();
            info.reason = reason;
            info.canPrune = false;
        } else {
            pinned.put(key, new PinInfo(reason));
        }

        // Remove older keys if we exceed the max amount of pinned keys
        if (pinned.size() > MAX_PINNED) {
            ObjectArrayList<Map.Entry<AEKey, PinInfo>> toRemove = new ObjectArrayList<>(pinned.entrySet());
            toRemove.sort(TIME_COMPARATOR);
            for (Map.Entry<AEKey, PinInfo> entry : toRemove.subList(0, toRemove.size() - MAX_PINNED)) {
                pinned.remove(entry.getKey());
            }
        }
    }

    public static void unpin(AEKey what) {
        pinned.remove(what);
    }

    public static boolean isPinned(AEKey what) {
        return pinned.containsKey(what);
    }

    public static void prune() {
        pinned.values().removeIf(v -> v.canPrune);
    }

    public enum PinReason {
        CRAFTING
    }

    public static class PinInfo {
        // When was it pinned?
        public Instant since;
        // Why was it pinned?
        public PinReason reason;
        // Can it be pruned the next time the UI is opened?
        public boolean canPrune;

        public PinInfo(PinReason reason) {
            this.reason = reason;
            this.since = Instant.now();
        }
    }
}
