package ae2.api.implementations.items;

import ae2.items.tools.powered.WirelessTerminalItem;
import net.minecraft.item.ItemStack;

import java.util.Objects;
import java.util.function.Function;

public final class WirelessTerminalDefinitionBuilder {
    private final AddWirelessTerminalEvent event;
    private final String id;
    private final WirelessTerminalItem item;
    private final WirelessTerminalDefinition.GuiOpener guiOpener;
    private final WirelessTerminalDefinition.HostFactory hostFactory;
    private final Function<WirelessTerminalItem, ItemStack> iconFactory;
    private String hotkeyName;
    private int upgradeSlots = 2;

    WirelessTerminalDefinitionBuilder(AddWirelessTerminalEvent event, String id, WirelessTerminalItem item,
                                      WirelessTerminalDefinition.GuiOpener guiOpener,
                                      WirelessTerminalDefinition.HostFactory hostFactory,
                                      Function<WirelessTerminalItem, ItemStack> iconFactory) {
        this.event = Objects.requireNonNull(event, "event");
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        this.guiOpener = Objects.requireNonNull(guiOpener, "guiOpener");
        this.hostFactory = Objects.requireNonNull(hostFactory, "hostFactory");
        this.iconFactory = Objects.requireNonNull(iconFactory, "iconFactory");
        this.hotkeyName = "wireless_" + id + "_terminal";
    }

    public WirelessTerminalDefinitionBuilder hotkeyName(String hotkeyName) {
        this.hotkeyName = Objects.requireNonNull(hotkeyName, "hotkeyName");
        return this;
    }

    public WirelessTerminalDefinitionBuilder upgradeSlots(int upgradeSlots) {
        this.upgradeSlots = upgradeSlots;
        return this;
    }

    @SuppressWarnings("unused")
    public WirelessTerminalDefinitionBuilder upgradeCount(int upgradeCount) {
        return upgradeSlots(upgradeCount);
    }

    @SuppressWarnings("unused")
    public WirelessTerminalDefinitionBuilder noUpgrades() {
        return upgradeSlots(0);
    }

    @SuppressWarnings("UnusedReturnValue")
    public WirelessTerminalDefinition addTerminal() {
        WirelessTerminalDefinition definition = new WirelessTerminalDefinition(this.id, this.item, this.guiOpener,
            this.iconFactory, this.hostFactory, this.hotkeyName, this.upgradeSlots);
        this.event.add(definition);
        return definition;
    }
}
