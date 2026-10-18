package ae2.items.tools.fluix;

import ae2.hooks.IntrinsicEnchantItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class FluixSwordItem extends ItemSword implements IntrinsicEnchantItem {
    private final IntrinsicEnchantment intrinsicEnchantment = new IntrinsicEnchantment(Enchantments.LOOTING);

    public FluixSwordItem() {
        super(Objects.requireNonNull(FluixToolType.FLUIX));
        this.setMaxStackSize(1);
    }

    @Override
    public Integer getIntrinsicEnchantLevel(ItemStack stack, Enchantment enchantment) {
        return intrinsicEnchantment.getLevel(enchantment);
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, World world, @NotNull List<String> lines, @NotNull ITooltipFlag advancedTooltips) {
        super.addInformation(stack, world, lines, advancedTooltips);
        intrinsicEnchantment.appendHoverText(lines);
    }
}
