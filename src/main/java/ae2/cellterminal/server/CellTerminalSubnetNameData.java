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
 * Shared Cell Terminal subnet display names stored at server-world scope.
 * <p>
 * Cell Terminal hosts keep player-local preferences in their own ledger. Subnet names live here so every terminal that
 * can see the same subnet renders the same user-facing name.
 */
public final class CellTerminalSubnetNameData extends AESavedData {
    public static final String ID = "ae2_cell_terminal_subnet_names";
    private static final String TAG_NAMES = "names";
    private static final String TAG_HANDLE = "handle";
    private static final String TAG_DISPLAY_NAME = "displayName";

    private final LinkedHashMap<String, Entry> displayNames = new LinkedHashMap<>();

    public CellTerminalSubnetNameData() {
        this(ID);
    }

    public CellTerminalSubnetNameData(String name) {
        super(name);
    }

    /**
     * Loads shared subnet names from the server's overworld storage.
     *
     * @param world Server world used to locate the owning server.
     * @return Shared subnet name data.
     */
    public static CellTerminalSubnetNameData get(World world) {
        if (!(world instanceof WorldServer worldServer)) {
            throw new IllegalStateException("CellTerminalSubnetNameData requires a server world");
        }

        MinecraftServer server = worldServer.getMinecraftServer();
        if (server == null) {
            throw new IllegalStateException("CellTerminalSubnetNameData requires a server");
        }

        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            throw new IllegalStateException("CellTerminalSubnetNameData requires an overworld");
        }

        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            throw new IllegalStateException("CellTerminalSubnetNameData requires world storage");
        }

        CellTerminalSubnetNameData result =
            (CellTerminalSubnetNameData) storage.getOrLoadData(CellTerminalSubnetNameData.class, ID);
        if (result == null) {
            result = new CellTerminalSubnetNameData();
            storage.setData(ID, result);
        }
        return result;
    }

    /**
     * Sets the shared display name for one subnet.
     *
     * @param handle      Stable subnet handle.
     * @param displayName New user-facing name.
     */
    public void setDisplayName(CellTerminalSubnetHandle handle, String displayName) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(displayName, "displayName");
        this.displayNames.put(handle.subnetId(), new Entry(handle, displayName));
        markDirty();
    }

    /**
     * Reads the shared display name for one subnet.
     *
     * @param handle Stable subnet handle.
     * @return Stored display name, or {@code null}.
     */
    public @Nullable String getDisplayName(CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Entry entry = this.displayNames.get(handle.subnetId());
        return entry == null ? null : entry.displayName;
    }

    /**
     * Reads a shared display name and migrates one legacy host-owned name when shared data has no entry yet.
     *
     * @param handle            Stable subnet handle.
     * @param legacyDisplayName Name loaded from a host ledger.
     * @return Shared or migrated display name, or {@code null}.
     */
    public @Nullable String getOrMigrateDisplayName(CellTerminalSubnetHandle handle,
                                                    @Nullable String legacyDisplayName) {
        Objects.requireNonNull(handle, "handle");
        Entry entry = this.displayNames.get(handle.subnetId());
        String displayName = entry == null ? null : entry.displayName;
        if (displayName != null || legacyDisplayName == null) {
            return displayName;
        }
        this.displayNames.put(handle.subnetId(), new Entry(handle, legacyDisplayName));
        markDirty();
        return legacyDisplayName;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.displayNames.clear();
        NBTTagList namesTag = nbt.getTagList(TAG_NAMES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < namesTag.tagCount(); index++) {
            NBTTagCompound nameTag = namesTag.getCompoundTagAt(index);
            CellTerminalSubnetHandle handle = CellTerminalSubnetHandle.fromTag(nameTag.getCompoundTag(TAG_HANDLE));
            if (handle != null && nameTag.hasKey(TAG_DISPLAY_NAME, Constants.NBT.TAG_STRING)) {
                this.displayNames.put(handle.subnetId(), new Entry(handle, nameTag.getString(TAG_DISPLAY_NAME)));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList namesTag = new NBTTagList();
        for (Entry entry : this.displayNames.values()) {
            NBTTagCompound nameTag = new NBTTagCompound();
            nameTag.setTag(TAG_HANDLE, entry.handle.toTag());
            nameTag.setString(TAG_DISPLAY_NAME, entry.displayName);
            namesTag.appendTag(nameTag);
        }
        compound.setTag(TAG_NAMES, namesTag);
        return compound;
    }

    private record Entry(CellTerminalSubnetHandle handle, String displayName) {
        private Entry {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}
