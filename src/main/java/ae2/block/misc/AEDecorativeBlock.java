package ae2.block.misc;

import ae2.block.AEBaseBlock;
import net.minecraft.block.material.Material;

public class AEDecorativeBlock extends AEBaseBlock {
    public AEDecorativeBlock(Material material, float hardness, float resistance) {
        super(material);
        this.setHardness(hardness);
        this.setResistance(resistance);
    }
}

