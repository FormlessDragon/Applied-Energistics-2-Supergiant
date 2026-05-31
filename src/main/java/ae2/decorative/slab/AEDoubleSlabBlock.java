package ae2.decorative.slab;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

public class AEDoubleSlabBlock extends AEDecorativeSlabBlock {

    public AEDoubleSlabBlock(Material material, MapColor mapColor, Block fullBlock, float hardness, float resistance) {
        super(material, mapColor, fullBlock, hardness, resistance);
    }

    @Override
    public boolean isDouble() {
        return true;
    }
}