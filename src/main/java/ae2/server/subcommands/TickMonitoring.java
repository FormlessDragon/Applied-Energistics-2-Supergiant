package ae2.server.subcommands;

import ae2.core.AEConfig;
import ae2.core.localization.PlayerMessages;
import ae2.core.localization.Tooltips;
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
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class TickMonitoring implements ISubCommand {
    private static ITextComponent getModeLabel(boolean enabled) {
        return enabled ? Tooltips.On.text() : Tooltips.Off.text();
    }

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
        if (args.length == 1) {
            sender.sendMessage(PlayerMessages.TickMonitoringCurrent.text(
                getModeLabel(AEConfig.instance().isTickMonitoringEnabled())));
            return;
        }

        if (args.length != 2) {
            throw new WrongUsageException("commands.ae2.tickmonitor");
        }

        if ("true".equalsIgnoreCase(args[1]) || "on".equalsIgnoreCase(args[1])) {
            AEConfig.instance().setTickMonitoringEnabled(true);
            for (Grid grid : TickHandler.instance().getGridList()) {
                ((TickManagerService) grid.getTickManager()).resetMonitoringStatistics();
            }
        } else if ("false".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1])) {
            AEConfig.instance().setTickMonitoringEnabled(false);
        } else {
            throw new WrongUsageException("commands.ae2.tickmonitor");
        }

        sender.sendMessage((AEConfig.instance().isTickMonitoringEnabled()
            ? PlayerMessages.TickMonitoringEnabled
            : PlayerMessages.TickMonitoringDisabled).text());
    }
}
