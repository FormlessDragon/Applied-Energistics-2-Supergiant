package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.container.guisync.GuiSync;
import ae2.parts.automation.StockExportBusPart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerAdvancedIOBus extends ContainerStockExportBus {
    @GuiSync(21)
    public YesNo regulate = YesNo.YES;

    public ContainerAdvancedIOBus(InventoryPlayer ip, StockExportBusPart host) {
        super(ip, host);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getHost().getConfigManager().hasSetting(Settings.REGULATE_STOCK)) {
            this.regulate = getHost().getConfigManager().getSetting(Settings.REGULATE_STOCK);
        }
        super.broadcastChanges();
    }

    public YesNo getRegulate() {
        return regulate;
    }
}
