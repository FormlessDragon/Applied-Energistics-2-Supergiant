package appeng.worldgen.meteorite;

import it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.BitSet;
import java.util.Random;

public class MeteoritesWorldData extends WorldSavedData {
    public static final String ID = "ae2_meteorites";
    private static final int REGION_SIZE = 16;
    private static final int CHUNK_RADIUS = 4;
    private static final int CHUNK_DIAMETER = CHUNK_RADIUS * 2 + 1;
    private static final int COMPLETED_BITMAP_LENGTH = CHUNK_DIAMETER * CHUNK_DIAMETER;

    private final Long2ObjectLinkedOpenHashMap<PlacedMeteoriteSettings> meteorites = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2BooleanLinkedOpenHashMap generatedChunks = new Long2BooleanLinkedOpenHashMap();
    private final Long2ObjectLinkedOpenHashMap<BitSet> completedMeteoriteChunks = new Long2ObjectLinkedOpenHashMap<>();

    public MeteoritesWorldData() {
        this(ID);
    }

    public MeteoritesWorldData(String name) {
        super(name);
    }

    public static MeteoritesWorldData get(World world) {
        if (!(world instanceof WorldServer worldServer)) {
            throw new IllegalStateException("MeteoritesWorldData requires a server world");
        }

        MapStorage storage = worldServer.getPerWorldStorage();
        MeteoritesWorldData result = (MeteoritesWorldData) storage.getOrLoadData(MeteoritesWorldData.class, ID);
        if (result == null) {
            result = new MeteoritesWorldData();
            storage.setData(ID, result);
        }
        return result;
    }

    private static int getCompletedChunkIndex(PlacedMeteoriteSettings settings, int chunkX, int chunkZ) {
        int meteorChunkX = settings.pos().getX() >> 4;
        int meteorChunkZ = settings.pos().getZ() >> 4;
        int relativeX = chunkX - meteorChunkX + CHUNK_RADIUS;
        int relativeZ = chunkZ - meteorChunkZ + CHUNK_RADIUS;
        if (relativeX < 0 || relativeX >= CHUNK_DIAMETER || relativeZ < 0 || relativeZ >= CHUNK_DIAMETER) {
            return -1;
        }
        return relativeX + relativeZ * CHUNK_DIAMETER;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX & 4294967295L) | (((long) chunkZ & 4294967295L) << 32);
    }

    private static int unpackChunkX(long packedChunk) {
        return (int) (packedChunk & 4294967295L);
    }

    private static int unpackChunkZ(long packedChunk) {
        return (int) (packedChunk >>> 32);
    }

    public void markGenerated(int chunkX, int chunkZ) {
        long key = packChunk(chunkX, chunkZ);
        if (!generatedChunks.containsKey(key)) {
            generatedChunks.put(key, true);
            markDirty();
        }
    }

    public boolean hasGenerated(int chunkX, int chunkZ) {
        return generatedChunks.containsKey(packChunk(chunkX, chunkZ));
    }

    public void addMeteorite(PlacedMeteoriteSettings settings) {
        long key = settings.pos().toLong();
        if (!meteorites.containsKey(key)) {
            meteorites.put(key, settings);
            markDirty();
        }
    }

    public Iterable<PlacedMeteoriteSettings> getNearByMeteorites(int chunkX, int chunkZ) {
        ObjectList<PlacedMeteoriteSettings> result = new ObjectArrayList<>();
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        for (PlacedMeteoriteSettings settings : meteorites.values()) {
            int meteorChunkX = settings.pos().getX() >> 4;
            int meteorChunkZ = settings.pos().getZ() >> 4;
            int meteorRegionX = Math.floorDiv(meteorChunkX, REGION_SIZE);
            int meteorRegionZ = Math.floorDiv(meteorChunkZ, REGION_SIZE);
            if (Math.abs(meteorRegionX - regionX) <= 1 && Math.abs(meteorRegionZ - regionZ) <= 1) {
                result.add(settings);
            }
        }
        return result;
    }

    public void completeChunk(World world, int chunkX, int chunkZ, Random random) {
        StructureBoundingBox chunkBounds = new StructureBoundingBox(chunkX << 4, 0, chunkZ << 4, (chunkX << 4) + 15,
            255, (chunkZ << 4) + 15);
        for (PlacedMeteoriteSettings settings : getNearByMeteorites(chunkX, chunkZ)) {
            completeChunk(world, settings, chunkX, chunkZ, random, chunkBounds);
        }
        markGenerated(chunkX, chunkZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        meteorites.clear();
        generatedChunks.clear();
        completedMeteoriteChunks.clear();

        NBTTagList meteoriteTags = nbt.getTagList("meteorites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < meteoriteTags.tagCount(); i++) {
            PlacedMeteoriteSettings settings = PlacedMeteoriteSettings.read(meteoriteTags.getCompoundTagAt(i));
            meteorites.put(settings.pos().toLong(), settings);
        }

        NBTTagList chunkTags = nbt.getTagList("generated", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chunkTags.tagCount(); i++) {
            NBTTagCompound chunkTag = chunkTags.getCompoundTagAt(i);
            int chunkX = chunkTag.getInteger("x");
            int chunkZ = chunkTag.getInteger("z");
            generatedChunks.put(packChunk(chunkX, chunkZ), true);
        }

        NBTTagList completedTags = nbt.getTagList("completed", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < completedTags.tagCount(); i++) {
            NBTTagCompound completedTag = completedTags.getCompoundTagAt(i);
            long meteoriteKey = completedTag.getLong("meteorite");
            if (completedTag.hasKey("chunks", Constants.NBT.TAG_BYTE_ARRAY)) {
                BitSet completedChunks = BitSet.valueOf(completedTag.getByteArray("chunks"));
                if (!completedChunks.isEmpty()) {
                    completedMeteoriteChunks.put(meteoriteKey, completedChunks);
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList generatedTags = new NBTTagList();
        for (long packedChunk : generatedChunks.keySet()) {
            NBTTagCompound chunkTag = new NBTTagCompound();
            chunkTag.setInteger("x", unpackChunkX(packedChunk));
            chunkTag.setInteger("z", unpackChunkZ(packedChunk));
            generatedTags.appendTag(chunkTag);
        }
        compound.setTag("generated", generatedTags);

        NBTTagList meteoriteTags = new NBTTagList();
        for (PlacedMeteoriteSettings settings : meteorites.values()) {
            meteoriteTags.appendTag(settings.write(new NBTTagCompound()));
        }
        compound.setTag("meteorites", meteoriteTags);

        NBTTagList completedTags = new NBTTagList();
        for (Long2ObjectMap.Entry<BitSet> entry : completedMeteoriteChunks.long2ObjectEntrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            NBTTagCompound completedTag = new NBTTagCompound();
            completedTag.setLong("meteorite", entry.getLongKey());
            completedTag.setByteArray("chunks", entry.getValue().toByteArray());
            completedTags.appendTag(completedTag);
        }
        compound.setTag("completed", completedTags);

        return compound;
    }

    public boolean markChunkCompleted(PlacedMeteoriteSettings settings, int chunkX, int chunkZ) {
        long meteoriteKey = settings.pos().toLong();
        int completionIndex = getCompletedChunkIndex(settings, chunkX, chunkZ);
        if (completionIndex < 0) {
            return false;
        }

        BitSet completedChunks = completedMeteoriteChunks.get(meteoriteKey);
        if (completedChunks == null) {
            completedChunks = new BitSet(COMPLETED_BITMAP_LENGTH);
            completedMeteoriteChunks.put(meteoriteKey, completedChunks);
        }

        if (completedChunks.get(completionIndex)) {
            return false;
        }

        completedChunks.set(completionIndex);
        markDirty();
        return true;
    }

    private void completeChunk(World world, PlacedMeteoriteSettings settings, int chunkX, int chunkZ, Random random,
                               StructureBoundingBox chunkBounds) {
        MeteoriteStructurePiece piece = new MeteoriteStructurePiece(settings);
        if (piece.intersectsChunk(chunkX, chunkZ) && markChunkCompleted(settings, chunkX, chunkZ)) {
            piece.postProcess(world, random, chunkBounds);
        }
    }
}
