package ae2.server.subcommands;

import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.server.ISubCommand;
import ae2.worldgen.meteorite.CraterType;
import ae2.worldgen.meteorite.MeteoriteStructurePiece;
import ae2.worldgen.meteorite.MeteoritesWorldData;
import ae2.worldgen.meteorite.PlacedMeteoriteSettings;
import ae2.worldgen.meteorite.fallout.FalloutMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

public class TestMeteoritesCommand implements ISubCommand {
    private static final int DEFAULT_RADIUS = 100;
    private static final int MAX_RESULTS = 10;

    private static ObjectList<PlacedMeteoriteSettings> collectNearbyMeteorites(MeteoritesWorldData worldData,
                                                                               int chunkX, int chunkZ,
                                                                               BlockPos origin) {
        ObjectList<PlacedMeteoriteSettings> result = new ObjectArrayList<>();
        for (PlacedMeteoriteSettings settings : worldData.getNearByMeteorites(chunkX, chunkZ)) {
            result.add(settings);
        }
        result.sort(Comparator.comparingDouble(settings -> settings.pos().distanceSq(origin)));
        return result;
    }

    private static double findNearestDistance(List<PlacedMeteoriteSettings> all, PlacedMeteoriteSettings current) {
        double best = Double.NaN;
        for (PlacedMeteoriteSettings other : all) {
            if (other == current) {
                continue;
            }
            double distance = Math.sqrt(current.pos().distanceSq(other.pos()));
            if (Double.isNaN(best) || distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private static void sendLine(ICommandSender sender, PlayerMessages message, Object... args) {
        sender.sendMessage(message.text(args));
    }

    private static int parseCoordinate(String input, int current) throws CommandException {
        if ("~".equals(input)) {
            return current;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", input);
        }
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.test_meteorites";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "~", Integer.toString(sender.getPosition().getX()));
        }

        if (args.length == 3) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "~", Integer.toString(sender.getPosition().getZ()));
        }

        return ISubCommand.super.getTabCompletions(srv, sender, args, targetPos);
    }

    private static ITextComponent getCraterTypeName(CraterType craterType) {
        return switch (craterType) {
            case NONE -> GuiText.MeteoriteCraterNone.text();
            case NORMAL -> GuiText.MeteoriteCraterNormal.text();
            case LAVA -> GuiText.MeteoriteCraterLava.text();
            case OBSIDIAN -> GuiText.MeteoriteCraterObsidian.text();
            case WATER -> GuiText.MeteoriteCraterWater.text();
            case SNOW -> GuiText.MeteoriteCraterSnow.text();
            case ICE -> GuiText.MeteoriteCraterIce.text();
        };
    }

    private static ITextComponent getFalloutModeName(FalloutMode falloutMode) {
        return switch (falloutMode) {
            case NONE -> GuiText.MeteoriteFalloutNone.text();
            case DEFAULT -> GuiText.MeteoriteFalloutDefault.text();
            case SAND -> GuiText.MeteoriteFalloutSand.text();
            case TERRACOTTA -> GuiText.MeteoriteFalloutTerracotta.text();
            case ICE_SNOW -> GuiText.MeteoriteFalloutIceSnow.text();
        };
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        if (args.length != 1 && args.length != 3) {
            throw new WrongUsageException("commands.ae2.test_meteorites");
        }

        World world = sender.getEntityWorld();
        BlockPos origin = sender.getPosition();

        if (world == null) {
            world = srv.getWorld(0);
        }

        if (world == null) {
            throw new CommandException("commands.ae2.test_meteorites");
        }

        if (origin == null) {
            origin = world.getSpawnPoint();
        }

        if (args.length == 3) {
            int blockX = parseCoordinate(args[1], origin.getX());
            int blockZ = parseCoordinate(args[2], origin.getZ());
            origin = new BlockPos(blockX, origin.getY(), blockZ);
        }

        MeteoritesWorldData worldData = MeteoritesWorldData.get(world);
        ChunkPos chunkPos = new ChunkPos(origin);
        ObjectList<PlacedMeteoriteSettings> nearby = collectNearbyMeteorites(worldData, chunkPos.x, chunkPos.z,
            origin);

        sendLine(sender, PlayerMessages.MeteoriteTestSummary,
            world.provider.getDimension(), chunkPos.x, chunkPos.z, nearby.size(), DEFAULT_RADIUS);

        if (nearby.isEmpty()) {
            return;
        }

        double minNearest = Double.NaN;
        double maxNearest = Double.NaN;
        double sumNearest = 0.0D;
        int nearestCount = 0;

        for (PlacedMeteoriteSettings settings : nearby) {
            double nearest = findNearestDistance(nearby, settings);
            if (!Double.isNaN(nearest)) {
                if (Double.isNaN(minNearest) || nearest < minNearest) {
                    minNearest = nearest;
                }
                if (Double.isNaN(maxNearest) || nearest > maxNearest) {
                    maxNearest = nearest;
                }
                sumNearest += nearest;
                nearestCount++;
            }
        }

        if (nearestCount > 0) {
            sendLine(sender, PlayerMessages.MeteoriteTestNearestSpacing,
                minNearest, maxNearest, sumNearest / nearestCount);
        }

        int listed = Math.min(MAX_RESULTS, nearby.size());
        for (int i = 0; i < listed; i++) {
            PlacedMeteoriteSettings settings = nearby.get(i);
            BlockPos pos = settings.pos();
            ChunkPos meteorChunk = new ChunkPos(pos);
            boolean intersects = new MeteoriteStructurePiece(settings).intersectsChunk(chunkPos.x, chunkPos.z);

            double distance = Math.sqrt(pos.distanceSq(origin));
            sendLine(sender, PlayerMessages.MeteoriteTestEntry,
                i + 1,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                meteorChunk.x,
                meteorChunk.z,
                distance,
                settings.meteoriteRadius(),
                getCraterTypeName(settings.craterType()),
                getFalloutModeName(settings.fallout()),
                PlayerMessages.MeteoriteTestBoolean.text(intersects));
        }
    }
}
