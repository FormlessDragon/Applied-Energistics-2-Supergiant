package ae2.server.services.compass;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.BitSet;

public final class CompassRegion extends WorldSavedData {
    // Persistent storage/scanning shard size. Meteorite generation and compass queries use 24-chunk regions.
    private static final int CHUNKS_PER_STORAGE_REGION = 1024;
    private static final int BITMAP_LENGTH = CHUNKS_PER_STORAGE_REGION * CHUNKS_PER_STORAGE_REGION;
    private static final int MAX_BITMAP_BYTES = BITMAP_LENGTH / Byte.SIZE;
    private static final int DATA_VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_CHECKED = "checked";
    private static final String TAG_SECTION_PREFIX = "section";
    private static final int MIN_SECTION_INDEX = 0;
    private static final int MAX_SECTION_INDEX = 15;

    private final Int2ObjectOpenHashMap<BitSet> sections = new Int2ObjectOpenHashMap<>();
    private BitSet checkedChunks = new BitSet(BITMAP_LENGTH);

    public CompassRegion(String name) {
        super(name);
    }

    public static CompassRegion get(WorldServer level, ChunkPos chunkPos) {
        int regionX = Math.floorDiv(chunkPos.x, CHUNKS_PER_STORAGE_REGION);
        int regionZ = Math.floorDiv(chunkPos.z, CHUNKS_PER_STORAGE_REGION);
        String name = "ae2_compass_" + regionX + "_" + regionZ;
        CompassRegion data = (CompassRegion) level.getPerWorldStorage().getOrLoadData(CompassRegion.class, name);
        if (data == null) {
            data = new CompassRegion(name);
            level.getPerWorldStorage().setData(name, data);
        }
        return data;
    }

    private static int getBitmapIndex(int cx, int cz) {
        cx &= CHUNKS_PER_STORAGE_REGION - 1;
        cz &= CHUNKS_PER_STORAGE_REGION - 1;
        return cx + cz * CHUNKS_PER_STORAGE_REGION;
    }

    private static BitSet readBitmap(NBTTagCompound nbt, String key) {
        byte[] data = nbt.getByteArray(key);
        if (data.length > MAX_BITMAP_BYTES) {
            return new BitSet(BITMAP_LENGTH);
        }
        return BitSet.valueOf(data);
    }

    private static boolean isValidSectionIndex(int sectionIndex) {
        return sectionIndex >= MIN_SECTION_INDEX && sectionIndex <= MAX_SECTION_INDEX;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        sections.clear();
        boolean currentVersion = nbt.getInteger(TAG_VERSION) >= DATA_VERSION;
        checkedChunks = currentVersion && nbt.hasKey(TAG_CHECKED, NBT.TAG_BYTE_ARRAY)
            ? readBitmap(nbt, TAG_CHECKED)
            : new BitSet(BITMAP_LENGTH);
        for (String key : nbt.getKeySet()) {
            if (!key.startsWith(TAG_SECTION_PREFIX)) {
                continue;
            }

            String suffix = key.substring(TAG_SECTION_PREFIX.length());
            try {
                int sectionIndex = Integer.parseInt(suffix);
                if (isValidSectionIndex(sectionIndex) && nbt.hasKey(key, NBT.TAG_BYTE_ARRAY)) {
                    BitSet section = readBitmap(nbt, key);
                    if (!section.isEmpty()) {
                        sections.put(sectionIndex, section);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (!currentVersion) {
            markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger(TAG_VERSION, DATA_VERSION);
        if (!checkedChunks.isEmpty()) {
            compound.setByteArray(TAG_CHECKED, checkedChunks.toByteArray());
        }
        for (Int2ObjectMap.Entry<BitSet> entry : sections.int2ObjectEntrySet()) {
            BitSet section = entry.getValue();
            if (!section.isEmpty()) {
                compound.setByteArray(TAG_SECTION_PREFIX + entry.getIntKey(), section.toByteArray());
            }
        }
        return compound;
    }

    boolean isChunkChecked(int cx, int cz) {
        return checkedChunks.get(getBitmapIndex(cx, cz));
    }

    boolean markChunkChecked(int cx, int cz) {
        int bitmapIndex = getBitmapIndex(cx, cz);
        if (checkedChunks.get(bitmapIndex)) {
            return false;
        }

        checkedChunks.set(bitmapIndex);
        markDirty();
        return true;
    }

    boolean hasCompassTarget(int cx, int cz, int sectionIndex) {
        if (!isValidSectionIndex(sectionIndex)) {
            return false;
        }
        BitSet section = sections.get(sectionIndex);
        return section != null && section.get(getBitmapIndex(cx, cz));
    }

    boolean setHasCompassTarget(int cx, int cz, int sectionIndex, boolean hasTarget) {
        if (!isValidSectionIndex(sectionIndex)) {
            return false;
        }
        int bitmapIndex = getBitmapIndex(cx, cz);
        BitSet section = sections.get(sectionIndex);
        if (section == null) {
            if (hasTarget) {
                section = new BitSet(BITMAP_LENGTH);
                section.set(bitmapIndex);
                sections.put(sectionIndex, section);
                markDirty();
                return true;
            }
            return false;
        }

        boolean changed = section.get(bitmapIndex) != hasTarget;
        if (hasTarget) {
            section.set(bitmapIndex);
        } else {
            section.clear(bitmapIndex);
            if (section.isEmpty()) {
                sections.remove(sectionIndex);
            }
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }
}
