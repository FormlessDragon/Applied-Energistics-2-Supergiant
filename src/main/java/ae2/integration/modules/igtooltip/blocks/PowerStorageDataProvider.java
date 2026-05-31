package ae2.integration.modules.igtooltip.blocks;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

/**
 * Shows stored power and max stored power for an {@link IAEPowerStorage} tile entity.
 */
public final class PowerStorageDataProvider implements BodyProvider<TileEntity>, ServerDataProvider<TileEntity> {

    /**
     * Power key used for the transferred {@link NBTTagCompound}
     */
    private static final String TAG_CURRENT_POWER = "currentPower";
    private static final String TAG_MAX_POWER = "maxPower";

    @Override
    public void buildTooltip(TileEntity object, TooltipContext context, TooltipBuilder tooltip) {
        var tag = context.serverData();
        if (tag.hasKey(TAG_MAX_POWER, Constants.NBT.TAG_DOUBLE)) {
            var currentPower = tag.getDouble(TAG_CURRENT_POWER);
            var maxPower = tag.getDouble(TAG_MAX_POWER);

            var formatCurrentPower = Platform.formatPower(currentPower, false);
            var formatMaxPower = Platform.formatPower(maxPower, false);

            tooltip.addLine(TopText.stored_energy.text(formatCurrentPower, formatMaxPower));
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, TileEntity object, NBTTagCompound serverData) {
        if (object instanceof IAEPowerStorage storage) {
            if (storage.getAEMaxPower() > 0) {
                serverData.setDouble(TAG_CURRENT_POWER, storage.getAECurrentPower());
                serverData.setDouble(TAG_MAX_POWER, storage.getAEMaxPower());
            }
        }
    }
}
