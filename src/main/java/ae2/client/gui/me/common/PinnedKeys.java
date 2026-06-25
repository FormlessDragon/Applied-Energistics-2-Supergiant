package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import java.util.Objects;
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
    private static final ObjectList<PlayerPin> playerPins = new ObjectArrayList<>();
    private static final Map<AEKey, PlayerPin> playerPinsByKey = new Object2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<PlayerPin> playerPinsBySlot = new Int2ObjectOpenHashMap<>();
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
        for (var pin : playerPins) {
            keys.add(pin.key());
        }
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
        ObjectArrayList<AEKey> keys = new ObjectArrayList<>(playerPins.size());
        for (var pin : playerPins) {
            keys.add(pin.key());
        }
        return Collections.unmodifiableList(keys);
    }

    public static List<PlayerPin> getPlayerPins() {
        ensurePlayerPinsLoaded();
        return Collections.unmodifiableList(playerPins);
    }

    @Nullable
    public static PlayerPin getPlayerPinSlot(int slotIndex) {
        ensurePlayerPinsLoaded();
        checkPlayerPinSlotIndex(slotIndex);
        return playerPinsBySlot.get(slotIndex);
    }

    public static int getPlayerPinSlotIndex(AEKey key) {
        ensurePlayerPinsLoaded();
        PlayerPin pin = playerPinsByKey.get(key);
        return pin != null ? pin.slotIndex() : -1;
    }

    @Nullable
    public static PinInfo getPinInfo(AEKey key) {
        ensurePlayerPinsLoaded();
        PinInfo info = craftingPins.get(key);
        if (info != null) {
            return info;
        }
        return playerPinsByKey.containsKey(key) ? new PinInfo(PinReason.PLAYER) : null;
    }

    @Nullable
    public static PinInfo getCraftingPinInfo(AEKey key) {
        return craftingPins.get(key);
    }

    public static void clearPinnedKeys() {
        craftingPins.clear();
        playerPins.clear();
        playerPinRows = 0;
        playerPinsLoaded = false;
    }

    public static void pinKey(AEKey key, PinReason reason) {
        if (reason == PinReason.PLAYER) {
            autoPin(key, MAX_PLAYER_PIN_ROWS * MAX_PINNED);
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
        return craftingPins.containsKey(what) || playerPinsByKey.containsKey(what);
    }

    public static boolean isCraftingPinned(AEKey what) {
        return craftingPins.containsKey(what);
    }

    public static boolean isPlayerPinned(AEKey what) {
        ensurePlayerPinsLoaded();
        return playerPinsByKey.containsKey(what);
    }

    public static boolean togglePlayerPin(AEKey what, int capacity) {
        ensurePlayerPinsLoaded();
        if (removePlayerPin(what)) {
            savePlayerPins();
            return true;
        }
        return autoPin(what, capacity);
    }

    public static boolean autoPin(AEKey what, int capacity) {
        ensurePlayerPinsLoaded();
        if (playerPinsByKey.containsKey(what)) {
            return true;
        }
        int slotIndex = findFirstEmptySlot(capacity);
        if (slotIndex < 0) {
            return false;
        }
        addPlayerPin(new PlayerPin(slotIndex, what, PinKind.AUTO));
        playerPinRows = Math.max(playerPinRows, getRequiredRows());
        savePlayerPins();
        return true;
    }

    public static boolean manualPin(AEKey what, int slotIndex) {
        ensurePlayerPinsLoaded();
        checkPlayerPinSlotIndex(slotIndex);
        if (playerPinsByKey.containsKey(what) || playerPinsBySlot.containsKey(slotIndex)) {
            return false;
        }
        addPlayerPin(new PlayerPin(slotIndex, what, PinKind.MANUAL));
        playerPinRows = Math.max(playerPinRows, getRequiredRows());
        savePlayerPins();
        return true;
    }

    public static boolean removePlayerPinSlot(int slotIndex) {
        ensurePlayerPinsLoaded();
        checkPlayerPinSlotIndex(slotIndex);
        PlayerPin pin = playerPinsBySlot.get(slotIndex);
        if (pin != null) {
            removePlayerPin(pin);
            savePlayerPins();
            return true;
        }
        return false;
    }

    public static int getPlayerPinRows() {
        ensurePlayerPinsLoaded();
        return playerPinRows;
    }

    public static boolean setPlayerPinRows(int rows, int rowSize) {
        ensurePlayerPinsLoaded();
        if (rowSize <= 0 || rowSize > MAX_PINNED) {
            throw new IllegalArgumentException("Invalid player pin row size: " + rowSize);
        }

        int clampedRows = Math.clamp(rows, 0, MAX_PLAYER_PIN_ROWS);
        if (clampedRows < playerPinRows) {
            int maxSlotIndex = clampedRows * rowSize;
            for (int i = playerPins.size() - 1; i >= 0; i--) {
                PlayerPin pin = playerPins.get(i);
                if (pin.slotIndex() >= maxSlotIndex) {
                    removePlayerPin(pin);
                }
            }
        }

        if (playerPinRows == clampedRows) {
            return false;
        }

        playerPinRows = clampedRows;
        savePlayerPins();
        return true;
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
        if (!canRemoveEmptyPlayerPinRow(rowSize)) {
            return false;
        }
        playerPinRows--;
        savePlayerPins();
        return true;
    }

    public static boolean canRemoveEmptyPlayerPinRow(int rowSize) {
        ensurePlayerPinsLoaded();
        return playerPinRows > 0 && getHighestOccupiedSlotIndex() < Math.max(0, playerPinRows - 1) * rowSize;
    }

    public static void prune() {
        craftingPins.values().removeIf(v -> v.canPrune);
    }

    private static boolean removePlayerPin(AEKey what) {
        ensurePlayerPinsLoaded();
        PlayerPin pin = playerPinsByKey.get(what);
        if (pin != null) {
            removePlayerPin(pin);
            return true;
        }
        return false;
    }

    private static void ensurePlayerPinsLoaded() {
        if (playerPinsLoaded) {
            return;
        }
        playerPinsLoaded = true;
        PlayerPinStorage.Data data = PlayerPinStorage.load();
        playerPinRows = Math.clamp(data.rows(), 0, MAX_PLAYER_PIN_ROWS);
        playerPins.clear();
        playerPinsByKey.clear();
        playerPinsBySlot.clear();
        for (var slot : data.slots()) {
            addPlayerPin(new PlayerPin(slot.slotIndex(), slot.key(), slot.kind()));
        }
    }

    private static void savePlayerPins() {
        ensurePlayerPinsLoaded();
        ObjectArrayList<PlayerPinStorage.PinSlot> slots = new ObjectArrayList<>(playerPins.size());
        for (var pin : playerPins) {
            slots.add(new PlayerPinStorage.PinSlot(pin.slotIndex(), pin.key(), pin.kind()));
        }
        PlayerPinStorage.save(playerPinRows, slots);
    }

    private static int findFirstEmptySlot(int capacity) {
        int maxSlots = Math.min(capacity, MAX_PLAYER_PIN_ROWS * MAX_PINNED);
        for (int slotIndex = 0; slotIndex < maxSlots; slotIndex++) {
            if (!playerPinsBySlot.containsKey(slotIndex)) {
                return slotIndex;
            }
        }
        return -1;
    }

    private static void addPlayerPin(PlayerPin pin) {
        playerPins.add(pin);
        playerPins.sort(PlayerPin.SLOT_INDEX_COMPARATOR);
        playerPinsByKey.put(pin.key(), pin);
        playerPinsBySlot.put(pin.slotIndex(), pin);
    }

    private static void removePlayerPin(PlayerPin pin) {
        playerPins.remove(pin);
        playerPinsByKey.remove(pin.key());
        playerPinsBySlot.remove(pin.slotIndex());
    }

    private static int getRequiredRows() {
        int rows = 0;
        for (var pin : playerPins) {
            rows = Math.max(rows, pin.slotIndex() / MAX_PINNED + 1);
        }
        return Math.min(rows, MAX_PLAYER_PIN_ROWS);
    }

    private static int getHighestOccupiedSlotIndex() {
        int slotIndex = -1;
        for (var pin : playerPins) {
            slotIndex = Math.max(slotIndex, pin.slotIndex());
        }
        return slotIndex;
    }

    private static void checkPlayerPinSlotIndex(int slotIndex) {
        int maxSlots = MAX_PLAYER_PIN_ROWS * MAX_PINNED;
        if (slotIndex < 0 || slotIndex >= maxSlots) {
            throw new IllegalArgumentException("Invalid player pin slot index: " + slotIndex);
        }
    }

    public enum PinReason {
        CRAFTING,
        PLAYER
    }

    public enum PinKind {
        AUTO,
        MANUAL
    }

    public record PlayerPin(int slotIndex, AEKey key, PinKind kind) {
        private static final Comparator<PlayerPin> SLOT_INDEX_COMPARATOR =
            Comparator.comparingInt(PlayerPin::slotIndex);

        public PlayerPin {
            if (slotIndex < 0 || slotIndex >= MAX_PLAYER_PIN_ROWS * MAX_PINNED) {
                throw new IllegalArgumentException("Invalid player pin slot index: " + slotIndex);
            }
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(kind, "kind");
        }
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
