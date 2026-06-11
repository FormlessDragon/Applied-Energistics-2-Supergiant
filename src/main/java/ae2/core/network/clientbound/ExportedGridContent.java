package ae2.core.network.clientbound;

import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import ae2.util.EmptyArrays;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class ExportedGridContent extends ClientboundPacket {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final int MAX_CHUNK_BYTES = 512 * 1024;
    private static final long MAX_EXPORT_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ACTIVE_EXPORTS = 4;
    private static final long ACTIVE_EXPORT_TTL_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final Int2ObjectOpenHashMap<ExportState> EXPORTS = new Int2ObjectOpenHashMap<>();
    private static final OpenOption[] CREATE_OR_TRUNCATE_OPTIONS = {
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
    };
    private static final OpenOption[] APPEND_OPTIONS = {
        StandardOpenOption.APPEND
    };

    private int serialNumber;
    private ContentType contentType;
    private byte[] compressedData;

    public ExportedGridContent() {
    }

    public ExportedGridContent(int serialNumber, ContentType contentType, byte[] compressedData) {
        this.serialNumber = serialNumber;
        this.contentType = contentType;
        this.compressedData = compressedData;
    }

    public static void clearActiveExports() {
        for (ExportState state : EXPORTS.values()) {
            deleteTempFile(state);
        }
        EXPORTS.clear();
    }

    private static void deleteTempFile(ExportState state) {
        try {
            Files.deleteIfExists(state.tempPath);
        } catch (IOException e) {
            AELog.error(e, "Failed to clean up exported grid data");
        }
    }

    private static Path resolveFinalPath(Minecraft minecraft, String filename) {
        if (minecraft.isSingleplayer()) {
            var integratedServer = minecraft.getIntegratedServer();
            if (integratedServer != null) {
                return integratedServer.getFile(filename).toPath().toAbsolutePath().normalize();
            }
        }

        return minecraft.gameDir.toPath().toAbsolutePath().normalize().resolve(filename);
    }

    private static String buildServerPrefix(ServerData serverData) {
        String serverName = sanitizeFilenameSegment(serverData == null ? null : serverData.serverName);
        String serverIp = sanitizeFilenameSegment(serverData == null ? null : serverData.serverIP);
        StringBuilder prefix = new StringBuilder();
        if (!serverName.isEmpty()) {
            prefix.append(serverName);
        }
        if (!serverIp.isEmpty() && !serverIp.equals(serverName)) {
            if (!prefix.isEmpty()) {
                prefix.append('_');
            }
            prefix.append(serverIp);
        }
        if (prefix.isEmpty()) {
            prefix.append("server");
        }
        prefix.append('_');
        return prefix.toString();
    }

    private static String sanitizeFilenameSegment(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        return sanitized;
    }

    private static ITextComponent error(ITextComponent message) {
        var component = message.createCopy();
        component.getStyle().setColor(TextFormatting.RED);
        return component;
    }

    private static void pruneActiveExports() {
        long cutoffNanos = System.nanoTime() - ACTIVE_EXPORT_TTL_NANOS;
        var iterator = EXPORTS.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<ExportState> entry = iterator.next();
            ExportState state = entry.getValue();
            if (state.createdAtNanos <= cutoffNanos) {
                deleteTempFile(state);
                iterator.remove();
            }
        }
    }

    private static void evictOldestExportIfNeeded() {
        if (EXPORTS.size() < MAX_ACTIVE_EXPORTS) {
            return;
        }

        int oldestSerialNumber = 0;
        ExportState oldestState = null;
        for (Int2ObjectMap.Entry<ExportState> entry : EXPORTS.int2ObjectEntrySet()) {
            ExportState state = entry.getValue();
            if (oldestState == null || state.createdAtNanos < oldestState.createdAtNanos) {
                oldestSerialNumber = entry.getIntKey();
                oldestState = state;
            }
        }

        if (oldestState != null) {
            EXPORTS.remove(oldestSerialNumber);
            deleteTempFile(oldestState);
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        byte[] data = this.compressedData != null ? this.compressedData : EmptyArrays.EMPTY_BYTE_ARRAY;
        packetBuffer.writeInt(this.serialNumber);
        packetBuffer.writeEnumValue(this.contentType != null ? this.contentType : ContentType.CHUNK);
        packetBuffer.writeVarInt(data.length);
        packetBuffer.writeBytes(data);
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        PreparedExport export = prepareExport(minecraft);
        if (export == null) {
            return;
        }

        byte[] data = this.compressedData != null ? this.compressedData : EmptyArrays.EMPTY_BYTE_ARRAY;
        if (!export.state.addBytes(data.length)) {
            failExport(minecraft, export.state, null, "Exported grid data exceeded the client size limit",
                PlayerMessages.GridExportWriteFailed.text(export.state.tempPath));
            return;
        }

        try {
            Files.write(export.state.tempPath, data, export.options);
        } catch (IOException e) {
            failExport(minecraft, export.state, e, "Failed to write exported grid data",
                PlayerMessages.GridExportWriteFailed.text(export.state.tempPath));
            return;
        }

        if (this.contentType == ContentType.LAST_CHUNK) {
            try {
                Files.move(export.state.tempPath, export.state.finalPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                failExport(minecraft, export.state, e, "Failed to finish exported grid data",
                    PlayerMessages.GridExportFinishFailed.text(export.state.finalPath));
                return;
            }

            EXPORTS.remove(this.serialNumber);
            var message = PlayerMessages.GridDataSaved.text(this.serialNumber);
            var path = new TextComponentString(export.state.finalPath.toString());
            path.getStyle()
                .setUnderlined(true)
                .setClickEvent(
                    new ClickEvent(ClickEvent.Action.OPEN_FILE, export.state.finalPath.getParent().toString()));
            message.appendSibling(path);
            minecraft.player.sendMessage(message);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.serialNumber = packetBuffer.readInt();
        this.contentType = NetworkPacketHelper.readEnumOrNull(packetBuffer, ContentType.class);
        if (this.contentType == null) {
            throw new IllegalArgumentException("Invalid exported grid chunk content type");
        }
        int length = packetBuffer.readVarInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > packetBuffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid exported grid chunk length: " + length);
        }
        this.compressedData = new byte[length];
        packetBuffer.readBytes(this.compressedData);
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing exported grid chunk payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    private @Nullable PreparedExport prepareExport(Minecraft minecraft) {
        if (this.contentType == ContentType.FIRST_CHUNK) {
            ExportState previousState = EXPORTS.remove(this.serialNumber);
            if (previousState != null) {
                deleteTempFile(previousState);
            }
            pruneActiveExports();
            evictOldestExportIfNeeded();
            ExportState state = createState(minecraft);
            EXPORTS.put(this.serialNumber, state);
            return new PreparedExport(state, CREATE_OR_TRUNCATE_OPTIONS);
        }

        ExportState state = EXPORTS.get(this.serialNumber);
        if (state != null) {
            return new PreparedExport(state, APPEND_OPTIONS);
        }

        EXPORTS.remove(this.serialNumber);
        AELog.error("Received exported grid chunk without active export state for grid #%d", this.serialNumber);
        minecraft.player.sendMessage(error(PlayerMessages.GridExportIncomplete.text(this.serialNumber)));
        return null;
    }

    private void failExport(Minecraft minecraft, ExportState state, @Nullable IOException exception, String logMessage,
                            ITextComponent playerMessage) {
        EXPORTS.remove(this.serialNumber);
        deleteTempFile(state);
        if (exception != null) {
            AELog.error(exception, logMessage);
        } else {
            AELog.error(logMessage);
        }
        minecraft.player.sendMessage(error(playerMessage));
    }

    private ExportState createState(Minecraft minecraft) {
        String filename = buildFilename(minecraft);
        Path finalPath = resolveFinalPath(minecraft, filename);
        return new ExportState(finalPath.resolveSibling(filename + ".tmp"), finalPath, System.nanoTime());
    }

    private String buildFilename(Minecraft minecraft) {
        StringBuilder filename = new StringBuilder();
        if (!minecraft.isSingleplayer()) {
            filename.append(buildServerPrefix(minecraft.getCurrentServerData()));
        }
        filename.append("ae2_grid_")
                .append(this.serialNumber)
                .append('_')
                .append(TIMESTAMP_FORMATTER.format(LocalDateTime.now()))
                .append(".zip");
        return filename.toString();
    }

    public enum ContentType {
        FIRST_CHUNK,
        CHUNK,
        LAST_CHUNK
    }

    private record PreparedExport(ExportState state, OpenOption[] options) {
    }

    private static final class ExportState {
        private final Path tempPath;
        private final Path finalPath;
        private final long createdAtNanos;
        private long writtenBytes;

        private ExportState(Path tempPath, Path finalPath, long createdAtNanos) {
            this.tempPath = tempPath;
            this.finalPath = finalPath;
            this.createdAtNanos = createdAtNanos;
        }

        private boolean addBytes(int bytes) {
            long newWrittenBytes = this.writtenBytes + bytes;
            if (newWrittenBytes > MAX_EXPORT_BYTES) {
                return false;
            }
            this.writtenBytes = newWrittenBytes;
            return true;
        }
    }
}
