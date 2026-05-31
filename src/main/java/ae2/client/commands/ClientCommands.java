package ae2.client.commands;

import ae2.core.AEConfig;
import ae2.core.localization.PlayerMessages;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ClientCommands {
    private static final List<String> DEBUG_COMMANDS = ObjectLists.singleton("highlight_gui_areas");

    private ClientCommands() {
    }

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new AE2ClientCommand());
    }

    private static final class AE2ClientCommand extends CommandBase {
        @Override
        public String getName() {
            return "ae2client";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "commands.ae2client.usage";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender));
            }

            String action = args[0].toLowerCase(Locale.ROOT);
            if ("highlight_gui_areas".equals(action) && AEConfig.instance().isDebugToolsEnabled()) {
                boolean enabled = !AEConfig.instance().isShowDebugGuiOverlays();
                AEConfig.instance().setShowDebugGuiOverlays(enabled);
                sender.sendMessage((enabled ? PlayerMessages.GuiOverlaysEnabled : PlayerMessages.GuiOverlaysDisabled).text());
                return;
            }

            throw new WrongUsageException(getUsage(sender));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              BlockPos targetPos) {
            if (args.length == 1 && AEConfig.instance().isDebugToolsEnabled()) {
                return getListOfStringsMatchingLastWord(args, DEBUG_COMMANDS);
            }

            return Collections.emptyList();
        }
    }
}
