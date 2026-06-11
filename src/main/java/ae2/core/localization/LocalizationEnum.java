package ae2.core.localization;

import ae2.util.EmptyArrays;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface LocalizationEnum {

    String getTranslationKey();

    @SideOnly(Side.CLIENT)
    default String getLocal() {
        return I18n.format(getTranslationKey(), EmptyArrays.EMPTY_OBJECT_ARRAY);
    }

    @SideOnly(Side.CLIENT)
    default String getLocal(Object... args) {
        return I18n.format(getTranslationKey(), args);
    }

    default ITextComponent text() {
        return new TextComponentTranslation(this.getTranslationKey(), EmptyArrays.EMPTY_OBJECT_ARRAY);
    }

    default ITextComponent text(Object... args) {
        return new TextComponentTranslation(this.getTranslationKey(), args);
    }
}
