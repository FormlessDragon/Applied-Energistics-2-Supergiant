package ae2.decorative.stair;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;

public class AEDecorativeStairBlock extends BlockStairs {

    public AEDecorativeStairBlock(IBlockState modelState, float hardness, float resistance) {
        super(modelState);
        this.setHardness(hardness);
        this.setResistance(resistance);
        this.useNeighborBrightness = true;
    }
}