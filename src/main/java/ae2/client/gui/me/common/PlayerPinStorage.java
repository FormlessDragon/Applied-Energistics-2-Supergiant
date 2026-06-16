package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@SideOnly(Side.CLIENT)
final class PlayerPinStorage {
    private static final int VERSION = 2;
    private static final String DATA_DIR = "ae2";
    private static final String DATA_FILE = "player_pins.dat";
    private static final String SERVER_DATA_DIR = "player_pins/servers";
    private static final String UNKNOWN_SERVER = "unknown_server";
    private static final String TAG_VERSION = "version";
    private static final String TAG_ROWS = "playerPinRows";
    private static final String TAG_KEYS = "playerPinnedKeys";
    private static final String TAG_SLOTS = "playerPinSlots";
    private static final String TAG_SLOT_INDEX = "slotIndex";
    private static final String TAG_KEY = "key";
    private static final String TAG_KIND = "kind";

    private PlayerPinStorage() {
    }

    static Data load() {
        File file = getFile();
        if (!file.isFile()) {
            return new Data(0, List.of());
        }

        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) {
                return Data.empty();
            }

            return loadFromTag(root);
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to load AE2 player pins from %s: %s", file, e);
            return Data.empty();
        }
    }

    static Data loadFromTag(NBTTagCompound root) {
        int rows = Math.clamp(root.getInteger(TAG_ROWS), 0, PinnedKeys.MAX_PLAYER_PIN_ROWS);
        ObjectArrayList<PinSlot> slots = root.hasKey(TAG_SLOTS, Constants.NBT.TAG_LIST)
            ? loadSlots(root.getTagList(TAG_SLOTS, Constants.NBT.TAG_COMPOUND))
            : migrateLegacyKeys(root.getTagList(TAG_KEYS, Constants.NBT.TAG_COMPOUND));
        rows = Math.max(rows, getRequiredRows(slots));
        return new Data(rows, slots);
    }

    static NBTTagCompound saveToTag(int rows, List<PinSlot> slots) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_VERSION, VERSION);
        root.setInteger(TAG_ROWS, Math.clamp(rows, 0, PinnedKeys.MAX_PLAYER_PIN_ROWS));
        NBTTagList list = new NBTTagList();
        ObjectArrayList<PinSlot> uniqueSlots = new ObjectArrayList<>(slots.size());
        for (PinSlot slot : slots) {
            if (slot.slotIndex() < 0
                || slot.slotIndex() >= PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED) {
                throw new IllegalArgumentException("Invalid player pin slot index: " + slot.slotIndex());
            }
            addUniqueSlot(uniqueSlots, slot);
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger(TAG_SLOT_INDEX, slot.slotIndex());
            slotTag.setString(TAG_KIND, slot.kind().name());
            slotTag.setTag(TAG_KEY, slot.key().toTagGeneric());
            list.appendTag(slotTag);
        }
        root.setTag(TAG_SLOTS, list);
        return root;
    }

    static void save(int rows, List<PinSlot> slots) {
        File file = getFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            AELog.warn("Failed to create AE2 client data directory %s", parent);
            return;
        }

        NBTTagCompound root = saveToTag(rows, slots);

        try {
            CompressedStreamTools.safeWrite(root, file);
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to save AE2 player pins to %s: %s", file, e);
        }
    }

    private static ObjectArrayList<PinSlot> loadSlots(NBTTagList list) {
        int maxSlots = PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED;
        ObjectArrayList<PinSlot> slots = new ObjectArrayList<>(Math.min(list.tagCount(), maxSlots));
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound slotTag = list.getCompoundTagAt(i);
            int slotIndex = slotTag.getInteger(TAG_SLOT_INDEX);
            if (slotIndex < 0 || slotIndex >= maxSlots) {
                throw new IllegalArgumentException("Invalid player pin slot index: " + slotIndex);
            }

            AEKey key = AEKey.fromTagGeneric(slotTag.getCompoundTag(TAG_KEY));
            if (key == null) {
                throw new IllegalArgumentException("Player pin slot is missing its key");
            }

            PinnedKeys.PinKind kind = PinnedKeys.PinKind.valueOf(slotTag.getString(TAG_KIND));
            addUniqueSlot(slots, new PinSlot(slotIndex, key, kind));
        }
        slots.sort(PinSlot.SLOT_INDEX_COMPARATOR);
        return slots;
    }

    private static ObjectArrayList<PinSlot> migrateLegacyKeys(NBTTagList list) {
        int keyCount = Math.min(list.tagCount(), PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED);
        ObjectArrayList<PinSlot> slots = new ObjectArrayList<>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            AEKey key = AEKey.fromTagGeneric(list.getCompoundTagAt(i));
            if (key != null && findByKey(slots, key) == null) {
                slots.add(new PinSlot(slots.size(), key, PinnedKeys.PinKind.AUTO));
            }
        }
        return slots;
    }

    private static void addUniqueSlot(ObjectArrayList<PinSlot> slots, PinSlot next) {
        if (findBySlotIndex(slots, next.slotIndex()) != null) {
            throw new IllegalArgumentException("Duplicate player pin slot index: " + next.slotIndex());
        }
        if (findByKey(slots, next.key()) != null) {
            throw new IllegalArgumentException("Duplicate player pin key: " + next.key());
        }
        slots.add(next);
    }

    private static int getRequiredRows(List<PinSlot> slots) {
        int rows = 0;
        for (PinSlot slot : slots) {
            rows = Math.max(rows, slot.slotIndex() / PinnedKeys.MAX_PINNED + 1);
        }
        return Math.min(rows, PinnedKeys.MAX_PLAYER_PIN_ROWS);
    }

    private static PinSlot findBySlotIndex(List<PinSlot> slots, int slotIndex) {
        for (PinSlot slot : slots) {
            if (slot.slotIndex() == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static PinSlot findByKey(List<PinSlot> slots, AEKey key) {
        for (PinSlot slot : slots) {
            if (slot.key().equals(key)) {
                return slot;
            }
        }
        return null;
    }

    private static File getFile() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isSingleplayer()) {
            var integratedServer = minecraft.getIntegratedServer();
            if (integratedServer != null) {
                File worldDir = integratedServer.getEntityWorld().getSaveHandler().getWorldDirectory();
                return new File(new File(worldDir, DATA_DIR), DATA_FILE);
            }
        }

        File dataDir = new File(new File(minecraft.gameDir, DATA_DIR), SERVER_DATA_DIR);
        return new File(new File(dataDir, getServerKey(minecraft.getCurrentServerData())), DATA_FILE);
    }

    private static String getServerKey(ServerData serverData) {
        String serverIp = serverData == null ? "" : sanitizePathSegment(serverData.serverIP);
        return serverIp.isEmpty() ? UNKNOWN_SERVER : serverIp;
    }

    private static String sanitizePathSegment(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.replaceAll("_+", "_");
    }

    record Data(int rows, List<PinSlot> slots) {
        static Data empty() {
            return new Data(0, List.of());
        }
    }

    record PinSlot(int slotIndex, AEKey key, PinnedKeys.PinKind kind) {
        private static final java.util.Comparator<PinSlot> SLOT_INDEX_COMPARATOR =
            java.util.Comparator.comparingInt(PinSlot::slotIndex);

        PinSlot {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
