package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.util.text.ITextComponent;

/**
 * Shared previous/next page toolbar button.
 */
public class PageNavigationButton extends SimpleIconButton {
    public PageNavigationButton(Icon icon, ITextComponent previousPageText, ITextComponent nextPageText,
                                Runnable onPress) {
        super(icon, icon == Icon.ARROW_LEFT ? previousPageText : nextPageText, onPress);
    }
}
