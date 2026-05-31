package ae2.core.localization;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface LocalizationEnum {

    Object[] emptyArgs = new Object[0];

    String getTranslationKey();

    @SideOnly(Side.CLIENT)
    default String getLocal() {
        return I18n.format(getTranslationKey(), emptyArgs);
    }

    @SideOnly(Side.CLIENT)
    default String getLocal(Object... args) {
        return I18n.format(getTranslationKey(), args);
    }

    default ITextComponent text() {
        return new TextComponentTranslation(this.getTranslationKey(), emptyArgs);
    }

    default ITextComponent text(Object... args) {
        return new TextComponentTranslation(this.getTranslationKey(), args);
    }
}
