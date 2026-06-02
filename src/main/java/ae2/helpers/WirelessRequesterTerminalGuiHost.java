package ae2.helpers;

import ae2.api.networking.IGridNode;
import ae2.container.ISubGui;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.powered.WirelessTerminalItem;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class WirelessRequesterTerminalGuiHost
    extends WirelessTerminalGuiHost<WirelessTerminalItem>
    implements RequesterTerminalHost {

    public WirelessRequesterTerminalGuiHost(WirelessTerminalItem stackItem,
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
}
