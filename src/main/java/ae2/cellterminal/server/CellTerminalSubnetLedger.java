package ae2.cellterminal.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent server-side subnet metadata owned by one Cell Terminal session host.
 * <p>
 * The ledger stores user-facing subnet state such as rename, favorite, last-loaded target and the latest highlight
 * feedback so future GUI layers can render and mutate subnet state without reintroducing ad-hoc packet state.
 */
public final class CellTerminalSubnetLedger {
    private static final String SUBNET_HIGHLIGHT_OK = "gui.ae2.CellTerminal.subnet.highlight.ok";
    private static final String LEGACY_SUBNET_HIGHLIGHT_OK = "highlight_ok";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_LAST_LOADED = "lastLoaded";
    private static final String TAG_LAST_HIGHLIGHT = "lastHighlight";
    private static final String TAG_SUBNET_ID = "subnetId";
    private static final String TAG_DISPLAY_NAME = "displayName";
    private static final String TAG_FAVORITE = "favorite";
    private static final String TAG_HANDLE = "handle";
    private static final String TAG_STATUS = "status";
    private static final String TAG_MESSAGE = "message";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER_ID = "playerId";
    private static final String TAG_FAVORITES = "favorites";

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, PlayerEntry> playerEntries = new LinkedHashMap<>();
    @Nullable
    private CellTerminalSubnetHighlightResult lastHighlightResult;

    /**
     * Restores a ledger from NBT.
     *
     * @param tag Serialized tag.
     * @return Restored ledger.
     */
    public static CellTerminalSubnetLedger fromTag(@Nullable NBTTagCompound tag) {
        var ledger = new CellTerminalSubnetLedger();
        if (tag == null || tag.isEmpty()) {
            return ledger;
        }
        var entriesTag = tag.getTagList(TAG_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < entriesTag.tagCount(); index++) {
            var entryTag = entriesTag.getCompoundTagAt(index);
            String subnetId = entryTag.getString(TAG_SUBNET_ID);
            if (subnetId.isEmpty()) {
                continue;
            }
            CellTerminalSubnetHandle handle = CellTerminalSubnetHandle.fromTag(entryTag.getCompoundTag(TAG_HANDLE));
            String entryKey = handle != null ? key(handle) : subnetId;
            if (entryKey == null) {
                continue;
            }
            ledger.entries.put(entryKey, new Entry(
                handle,
                entryTag.hasKey(TAG_DISPLAY_NAME, Constants.NBT.TAG_STRING) ? entryTag.getString(TAG_DISPLAY_NAME) : null,
                entryTag.getBoolean(TAG_FAVORITE)));
        }
        var playersTag = tag.getTagList(TAG_PLAYERS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < playersTag.tagCount(); index++) {
            var playerTag = playersTag.getCompoundTagAt(index);
            if (!playerTag.hasUniqueId(TAG_PLAYER_ID)) {
                continue;
            }
            PlayerEntry playerEntry = new PlayerEntry();
            playerEntry.lastLoadedHandle = CellTerminalSubnetHandle.fromTag(playerTag.getCompoundTag(TAG_LAST_LOADED));
            var favoritesTag = playerTag.getTagList(TAG_FAVORITES, Constants.NBT.TAG_COMPOUND);
            for (int favoriteIndex = 0; favoriteIndex < favoritesTag.tagCount(); favoriteIndex++) {
                var favoriteTag = favoritesTag.getCompoundTagAt(favoriteIndex);
                CellTerminalSubnetHandle handle = CellTerminalSubnetHandle.fromTag(
                    favoriteTag.getCompoundTag(TAG_HANDLE));
                if (handle != null) {
                    playerEntry.favoriteKeys.put(key(handle),
                        !favoriteTag.hasKey(TAG_FAVORITE, Constants.NBT.TAG_BYTE)
                            || favoriteTag.getBoolean(TAG_FAVORITE));
                }
            }
            ledger.playerEntries.put(playerTag.getUniqueId(TAG_PLAYER_ID), playerEntry);
        }
        var lastHighlightTag = tag.getCompoundTag(TAG_LAST_HIGHLIGHT);
        if (!lastHighlightTag.isEmpty()) {
            var handle = CellTerminalSubnetHandle.fromTag(lastHighlightTag.getCompoundTag(TAG_HANDLE));
            if (handle != null && lastHighlightTag.hasKey(TAG_STATUS, Constants.NBT.TAG_STRING)) {
                ledger.lastHighlightResult = new CellTerminalSubnetHighlightResult(
                    handle,
                    CellTerminalActionStatus.valueOf(lastHighlightTag.getString(TAG_STATUS)),
                    normalizeHighlightMessage(lastHighlightTag.getString(TAG_MESSAGE)));
            }
        }
        return ledger;
    }

    private static String normalizeHighlightMessage(String message) {
        if (LEGACY_SUBNET_HIGHLIGHT_OK.equals(message)) {
            return SUBNET_HIGHLIGHT_OK;
        }
        return message;
    }

    private static String key(CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return handle.stableTargetId() + "|" + handle.locator() + "|" + handle.subnetId();
    }

    /**
     * Writes this ledger to NBT.
     *
     * @return Serialized tag.
     */
    public NBTTagCompound writeToTag() {
        var tag = new NBTTagCompound();
        var entriesTag = new NBTTagList();
        for (var entry : this.entries.entrySet()) {
            var entryTag = new NBTTagCompound();
            entryTag.setString(TAG_SUBNET_ID, entry.getKey());
            if (entry.getValue().handle != null) {
                entryTag.setTag(TAG_HANDLE, entry.getValue().handle.toTag());
                entryTag.setString(TAG_SUBNET_ID, entry.getValue().handle.subnetId());
            }
            if (entry.getValue().displayName != null) {
                entryTag.setString(TAG_DISPLAY_NAME, entry.getValue().displayName);
            }
            entryTag.setBoolean(TAG_FAVORITE, entry.getValue().favorite);
            entriesTag.appendTag(entryTag);
        }
        tag.setTag(TAG_ENTRIES, entriesTag);
        var playersTag = new NBTTagList();
        for (var playerEntry : this.playerEntries.entrySet()) {
            var playerTag = new NBTTagCompound();
            playerTag.setUniqueId(TAG_PLAYER_ID, playerEntry.getKey());
            if (playerEntry.getValue().lastLoadedHandle != null) {
                playerTag.setTag(TAG_LAST_LOADED, playerEntry.getValue().lastLoadedHandle.toTag());
            }
            var favoritesTag = new NBTTagList();
            for (var favorite : playerEntry.getValue().favoriteKeys.entrySet()) {
                Entry entry = this.entries.get(favorite.getKey());
                if (entry == null) {
                    entry = entryBySubnetId(favorite.getKey());
                }
                if (entry != null && entry.handle != null) {
                    var favoriteTag = new NBTTagCompound();
                    favoriteTag.setTag(TAG_HANDLE, entry.handle.toTag());
                    favoriteTag.setBoolean(TAG_FAVORITE, favorite.getValue());
                    favoritesTag.appendTag(favoriteTag);
                }
            }
            playerTag.setTag(TAG_FAVORITES, favoritesTag);
            playersTag.appendTag(playerTag);
        }
        tag.setTag(TAG_PLAYERS, playersTag);
        if (this.lastHighlightResult != null) {
            var highlightTag = new NBTTagCompound();
            highlightTag.setTag(TAG_HANDLE, this.lastHighlightResult.handle().toTag());
            highlightTag.setString(TAG_STATUS, this.lastHighlightResult.status().name());
            highlightTag.setString(TAG_MESSAGE, this.lastHighlightResult.message());
            tag.setTag(TAG_LAST_HIGHLIGHT, highlightTag);
        }
        return tag;
    }

    /**
     * Creates a detached copy for handing session metadata across host/container boundaries.
     *
     * @return Independent ledger with the same serialized state.
     */
    public CellTerminalSubnetLedger copy() {
        return fromTag(writeToTag());
    }

    /**
     * Renames a subnet.
     *
     * @param handle      Target subnet handle.
     * @param displayName New user-facing name.
     */
    public void rename(CellTerminalSubnetHandle handle, String displayName) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(displayName, "displayName");
        entry(handle).displayName = displayName;
    }

    /**
     * Updates favorite state for one player and one subnet.
     *
     * @param playerId Player UUID that owns the preference.
     * @param handle   Target subnet handle.
     * @param favorite Favorite state.
     */
    public void setFavorite(UUID playerId, CellTerminalSubnetHandle handle, boolean favorite) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(handle, "handle");
        entry(handle);
        playerEntry(playerId).favoriteKeys.put(handle.subnetId(), favorite);
    }

    /**
     * Marks a subnet as the latest loaded target for one player.
     *
     * @param playerId Player UUID that owns the preference.
     * @param handle   Target subnet handle.
     */
    public void markLastLoaded(UUID playerId, CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(playerId, "playerId");
        playerEntry(playerId).lastLoadedHandle = Objects.requireNonNull(handle, "handle");
    }

    /**
     * Records a successful highlight request.
     *
     * @param handle Target subnet handle.
     */
    public void recordHighlightSuccess(CellTerminalSubnetHandle handle) {
        this.lastHighlightResult = new CellTerminalSubnetHighlightResult(
            Objects.requireNonNull(handle, "handle"),
            CellTerminalActionStatus.SUCCESS,
            SUBNET_HIGHLIGHT_OK);
    }

    /**
     * Returns the renamed display name for one subnet when present.
     *
     * @param handle Stable subnet handle.
     * @return Stored display name, or {@code null}.
     */
    public @Nullable String getDisplayName(CellTerminalSubnetHandle handle) {
        Entry entry = entryForRead(handle);
        return entry != null ? entry.displayName : null;
    }

    /**
     * Returns whether the subnet is favorited.
     *
     * @param handle Stable subnet handle.
     * @return Favorite state.
     */
    public boolean isFavorite(CellTerminalSubnetHandle handle) {
        Entry entry = entryForRead(handle);
        return entry != null && entry.favorite;
    }

    /**
     * Returns whether one player has favorited the subnet.
     *
     * @param playerId Player UUID that owns the preference.
     * @param handle   Stable subnet handle.
     * @return Favorite state.
     */
    public boolean isFavorite(UUID playerId, CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerEntry playerEntry = this.playerEntries.get(playerId);
        if (playerEntry != null) {
            Boolean favorite = playerEntry.favoriteKeys.get(key(handle));
            if (favorite != null) {
                return favorite;
            }
            favorite = playerEntry.favoriteKeys.get(handle.subnetId());
            if (favorite != null) {
                return favorite;
            }
        }
        return isFavorite(handle);
    }

    /**
     * Returns the last loaded subnet handle for one player when available.
     *
     * @param playerId Player UUID that owns the preference.
     * @return Last loaded subnet handle, or {@code null}.
     */
    public @Nullable CellTerminalSubnetHandle getLastLoadedHandle(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerEntry playerEntry = this.playerEntries.get(playerId);
        return playerEntry != null ? playerEntry.lastLoadedHandle : null;
    }

    /**
     * Returns whether one subnet is the latest loaded target for one player.
     *
     * @param playerId Player UUID that owns the preference.
     * @param handle   Stable subnet handle.
     * @return Last-loaded state.
     */
    public boolean isLastLoaded(UUID playerId, CellTerminalSubnetHandle handle) {
        CellTerminalSubnetHandle playerLastLoaded = getLastLoadedHandle(playerId);
        return playerLastLoaded != null && playerLastLoaded.subnetId().equals(handle.subnetId());
    }

    private Entry entry(CellTerminalSubnetHandle handle) {
        return this.entries.computeIfAbsent(key(handle), ignored -> new Entry(handle));
    }

    private @Nullable Entry entryForRead(CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Entry entry = this.entries.get(key(handle));
        if (entry != null) {
            return entry;
        }
        entry = this.entries.get(handle.subnetId());
        return entry != null ? entry : entryBySubnetId(handle.subnetId());
    }

    private @Nullable Entry entryBySubnetId(String subnetId) {
        for (Entry entry : this.entries.values()) {
            if (entry.handle != null && entry.handle.subnetId().equals(subnetId)) {
                return entry;
            }
        }
        return null;
    }

    private PlayerEntry playerEntry(UUID playerId) {
        return this.playerEntries.computeIfAbsent(playerId, ignored -> new PlayerEntry());
    }

    private static final class Entry {
        @Nullable
        private final CellTerminalSubnetHandle handle;
        @Nullable
        private String displayName;
        private boolean favorite;

        private Entry(@Nullable CellTerminalSubnetHandle handle) {
            this.handle = handle;
        }

        private Entry(@Nullable CellTerminalSubnetHandle handle, @Nullable String displayName, boolean favorite) {
            this.handle = handle;
            this.displayName = displayName;
            this.favorite = favorite;
        }
    }

    private static final class PlayerEntry {
        private final Map<String, Boolean> favoriteKeys = new LinkedHashMap<>();
        @Nullable
        private CellTerminalSubnetHandle lastLoadedHandle;
    }
}
