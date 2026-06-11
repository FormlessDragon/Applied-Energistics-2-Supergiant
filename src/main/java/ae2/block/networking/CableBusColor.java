package ae2.block.networking;

import ae2.api.util.AEColor;
import ae2.tile.networking.TileCableBus;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.Nullable;

public class CableBusColor implements IBlockColor {
    @Override
    public int colorMultiplier(IBlockState state, @Nullable IBlockAccess world, @Nullable BlockPos pos, int tintIndex) {
        AEColor color = AEColor.TRANSPARENT;
        if (world != null && pos != null) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileCableBus t) {
                color = t.getColor();
            }
        }
        return color.getVariantByTintIndex(tintIndex);
    }
}
