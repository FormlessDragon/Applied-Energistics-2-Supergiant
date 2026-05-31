package ae2.server.subcommands;

import ae2.api.features.IPlayerRegistry;
import ae2.core.definitions.AEItems;
import ae2.core.localization.PlayerMessages;
import ae2.items.storage.SpatialStorageCellItem;
import ae2.server.ISubCommand;
import ae2.spatial.SpatialStorageDimensionIds;
import ae2.spatial.SpatialStoragePlot;
import ae2.spatial.SpatialStoragePlotManager;
import ae2.spatial.TransitionInfo;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SpatialStorageCommand implements ISubCommand {

    private static void teleportPlayer(MinecraftServer server, EntityPlayerMP player, int dimensionId, BlockPos pos) {
        WorldServer targetLevel = server.getWorld(dimensionId);
        if (targetLevel == null) {
            DimensionManager.initDimension(dimensionId);
            targetLevel = server.getWorld(dimensionId);
        }
        if (targetLevel == null) {
            throw new IllegalStateException("Missing target dimension " + dimensionId);
        }

        if (player.dimension == dimensionId) {
            player.connection.setPlayerLocation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.rotationYaw,
                player.rotationPitch);
            return;
        }

        server.getPlayerList().transferPlayerToDimension(player, dimensionId, new FixedTeleporter(targetLevel, pos));
        player.connection.setPlayerLocation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.rotationYaw,
            player.rotationPitch);
    }

    private static EntityPlayerMP getPlayer(ICommandSender sender) throws CommandException {
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP player) {
            return player;
        }
        throw new CommandException("commands.ae2.spatial.requires_player");
    }

    private static SpatialStoragePlot getPlot(int plotId) throws CommandException {
        SpatialStoragePlot plot = SpatialStoragePlotManager.INSTANCE.getPlot(plotId);
        if (plot == null) {
            throw new CommandException("commands.ae2.spatial.plot_not_found", plotId);
        }
        return plot;
    }

    private static SpatialStoragePlot getCurrentPlot(ICommandSender sender) throws CommandException {
        BlockPos playerPos = sender.getPosition();
        if (playerPos == null || sender.getEntityWorld().provider.getDimension() != SpatialStorageDimensionIds.getDimensionId()) {
            throw new CommandException("commands.ae2.spatial.not_in_dimension");
        }

        for (SpatialStoragePlot plot : SpatialStoragePlotManager.INSTANCE.getPlots()) {
            BlockPos origin = plot.getOrigin();
            BlockPos size = plot.getSize();
            if (playerPos.getX() >= origin.getX() && playerPos.getX() <= origin.getX() + size.getX()
                && playerPos.getZ() >= origin.getZ() && playerPos.getZ() <= origin.getZ() + size.getZ()) {
                return plot;
            }
        }

        throw new CommandException("commands.ae2.spatial.no_plot_at_position");
    }

    private static int parsePlotId(String input) throws CommandException {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new CommandException("commands.ae2.spatial.invalid_plot_id", input);
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static ITextComponent describeOwner(MinecraftServer server, int ownerId) {
        if (ownerId == -1) {
            return PlayerMessages.Unknown.text();
        }

        EntityPlayerMP connectedPlayer = IPlayerRegistry.getConnected(server, ownerId);
        if (connectedPlayer != null) {
            return PlayerMessages.PlayerConnected.text(connectedPlayer.getGameProfile().getName());
        }

        UUID profileId = IPlayerRegistry.getMapping(server).getProfileId(ownerId);
        if (profileId != null) {
            GameProfile cachedProfile = server.getPlayerProfileCache().getProfileByUUID(profileId);
            if (cachedProfile != null) {
                return PlayerMessages.PlayerDisconnected.text(cachedProfile.getName());
            }
            return PlayerMessages.UnknownAE2Player.text(profileId.toString());
        }

        return PlayerMessages.UnknownAE2Player.text(ownerId);
    }

    private static java.time.Instant getLastTransitionTimestamp(SpatialStoragePlot plot) {
        TransitionInfo transition = plot.getLastTransition();
        return transition != null ? transition.timestamp() : java.time.Instant.MIN;
    }

    private static boolean acceptsPlotId(String action) {
        String normalized = action.toLowerCase(Locale.ROOT);
        return "info".equals(normalized) || "tp".equals(normalized) || "tpback".equals(normalized)
            || "givecell".equals(normalized);
    }

    private static ObjectList<String> getPlotIdCandidates() {
        ObjectList<String> candidates = new ObjectArrayList<>();
        try {
            for (SpatialStoragePlot plot : SpatialStoragePlotManager.INSTANCE.getPlots()) {
                candidates.add(Integer.toString(plot.getId()));
            }
        } catch (IllegalStateException ignored) {
        }
        return candidates;
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.spatial";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        try {
            SpatialStoragePlotManager.INSTANCE.getLevel();
        } catch (IllegalStateException e) {
            sender.sendMessage(PlayerMessages.SpatialStorageLevelUnavailable.text(e.getMessage()));
            return;
        }

        if (args.length == 1) {
            listPlots(srv, sender);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "info" -> {
                if (args.length == 2) {
                    showPlotInfo(sender, getCurrentPlot(sender));
                    return;
                }
                showPlotInfo(sender, getPlot(parsePlotId(args[2])));
                return;
            }
            case "tp" -> {
                if (args.length != 3) {
                    throw new WrongUsageException("commands.ae2.spatial");
                }
                teleportToPlot(srv, sender, parsePlotId(args[2]));
                return;
            }
            case "tpback" -> {
                if (args.length == 2) {
                    teleportBack(srv, sender, getCurrentPlot(sender));
                    return;
                }
                teleportBack(srv, sender, getPlot(parsePlotId(args[2])));
                return;
            }
            case "givecell" -> {
                if (args.length != 3) {
                    throw new WrongUsageException("commands.ae2.spatial");
                }
                giveCell(sender, parsePlotId(args[2]));
                return;
            }
        }

        throw new WrongUsageException("commands.ae2.spatial");
    }

    private void listPlots(MinecraftServer server, ICommandSender sender) {
        ObjectList<SpatialStoragePlot> plots = new ObjectArrayList<>(SpatialStoragePlotManager.INSTANCE.getPlots());
        if (plots.isEmpty()) {
            sender.sendMessage(PlayerMessages.NoSpatialIOPlots.text());
            return;
        }

        plots.sort(Comparator.comparing(SpatialStorageCommand::getLastTransitionTimestamp).reversed());
        for (int i = 0; i < Math.min(5, plots.size()); i++) {
            SpatialStoragePlot plot = plots.get(i);
            sender.sendMessage(PlayerMessages.SpatialPlotSummary.text(
                plot.getId(), format(plot.getSize()), format(plot.getOrigin())));
        }
    }

    private void showPlotInfo(ICommandSender sender, SpatialStoragePlot plot) {
        sender.sendMessage(PlayerMessages.SpatialPlotId.text(plot.getId()));
        sender.sendMessage(PlayerMessages.SpatialPlotOwner.text(describeOwner(sender.getServer(), plot.getOwner())));
        sender.sendMessage(PlayerMessages.SpatialPlotSize.text(format(plot.getSize())));
        sender.sendMessage(PlayerMessages.SpatialPlotOrigin.text(format(plot.getOrigin())));
        sender.sendMessage(PlayerMessages.SpatialPlotRegion.text(plot.getRegionFilename()));

        TransitionInfo transition = plot.getLastTransition();
        if (transition != null) {
            sender.sendMessage(PlayerMessages.SpatialPlotLastSource.text(transition.worldId()));
            sender.sendMessage(PlayerMessages.SpatialPlotLastMin.text(format(transition.min())));
            sender.sendMessage(PlayerMessages.SpatialPlotLastMax.text(format(transition.max())));
            sender.sendMessage(PlayerMessages.SpatialPlotLastTime.text(transition.timestamp()));
        }
    }

    private void teleportToPlot(MinecraftServer server, ICommandSender sender, int plotId) throws CommandException {
        EntityPlayerMP player = getPlayer(sender);
        SpatialStoragePlot plot = getPlot(plotId);
        teleportPlayer(server, player, SpatialStorageDimensionIds.getDimensionId(), plot.getOrigin().add(0, 1, 0));
    }

    private void teleportBack(MinecraftServer server, ICommandSender sender, SpatialStoragePlot plot)
        throws CommandException {
        EntityPlayerMP player = getPlayer(sender);
        TransitionInfo transition = plot.getLastTransition();
        if (transition == null) {
            throw new CommandException("commands.ae2.spatial.no_previous_transition");
        }

        int dimensionId = transition.dimensionId();
        teleportPlayer(server, player, dimensionId, transition.min().add(0, 1, 0));
    }

    private void giveCell(ICommandSender sender, int plotId) throws CommandException {
        EntityPlayerMP player = getPlayer(sender);
        SpatialStoragePlot plot = getPlot(plotId);
        ItemStack cell;
        int longestSide = Math.max(plot.getSize().getX(), Math.max(plot.getSize().getY(), plot.getSize().getZ()));
        if (longestSide <= 2) {
            cell = AEItems.SPATIAL_CELL2.stack();
        } else if (longestSide <= 16) {
            cell = AEItems.SPATIAL_CELL16.stack();
        } else {
            cell = AEItems.SPATIAL_CELL128.stack();
        }

        if (!(cell.getItem() instanceof SpatialStorageCellItem spatialCellItem)) {
            throw new CommandException("commands.ae2.spatial.not_spatial_cell");
        }

        spatialCellItem.setStoredDimension(cell, plot.getId(), plot.getSize());
        player.addItemStackToInventory(cell);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "info", "tp", "tpback", "givecell");
        }

        if (args.length == 3 && acceptsPlotId(args[1])) {
            return CommandBase.getListOfStringsMatchingLastWord(args, getPlotIdCandidates());
        }

        return Collections.emptyList();
    }

    private static final class FixedTeleporter extends Teleporter {
        private final BlockPos pos;

        private FixedTeleporter(WorldServer worldIn, BlockPos pos) {
            super(worldIn);
            this.pos = pos;
        }

        @Override
        public void placeInPortal(net.minecraft.entity.Entity entity, float rotationYaw) {
            entity.setLocationAndAngles(this.pos.getX() + 0.5, this.pos.getY(), this.pos.getZ() + 0.5,
                entity.rotationYaw, entity.rotationPitch);
        }

        @Override
        public boolean placeInExistingPortal(net.minecraft.entity.Entity entity, float rotationYaw) {
            this.placeInPortal(entity, rotationYaw);
            return true;
        }

        @Override
        public boolean makePortal(net.minecraft.entity.Entity entity) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long worldTime) {
        }
    }
}
