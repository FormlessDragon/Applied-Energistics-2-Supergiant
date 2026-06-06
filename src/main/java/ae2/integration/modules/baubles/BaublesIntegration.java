package ae2.integration.modules.baubles;

import baubles.api.BaublesApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

public final class BaublesIntegration {
    private static final String MOD_ID = "baubles";

    private BaublesIntegration() {
    }

    public static boolean isEnabled() {
        return Loader.isModLoaded(MOD_ID);
    }

    public static int getSlots(EntityPlayer player) {
        if (!isEnabled()) {
            return 0;
        }
        return BaublesApi.getBaublesHandler(player).getSlots();
    }

    public static ItemStack getStackInSlot(EntityPlayer player, int slot) {
        if (!isEnabled()) {
            return ItemStack.EMPTY;
        }
        var handler = BaublesApi.getBaublesHandler(player);
        if (slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return handler.getStackInSlot(slot);
    }
}
