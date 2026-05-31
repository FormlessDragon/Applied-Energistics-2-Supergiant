package ae2.hooks;

import ae2.api.implementations.blockentities.IColorableBlockEntity;
import ae2.api.implementations.tiles.IColorableTile;
import ae2.core.definitions.AEItems;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ColorApplicatorSelectColorPacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;

public final class ColorApplicatorPickColorHook {
    private ColorApplicatorPickColorHook() {
    }

    public static boolean onPickColor(EntityPlayer player, RayTraceResult hitResult) {
        if (!AEItems.COLOR_APPLICATOR.is(player.getHeldItemOffhand())
            && !AEItems.COLOR_APPLICATOR.is(player.getHeldItemMainhand())) {
            return false;
        }

        TileEntity tile = player.world.getTileEntity(hitResult.getBlockPos());
        if (tile instanceof IColorableBlockEntity colorableBlockEntity) {
            InitNetwork.sendToServer(new ColorApplicatorSelectColorPacket(colorableBlockEntity.getColor()));
            return true;
        }

        if (tile instanceof IColorableTile colorableTile) {
            InitNetwork.sendToServer(new ColorApplicatorSelectColorPacket(colorableTile.getColor()));
            return true;
        }

        return false;
    }
}
