package ae2.client.render;

import ae2.api.util.AEColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.Nullable;

public class StaticBlockColor implements IBlockColor {
    private final AEColor color;

    public StaticBlockColor(AEColor color) {
        this.color = color;
    }

    @Override
    public int colorMultiplier(IBlockState state, @Nullable IBlockAccess world, @Nullable BlockPos pos, int tintIndex) {
        return this.color.getVariantByTintIndex(tintIndex);
    }
}
