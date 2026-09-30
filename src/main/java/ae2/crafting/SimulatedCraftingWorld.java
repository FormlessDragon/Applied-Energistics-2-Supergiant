package ae2.crafting;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import java.io.File;

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
    public Chunk getChunk(int chunkX, int chunkZ) {
        return new EmptyChunk(this, chunkX, chunkZ);
    }

    @Override
    public Chunk getChunk(BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public MinecraftServer getMinecraftServer() {
        return server;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return Biomes.PLAINS;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return false;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return false;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState newState) {
        return false;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        return false;
    }

    @Override
    public boolean setBlockToAir(BlockPos pos) {
        return false;
    }

    @Override
    public boolean spawnEntity(Entity entityIn) {
        return false;
    }

    /**
     * Keeps the world off the disk: {@link net.minecraft.world.storage.MapStorage} short-circuits every file
     * operation as soon as {@link #getMapFileFromName} answers {@code null}, and the simulated world is never
     * asked to save anything else.
     */
    private static final class StubSaveHandler implements ISaveHandler {
        @Override
        public WorldInfo loadWorldInfo() {
            return null;
        }

        @Override
        public void checkSessionLock() {
        }

        @Override
        public IChunkLoader getChunkLoader(WorldProvider provider) {
            return null;
        }

        @Override
        public void saveWorldInfoWithPlayer(WorldInfo worldInformation, NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(WorldInfo worldInformation) {
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
        public File getMapFileFromName(String mapName) {
            return null;
        }

        @Override
        public TemplateManager getStructureTemplateManager() {
            return null;
        }
    }
}
