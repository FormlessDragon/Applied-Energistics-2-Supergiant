package ae2.server.subcommands;

import ae2.api.networking.pathing.ChannelMode;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.hooks.ticking.TickHandler;
import ae2.me.Grid;
import ae2.server.ISubCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.Locale;

public class ChannelModeCommand implements ISubCommand {
    private static ChannelMode parseMode(String name) throws WrongUsageException {
        try {
            return ChannelMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException("commands.ae2.channelmode");
        }
    }

    private static String[] getModeNames() {
        ChannelMode[] modes = ChannelMode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            names[i] = modes[i].name().toLowerCase(Locale.ROOT);
        }
        return names;
    }

    private static ITextComponent getModeLabel(ChannelMode mode) {
        return switch (mode) {
            case DEFAULT -> GuiText.ChannelModeDefault.text();
            case INFINITE -> GuiText.ChannelModeInfinite.text();
            case X2 -> GuiText.ChannelModeX2.text();
            case X3 -> GuiText.ChannelModeX3.text();
            case X4 -> GuiText.ChannelModeX4.text();
        };
    }

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.channelmode";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) throws CommandException {
        if (args.length == 1) {
            ChannelMode mode = AEConfig.instance().getChannelMode();
            sender.sendMessage(PlayerMessages.ChannelModeCurrent.text(getModeLabel(mode)));
            return;
        }

        if (args.length != 2) {
            throw new WrongUsageException("commands.ae2.channelmode");
        }

        ChannelMode mode = parseMode(args[1]);
        AELog.info("%s is changing channel mode to %s", sender.getName(), mode);
        AEConfig.instance().setChannelMode(mode);
        AEConfig.instance().save();

        int gridCount = 0;
        for (Grid grid : TickHandler.instance().getGridList()) {
            grid.getPathingService().repath();
            gridCount++;
        }

        sender.sendMessage(PlayerMessages.ChannelModeSet.text(getModeLabel(mode), gridCount));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length != 2) {
            return ISubCommand.super.getTabCompletions(srv, sender, args, targetPos);
        }

        return CommandBase.getListOfStringsMatchingLastWord(args, getModeNames());
    }
}
