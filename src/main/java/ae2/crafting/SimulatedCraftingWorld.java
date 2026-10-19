package ae2.crafting;

import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * A server-side stand-in for a client level, handed to the simulated player used by
 * {@link CraftingEventSimulation}.
 *
 * <p>Its only job is to report {@code isRemote == true} so that mods which gate their side effects on it (such
 * as Thaumcraft's crafting handler) take their client branch and stay out of the way. Everything that could
 * read or mutate real level state is stubbed out: reads answer with empty values and writes are discarded, so
 * a mod that ignores {@code isRemote} still cannot touch the live level.</p>
 *
 * <p>The instance is immutable after construction and safe to share; see {@link CraftingEventSimulation} for
 * the caching policy.</p>
 */
@SuppressWarnings("NullableProblems")
final class SimulatedCraftingWorld extends World {

    private final MinecraftServer server;

    SimulatedCraftingWorld(WorldServer source) {
        super(new StubSaveHandler(),
            new WorldInfo(source.getWorldInfo()),
            DimensionManager.createProviderFor(0),
            new Profiler(),
            true);
        this.server = source.getMinecraftServer();
        this.provider.setWorld(this);
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return false;
    }

    /**
     * Every chunk-backed read ({@code getLight}, {@code canSeeSky}, {@code getBiomeForCoordsBody}, ...) is
     * reachable from here, so answering with the empty chunk keeps all of them on the "no blocks here" answer
     * instead of tripping over the absent chunk provider.
     */
    @Override
    public @NonNull Chunk getChunk(int chunkX, int chunkZ) {
        return new EmptyChunk(this, chunkX, chunkZ);
    }

    @Override
    public @NonNull Chunk getChunk(BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public MinecraftServer getMinecraftServer() {
        return server;
    }

    @Override
    public @NonNull IBlockState getBlockState(@NonNull BlockPos pos) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public TileEntity getTileEntity(@NonNull BlockPos pos) {
        return null;
    }

    @Override
    public @NonNull Biome getBiome(@NonNull BlockPos pos) {
        return Biomes.PLAINS;
    }

    @Override
    public boolean isAirBlock(@NonNull BlockPos pos) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(@NonNull BlockPos pos) {
        return false;
    }

    @Override
    public boolean isBlockLoaded(@NonNull BlockPos pos, boolean allowEmpty) {
        return false;
    }

    @Override
    public boolean setBlockState(@NonNull BlockPos pos, @NonNull IBlockState newState) {
        return false;
    }

    @Override
    public boolean setBlockState(@NonNull BlockPos pos, @NonNull IBlockState newState, int flags) {
        return false;
    }

    @Override
    public boolean setBlockToAir(@NonNull BlockPos pos) {
        return false;
    }

    @Override
    public boolean spawnEntity(@NonNull Entity entityIn) {
        return false;
    }

    /**
     * Keeps the world off the disk: {@link net.minecraft.world.storage.MapStorage} short-circuits every file
     * operation as soon as {@link #getMapFileFromName} answers {@code null}, and the simulated world is never
     * asked to save anything else.
     */
    @SuppressWarnings("NullableProblems")
    private static final class StubSaveHandler implements ISaveHandler {
        @Override
        public WorldInfo loadWorldInfo() {
            return null;
        }

        @Override
        public void checkSessionLock() {
        }

        @Override
        public IChunkLoader getChunkLoader(@NonNull WorldProvider provider) {
            return null;
        }

        @Override
        public void saveWorldInfoWithPlayer(@NonNull WorldInfo worldInformation, @NonNull NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(@NonNull WorldInfo worldInformation) {
        }

        @Override
        public IPlayerFileData getPlayerNBTManager() {
            return null;
        }

        @Override
        public void flush() {
        }

        @Override
        public File getWorldDirectory() {
            return null;
        }

        @Override
        public File getMapFileFromName(@NonNull String mapName) {
            return null;
        }

        @Override
        public TemplateManager getStructureTemplateManager() {
            return null;
        }
    }

    private static class EmptyChunk extends Chunk {
        public EmptyChunk(World world, int x, int z) {
            super(world, x, z);
        }

        public boolean isAtLocation(int x, int z) {
            return this.x == x && this.z == z;
        }

        public int getHeightValue(int x, int z) {
            return 0;
        }

        public void generateHeightMap() {
        }

        public void generateSkylightMap() {
        }

        public @NonNull IBlockState getBlockState(@NonNull BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        public int getBlockLightOpacity(@NonNull BlockPos pos) {
            return 255;
        }

        public int getLightFor(EnumSkyBlock sky, @NonNull BlockPos pos) {
            return sky.defaultLightValue;
        }

        public void setLightFor(@NonNull EnumSkyBlock sky, @NonNull BlockPos pos, int value) {
        }

        public int getLightSubtracted(@NonNull BlockPos pos, int amount) {
            return 0;
        }

        public void addEntity(@NonNull Entity entity) {
        }

        public void removeEntity(@NonNull Entity entity) {
        }

        public void removeEntityAtIndex(@NonNull Entity entity, int index) {
        }

        public boolean canSeeSky(@NonNull BlockPos pos) {
            return false;
        }

        @Nullable
        public TileEntity getTileEntity(@NonNull BlockPos pos, Chunk.@NonNull EnumCreateEntityType createType) {
            return null;
        }

        public void addTileEntity(@NonNull TileEntity tileEntity) {
        }

        public void addTileEntity(@NonNull BlockPos pos, @NonNull TileEntity tileEntity) {
        }

        public void removeTileEntity(@NonNull BlockPos pos) {
        }

        public void onLoad() {
        }

        public void onUnload() {
        }

        public void markDirty() {
        }

        public void getEntitiesWithinAABBForEntity(@Nullable Entity entity, @NonNull AxisAlignedBB aabb, @NonNull List<Entity> listToFill, @NonNull Predicate<? super Entity> valid) {
        }

        public <T extends Entity> void getEntitiesOfTypeWithinAABB(@NonNull Class<? extends T> entityClass, @NonNull AxisAlignedBB aabb, @NonNull List<T> listToFill, @NonNull Predicate<? super T> valid) {
        }

        public boolean needsSaving(boolean flag) {
            return false;
        }

        public @NonNull Random getRandomWithSeed(long seed) {
            return new Random((this.getWorld().getSeed() + ((long) this.x * this.x * 4987142) + (this.x * 5947611L) + (long) this.z * this.z * 4392871L + (this.z * 389711L)) ^ seed);
        }

        public boolean isEmpty() {
            return true;
        }

        public boolean isEmptyBetween(int startY, int endY) {
            return true;
        }
    }
}
