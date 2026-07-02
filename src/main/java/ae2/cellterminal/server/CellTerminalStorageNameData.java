package ae2.cellterminal.server;

import ae2.core.worlddata.AESavedData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Shared Cell Terminal storage/bus display names stored at server-world scope.
 * <p>
 * Drives, ME chests and storage buses do not carry a Cell Terminal display name on their own. Names live here keyed by
 * the target's stable identifier so every terminal that can see the same target renders the same user-facing name.
 */
public final class CellTerminalStorageNameData extends AESavedData {
    public static final String ID = "ae2_cell_terminal_storage_names";
    private static final String TAG_NAMES = "names";
    private static final String TAG_KEY = "key";
    private static final String TAG_DISPLAY_NAME = "displayName";

    private final LinkedHashMap<String, String> displayNames = new LinkedHashMap<>();

    public CellTerminalStorageNameData() {
        this(ID);
    }

    public CellTerminalStorageNameData(String name) {
        super(name);
    }

    public static CellTerminalStorageNameData get(World world) {
        if (!(world instanceof WorldServer worldServer)) {
            throw new IllegalStateException("CellTerminalStorageNameData requires a server world");
        }
        MinecraftServer server = worldServer.getMinecraftServer();
        if (server == null) {
            throw new IllegalStateException("CellTerminalStorageNameData requires a server");
        }
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            throw new IllegalStateException("CellTerminalStorageNameData requires an overworld");
        }
        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            throw new IllegalStateException("CellTerminalStorageNameData requires world storage");
        }
        CellTerminalStorageNameData result =
            (CellTerminalStorageNameData) storage.getOrLoadData(CellTerminalStorageNameData.class, ID);
        if (result == null) {
            result = new CellTerminalStorageNameData();
            storage.setData(ID, result);
        }
        return result;
    }

    public void setDisplayName(String key, String displayName) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isEmpty()) {
            if (this.displayNames.remove(key) != null) {
                markDirty();
            }
            return;
        }
        this.displayNames.put(key, displayName);
        markDirty();
    }

    public @Nullable String getDisplayName(String key) {
        Objects.requireNonNull(key, "key");
        return this.displayNames.get(key);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.displayNames.clear();
        NBTTagList namesTag = nbt.getTagList(TAG_NAMES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < namesTag.tagCount(); index++) {
            NBTTagCompound nameTag = namesTag.getCompoundTagAt(index);
            if (nameTag.hasKey(TAG_KEY, Constants.NBT.TAG_STRING)
                && nameTag.hasKey(TAG_DISPLAY_NAME, Constants.NBT.TAG_STRING)) {
                this.displayNames.put(nameTag.getString(TAG_KEY), nameTag.getString(TAG_DISPLAY_NAME));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList namesTag = new NBTTagList();
        for (var entry : this.displayNames.entrySet()) {
            NBTTagCompound nameTag = new NBTTagCompound();
            nameTag.setString(TAG_KEY, entry.getKey());
            nameTag.setString(TAG_DISPLAY_NAME, entry.getValue());
            namesTag.appendTag(nameTag);
        }
        compound.setTag(TAG_NAMES, namesTag);
        return compound;
    }
}
