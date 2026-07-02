package ae2.items.tools.powered;

import ae2.api.cellterminal.CellTerminalContainerHost;
import ae2.api.implementations.guiobjects.IPortableTerminal;
import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.client.gui.implementations.GuiCellTerminal;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.me.items.GuiCraftingTerm;
import ae2.client.gui.me.items.GuiPatternEncodingTerm;
import ae2.client.gui.me.patternaccess.GuiPatternAccessTerm;
import ae2.client.gui.me.requester.GuiRequesterTerm;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerCellTerminal;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.container.implementations.ContainerRequesterTerm;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.container.me.items.ContainerWirelessCraftingTerm;
import ae2.helpers.WirelessCraftingTerminalGuiHost;
import ae2.helpers.WirelessPatternAccessTerminalGuiHost;
import ae2.helpers.WirelessPatternEncodingTerminalGuiHost;
import ae2.helpers.WirelessRequesterTerminalGuiHost;

final class WirelessTerminalDefinitionFactories {
    private WirelessTerminalDefinitionFactories() {
    }

    static WirelessTerminalDefinition.ContainerFactory storageContainer(GuiIds.GuiKey guiKey) {
        return (definition, inventory, host) -> host instanceof IPortableTerminal portableTerminal
            ? new ContainerMEStorage(guiKey, inventory, portableTerminal)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory storageScreen(String stylePath) {
        return (definition, container, inventory) -> container instanceof ContainerMEStorage storage
            ? new GuiMEStorage<>(storage, inventory, null, GuiStyleManager.loadStyleDoc(stylePath))
            : null;
    }

    static WirelessTerminalDefinition.ContainerFactory cellTerminalContainer() {
        return (definition, inventory, host) -> host instanceof CellTerminalContainerHost cellTerminalHost
            ? new ContainerCellTerminal(inventory, cellTerminalHost)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory cellTerminalScreen() {
        return (definition, container, inventory) -> container instanceof ContainerCellTerminal cellTerminal
            ? new GuiCellTerminal(cellTerminal, inventory)
            : null;
    }

    static WirelessTerminalDefinition.ContainerFactory craftingContainer() {
        return (definition, inventory, host) -> host instanceof WirelessCraftingTerminalGuiHost craftingHost
            ? new ContainerWirelessCraftingTerm(inventory, craftingHost)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory craftingScreen() {
        return (definition, container, inventory) -> container instanceof ContainerWirelessCraftingTerm craftingTerm
            ? new GuiCraftingTerm(craftingTerm, inventory, null,
                GuiStyleManager.loadStyleDoc("/screens/terminals/crafting_terminal.json"))
            : null;
    }

    static WirelessTerminalDefinition.ContainerFactory patternEncodingContainer() {
        return (definition, inventory, host) -> host instanceof WirelessPatternEncodingTerminalGuiHost patternHost
            ? new ContainerPatternEncodingTerm(GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL, inventory,
                patternHost, true)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory patternEncodingScreen() {
        return (definition, container, inventory) -> container instanceof ContainerPatternEncodingTerm patternTerm
            ? new GuiPatternEncodingTerm(patternTerm, inventory, null,
                GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"))
            : null;
    }

    static WirelessTerminalDefinition.ContainerFactory patternAccessContainer() {
        return (definition, inventory, host) -> host instanceof WirelessPatternAccessTerminalGuiHost patternHost
            ? new ContainerPatternAccessTerm(inventory, patternHost)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory patternAccessScreen() {
        return (definition, container, inventory) -> container instanceof ContainerPatternAccessTerm patternAccess
            ? new GuiPatternAccessTerm<>(patternAccess, inventory, null,
                GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_access_terminal.json"))
            : null;
    }

    static WirelessTerminalDefinition.ContainerFactory requesterContainer() {
        return (definition, inventory, host) -> host instanceof WirelessRequesterTerminalGuiHost requesterHost
            ? new ContainerRequesterTerm(inventory, requesterHost)
            : null;
    }

    static WirelessTerminalDefinition.ScreenFactory requesterScreen() {
        return (definition, container, inventory) -> container instanceof ContainerRequesterTerm requesterTerm
            ? new GuiRequesterTerm(requesterTerm, inventory, null,
                GuiStyleManager.loadStyleDoc("/screens/terminals/requester_terminal.json"))
            : null;
    }
}
