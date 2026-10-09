package ae2.api.implementations.items;

import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionHost;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.BaublesItemLocator;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.InventoryItemLocator;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.modules.baubles.BaublesIntegration;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.me.helpers.ActionHostEnergySource;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class WirelessTerminalApi {
    private static final Object2ObjectMap<UUID, Object2ObjectMap<Object, WirelessTerminalGuiHost<?>>> HOST_CACHE =
        new Object2ObjectOpenHashMap<>();

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

    public static void clear() {
        HOST_CACHE.clear();
    }

    public static void clear(UUID playerId) {
        HOST_CACHE.remove(playerId);
    }

    /**
     * The usable terminal in one slot, or null when that slot holds no terminal or the one it holds is not usable.
     */
    @Nullable
    public static TerminalContext findUsableWirelessTermOfPlayer(EntityPlayerMP player, Predicate<TerminalContext> predicate,
                                                                 int slot, boolean isBaubleSlot) {
        ItemGuiHostLocator locator = isBaubleSlot
            ? GuiHostLocators.forBaubleSlot(slot)
            : GuiHostLocators.forInventorySlot(slot);
        TerminalContext context = getTerminal(player, locator);
        return context != null && predicate.test(context) ? context : null;
    }

    public static TerminalContext findUsableWirelessTermOfPlayer(EntityPlayerMP player, Predicate<TerminalContext> predicate) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            var context = findUsableWirelessTermOfPlayer(player, predicate, slot, false);
            if (context != null) {
                return context;
            }
        }

        int baubleSlots = BaublesIntegration.getSlots(player);
        for (int slot = 0; slot < baubleSlots; slot++) {
            var context = findUsableWirelessTermOfPlayer(player, predicate, slot, true);
            if (context != null) {
                return context;
            }
        }

        return null;
    }

    @Nullable
    private static TerminalContext getTerminal(EntityPlayerMP player, ItemGuiHostLocator locator) {
        ItemStack stack = locator.locateItem(player);
        WirelessTerminalItem terminal = WirelessTerminalRegistry.ofStack(stack);
        if (terminal == null) {
            return null;
        }

        Object cacheKey = locator instanceof InventoryItemLocator inventoryLocator
            ? new TerminalSlotKey(false, inventoryLocator.getItemIndex())
            : locator instanceof BaublesItemLocator baublesLocator
            ? new TerminalSlotKey(true, baublesLocator.getBaubleSlot())
            : locator;
        Object2ObjectMap<Object, WirelessTerminalGuiHost<?>> playerHosts = HOST_CACHE.get(player.getUniqueID());
        if (playerHosts == null) {
            playerHosts = new Object2ObjectOpenHashMap<>();
            HOST_CACHE.put(player.getUniqueID(), playerHosts);
        }

        WirelessTerminalGuiHost<?> host = playerHosts.get(cacheKey);
        if (host == null || host.getItem() != stack.getItem() || host.getTerminalItem() != terminal) {
            host = locator.locate(player, WirelessTerminalGuiHost.class);
            if (host == null) {
                return null;
            }
            playerHosts.put(cacheKey, host);
        } else {
            host.refreshConnectionState();
        }

        if (!host.getLinkStatus().connected()) {
            return null;
        }
        IGridNode node = host.getActionableNode();
        if (node == null || !node.isActive()) {
            return null;
        }
        IEnergySource energySource = host instanceof IEnergySource source
            ? source
            : new ActionHostEnergySource(host);
        return new TerminalContext(player, locator, stack, terminal, host, host, node, energySource);
    }

    private record TerminalSlotKey(boolean bauble, int slot) {
    }

    public record TerminalContext(EntityPlayerMP player, ItemGuiHostLocator locator, ItemStack stack,
                                   WirelessTerminalItem terminal, WirelessTerminalGuiHost<?> host,
                                   IActionHost actionHost,
                                   IGridNode node, IEnergySource energySource) {
    }
}
