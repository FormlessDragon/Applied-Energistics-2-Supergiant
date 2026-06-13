package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SideOnly(Side.CLIENT)
final class PlayerPinStorage {
    private static final int VERSION = 1;
    private static final String DATA_DIR = "ae2";
    private static final String DATA_FILE = "player_pins.dat";
    private static final String SERVER_DATA_DIR = "player_pins/servers";
    private static final String UNKNOWN_SERVER = "unknown_server";
    private static final String TAG_VERSION = "version";
    private static final String TAG_ROWS = "playerPinRows";
    private static final String TAG_KEYS = "playerPinnedKeys";

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
                return new Data(0, List.of());
            }

            int rows = Math.clamp(root.getInteger(TAG_ROWS), 0, PinnedKeys.MAX_PLAYER_PIN_ROWS);
            NBTTagList list = root.getTagList(TAG_KEYS, 10);
            int keyCount = Math.min(list.tagCount(), PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED);
            ObjectArrayList<AEKey> keys = new ObjectArrayList<>(keyCount);
            for (int i = 0; i < keyCount; i++) {
                AEKey key = AEKey.fromTagGeneric(list.getCompoundTagAt(i));
                if (key != null && !keys.contains(key)) {
                    keys.add(key);
                }
            }
            return new Data(rows, keys);
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to load AE2 player pins from %s: %s", file, e);
            return new Data(0, List.of());
        }
    }

    static void save(int rows, List<AEKey> keys) {
        File file = getFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            AELog.warn("Failed to create AE2 client data directory %s", parent);
            return;
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_VERSION, VERSION);
        root.setInteger(TAG_ROWS, Math.clamp(rows, 0, PinnedKeys.MAX_PLAYER_PIN_ROWS));
        NBTTagList list = new NBTTagList();
        for (AEKey key : keys) {
            list.appendTag(key.toTagGeneric());
        }
        root.setTag(TAG_KEYS, list);

        try {
            CompressedStreamTools.safeWrite(root, file);
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to save AE2 player pins to %s: %s", file, e);
        }
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

    record Data(int rows, List<AEKey> keys) {
    }
}
