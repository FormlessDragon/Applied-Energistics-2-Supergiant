package ae2.server.services.compass;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.BitSet;

public final class CompassRegion extends WorldSavedData {
    private static final int CHUNKS_PER_REGION = 1024;
    private static final int BITMAP_LENGTH = CHUNKS_PER_REGION * CHUNKS_PER_REGION;

    private final Int2ObjectOpenHashMap<BitSet> sections = new Int2ObjectOpenHashMap<>();

    public CompassRegion(String name) {
        super(name);
    }

    public static CompassRegion get(WorldServer level, ChunkPos chunkPos) {
        int regionX = chunkPos.x / CHUNKS_PER_REGION;
        int regionZ = chunkPos.z / CHUNKS_PER_REGION;
        String name = "ae2_compass_" + regionX + "_" + regionZ;
        CompassRegion data = (CompassRegion) level.getPerWorldStorage().getOrLoadData(CompassRegion.class, name);
        if (data == null) {
            data = new CompassRegion(name);
            level.getPerWorldStorage().setData(name, data);
        }
        return data;
    }

    private static int getBitmapIndex(int cx, int cz) {
        cx &= CHUNKS_PER_REGION - 1;
        cz &= CHUNKS_PER_REGION - 1;
        return cx + cz * CHUNKS_PER_REGION;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        sections.clear();
        for (String key : nbt.getKeySet()) {
            if (!key.startsWith("section")) {
                continue;
            }

            String suffix = key.substring("section".length());
            try {
                int sectionIndex = Integer.parseInt(suffix);
                if (nbt.hasKey(key, Constants.NBT.TAG_BYTE_ARRAY)) {
                    BitSet section = BitSet.valueOf(nbt.getByteArray(key));
                    if (!section.isEmpty()) {
                        sections.put(sectionIndex, section);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        for (Int2ObjectMap.Entry<BitSet> entry : sections.int2ObjectEntrySet()) {
            BitSet section = entry.getValue();
            if (!section.isEmpty()) {
                compound.setByteArray("section" + entry.getIntKey(), section.toByteArray());
            }
        }
        return compound;
    }

    boolean hasCompassTarget(int cx, int cz) {
        int bitmapIndex = getBitmapIndex(cx, cz);
        for (BitSet bitmap : sections.values()) {
            if (bitmap.get(bitmapIndex)) {
                return true;
            }
        }
        return false;
    }

    boolean hasCompassTarget(int cx, int cz, int sectionIndex) {
        BitSet section = sections.get(sectionIndex);
        return section != null && section.get(getBitmapIndex(cx, cz));
    }

    void setHasCompassTarget(int cx, int cz, int sectionIndex, boolean hasTarget) {
        int bitmapIndex = getBitmapIndex(cx, cz);
        BitSet section = sections.get(sectionIndex);
        if (section == null) {
            if (hasTarget) {
                section = new BitSet(BITMAP_LENGTH);
                section.set(bitmapIndex);
                sections.put(sectionIndex, section);
                markDirty();
            }
            return;
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
    }
}
