package ae2.server.subcommands;

import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ChunkLogger implements ISubCommand {
    private boolean enabled;

    private static String getCenter(Chunk chunk) {
        int x = (chunk.x << 4) + 8;
        int z = (chunk.z << 4) + 8;
        int y = chunk.getHeightValue(8, 8) + 1;
        return new BlockPos(x, y, z).toString();
    }

    private void displayStack() {
        if (AEConfig.instance().isChunkLoggerTraceEnabled()) {
            boolean output = false;
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                if (output) {
                    AELog.info("\t\t%s.%s (%d)", e.getClassName(), e.getMethodName(), e.getLineNumber());
                } else {
                    output = e.getClassName().contains("EventBus") && e.getMethodName().contains("post");
                }
            }
        }
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.chunklogger";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        this.enabled = !this.enabled;

        if (this.enabled) {
            MinecraftForge.EVENT_BUS.register(this);
            sender.sendMessage(PlayerMessages.ChunkLoggerEnabled.text());
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            sender.sendMessage(PlayerMessages.ChunkLoggerDisabled.text());
        }
    }

    @SubscribeEvent
    public void onChunkLoadEvent(ChunkEvent.Load event) {
        if (event.getWorld() instanceof WorldServer) {
            Chunk chunk = event.getChunk();
            AELog.info("Loaded chunk %d,%d [center: %s] in dimension %d",
                chunk.x, chunk.z, getCenter(chunk), event.getWorld().provider.getDimension());
            this.displayStack();
        }
    }

    @SubscribeEvent
    public void onChunkUnloadEvent(ChunkEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            Chunk chunk = event.getChunk();
            AELog.info("Unloaded chunk %d,%d [center: %s] in dimension %d",
                chunk.x, chunk.z, getCenter(chunk), event.getWorld().provider.getDimension());
            this.displayStack();
        }
    }
}
