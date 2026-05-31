package ae2.api.implementations.items;

import ae2.api.upgrades.Upgrades;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.item.Item;

@SuppressWarnings("unused")
public final class WirelessTerminalUpgradeHelper {
    private static final Reference2IntMap<Item> DEFERRED_UPGRADES = new Reference2IntLinkedOpenHashMap<>();
    private static boolean ready;

    private WirelessTerminalUpgradeHelper() {
    }

    public static synchronized void addDefaultUpgrades() {
        addUpgradeToAllTerminals(AEItems.ENERGY_CARD.item(), 0);
        ready = true;
        for (var upgrade : DEFERRED_UPGRADES.reference2IntEntrySet()) {
            addUpgradeToAllTerminals(upgrade.getKey(), upgrade.getIntValue());
        }
        DEFERRED_UPGRADES.clear();
    }

    public static synchronized void addUpgradeToAllTerminals(Item upgradeCard, int maxSupported) {
        if (!ready) {
            DEFERRED_UPGRADES.put(upgradeCard, maxSupported);
            return;
        }

        int universalMax = 0;
        String group = GuiText.WirelessTerminals.getTranslationKey();
        for (WirelessTerminalDefinition definition : WirelessTerminalRegistry.allDefinitions()) {
            int max = definition.upgradeSlots();
            if (max <= 0) {
                continue;
            }
            int terminalMax = maxSupported == 0 ? max : Math.min(maxSupported, max);
            Upgrades.add(upgradeCard, definition.item(), terminalMax, group);
            universalMax += terminalMax;
        }

        Item universalTerminal = AEItems.WIRELESS_UNIVERSAL_TERMINAL.item();
        if (universalTerminal != null && universalMax > 0) {
            Upgrades.add(upgradeCard, universalTerminal, maxSupported == 0 ? universalMax : maxSupported, group);
        }
    }
}
