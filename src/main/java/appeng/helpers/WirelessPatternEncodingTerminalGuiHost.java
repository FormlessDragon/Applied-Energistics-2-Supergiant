package appeng.helpers;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.container.ISubGui;
import appeng.core.gui.locator.ItemGuiHostLocator;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.WirelessTerminals;
import appeng.parts.encoding.PatternEncodingLogic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.function.BiConsumer;

public class WirelessPatternEncodingTerminalGuiHost
    extends WirelessTerminalGuiHost<WirelessTerminalItem>
    implements IPatternTerminalGuiHost, IPatternTerminalLogicHost {

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final IConfigManager configManager = WirelessTerminals.configBuilder(this::getItemStack, getTerminalItem())
                                                                  .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                                                                  .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                                                                  .registerSetting(Settings.SORT_DIRECTION,
                                                                      SortDir.ASCENDING)
                                                                  .registerSetting(Settings.PATTERN_AUTO_FILL, YesNo.NO)
                                                                  .build();

    public WirelessPatternEncodingTerminalGuiHost(WirelessTerminalItem stackItem,
                                                  WirelessTerminalItem terminalItem,
                                                  EntityPlayer player, ItemGuiHostLocator locator,
                                                  BiConsumer<EntityPlayer, ISubGui> returnToMainGui) {
        super(stackItem, terminalItem, player, locator, returnToMainGui);
        NBTTagCompound terminalData = WirelessTerminals.getExistingTerminalData(getItemStack(), terminalItem);
        if (terminalData != null && terminalData.hasKey(WirelessTerminals.TAG_PATTERN_ENCODING, 10)) {
            this.logic.readFromNBT(terminalData.getCompoundTag(WirelessTerminals.TAG_PATTERN_ENCODING));
        }
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return this.logic;
    }

    @Override
    public World getLevel() {
        return getPlayer().world;
    }

    @Override
    public void markForSave() {
        NBTTagCompound logicTag = new NBTTagCompound();
        this.logic.writeToNBT(logicTag);
        WirelessTerminals.getTerminalData(getItemStack(), getTerminalItem())
                         .setTag(WirelessTerminals.TAG_PATTERN_ENCODING, logicTag);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }
}
