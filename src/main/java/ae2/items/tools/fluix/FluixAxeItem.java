package ae2.items.tools.fluix;

import ae2.hooks.IntrinsicEnchantItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

public class FluixAxeItem extends ItemAxe implements IntrinsicEnchantItem {
    private final IntrinsicEnchantment intrinsicEnchantment = new IntrinsicEnchantment(Enchantments.FORTUNE);

    public FluixAxeItem() {
        super(FluixToolType.FLUIX, 6.0F, -3.1F);
        this.setMaxStackSize(1);
    }

    @Override
    public int getIntrinsicEnchantLevel(ItemStack stack, Enchantment enchantment) {
        return intrinsicEnchantment.getLevel(enchantment);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addInformation(stack, world, lines, advancedTooltips);
        intrinsicEnchantment.appendHoverText(lines);
    }
}
