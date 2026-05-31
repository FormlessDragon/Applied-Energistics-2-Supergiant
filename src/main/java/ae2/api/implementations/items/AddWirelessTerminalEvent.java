package ae2.api.implementations.items;

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
                                                     Function<WirelessTerminalItem, ItemStack> iconFactory) {
        return new WirelessTerminalDefinitionBuilder(this, id, item, guiOpener, hostFactory, iconFactory);
    }

    void add(WirelessTerminalDefinition definition) {
        WirelessTerminalRegistry.register(definition);
    }
}
