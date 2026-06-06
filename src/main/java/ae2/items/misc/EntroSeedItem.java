package ae2.items.misc;

import ae2.core.definitions.AEBlocks;
import ae2.core.localization.ItemTooltip;
import ae2.items.AEBaseItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class EntroSeedItem extends AEBaseItem {
    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add(TextFormatting.GRAY + ItemTooltip.EntroSeed.getLocal());
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != AEBlocks.FLUIX_BLOCK.block()) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            world.setBlockState(pos, AEBlocks.ENTRO_BUDDING_FULLY.block().getDefaultState(), 3);
            ItemStack seed = player.getHeldItem(hand);
            if (!player.capabilities.isCreativeMode) {
                seed.shrink(1);
            }
        }

        return EnumActionResult.SUCCESS;
    }
}
