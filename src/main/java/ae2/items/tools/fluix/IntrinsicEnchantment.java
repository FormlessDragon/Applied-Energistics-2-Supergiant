package ae2.items.tools.fluix;

import ae2.core.localization.GuiText;
import net.minecraft.enchantment.Enchantment;

import java.util.List;

final class IntrinsicEnchantment {
    private final Enchantment enchantment;
    private final int level;

    IntrinsicEnchantment(Enchantment enchantment) {
        this.enchantment = enchantment;
        this.level = 1;
    }

    void appendHoverText(List<String> tooltipComponents) {
        tooltipComponents.add(GuiText.IntrinsicEnchant.getLocal(this.enchantment.getTranslatedName(level)));
    }

    int getLevel(Enchantment other) {
        return other == this.enchantment ? level : 0;
    }
}
