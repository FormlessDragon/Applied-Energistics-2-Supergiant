package ae2.integration.modules.igtooltip.blocks;

import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.text.TextComponentItemStack;
import ae2.tile.misc.TileCharger;
import net.minecraft.item.ItemStack;

/**
 * Shows the tooltip of the item being charged, which usually includes a charge meter.
 */
public final class ChargerDataProvider implements BodyProvider<TileCharger> {
    @Override
    public void buildTooltip(TileCharger charger, TooltipContext context, TooltipBuilder tooltip) {
        ItemStack chargingItem = charger.getClientDisplayItem();

        if (!chargingItem.isEmpty()) {
            tooltip.addLine(TopText.contains.text(TextComponentItemStack.of(chargingItem)));

            if (chargingItem.getItem() instanceof IAEItemPowerStorage powerStorage) {
                var fillRate = (int) Math.floor(
                    powerStorage.getAECurrentPower(chargingItem) * 100 / powerStorage.getAEMaxPower(chargingItem));
                tooltip.addLine(TopText.charged.text(fillRate));
            }
        }
    }
}
