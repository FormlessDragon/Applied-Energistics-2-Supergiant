package ae2.worldgen.meteorite;

import ae2.server.services.compass.ServerCompassService;
import ae2.util.MeteoriteCompassSearch;
import it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import java.io.File;
import java.util.BitSet;
import java.util.Collection;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class MeteoritesWorldData extends WorldSavedData {
    public static final String ID = "ae2_meteorites";
    // Used for completion bookkeeping; compass search regions follow MeteoriteCompassSearch.REGION_SIZE_CHUNKS.
    private static final int COMPLETION_REGION_SIZE_CHUNKS = 16;
    private static final int CHUNK_RADIUS = 4;
    private static final int CHUNK_DIAMETER = CHUNK_RADIUS * 2 + 1;
    private static final int COMPLETED_BITMAP_LENGTH = CHUNK_DIAMETER * CHUNK_DIAMETER;
    private static final int MAX_COMPLETED_BITMAP_BYTES = (COMPLETED_BITMAP_LENGTH + Byte.SIZE - 1) / Byte.SIZE;
    private static final String TAG_COMPASS_TARGETS = "compassTargets";
    private static final String TAG_TARGET_POS = "pos";
    private static final String TAG_LEGACY_SPAWN_DATA_CHECKED = "legacySpawnDataChecked";
    private static final String TAG_LEGACY_SPAWN_DATA_SIGNATURE = "legacySpawnDataSignature";
    private static final String TAG_LEGACY_SPAWN_DATA_FAILED_FILES = "legacySpawnDataFailedFiles";
    private static final String TAG_FAILED_FILE_NAME = "name";
    private static final String TAG_FAILED_FILE_LENGTH = "length";
    private static final String TAG_FAILED_FILE_LAST_MODIFIED = "lastModified";

    private final Long2ObjectLinkedOpenHashMap<PlacedMeteoriteSettings> meteorites = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ObjectList<PlacedMeteoriteSettings>> meteoritesByCompassRegion =
        new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ObjectList<PlacedMeteoriteSettings>> meteoritesByCompletionRegion =
        new Long2ObjectOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<BlockPos> compassTargets = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ObjectList<BlockPos>> compassTargetsByRegion = new Long2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<SectionKey, ObjectList<BlockPos>> compassTargetsBySection =
        new Object2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap compassTargetCountsByChunk = new Long2IntOpenHashMap();
    private final Long2BooleanLinkedOpenHashMap generatedChunks = new Long2BooleanLinkedOpenHashMap();
    private final Long2ObjectLinkedOpenHashMap<BitSet> completedMeteoriteChunks = new Long2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, LegacySpawnDataFileSignature> legacySpawnDataFailedFiles =
        new Object2ObjectOpenHashMap<>();
    private boolean legacySpawnDataChecked;
    private String legacySpawnDataSignature;

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
        String currentLegacySpawnDataSignature = LegacySpawnDataMigration.getCurrentSignature(worldServer);
        if (result.legacySpawnDataChecked && result.legacySpawnDataSignature == null) {
            result.legacySpawnDataSignature = currentLegacySpawnDataSignature;
            result.markDirty();
        } else if (!result.legacySpawnDataChecked
            || !Objects.equals(result.legacySpawnDataSignature, currentLegacySpawnDataSignature)) {
            LegacySpawnDataMigration.Result migrationResult = LegacySpawnDataMigration.migrate(worldServer, result);
            result.legacySpawnDataChecked = migrationResult.complete();
            if (migrationResult.complete()) {
                result.legacySpawnDataSignature = currentLegacySpawnDataSignature;
            }
            if (migrationResult.changed() || migrationResult.stateChanged() || migrationResult.complete()
                || !Objects.equals(result.legacySpawnDataSignature, currentLegacySpawnDataSignature)) {
                result.markDirty();
            }
            if (migrationResult.changed()) {
                ServerCompassService.clearCacheAndNotifyClients(worldServer);
            }
        }
        return result;
    }

    static File getLegacySpawnDataDirectory(WorldServer world) {
        File worldDir = world.getSaveHandler().getWorldDirectory();
        return new File(new File(worldDir, "AE2"), "spawndata");
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

    private static long packCompassRegion(int regionX, int regionZ) {
        return packChunk(regionX, regionZ);
    }

    private static long packCompletionRegion(int regionX, int regionZ) {
        return packChunk(regionX, regionZ);
    }

    private static long getCompassRegionKey(PlacedMeteoriteSettings settings) {
        return getCompassRegionKey(settings.pos());
    }

    private static long getCompassRegionKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return packCompassRegion(MeteoriteCompassSearch.getRegion(chunkX), MeteoriteCompassSearch.getRegion(chunkZ));
    }

    private static long getCompletionRegionKey(PlacedMeteoriteSettings settings) {
        BlockPos pos = settings.pos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return packCompletionRegion(Math.floorDiv(chunkX, COMPLETION_REGION_SIZE_CHUNKS),
            Math.floorDiv(chunkZ, COMPLETION_REGION_SIZE_CHUNKS));
    }

    private static SectionKey getCompassSectionKey(int chunkX, int chunkZ, int sectionIndex) {
        return new SectionKey(chunkX, chunkZ, sectionIndex);
    }

    private static SectionKey getCompassSectionKey(BlockPos pos) {
        return getCompassSectionKey(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4);
    }

    private static int unpackChunkX(long packedChunk) {
        return (int) (packedChunk & 4294967295L);
    }

    private static int unpackChunkZ(long packedChunk) {
        return (int) (packedChunk >>> 32);
    }

    private static BitSet readCompletedChunks(NBTTagCompound tag) {
        byte[] chunks = tag.getByteArray("chunks");
        if (chunks.length > MAX_COMPLETED_BITMAP_BYTES) {
            return new BitSet(COMPLETED_BITMAP_LENGTH);
        }
        BitSet completedChunks = BitSet.valueOf(chunks);
        completedChunks.clear(COMPLETED_BITMAP_LENGTH, completedChunks.length());
        return completedChunks;
    }

    private static boolean hasMeteoriteSettings(NBTTagCompound tag) {
        return tag.hasKey(Constants.TAG_POS, NBT.TAG_LONG)
            && tag.hasKey(Constants.TAG_RADIUS, NBT.TAG_FLOAT)
            && tag.hasKey(Constants.TAG_CRATER, NBT.TAG_BYTE)
            && tag.hasKey(Constants.TAG_FALLOUT, NBT.TAG_BYTE)
            && tag.hasKey(Constants.TAG_PURE, NBT.TAG_BYTE)
            && tag.hasKey(Constants.TAG_LAKE, NBT.TAG_BYTE);
    }

    private static boolean containsSameTargets(ObjectList<BlockPos> previousTargets, Collection<BlockPos> targets) {
        if (previousTargets.size() != targets.size()) {
            return false;
        }

        LongOpenHashSet previousTargetSet = new LongOpenHashSet(previousTargets.size());
        for (BlockPos pos : previousTargets) {
            previousTargetSet.add(pos.toLong());
        }
        for (BlockPos pos : targets) {
            if (!previousTargetSet.contains(pos.toLong())) {
                return false;
            }
        }
        return true;
    }

    public boolean hasGenerated(int chunkX, int chunkZ) {
        return generatedChunks.containsKey(packChunk(chunkX, chunkZ));
    }

    public boolean markGenerated(int chunkX, int chunkZ) {
        long key = packChunk(chunkX, chunkZ);
        if (!generatedChunks.containsKey(key)) {
            generatedChunks.put(key, true);
            markDirty();
            return true;
        }
        return false;
    }

    public void addMeteorite(WorldServer world, PlacedMeteoriteSettings settings) {
        long key = settings.pos().toLong();
        boolean changed = removeCompassTarget(settings.pos());
        if (!meteorites.containsKey(key)) {
            meteorites.put(key, settings);
            addToCompassRegionBucket(settings);
            addToCompletionRegionBucket(settings);
            changed = true;
        }
        if (changed) {
            markDirty();
            ServerCompassService.clearCacheAndNotifyClients(world);
        }
    }

    private void addToCompassRegionBucket(PlacedMeteoriteSettings settings) {
        long key = getCompassRegionKey(settings);
        ObjectList<PlacedMeteoriteSettings> bucket = meteoritesByCompassRegion.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<>();
            meteoritesByCompassRegion.put(key, bucket);
        }
        bucket.add(settings);
    }

    private void addToCompletionRegionBucket(PlacedMeteoriteSettings settings) {
        long key = getCompletionRegionKey(settings);
        ObjectList<PlacedMeteoriteSettings> bucket = meteoritesByCompletionRegion.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<>();
            meteoritesByCompletionRegion.put(key, bucket);
        }
        bucket.add(settings);
    }

    public boolean syncCompassTargetsInSection(int chunkX, int chunkZ, int sectionIndex, Collection<BlockPos> targets) {
        boolean changed = false;
        ObjectList<BlockPos> previousTargets = compassTargetsBySection.get(getCompassSectionKey(chunkX, chunkZ,
            sectionIndex));
        if (previousTargets != null && containsSameTargets(previousTargets, targets)) {
            return false;
        }
        if (previousTargets != null) {
            for (BlockPos pos : new ObjectArrayList<>(previousTargets)) {
                changed |= removeCompassTarget(pos);
            }
        }
        for (BlockPos pos : targets) {
            changed |= addCompassTargetIfExtra(pos);
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean hasCompassTargetsInChunk(int chunkX, int chunkZ) {
        return compassTargetCountsByChunk.get(packChunk(chunkX, chunkZ)) > 0;
    }

    boolean addCompassTarget(BlockPos pos) {
        return addCompassTargetIfExtra(pos);
    }

    boolean isUnchangedFailedLegacySpawnDataFile(File file) {
        LegacySpawnDataFileSignature signature = legacySpawnDataFailedFiles.get(file.getName());
        return signature != null && signature.matches(file);
    }

    boolean recordFailedLegacySpawnDataFile(File file) {
        LegacySpawnDataFileSignature signature = LegacySpawnDataFileSignature.of(file);
        LegacySpawnDataFileSignature previous = legacySpawnDataFailedFiles.put(file.getName(), signature);
        return !signature.equals(previous);
    }

    boolean clearFailedLegacySpawnDataFile(File file) {
        return legacySpawnDataFailedFiles.remove(file.getName()) != null;
    }

    boolean retainFailedLegacySpawnDataFiles(Set<String> fileNames) {
        return legacySpawnDataFailedFiles.keySet().removeIf(name -> !fileNames.contains(name));
    }

    private boolean addCompassTargetIfExtra(BlockPos pos) {
        if (meteorites.containsKey(pos.toLong())) {
            return false;
        }

        long key = pos.toLong();
        if (compassTargets.containsKey(key)) {
            return false;
        }

        compassTargets.put(key, pos);
        addToCompassTargetBucket(pos);
        addToCompassTargetSectionBucket(pos);
        addToCompassTargetChunkCount(pos);
        return true;
    }

    private boolean removeCompassTarget(BlockPos pos) {
        BlockPos removed = compassTargets.remove(pos.toLong());
        if (removed == null) {
            return false;
        }

        removeFromCompassTargetBucket(removed);
        removeFromCompassTargetSectionBucket(removed);
        removeFromCompassTargetChunkCount(removed);
        return true;
    }

    private void addToCompassTargetBucket(BlockPos pos) {
        long key = getCompassRegionKey(pos);
        ObjectList<BlockPos> bucket = compassTargetsByRegion.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<>();
            compassTargetsByRegion.put(key, bucket);
        }
        bucket.add(pos);
    }

    private void addToCompassTargetSectionBucket(BlockPos pos) {
        SectionKey key = getCompassSectionKey(pos);
        ObjectList<BlockPos> bucket = compassTargetsBySection.computeIfAbsent(key, ignored -> new ObjectArrayList<>());
        bucket.add(pos);
    }

    private void removeFromCompassTargetBucket(BlockPos pos) {
        long key = getCompassRegionKey(pos);
        ObjectList<BlockPos> bucket = compassTargetsByRegion.get(key);
        if (bucket == null) {
            return;
        }

        bucket.remove(pos);
        if (bucket.isEmpty()) {
            compassTargetsByRegion.remove(key);
        }
    }

    private void removeFromCompassTargetSectionBucket(BlockPos pos) {
        SectionKey key = getCompassSectionKey(pos);
        ObjectList<BlockPos> bucket = compassTargetsBySection.get(key);
        if (bucket == null) {
            return;
        }

        bucket.remove(pos);
        if (bucket.isEmpty()) {
            compassTargetsBySection.remove(key);
        }
    }

    private void addToCompassTargetChunkCount(BlockPos pos) {
        long key = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
        compassTargetCountsByChunk.addTo(key, 1);
    }

    private void removeFromCompassTargetChunkCount(BlockPos pos) {
        long key = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
        int count = compassTargetCountsByChunk.get(key);
        if (count <= 1) {
            compassTargetCountsByChunk.remove(key);
        } else {
            compassTargetCountsByChunk.put(key, count - 1);
        }
    }

    public Iterable<PlacedMeteoriteSettings> getNearByMeteorites(int chunkX, int chunkZ) {
        ObjectList<PlacedMeteoriteSettings> result = new ObjectArrayList<>();
        int regionX = Math.floorDiv(chunkX, COMPLETION_REGION_SIZE_CHUNKS);
        int regionZ = Math.floorDiv(chunkZ, COMPLETION_REGION_SIZE_CHUNKS);
        for (int candidateRegionX = regionX - 1; candidateRegionX <= regionX + 1; candidateRegionX++) {
            for (int candidateRegionZ = regionZ - 1; candidateRegionZ <= regionZ + 1; candidateRegionZ++) {
                ObjectList<PlacedMeteoriteSettings> bucket =
                    meteoritesByCompletionRegion.get(packCompletionRegion(candidateRegionX, candidateRegionZ));
                if (bucket != null) {
                    result.addAll(bucket);
                }
            }
        }
        return result;
    }

    public ObjectList<BlockPos> getMeteoriteTargetsInCompassRegion(int regionX, int regionZ,
                                                                   Predicate<BlockPos> includeGeneratedMeteoriteTarget) {
        ObjectList<BlockPos> result = new ObjectArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();

        ObjectList<PlacedMeteoriteSettings> meteoriteBucket =
            meteoritesByCompassRegion.get(packCompassRegion(regionX, regionZ));
        if (meteoriteBucket != null) {
            for (PlacedMeteoriteSettings settings : meteoriteBucket) {
                BlockPos pos = settings.pos();
                if (includeGeneratedMeteoriteTarget.test(pos) && seen.add(pos.toLong())) {
                    result.add(pos);
                }
            }
        }

        ObjectList<BlockPos> compassTargetBucket = compassTargetsByRegion.get(packCompassRegion(regionX, regionZ));
        if (compassTargetBucket != null) {
            for (BlockPos pos : compassTargetBucket) {
                if (seen.add(pos.toLong())) {
                    result.add(pos);
                }
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
        meteoritesByCompassRegion.clear();
        meteoritesByCompletionRegion.clear();
        compassTargets.clear();
        compassTargetsByRegion.clear();
        compassTargetsBySection.clear();
        compassTargetCountsByChunk.clear();
        generatedChunks.clear();
        completedMeteoriteChunks.clear();
        legacySpawnDataFailedFiles.clear();
        legacySpawnDataChecked = nbt.getBoolean(TAG_LEGACY_SPAWN_DATA_CHECKED);
        legacySpawnDataSignature = nbt.hasKey(TAG_LEGACY_SPAWN_DATA_SIGNATURE, NBT.TAG_STRING)
            ? nbt.getString(TAG_LEGACY_SPAWN_DATA_SIGNATURE)
            : null;

        NBTTagList meteoriteTags = nbt.getTagList("meteorites", NBT.TAG_COMPOUND);
        for (int i = 0; i < meteoriteTags.tagCount(); i++) {
            NBTTagCompound meteoriteTag = meteoriteTags.getCompoundTagAt(i);
            if (!hasMeteoriteSettings(meteoriteTag)) {
                continue;
            }
            PlacedMeteoriteSettings settings = PlacedMeteoriteSettings.read(meteoriteTag);
            meteorites.put(settings.pos().toLong(), settings);
        }
        for (PlacedMeteoriteSettings settings : meteorites.values()) {
            addToCompassRegionBucket(settings);
            addToCompletionRegionBucket(settings);
        }

        NBTTagList compassTargetTags = nbt.getTagList(TAG_COMPASS_TARGETS, NBT.TAG_COMPOUND);
        for (int i = 0; i < compassTargetTags.tagCount(); i++) {
            NBTTagCompound targetTag = compassTargetTags.getCompoundTagAt(i);
            if (!targetTag.hasKey(TAG_TARGET_POS, NBT.TAG_LONG)) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(targetTag.getLong(TAG_TARGET_POS));
            addCompassTarget(pos);
        }

        NBTTagList chunkTags = nbt.getTagList("generated", NBT.TAG_COMPOUND);
        for (int i = 0; i < chunkTags.tagCount(); i++) {
            NBTTagCompound chunkTag = chunkTags.getCompoundTagAt(i);
            if (!chunkTag.hasKey("x", NBT.TAG_INT) || !chunkTag.hasKey("z", NBT.TAG_INT)) {
                continue;
            }
            int chunkX = chunkTag.getInteger("x");
            int chunkZ = chunkTag.getInteger("z");
            generatedChunks.put(packChunk(chunkX, chunkZ), true);
        }

        NBTTagList completedTags = nbt.getTagList("completed", NBT.TAG_COMPOUND);
        for (int i = 0; i < completedTags.tagCount(); i++) {
            NBTTagCompound completedTag = completedTags.getCompoundTagAt(i);
            if (!completedTag.hasKey("meteorite", NBT.TAG_LONG)) {
                continue;
            }
            long meteoriteKey = completedTag.getLong("meteorite");
            if (completedTag.hasKey("chunks", NBT.TAG_BYTE_ARRAY)) {
                BitSet completedChunks = readCompletedChunks(completedTag);
                if (!completedChunks.isEmpty()) {
                    completedMeteoriteChunks.put(meteoriteKey, completedChunks);
                }
            }
        }

        NBTTagList failedLegacyFiles = nbt.getTagList(TAG_LEGACY_SPAWN_DATA_FAILED_FILES, NBT.TAG_COMPOUND);
        for (int i = 0; i < failedLegacyFiles.tagCount(); i++) {
            NBTTagCompound failedFile = failedLegacyFiles.getCompoundTagAt(i);
            if (failedFile.hasKey(TAG_FAILED_FILE_NAME, NBT.TAG_STRING)
                && failedFile.hasKey(TAG_FAILED_FILE_LENGTH, NBT.TAG_LONG)
                && failedFile.hasKey(TAG_FAILED_FILE_LAST_MODIFIED, NBT.TAG_LONG)) {
                LegacySpawnDataFileSignature signature = LegacySpawnDataFileSignature.read(failedFile);
                legacySpawnDataFailedFiles.put(signature.name(), signature);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setBoolean(TAG_LEGACY_SPAWN_DATA_CHECKED, legacySpawnDataChecked);
        if (legacySpawnDataSignature != null) {
            compound.setString(TAG_LEGACY_SPAWN_DATA_SIGNATURE, legacySpawnDataSignature);
        }

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

        NBTTagList compassTargetTags = new NBTTagList();
        for (BlockPos pos : compassTargets.values()) {
            NBTTagCompound targetTag = new NBTTagCompound();
            targetTag.setLong(TAG_TARGET_POS, pos.toLong());
            compassTargetTags.appendTag(targetTag);
        }
        compound.setTag(TAG_COMPASS_TARGETS, compassTargetTags);

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

        NBTTagList failedLegacyFiles = new NBTTagList();
        for (LegacySpawnDataFileSignature signature : legacySpawnDataFailedFiles.values()) {
            failedLegacyFiles.appendTag(signature.write(new NBTTagCompound()));
        }
        compound.setTag(TAG_LEGACY_SPAWN_DATA_FAILED_FILES, failedLegacyFiles);

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

    private record SectionKey(int chunkX, int chunkZ, int sectionIndex) {
    }

    private record LegacySpawnDataFileSignature(String name, long length, long lastModified) {
        static LegacySpawnDataFileSignature of(File file) {
            return new LegacySpawnDataFileSignature(file.getName(), file.length(), file.lastModified());
        }

        static LegacySpawnDataFileSignature read(NBTTagCompound tag) {
            return new LegacySpawnDataFileSignature(tag.getString(TAG_FAILED_FILE_NAME),
                tag.getLong(TAG_FAILED_FILE_LENGTH),
                tag.getLong(TAG_FAILED_FILE_LAST_MODIFIED));
        }

        boolean matches(File file) {
            return name.equals(file.getName()) && length == file.length() && lastModified == file.lastModified();
        }

        NBTTagCompound write(NBTTagCompound tag) {
            tag.setString(TAG_FAILED_FILE_NAME, name);
            tag.setLong(TAG_FAILED_FILE_LENGTH, length);
            tag.setLong(TAG_FAILED_FILE_LAST_MODIFIED, lastModified);
            return tag;
        }
    }
}
