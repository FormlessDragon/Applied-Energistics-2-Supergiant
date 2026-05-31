package ae2.block.misc;

import ae2.core.localization.GuiText;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;

import java.util.List;

public class NotSoMysteriousCubeBlock extends AEDecorativeBlock {
    public NotSoMysteriousCubeBlock() {
        super(Material.IRON, 10.0F, 1000.0F);
        this.setOpaque();
        this.setFullSize();
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add(GuiText.NotSoMysteriousQuote.getLocal());
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }
}
