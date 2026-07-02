package ae2.api.implementations.items;

import ae2.core.gui.GuiOpener;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AddWirelessTerminalEvent {
    private static List<Consumer<AddWirelessTerminalEvent>> handlers = new ObjectArrayList<>();

    private AddWirelessTerminalEvent() {
    }

    public static synchronized void register(Consumer<AddWirelessTerminalEvent> handler) {
        if (handlers == null) {
            throw new IllegalStateException("Cannot register wireless terminal after terminal registration has finished");
        }
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public static synchronized void run() {
        if (handlers == null) {
            throw new IllegalStateException("Wireless terminal registration already ran");
        }
        AddWirelessTerminalEvent event = new AddWirelessTerminalEvent();
        for (Consumer<AddWirelessTerminalEvent> handler : handlers) {
            handler.accept(event);
        }
        handlers = null;
        WirelessTerminalRegistry.freeze();
    }

    @SuppressWarnings("unused")
    public static synchronized boolean didRun() {
        return handlers == null;
    }

    public WirelessTerminalDefinitionBuilder builder(String id, WirelessTerminalItem item,
                                                     WirelessTerminalDefinition.GuiOpener guiOpener,
                                                     WirelessTerminalDefinition.HostFactory hostFactory,
                                                     WirelessTerminalDefinition.ContainerFactory containerFactory,
                                                     WirelessTerminalDefinition.ScreenFactory screenFactory,
                                                     Function<WirelessTerminalItem, ItemStack> iconFactory) {
        return new WirelessTerminalDefinitionBuilder(this, id, item, guiOpener, hostFactory, containerFactory,
            screenFactory, iconFactory);
    }

    public WirelessTerminalDefinitionBuilder builder(String id, WirelessTerminalItem item,
                                                     WirelessTerminalDefinition.HostFactory hostFactory,
                                                     WirelessTerminalDefinition.ContainerFactory containerFactory,
                                                     WirelessTerminalDefinition.ScreenFactory screenFactory,
                                                     Function<WirelessTerminalItem, ItemStack> iconFactory) {
        return builder(id, item,
            (definition, player, locator, stack, returningFromSubmenu) ->
                !stack.isEmpty() && GuiOpener.openWirelessTerminalGui(player, definition, locator,
                    returningFromSubmenu),
            hostFactory, containerFactory, screenFactory, iconFactory);
    }

    void add(WirelessTerminalDefinition definition) {
        WirelessTerminalRegistry.register(definition);
    }
}
