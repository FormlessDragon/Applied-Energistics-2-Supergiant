package ae2.server;

import ae2.core.AEConfig;
import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AECommand extends CommandBase {
    private final MinecraftServer server;

    public AECommand(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getName() {
        return "ae2";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.ae2.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("commands.ae2.usage");
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(action)) {
            if (args.length == 1) {
                throw new WrongUsageException("commands.ae2.usage");
            }

            Commands command = Commands.fromName(args[1]);
            if (command == null) {
                throw new WrongUsageException("commands.ae2.usage");
            }

            throw new WrongUsageException(command.command.getHelp(this.server));
        }

        if ("list".equals(action)) {
            throw new WrongUsageException(Joiner.on(", ").join(Commands.values()));
        }

        Commands command = Commands.fromName(action);
        if (command == null) {
            throw new WrongUsageException("commands.ae2.usage");
        }

        if (!sender.canUseCommand(command.level, this.getName())) {
            throw new WrongUsageException("commands.ae2.permissions");
        }

        if (command.test && !AEConfig.instance().isDebugToolsEnabled()) {
            throw new WrongUsageException("commands.ae2.permissions");
        }

        command.command.call(this.server, args, sender);
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 1) {
            ObjectList<String> candidates = new ObjectArrayList<>();
            candidates.add("help");
            candidates.add("list");
            for (Commands command : Commands.values()) {
                candidates.add(command.getName());
            }
            return getListOfStringsMatchingLastWord(args, candidates);
        }

        if (args.length == 2 && "help".equalsIgnoreCase(args[0])) {
            ObjectList<String> candidates = new ObjectArrayList<>();
            for (Commands command : Commands.values()) {
                candidates.add(command.getName());
            }
            return getListOfStringsMatchingLastWord(args, candidates);
        }

        if (args.length > 1) {
            Commands command = Commands.fromName(args[0]);
            if (command == null || !sender.canUseCommand(command.level, this.getName())
                || command.test && !AEConfig.instance().isDebugToolsEnabled()) {
                return Collections.emptyList();
            }
            return command.command.getTabCompletions(this.server, sender, args, targetPos);
        }

        return Collections.emptyList();
    }
}
