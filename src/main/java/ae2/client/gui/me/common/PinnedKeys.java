package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public final class PinnedKeys {
    // One rows worth of keys
    public static final int MAX_PINNED = 9;
    public static final int MAX_PLAYER_PIN_ROWS = 8;

    // Compares by time the entry was pinned in ascending order
    private static final Comparator<Map.Entry<AEKey, PinInfo>> TIME_COMPARATOR = Comparator
        .comparing(e -> e.getValue().since);

    private static final Map<AEKey, PinInfo> craftingPins = new Object2ObjectOpenHashMap<>(MAX_PINNED);
    private static final ObjectList<AEKey> playerPins = new ObjectArrayList<>();
    private static int playerPinRows;
    private static boolean playerPinsLoaded;

    private PinnedKeys() {
    }

    public static boolean isEmpty() {
        ensurePlayerPinsLoaded();
        return craftingPins.isEmpty() && playerPins.isEmpty();
    }

    public static Set<AEKey> getPinnedKeys() {
        ensurePlayerPinsLoaded();
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        keys.addAll(craftingPins.keySet());
        keys.addAll(playerPins);
        return ImmutableSet.copyOf(keys);
    }

    public static List<AEKey> getCraftingPinnedKeys() {
        ObjectArrayList<Map.Entry<AEKey, PinInfo>> sorted = new ObjectArrayList<>(craftingPins.entrySet());
        sorted.sort(TIME_COMPARATOR);
        ObjectArrayList<AEKey> keys = new ObjectArrayList<>(sorted.size());
        for (var entry : sorted) {
            keys.add(entry.getKey());
        }
        return Collections.unmodifiableList(keys);
    }

    public static List<AEKey> getPlayerPinnedKeys() {
        ensurePlayerPinsLoaded();
        return Collections.unmodifiableList(playerPins);
    }

    @Nullable
    public static PinInfo getPinInfo(AEKey key) {
        ensurePlayerPinsLoaded();
        PinInfo info = craftingPins.get(key);
        if (info != null) {
            return info;
        }
        return playerPins.contains(key) ? new PinInfo(PinReason.PLAYER) : null;
    }

    @Nullable
    public static PinInfo getCraftingPinInfo(AEKey key) {
        return craftingPins.get(key);
    }

    public static void clearPinnedKeys() {
        craftingPins.clear();
    }

    public static void pinKey(AEKey key, PinReason reason) {
        if (reason == PinReason.PLAYER) {
            ensurePlayerPinsLoaded();
            if (!playerPins.contains(key)) {
                playerPins.add(key);
                savePlayerPins();
            }
            return;
        }

        // Refresh timer for existing pinned keys if they're re-pinned
        PinInfo info = craftingPins.get(key);
        if (info != null) {
            info.since = Instant.now();
            info.reason = reason;
            info.canPrune = false;
        } else {
            craftingPins.put(key, new PinInfo(reason));
        }

        // Remove older keys if we exceed the max amount of pinned keys
        if (craftingPins.size() > MAX_PINNED) {
            ObjectArrayList<Map.Entry<AEKey, PinInfo>> toRemove = new ObjectArrayList<>(craftingPins.entrySet());
            toRemove.sort(TIME_COMPARATOR);
            for (Map.Entry<AEKey, PinInfo> entry : toRemove.subList(0, toRemove.size() - MAX_PINNED)) {
                craftingPins.remove(entry.getKey());
            }
        }
    }

    public static void unpin(AEKey what) {
        craftingPins.remove(what);
        if (removePlayerPin(what)) {
            savePlayerPins();
        }
    }

    public static boolean isPinned(AEKey what) {
        ensurePlayerPinsLoaded();
        return craftingPins.containsKey(what) || playerPins.contains(what);
    }

    public static boolean isCraftingPinned(AEKey what) {
        return craftingPins.containsKey(what);
    }

    public static boolean isPlayerPinned(AEKey what) {
        ensurePlayerPinsLoaded();
        return playerPins.contains(what);
    }

    public static boolean togglePlayerPin(AEKey what, int capacity) {
        ensurePlayerPinsLoaded();
        if (removePlayerPin(what)) {
            savePlayerPins();
            return true;
        }
        if (playerPins.size() >= capacity) {
            return false;
        }
        playerPins.add(what);
        savePlayerPins();
        return true;
    }

    public static int getPlayerPinRows() {
        ensurePlayerPinsLoaded();
        return playerPinRows;
    }

    public static void addPlayerPinRow() {
        ensurePlayerPinsLoaded();
        if (playerPinRows < MAX_PLAYER_PIN_ROWS) {
            playerPinRows++;
            savePlayerPins();
        }
    }

    public static boolean removeEmptyPlayerPinRow(int rowSize) {
        ensurePlayerPinsLoaded();
        if (playerPinRows <= 0 || playerPins.size() > Math.max(0, playerPinRows - 1) * rowSize) {
            return false;
        }
        playerPinRows--;
        savePlayerPins();
        return true;
    }

    public static void prune() {
        craftingPins.values().removeIf(v -> v.canPrune);
    }

    private static boolean removePlayerPin(AEKey what) {
        ensurePlayerPinsLoaded();
        return playerPins.remove(what);
    }

    private static void ensurePlayerPinsLoaded() {
        if (playerPinsLoaded) {
            return;
        }
        playerPinsLoaded = true;
        PlayerPinStorage.Data data = PlayerPinStorage.load();
        playerPinRows = Math.clamp(data.rows(), 0, MAX_PLAYER_PIN_ROWS);
        playerPins.clear();
        for (AEKey key : data.keys()) {
            if (!playerPins.contains(key)) {
                playerPins.add(key);
            }
        }
    }

    private static void savePlayerPins() {
        ensurePlayerPinsLoaded();
        PlayerPinStorage.save(playerPinRows, playerPins);
    }

    public enum PinReason {
        CRAFTING,
        PLAYER
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
