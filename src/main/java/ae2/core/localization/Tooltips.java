package ae2.core.localization;

import ae2.api.behaviors.EmptyingAction;
import ae2.api.config.PowerUnit;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.text.TextComponentItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.text.DecimalFormat;
import java.text.MessageFormat;
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
    DoesntDespawn,
    MatterBalls,
    Singularity,
    QuantumKey;

    private static final char DECIMAL_SEPARATOR;
    private static final String[] DECIMAL_UNITS = new String[]{"k", "M", "G", "T", "P", "E"};
    private static final long[] DECIMAL_NUMS = new long[]{1_000L, 1_000_000L, 1_000_000_000L,
        1_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000_000L};

    static {
        var format = (DecimalFormat) DecimalFormat.getInstance();
        DECIMAL_SEPARATOR = format.getDecimalFormatSymbols().getDecimalSeparator();
    }

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

    public static String energyStorageTooltip(double energy, double max) {
        return TextFormatting.GRAY + GuiText.StoredEnergy.getLocal()
            + TextFormatting.GRAY + ": "
            + TextFormatting.LIGHT_PURPLE + formatNumber(energy, max)
            + TextFormatting.GOLD + " " + PowerUnit.AE.getLocal()
            + TextFormatting.GRAY + " ("
            + percentColor(energy, max) + formatPercent(energy, max)
            + TextFormatting.GRAY + ")";
    }

    public static String colored(LocalizationEnum text, TextFormatting color) {
        return color + text.getLocal();
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

    public static String getSetAmountTooltipLocal() {
        return TextFormatting.DARK_GRAY + ButtonToolTips.ModifyAmountAction.getLocal(ButtonToolTips.RightClick.getLocal());
    }

    public static String getRenameTooltipLocal() {
        return TextFormatting.DARK_GRAY + ButtonToolTips.RenameAction.getLocal("Alt + " + ButtonToolTips.MiddleClick.getLocal());
    }

    public static ITextComponent getAmountTooltip(LocalizationEnum baseText, AEKey what, long amount) {
        return muted(baseText.text(what.formatAmount(amount, AmountFormat.FULL)));
    }

    public static String getAmountTooltipLocal(LocalizationEnum baseText, GenericStack stack) {
        return getAmountTooltipLocal(baseText, stack.what(), stack.amount());
    }

    public static String getAmountTooltipLocal(LocalizationEnum baseText, AEKey what, long amount) {
        return TextFormatting.DARK_GRAY + baseText.getLocal(what.formatAmount(amount, AmountFormat.FULL));
    }

    private static String formatNumber(double number, double max) {
        MaxedAmount amount = getMaxedAmount(number, max);
        boolean numberUnit = !amount.digit().equals("0");
        return amount.digit() + (numberUnit ? amount.unit() : "") + TextFormatting.GRAY + "/"
            + TextFormatting.LIGHT_PURPLE + amount.maxDigit() + amount.unit();
    }

    private static MaxedAmount getMaxedAmount(double amount, double max) {
        if (max < 10_000) {
            return new MaxedAmount(formatAmount(amount, 1), formatAmount(max, 1), "");
        }

        int unitIndex = 0;
        while (unitIndex + 1 < DECIMAL_NUMS.length && max / DECIMAL_NUMS[unitIndex] >= 1_000) {
            unitIndex++;
        }
        long divisor = DECIMAL_NUMS[unitIndex];
        return new MaxedAmount(formatAmount(amount, divisor), formatAmount(max, divisor), DECIMAL_UNITS[unitIndex]);
    }

    private static String formatAmount(double amount, long divisor) {
        double fraction = amount / divisor;
        String result;
        if (fraction < 10) {
            result = String.format("%.3f", fraction);
        } else if (fraction < 100) {
            result = String.format("%.2f", fraction);
        } else {
            result = String.format("%.1f", fraction);
        }
        while (result.endsWith("0")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.endsWith(String.valueOf(DECIMAL_SEPARATOR))) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String formatPercent(double energy, double max) {
        double ratio = max <= 0 ? 0 : energy / max;
        return MessageFormat.format("{0,number,#.##%}", ratio);
    }

    private static TextFormatting percentColor(double energy, double max) {
        if (max <= 0) {
            return TextFormatting.RED;
        }
        double ratio = Math.clamp(energy / max, 0, 1);
        if (ratio >= 0.67) {
            return TextFormatting.GREEN;
        }
        if (ratio >= 0.33) {
            return TextFormatting.YELLOW;
        }
        return TextFormatting.RED;
    }

    private static ITextComponent normalTooltipText(ITextComponent text) {
        return text.createCopy().setStyle(normalStyle());
    }

    public static ITextComponent muted(ITextComponent text) {
        return text.createCopy().setStyle(new Style().setColor(TextFormatting.DARK_GRAY));
    }

    private static Style normalStyle() {
        return new Style().setColor(TextFormatting.GRAY).setItalic(false);
    }

    private static Style numberStyle() {
        return new Style().setColor(TextFormatting.LIGHT_PURPLE).setItalic(false);
    }

    private record MaxedAmount(String digit, String maxDigit, String unit) {
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
