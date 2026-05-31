package ae2.server.services.compass;

import ae2.core.localization.PlayerMessages;
import ae2.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class TestCompassCommand implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.compass";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        World world = sender.getEntityWorld();
        if (!(world instanceof WorldServer)) {
            sender.sendMessage(PlayerMessages.CommandRequiresServerWorld.text());
            return;
        }

        ChunkPos chunkPos = new ChunkPos(sender.getPosition());
        Entity entity = sender.getCommandSenderEntity();
        if (entity != null) {
            chunkPos = new ChunkPos(entity.chunkCoordX, entity.chunkCoordZ);
        }

        CompassRegion compassRegion = CompassRegion.get((WorldServer) world, chunkPos);
        int sections = world.getHeight() >> 4;
        for (int i = 0; i < sections; i++) {
            boolean hasSkyStone = compassRegion.hasCompassTarget(chunkPos.x, chunkPos.z, i);
            int yMin = i << 4;
            int yMax = yMin + 15;
            sender.sendMessage(PlayerMessages.CompassTestSection.text(i, yMin, yMax, hasSkyStone));
        }
    }
}
