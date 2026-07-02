package ae2.items.parts;

import ae2.api.parts.IPartItem;
import ae2.core.localization.P2PText;
import ae2.parts.p2p.P2PTunnelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import java.util.function.Function;

public class P2PPartItem<T extends P2PTunnelPart<?>> extends PartItem<T> {
    public P2PPartItem(Class<T> partClass, Function<IPartItem<T>, T> factory) {
        super(partClass, factory);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocalFormatted(P2PText.NamePrefix.getTranslationKey(), I18n.translateToLocal(getP2PTypeTranslationKey(stack)));
    }

    public String getP2PTypeTranslationKey(ItemStack stack) {
        return stack.getTranslationKey() + ".name";
    }
}
