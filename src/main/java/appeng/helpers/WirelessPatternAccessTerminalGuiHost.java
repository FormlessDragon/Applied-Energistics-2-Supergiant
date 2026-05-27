package appeng.helpers;

import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IPatternAccessTermContainerHost;
import appeng.api.util.IConfigManager;
import appeng.container.ISubGui;
import appeng.core.gui.locator.ItemGuiHostLocator;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.WirelessTerminals;
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
