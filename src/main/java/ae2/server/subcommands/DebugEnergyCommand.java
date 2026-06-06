package ae2.server.subcommands;

import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.localization.Tooltips;
import ae2.hooks.ticking.TickHandler;
import ae2.me.Grid;
import ae2.me.service.EnergyService;
import ae2.server.ISubCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class DebugEnergyCommand implements ISubCommand {
    private static ITextComponent getModeLabel(boolean enabled) {
        return enabled ? Tooltips.On.text() : Tooltips.Off.text();
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
