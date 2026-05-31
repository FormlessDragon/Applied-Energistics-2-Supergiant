package ae2.server.subcommands;

import ae2.core.localization.PlayerMessages;
import ae2.hooks.ticking.TickHandler;
import ae2.me.Grid;
import ae2.me.service.TickManagerService;
import ae2.server.ISubCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class TickMonitoring implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.tickmonitor";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
        }
        return ISubCommand.super.getTabCompletions(srv, sender, args, targetPos);
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("commands.ae2.tickmonitor");
        }

        if ("true".equalsIgnoreCase(args[1]) || "on".equalsIgnoreCase(args[1])) {
            TickManagerService.MONITORING_ENABLED = true;
            for (Grid grid : TickHandler.instance().getGridList()) {
                ((TickManagerService) grid.getTickManager()).resetMonitoringStatistics();
            }
        } else if ("false".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1])) {
            TickManagerService.MONITORING_ENABLED = false;
        } else {
            throw new WrongUsageException("commands.ae2.tickmonitor");
        }

        sender.sendMessage((TickManagerService.MONITORING_ENABLED
            ? PlayerMessages.TickMonitoringEnabled
            : PlayerMessages.TickMonitoringDisabled).text());
    }
}
