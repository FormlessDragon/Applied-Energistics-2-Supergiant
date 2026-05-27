package appeng.core.localization;

import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.text.TextComponentItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public enum Tooltips implements LocalizationEnum {

    Empty,
    On,
    Off,
    Stored,
    Capacity,
    SubstitutionsOn,
    SubstitutionsDescEnabled,
    SubstitutionsOff,
    SubstitutionsDescDisabled,
    FluidSubstitutions,
    FluidSubstitutionsDescEnabled,
    FluidSubstitutionsDescDisabled,
    MatterBalls,
    Singularity,
    QuantumKey;

    public static final TextFormatting RED = TextFormatting.RED;
    public static final TextFormatting GREEN = TextFormatting.GREEN;
    private final String translationKey;

    Tooltips() {
        this.translationKey = "gui.tooltips.ae2." + name();
    }

    public static List<ITextComponent> getEmptyingTooltip(LocalizationEnum baseAction, ItemStack carried,
                                                          EmptyingAction emptyingAction) {
        return List.of(
            muted(baseAction.text(
                getMouseButtonText(0),
                normalTooltipText(TextComponentItemStack.of(carried)))),
            muted(baseAction.text(
                getMouseButtonText(1),
                normalTooltipText(emptyingAction.description()))));
    }

    public static ITextComponent getMouseButtonText(int button) {
        return switch (button) {
            case 0 -> ButtonToolTips.LeftClick.text();
            case 1 -> ButtonToolTips.RightClick.text();
            case 2 -> ButtonToolTips.MiddleClick.text();
            default -> ButtonToolTips.MouseButton.text(button);
        };
    }

    public static ITextComponent of(ITextComponent text) {
        return text.createCopy().setStyle(normalStyle());
    }

    public static ITextComponent of(LocalizationEnum text) {
        return of(text.text());
    }

    public static ITextComponent of(LocalizationEnum text, TextFormatting color) {
        return text.text().createCopy().setStyle(new Style().setColor(color).setItalic(false));
    }

    public static ITextComponent of(String text) {
        return new TextComponentString(text).setStyle(normalStyle());
    }

    public static ITextComponent ofUnformattedNumber(long number) {
        return new TextComponentString(Long.toString(number)).setStyle(numberStyle());
    }

    public static ITextComponent bytesUsed(long bytes, long max) {
        var result = of(GuiText.BytesUsed.text(ofUnformattedNumber(bytes)));
        result.appendText(" / ");
        result.appendSibling(ofUnformattedNumber(max));
        return result;
    }

    public static ITextComponent typesUsed(long types, long max) {
        var result = ofUnformattedNumber(types);
        result.appendText(" ");
        result.appendSibling(of(GuiText.Of));
        result.appendText(" ");
        result.appendSibling(ofUnformattedNumber(max));
        result.appendText(" ");
        result.appendSibling(of(GuiText.Types));
        return result;
    }

    public static boolean shouldShowAmountTooltip(AEKey what, long amount) {
        ItemStack readOnlyStack = what instanceof AEItemKey itemKey ? itemKey.getReadOnlyStack() : null;
        return amount > 9999L * what.getAmountPerUnit()
            || what.getUnitSymbol() != null
            || readOnlyStack != null && readOnlyStack.isItemDamaged();
    }

    public static ITextComponent getAmountTooltip(LocalizationEnum baseText, GenericStack stack) {
        return getAmountTooltip(baseText, stack.what(), stack.amount());
    }

    public static ITextComponent getSetAmountTooltip() {
        return muted(ButtonToolTips.ModifyAmountAction.text(getMouseButtonText(2)));
    }

    public static ITextComponent getAmountTooltip(LocalizationEnum baseText, AEKey what, long amount) {
        return muted(baseText.text(what.formatAmount(amount, AmountFormat.FULL)));
    }

    private static ITextComponent normalTooltipText(ITextComponent text) {
        return text.createCopy().setStyle(normalStyle());
    }

    private static ITextComponent muted(ITextComponent text) {
        return text.createCopy().setStyle(new Style().setColor(TextFormatting.DARK_GRAY));
    }

    private static Style normalStyle() {
        return new Style().setColor(TextFormatting.GRAY).setItalic(false);
    }

    private static Style numberStyle() {
        return new Style().setColor(TextFormatting.LIGHT_PURPLE).setItalic(false);
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
