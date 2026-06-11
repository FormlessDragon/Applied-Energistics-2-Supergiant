package ae2.client.render;

import ae2.api.implementations.blockentities.IColorableBlockEntity;
import ae2.api.implementations.tiles.IColorableTile;
import ae2.api.util.AEColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.Nullable;

public class ColorableBlockEntityBlockColor implements IBlockColor {
    public static final ColorableBlockEntityBlockColor INSTANCE = new ColorableBlockEntityBlockColor();

    @Override
    public int colorMultiplier(IBlockState state, @Nullable IBlockAccess world, @Nullable BlockPos pos, int tintIndex) {
        AEColor color = AEColor.TRANSPARENT;
        if (world != null && pos != null) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof IColorableBlockEntity) {
                color = ((IColorableBlockEntity) tile).getColor();
            } else if (tile instanceof IColorableTile) {
                color = ((IColorableTile) tile).getColor();
            }
        }
        return color.getVariantByTintIndex(tintIndex);
    }
}
