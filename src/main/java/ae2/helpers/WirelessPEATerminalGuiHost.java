package ae2.helpers;

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.config.YesNo;
import ae2.api.storage.IPEATermContainerHost;
import ae2.api.util.IConfigManager;
import ae2.container.ISubGui;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.parts.encoding.PatternEncodingLogic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.function.BiConsumer;

public class WirelessPEATerminalGuiHost
    extends WirelessTerminalGuiHost<WirelessTerminalItem>
    implements IPEATermContainerHost, IPatternTerminalLogicHost {

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final IConfigManager configManager = WirelessTerminals.configBuilder(this::getItemStack, getTerminalItem())
                                                                  .registerSetting(Settings.PATTERN_AUTO_FILL, YesNo.NO)
                                                                  .registerSetting(
                                                                      Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                                                                      ShowPatternProviders.VISIBLE)
                                                                  .build();

    public WirelessPEATerminalGuiHost(WirelessTerminalItem stackItem,
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
