package ae2.worldgen.meteorite;

import ae2.core.AELog;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants.NBT;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegacySpawnDataMigration {
    private static final Pattern SPAWN_DATA_FILE = Pattern.compile("(-?\\d+)_(-?\\d+)_(-?\\d+)\\.dat");
    private static final Pattern GENERATED_CHUNK_KEY = Pattern.compile("(-?\\d+),(-?\\d+)");
    private static final Pattern COMPASS_TARGET_KEY = Pattern.compile("\\d+");

    private LegacySpawnDataMigration() {
    }

    static String getCurrentSignature(WorldServer world) {
        File spawnDir = MeteoritesWorldData.getLegacySpawnDataDirectory(world);
        File[] files = spawnDir.listFiles();
        if (files == null) {
            return getSignature(world.provider.getDimension(), new File[0]);
        }

        int dimension = world.provider.getDimension();
        File[] matchingFiles = getSpawnDataFilesForDimension(files, dimension);
        Arrays.sort(matchingFiles, Comparator.comparing(File::getName));
        return getSignature(dimension, matchingFiles);
    }

    static Result migrate(WorldServer world, MeteoritesWorldData target) {
        File spawnDir = MeteoritesWorldData.getLegacySpawnDataDirectory(world);
        File[] files = spawnDir.listFiles();
        if (files == null) {
            return new Result(false, target.retainFailedLegacySpawnDataFiles(Set.of()), true);
        }

        int dimension = world.provider.getDimension();
        boolean changed = false;
        boolean stateChanged = false;
        boolean complete = true;
        Set<String> retainedFailedFiles = new HashSet<>();
        for (File file : files) {
            Matcher fileName = SPAWN_DATA_FILE.matcher(file.getName());
            if (!file.isFile() || !fileName.matches()) {
                continue;
            }

            OptionalInt fileDimension = parseInt(fileName.group(1), "legacy spawn data dimension", file);
            if (fileDimension.isEmpty()) {
                complete = false;
                continue;
            }
            if (fileDimension.getAsInt() != dimension) {
                continue;
            }

            retainedFailedFiles.add(file.getName());
            if (target.isUnchangedFailedLegacySpawnDataFile(file)) {
                complete = false;
                continue;
            }

            NBTTagCompound tag = readCompressed(file);
            if (tag == null) {
                AELog.warn("Failed to read legacy meteorite spawn data file %s", file);
                stateChanged |= target.recordFailedLegacySpawnDataFile(file);
                complete = false;
                continue;
            }
            stateChanged |= target.clearFailedLegacySpawnDataFile(file);

            Result generatedChunks = migrateGeneratedChunks(tag, target, file);
            changed |= generatedChunks.changed();
            Result compassTargets = migrateCompassTargets(tag, target);
            changed |= compassTargets.changed();
        }
        stateChanged |= target.retainFailedLegacySpawnDataFiles(retainedFailedFiles);
        return new Result(changed, stateChanged, complete);
    }

    private static Result migrateGeneratedChunks(NBTTagCompound tag, MeteoritesWorldData target, File file) {
        boolean changed = false;
        for (String key : tag.getKeySet()) {
            Matcher generatedChunk = GENERATED_CHUNK_KEY.matcher(key);
            if (generatedChunk.matches() && tag.getBoolean(key)) {
                OptionalInt chunkX = parseInt(generatedChunk.group(1), "legacy generated chunk x", file);
                OptionalInt chunkZ = parseInt(generatedChunk.group(2), "legacy generated chunk z", file);
                if (chunkX.isPresent() && chunkZ.isPresent()) {
                    changed |= target.markGenerated(chunkX.getAsInt(), chunkZ.getAsInt());
                }
            }
        }
        return new Result(changed, false, true);
    }

    private static Result migrateCompassTargets(NBTTagCompound tag, MeteoritesWorldData target) {
        boolean changed = false;
        for (String key : tag.getKeySet()) {
            if (!COMPASS_TARGET_KEY.matcher(key).matches()) {
                continue;
            }
            if (!tag.hasKey(key, NBT.TAG_COMPOUND)) {
                continue;
            }

            NBTTagCompound meteorite = tag.getCompoundTag(key);
            if (!meteorite.hasKey("x", NBT.TAG_INT)
                || !meteorite.hasKey("y", NBT.TAG_INT)
                || !meteorite.hasKey("z", NBT.TAG_INT)) {
                continue;
            }

            changed |= target.addCompassTarget(new BlockPos(meteorite.getInteger("x"), meteorite.getInteger("y"),
                meteorite.getInteger("z")));
        }
        return new Result(changed, false, true);
    }

    private static NBTTagCompound readCompressed(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isSpawnDataFileForDimension(File file, int dimension) {
        if (!file.isFile()) {
            return false;
        }

        Matcher fileName = SPAWN_DATA_FILE.matcher(file.getName());
        if (!fileName.matches()) {
            return false;
        }

        try {
            return Integer.parseInt(fileName.group(1)) == dimension;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static File[] getSpawnDataFilesForDimension(File[] files, int dimension) {
        int matchingCount = 0;
        for (File file : files) {
            if (isSpawnDataFileForDimension(file, dimension)) {
                matchingCount++;
            }
        }

        File[] matchingFiles = new File[matchingCount];
        int index = 0;
        for (File file : files) {
            if (isSpawnDataFileForDimension(file, dimension)) {
                matchingFiles[index++] = file;
            }
        }
        return matchingFiles;
    }

    private static String getSignature(int dimension, File[] files) {
        StringBuilder signature = new StringBuilder("v1:")
            .append(dimension);
        for (File file : files) {
            signature.append('|')
                     .append(file.getName())
                     .append(':')
                     .append(file.length())
                     .append(':')
                     .append(file.lastModified());
        }
        return signature.toString();
    }

    private static OptionalInt parseInt(String value, String description, File file) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            AELog.warn("Skipping invalid %s Value %s in legacy meteorite spawn data file %s", description, value, file);
            return OptionalInt.empty();
        }
    }

    record Result(boolean changed, boolean stateChanged, boolean complete) {
    }
}
