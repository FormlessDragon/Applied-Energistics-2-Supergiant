package ae2.api.implementations.items;

import ae2.api.upgrades.IUpgradeInventory;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class WirelessTerminalApi {
    private WirelessTerminalApi() {
    }

    public static boolean hasQuantumBridgeCard(Supplier<IUpgradeInventory> upgrades) {
        return Objects.requireNonNull(upgrades, "upgrades").get().isInstalled(AEItems.QUANTUM_BRIDGE_CARD.item());
    }

    public static boolean isUniversalTerminal(Item item) {
        return item instanceof WirelessUniversalTerminalItem;
    }

    public static boolean isUniversalTerminal(ItemStack stack) {
        return !stack.isEmpty() && isUniversalTerminal(stack.getItem());
    }

    @Nullable
    public static Item getUniversalTerminal() {
        return AEItems.WIRELESS_UNIVERSAL_TERMINAL.item();
    }

    public static ItemStack makeUniversalTerminal(WirelessTerminalDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return makeUniversalTerminal(new ItemStack(definition.item()), definition);
    }

    public static ItemStack makeUniversalTerminal(ItemStack terminalStack) {
        WirelessTerminalDefinition definition = ofStack(terminalStack);
        return definition == null ? ItemStack.EMPTY : makeUniversalTerminal(terminalStack, definition);
    }

    public static ItemStack makeUniversalTerminal(ItemStack terminalStack, WirelessTerminalDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Item item = getUniversalTerminal();
        if (!(item instanceof WirelessUniversalTerminalItem universalTerminal)
            || terminalStack.isEmpty()
            || terminalStack.getItem() != definition.item()) {
            return ItemStack.EMPTY;
        }

        ItemStack universal = new ItemStack(universalTerminal);
        universalTerminal.addTerminal(universal, terminalStack, definition.item());
        return universal;
    }

    public static ItemStack mergeUniversalTerminal(ItemStack universalStack, ItemStack terminalStack) {
        WirelessTerminalDefinition definition = ofStack(terminalStack);
        if (definition == null
            || !(universalStack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return ItemStack.EMPTY;
        }
        if (universalTerminal.hasTerminal(universalStack, definition.item())) {
            return ItemStack.EMPTY;
        }

        ItemStack result = universalStack.copy();
        result.setCount(1);
        universalTerminal.addTerminal(result, terminalStack, definition.item());
        return result;
    }

    public static Collection<WirelessTerminalDefinition> wirelessTerminals() {
        return WirelessTerminalRegistry.allDefinitions();
    }

    @Nullable
    public static WirelessTerminalDefinition ofId(String id) {
        return WirelessTerminalRegistry.definitionOfId(id);
    }

    @Nullable
    public static WirelessTerminalDefinition ofItem(WirelessTerminalItem item) {
        return WirelessTerminalRegistry.ofItem(item);
    }

    @Nullable
    public static WirelessTerminalDefinition ofStack(ItemStack stack) {
        WirelessTerminalItem terminal = WirelessTerminalRegistry.ofStack(stack);
        return terminal == null ? null : terminal.getWirelessTerminalDefinition();
    }

    public static boolean selectTerminal(ItemStack stack, WirelessTerminalDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal
            && universalTerminal.selectTerminal(stack, definition.id());
    }

    public static boolean updateClientTerminal(EntityPlayerMP player, ItemGuiHostLocator locator, ItemStack stack) {
        WirelessTerminalDefinition definition = ofStack(stack);
        return definition != null && definition.open(player, locator, stack, true);
    }
}
