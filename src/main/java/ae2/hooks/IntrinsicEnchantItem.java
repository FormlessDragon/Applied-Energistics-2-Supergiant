package ae2.hooks;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;

/**
 * Allows items to have an "intrinsic" enchant level in places where a specific enchant is checked.
 */
public interface IntrinsicEnchantItem {

    int getIntrinsicEnchantLevel(ItemStack stack, Enchantment enchantment);

}
