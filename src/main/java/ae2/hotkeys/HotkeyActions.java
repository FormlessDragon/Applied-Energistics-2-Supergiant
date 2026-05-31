package ae2.hotkeys;

import ae2.api.features.HotkeyAction;
import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.ItemDefinition;
import ae2.core.gui.GuiOpener;
import ae2.items.tools.powered.AbstractPortableCell;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ae2.api.features.HotkeyAction.PATTERN_MODIFIER;
import static ae2.api.features.HotkeyAction.PORTABLE_FLUID_CELL;
import static ae2.api.features.HotkeyAction.PORTABLE_ITEM_CELL;

/**
 * Registry of {@link HotkeyAction}
 */
public final class HotkeyActions {
    public static final String WIRELESS_RESTOCK = "wireless_restock";
    public static final String WIRELESS_STOW = "wireless_stow";
    public static final String WIRELESS_MAGNET = "wireless_magnet";
    public static final Map<String, List<HotkeyAction>> REGISTRY = new Object2ObjectOpenHashMap<>();

    private HotkeyActions() {
    }

    public static void init() {
        for (WirelessTerminalDefinition definition : WirelessTerminalRegistry.allDefinitions()) {
            registerWirelessTerminal(definition);
        }

        registerPortableCell(AEItems.PORTABLE_ITEM_CELL1K, PORTABLE_ITEM_CELL);
        registerPortableCell(AEItems.PORTABLE_ITEM_CELL4K, PORTABLE_ITEM_CELL);
        registerPortableCell(AEItems.PORTABLE_ITEM_CELL16K, PORTABLE_ITEM_CELL);
        registerPortableCell(AEItems.PORTABLE_ITEM_CELL64K, PORTABLE_ITEM_CELL);
        registerPortableCell(AEItems.PORTABLE_ITEM_CELL256K, PORTABLE_ITEM_CELL);

        registerPortableCell(AEItems.PORTABLE_FLUID_CELL1K, PORTABLE_FLUID_CELL);
        registerPortableCell(AEItems.PORTABLE_FLUID_CELL4K, PORTABLE_FLUID_CELL);
        registerPortableCell(AEItems.PORTABLE_FLUID_CELL16K, PORTABLE_FLUID_CELL);
        registerPortableCell(AEItems.PORTABLE_FLUID_CELL64K, PORTABLE_FLUID_CELL);
        registerPortableCell(AEItems.PORTABLE_FLUID_CELL256K, PORTABLE_FLUID_CELL);

        register(new RestockHotkeyAction(), WIRELESS_RESTOCK);
        register(new StowHotkeyAction(), WIRELESS_STOW);
        register(new MagnetHotkeyAction(), WIRELESS_MAGNET);
        register(Objects.requireNonNull(AEItems.PATTERN_MODIFIER.item()),
            (player, locator) -> GuiOpener.openItemGui(player, GuiIds.GuiKey.PATTERN_MODIFIER, locator),
            PATTERN_MODIFIER);
    }

    public static void registerPortableCell(ItemDefinition<? extends AbstractPortableCell> cell, String id) {
        register(Objects.requireNonNull(cell.item()), (player, locator) -> cell.get().openFromInventory(player, locator),
            id);
    }

    private static void registerWirelessTerminal(WirelessTerminalDefinition definition) {
        register(new WirelessTerminalHotkeyAction(definition), definition.hotkeyName());
    }

    public static void register(Item item, InventoryHotkeyAction.Opener opener, String id) {
        register(new InventoryHotkeyAction(item, opener), id);
        register(new BaublesHotkeyAction(item, opener), id);
    }

    public static synchronized void register(HotkeyAction hotkeyAction, String id) {
        List<HotkeyAction> actions = REGISTRY.get(id);
        if (actions == null) {
            actions = new ObjectArrayList<>();
            REGISTRY.put(id, actions);
            AppEng.instance().registerHotkey(id);
        }

        actions.addFirst(hotkeyAction);
    }
}
