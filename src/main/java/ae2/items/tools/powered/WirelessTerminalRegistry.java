package ae2.items.tools.powered;

import ae2.api.implementations.items.WirelessTerminalDefinition;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class WirelessTerminalRegistry {
    private static final Map<String, WirelessTerminalDefinition> BY_ID = new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<WirelessTerminalItem, WirelessTerminalDefinition> BY_ITEM = new Object2ObjectLinkedOpenHashMap<>();
    private static List<WirelessTerminalDefinition> frozenDefinitions = List.of();
    private static boolean frozen;

    private WirelessTerminalRegistry() {
    }

    public static synchronized void register(WirelessTerminalDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("Cannot register wireless terminal after registration has finished");
        }
        WirelessTerminalDefinition existing = BY_ID.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            throw new IllegalStateException("Duplicate wireless terminal id: " + definition.id());
        }
        BY_ITEM.put(definition.item(), definition);
        definition.item().setWirelessTerminalDefinition(definition);
    }

    public static synchronized void freeze() {
        frozenDefinitions = List.copyOf(BY_ID.values());
        frozen = true;
    }

    public static synchronized Collection<WirelessTerminalDefinition> allDefinitions() {
        return frozen ? frozenDefinitions : List.copyOf(BY_ID.values());
    }

    public static synchronized Collection<WirelessTerminalItem> all() {
        return BY_ID.values().stream().map(WirelessTerminalDefinition::item).toList();
    }

    public static synchronized int indexOf(WirelessTerminalDefinition definition) {
        int index = allDefinitions().stream().toList().indexOf(definition);
        if (index < 0) {
            throw new IllegalArgumentException("Wireless terminal is not registered: " + definition.id());
        }
        return index;
    }

    @Nullable
    public static synchronized WirelessTerminalDefinition definitionAtIndex(int index) {
        Collection<WirelessTerminalDefinition> definitions = allDefinitions();
        if (index < 0 || index >= definitions.size()) {
            return null;
        }
        if (definitions instanceof List<WirelessTerminalDefinition> list) {
            return list.get(index);
        }
        return definitions.stream().skip(index).findFirst().orElse(null);
    }

    @Nullable
    @SuppressWarnings("unused")
    public static synchronized WirelessTerminalItem ofId(String id) {
        WirelessTerminalDefinition definition = BY_ID.get(id);
        return definition == null ? null : definition.item();
    }

    @Nullable
    public static synchronized WirelessTerminalDefinition definitionOfId(String id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static synchronized WirelessTerminalDefinition ofItem(WirelessTerminalItem item) {
        return BY_ITEM.get(item);
    }

    @Nullable
    public static WirelessTerminalItem ofStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal) {
            return universalTerminal.getCurrentTerminal(stack);
        }
        if (stack.getItem() instanceof WirelessTerminalItem terminal) {
            return ofItem(terminal) == null ? null : terminal;
        }
        return null;
    }
}
