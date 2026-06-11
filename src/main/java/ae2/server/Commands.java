package ae2.server;

import ae2.server.services.compass.TestCompassCommand;
import ae2.server.subcommands.ChannelModeCommand;
import ae2.server.subcommands.ChunkLogger;
import ae2.server.subcommands.DebugEnergyCommand;
import ae2.server.subcommands.GridsCommand;
import ae2.server.subcommands.SpatialStorageCommand;
import ae2.server.subcommands.TestMeteoritesCommand;
import ae2.server.subcommands.TickMonitoring;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum Commands {
    CHUNK_LOGGER("chunklogger", new ChunkLogger(), false),
    SPATIAL("spatial", new SpatialStorageCommand(), false),
    CHANNEL_MODE("channelmode", new ChannelModeCommand(), false),
    DEBUG_ENERGY("debugenergy", new DebugEnergyCommand(), false),
    TICK_MONITORING("tickmonitor", new TickMonitoring(), false),
    GRIDS("grids", new GridsCommand(), false),
    COMPASS("compass", new TestCompassCommand(), true),
    TEST_METEORITES("testmeteorites", new TestMeteoritesCommand(), true);

    public final int level;
    public final ISubCommand command;
    public final boolean test;
    private final String name;

    Commands(String name, ISubCommand command, boolean test) {
        this.name = name;
        this.level = 4;
        this.command = command;
        this.test = test;
    }

    public static @Nullable Commands fromName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (Commands command : values()) {
            if (command.name.equals(normalized)) {
                return command;
            }
        }
        return null;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
