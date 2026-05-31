package ae2.helpers;

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.networking.IGridNode;
import ae2.api.storage.IPatternAccessTermContainerHost;
import ae2.api.util.IConfigManager;
import ae2.container.ISubGui;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminals;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class WirelessPatternAccessTerminalGuiHost
    extends WirelessTerminalGuiHost<WirelessTerminalItem>
    implements IPatternAccessTermContainerHost {

    private final IConfigManager configManager = WirelessTerminals.configBuilder(this::getItemStack, getTerminalItem())
                                                                  .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                                                                  .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                                                                  .registerSetting(Settings.SORT_DIRECTION,
                                                                      SortDir.ASCENDING)
                                                                  .registerSetting(
                                                                      Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                                                                      ShowPatternProviders.VISIBLE)
                                                                  .build();

    public WirelessPatternAccessTerminalGuiHost(WirelessTerminalItem stackItem,
                                                WirelessTerminalItem terminalItem, EntityPlayer player,
                                                ItemGuiHostLocator locator,
                                                BiConsumer<EntityPlayer, ISubGui> returnToMainGui) {
        super(stackItem, terminalItem, player, locator, returnToMainGui);
    }

    @Nullable
    @Override
    public IGridNode getGridNode() {
        return getActionableNode();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }
}
