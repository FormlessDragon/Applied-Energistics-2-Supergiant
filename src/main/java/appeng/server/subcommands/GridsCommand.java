package appeng.server.subcommands;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.core.localization.PlayerMessages;
import appeng.core.network.InitNetwork;
import appeng.core.network.clientbound.ExportedGridContent;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.hooks.ticking.TickHandler;
import appeng.me.Grid;
import appeng.me.service.StatisticsService;
import appeng.parts.AEBasePart;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.server.ISubCommand;
import appeng.util.Platform;
import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SuppressWarnings("deprecation")
public class GridsCommand implements ISubCommand {
    private static final int FLUSH_AFTER = 512 * 1024;
    private static final Method WRITE_CHUNK_TO_NBT = resolveWriteChunkToNbt();

    public static String buildExportCommand(int serialNumber) {
        return "/ae2 grids export " + serialNumber;
    }

    private static int parseSerial(String value) throws CommandException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException("commands.ae2.grids.invalid_serial", value);
        }
    }

    private static Grid findGrid(int serialNumber) throws CommandException {
        for (Grid grid : TickHandler.instance().getGridList()) {
            if (grid.getSerialNumber() == serialNumber) {
                return grid;
            }
        }
        throw new CommandException("commands.ae2.grids.not_found", serialNumber);
    }

    private static Collection<Grid> collectReachableGrids(Grid startGrid) {
        ReferenceSet<Grid> reachableGrids = new ReferenceOpenHashSet<>();
        ReferenceSet<Grid> openSet = new ReferenceOpenHashSet<>();
        reachableGrids.add(startGrid);
        openSet.add(startGrid);

        while (!openSet.isEmpty()) {
            var iterator = openSet.iterator();
            Grid grid = iterator.next();
            iterator.remove();
            for (IGridNode node : grid.getNodes()) {
                Object owner = node.getOwner();
                if (owner instanceof AEBasePart basePart) {
                    visitGridInFrontOfPart(basePart, reachableGrids, openSet);
                } else if (owner instanceof PatternProviderLogicHost patternProvider) {
                    TileEntity tile = patternProvider.getTileEntity();
                    for (EnumFacing targetSide : patternProvider.getTargets()) {
                        visitGridAt(tile, tile.getPos().offset(targetSide), reachableGrids, openSet);
                    }
                } else if (owner instanceof MEP2PTunnelPart meTunnel) {
                    Object tunnelGrid = meTunnel.getMainNode().getGrid();
                    if (tunnelGrid instanceof Grid gridAtTunnel && reachableGrids.add(gridAtTunnel)) {
                        openSet.add(gridAtTunnel);
                    }
                }
            }
        }

        return reachableGrids;
    }

    private static void visitGridInFrontOfPart(AEBasePart part, ReferenceSet<Grid> reachableGrids,
                                               ReferenceSet<Grid> openSet) {
        EnumFacing side = part.getSide();
        if (side != null) {
            TileEntity tile = part.getTileEntity();
            visitGridAt(tile, tile.getPos().offset(side), reachableGrids, openSet);
        }
    }

    private static void visitGridAt(TileEntity source, BlockPos targetPos, ReferenceSet<Grid> reachableGrids,
                                    ReferenceSet<Grid> openSet) {
        if (source == null || source.getWorld() == null) {
            return;
        }

        var targetGridHost = GridHelper.getNodeHost(source.getWorld(), targetPos);
        if (targetGridHost == null) {
            return;
        }

        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IGridNode node = targetGridHost.getGridNode(side);
            if (node != null && node.grid() instanceof Grid grid && reachableGrids.add(grid)) {
                openSet.add(grid);
            }
        }
    }

    private static void exportGrids(EntityPlayerMP player, int serialNumber, Collection<Grid> grids) throws CommandException {
        if (player != null) {
            InitNetwork.sendToClient(player,
                new ExportedGridContent(serialNumber, ExportedGridContent.ContentType.FIRST_CHUNK, new byte[0]));
            try (SendToPlayerStream output = new SendToPlayerStream(player, serialNumber)) {
                exportGrids(grids, output);
                output.complete();
            }
            return;
        }

        try (OutputStream output = Files.newOutputStream(Paths.get("grids.zip"))) {
            exportGrids(grids, output);
        } catch (IOException e) {
            throw new CommandException("commands.ae2.grids.export_failed", e.getMessage());
        }
    }

    @Nullable
    private static EntityPlayerMP getPlayer(ICommandSender sender) {
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP player) {
            return player;
        }
        return null;
    }

    private static void exportGrids(Collection<Grid> grids, OutputStream output) throws CommandException {
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            Object2ObjectMap<WorldServer, ObjectSet<ChunkPos>> chunksByLevel = new Object2ObjectOpenHashMap<>();
            for (Grid grid : grids) {
                var statisticsService = grid.getService(StatisticsService.class);
                for (var entry : statisticsService.getChunks().entrySet()) {
                    chunksByLevel.computeIfAbsent(entry.getKey(), ignored -> new ObjectOpenHashSet<>())
                                 .addAll(entry.getValue().elementSet());
                }

                zip.putNextEntry(new ZipEntry("grid_" + grid.getSerialNumber() + ".json"));
                JsonWriter writer = new JsonWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
                writer.setIndent(" ");
                grid.export(writer);
                writer.flush();
                zip.closeEntry();
            }

            zip.putNextEntry(new ZipEntry("chunks/"));
            zip.closeEntry();
            for (var entry : chunksByLevel.entrySet()) {
                String baseName = sanitizeName(
                    entry.getKey().provider.getDimensionType().getName() + "_" + entry.getKey().provider.getDimension());
                for (ChunkPos chunkPos : entry.getValue()) {
                    NBTTagCompound serializedChunk = serializeChunk(entry.getKey(),
                        entry.getKey().getChunk(chunkPos.x, chunkPos.z));

                    zip.putNextEntry(new ZipEntry("chunks/" + baseName + "_" + chunkPos.x + "_" + chunkPos.z + ".nbt"));
                    writeCompressedChunk(zip, serializedChunk);
                    zip.closeEntry();

                    zip.putNextEntry(new ZipEntry("chunks/" + baseName + "_" + chunkPos.x + "_" + chunkPos.z + ".snbt"));
                    zip.write(serializedChunk.toString().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        } catch (IOException | ReflectiveOperationException e) {
            throw new CommandException("commands.ae2.grids.export_failed", e.getMessage());
        }
    }

    private static void writeCompressedChunk(ZipOutputStream zip, NBTTagCompound serializedChunk) throws IOException {
        CompressedStreamTools.writeCompressed(serializedChunk, new NonClosingOutputStream(zip));
    }

    private static NBTTagCompound serializeChunk(WorldServer level, Chunk chunk)
        throws ReflectiveOperationException {
        NBTTagCompound rootTag = new NBTTagCompound();
        NBTTagCompound levelTag = new NBTTagCompound();
        rootTag.setTag("Level", levelTag);
        rootTag.setInteger("DataVersion", 1343);
        FMLCommonHandler.instance().getDataFixer().writeVersionData(rootTag);
        invokeWriteChunkToNbt(chunk, level, levelTag);
        ForgeChunkManager.storeChunkNBT(chunk, levelTag);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, rootTag));
        return rootTag;
    }

    private static void invokeWriteChunkToNbt(Chunk chunk, WorldServer level, NBTTagCompound levelTag)
        throws ReflectiveOperationException {
        try {
            WRITE_CHUNK_TO_NBT.invoke(
                new AnvilChunkLoader(level.getChunkSaveLocation(), FMLCommonHandler.instance().getDataFixer()),
                chunk,
                level,
                levelTag);
        } catch (InvocationTargetException e) {
            throw new ReflectiveOperationException(e.getCause());
        }
    }

    private static Method resolveWriteChunkToNbt() {
        try {
            return ReflectionHelper.findMethod(AnvilChunkLoader.class, "writeChunkToNBT", "func_75820_a", Chunk.class,
                World.class, NBTTagCompound.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to access chunk serialization method.", e);
        }
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[^A-Za-z0-9-,]", "_");
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.grids";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "export");
        }

        if (args.length == 3 && "export".equalsIgnoreCase(args[1])) {
            ObjectSet<String> candidates = new ObjectOpenHashSet<>();
            for (Grid grid : TickHandler.instance().getGridList()) {
                candidates.add(Integer.toString(grid.getSerialNumber()));
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, candidates);
        }

        return ISubCommand.super.getTabCompletions(srv, sender, args, targetPos);
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        if (args.length < 2 || !"export".equals(args[1].toLowerCase(Locale.ROOT))) {
            throw new WrongUsageException("commands.ae2.grids");
        }

        EntityPlayerMP player = getPlayer(sender);
        int serialNumber = 0;
        Collection<Grid> grids;
        if (args.length == 2) {
            grids = TickHandler.instance().getGridList();
        } else if (args.length == 3) {
            serialNumber = parseSerial(args[2]);
            grids = collectReachableGrids(findGrid(serialNumber));
        } else {
            throw new WrongUsageException("commands.ae2.grids");
        }

        if (grids.isEmpty()) {
            throw new CommandException("commands.ae2.grids.no_grids_found");
        }

        sender.sendMessage(PlayerMessages.GridsExporting.text(grids.size()));
        exportGrids(player, serialNumber, grids);

        if (player != null) {
            sender.sendMessage(PlayerMessages.GridsExported.text(grids.size()));
        } else {
            sender.sendMessage(PlayerMessages.GridsExportedZip.text(grids.size()));
        }
    }

    private static final class SendToPlayerStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(FLUSH_AFTER);
        private final EntityPlayerMP player;
        private final int serialNumber;
        private boolean closed;

        private SendToPlayerStream(EntityPlayerMP player, int serialNumber) {
            this.player = player;
            this.serialNumber = serialNumber;
        }

        @Override
        public void write(int b) {
            Preconditions.checkState(!this.closed, "stream already closed");
            this.buffer.write(b);
            if (this.buffer.size() >= FLUSH_AFTER) {
                flushChunk(ExportedGridContent.ContentType.CHUNK);
            }
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) {
            Preconditions.checkState(!this.closed, "stream already closed");
            int remaining = len;
            int offset = off;
            while (remaining > 0) {
                if (this.buffer.size() >= FLUSH_AFTER) {
                    flushChunk(ExportedGridContent.ContentType.CHUNK);
                }

                int writable = Math.min(remaining, FLUSH_AFTER - this.buffer.size());
                this.buffer.write(b, offset, writable);
                offset += writable;
                remaining -= writable;
            }
        }

        @Override
        public void close() {
            abort();
        }

        private void complete() {
            Preconditions.checkState(!this.closed, "stream already closed");
            this.closed = true;
            flushChunk(ExportedGridContent.ContentType.LAST_CHUNK);
        }

        private void abort() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.buffer.reset();
        }

        private void flushChunk(ExportedGridContent.ContentType type) {
            InitNetwork.sendToClient(this.player,
                new ExportedGridContent(this.serialNumber, type, this.buffer.toByteArray()));
            this.buffer.reset();
        }
    }

    private static final class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        private NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            this.delegate.write(b);
        }

        @Override
        public void write(@Nonnull byte[] b) throws IOException {
            this.delegate.write(b);
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) throws IOException {
            this.delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override
        public void close() throws IOException {
            this.delegate.flush();
        }
    }
}
