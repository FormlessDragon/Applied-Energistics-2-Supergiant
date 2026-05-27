package appeng.server.subcommands;

import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import appeng.hooks.ticking.TickHandler;
import appeng.me.Grid;
import appeng.me.service.EnergyService;
import appeng.server.ISubCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class DebugEnergyCommand implements ISubCommand {
    private static String getModeLabel(boolean enabled) {
        return enabled ? Tooltips.On.text().getFormattedText() : Tooltips.Off.text().getFormattedText();
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.debugenergy";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "on", "off");
        }
        return ISubCommand.super.getTabCompletions(srv, sender, args, targetPos);
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        if (args.length == 1) {
            sender.sendMessage(PlayerMessages.DebugEnergyCurrent.text(
                getModeLabel(AEConfig.instance().isDebugEnergyEnabled())));
            return;
        }

        if (args.length != 2) {
            throw new WrongUsageException("commands.ae2.debugenergy");
        }

        boolean enabled;
        if ("on".equalsIgnoreCase(args[1])) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(args[1])) {
            enabled = false;
        } else {
            throw new WrongUsageException("commands.ae2.debugenergy");
        }

        AELog.info("%s is changing debug energy mode to %s", sender.getName(), enabled ? "on" : "off");
        AEConfig.instance().setDebugEnergyEnabled(enabled);
        AEConfig.instance().save();

        int gridCount = 0;
        for (Grid grid : TickHandler.instance().getGridList()) {
            ((EnergyService) grid.getEnergyService()).onCreativePowerModeChanged();
            gridCount++;
        }

        sender.sendMessage(PlayerMessages.DebugEnergySet.text(getModeLabel(enabled), gridCount));
    }
}
