package ae2.block.networking;

import ae2.block.AEBaseBlockItem;
import ae2.core.localization.InGameTooltip;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DenseBeamFormerBlockItem extends AEBaseBlockItem {

    public DenseBeamFormerBlockItem(Block id) {
        super(id);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(ItemStack itemStack, @Nullable World worldIn, List<String> toolTip,
                                      ITooltipFlag advancedTooltips) {
        toolTip.add(TextFormatting.GRAY + InGameTooltip.BeamFormerRange.getLocal());
        toolTip.add(TextFormatting.GRAY + InGameTooltip.BeamFormerDenseChannels.getLocal());
        toolTip.add(TextFormatting.GRAY + InGameTooltip.BeamFormerWrenchToggle.getLocal());
    }
}
