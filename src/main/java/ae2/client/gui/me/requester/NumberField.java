package ae2.client.gui.me.requester;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEKey;
import ae2.client.gui.MathExpressionParser;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ConfirmableTextField;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

public class NumberField extends ConfirmableTextField {

    private static final int PADDING = 8;
    private static final int WIDTH = 50;
    private static final int HEIGHT = 12;

    private static final int TEXT_COLOR = 0xFF_FFFF;
    private static final int ERROR_COLOR = 0xFF_0000;

    private static final int MIN_VALUE = 0;

    private final String name;
    private final DecimalFormat decimalFormat;

    private NumberEntryType type = NumberEntryType.UNITLESS;
    private boolean isFluid;

    NumberField(int x, int y, String name, GuiStyle style, Consumer<Long> onConfirm) {
        super(style, Minecraft.getMinecraft().fontRenderer, x, y, WIDTH, HEIGHT);
        this.name = name;

        this.decimalFormat = new DecimalFormat("#.######", new DecimalFormatSymbols());
        this.decimalFormat.setParseBigDecimal(true);
        this.decimalFormat.setNegativePrefix("-");

        this.setVisible(true);
        this.setMaxStringLength(7);
        this.setLongValue(0);
        this.setResponder(_ -> validate());
        this.setOnConfirm(() -> {
            if (getLongValue().isPresent()) {
                onConfirm.accept(getLongValue().getAsLong());
                setFocused(false);
            }
        });
        validate();
    }

    public void renderWidget(FontRenderer font) {
        super.drawTextBox();
        if (!isFluid) {
            return;
        }
        font.drawString("B", getX() + WIDTH - PADDING, getY(), 0x54_5454);
    }

    private void validate() {
        List<ITextComponent> validationErrors = new ObjectArrayList<>();
        List<ITextComponent> infoMessages = new ObjectArrayList<>();

        var possibleValue = getValueInternal();
        if (possibleValue.isPresent()) {
            if (possibleValue.get().scale() > 0) {
                validationErrors.add(GuiText.NumberNonInteger.text());
            } else {
                var value = convertToExternalValue(possibleValue.get());
                if (value < MIN_VALUE) {
                    var formatted = decimalFormat.format(convertToInternalValue(MIN_VALUE));
                    validationErrors.add(GuiText.NumberLessThanMinValue.text(formatted));
                } else if (!isNumber()) {
                    infoMessages.add(new TextComponentString("= " + decimalFormat.format(possibleValue.get())));
                }
            }
        } else {
            validationErrors.add(GuiText.InvalidNumber.text());
        }

        boolean valid = validationErrors.isEmpty();
        var tooltip = valid ? infoMessages : validationErrors;
        this.setTextColor(valid ? TEXT_COLOR : ERROR_COLOR);
        this.setTooltipMessage(tooltip);
    }

    private long convertToExternalValue(BigDecimal internalValue) {
        var multiplicand = BigDecimal.valueOf(type.amountPerUnit());
        var value = internalValue.multiply(multiplicand, MathContext.DECIMAL128);
        value = value.setScale(0, RoundingMode.UP);
        return value.longValue();
    }

    private BigDecimal convertToInternalValue(long externalValue) {
        var divisor = BigDecimal.valueOf(type.amountPerUnit());
        return BigDecimal.valueOf(externalValue).divide(divisor, MathContext.DECIMAL128);
    }

    OptionalLong getLongValue() {
        var internalValue = getValueInternal();
        if (internalValue.isEmpty()) {
            return OptionalLong.empty();
        }

        var externalValue = convertToExternalValue(internalValue.get());
        if (externalValue < MIN_VALUE) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(externalValue);
    }

    void setLongValue(long value) {
        var internalValue = convertToInternalValue(Math.max(value, MIN_VALUE));
        setText(decimalFormat.format(internalValue));
        setCursorPositionEnd();
        validate();
    }

    private boolean isNumber() {
        var position = new ParsePosition(0);
        var textValue = getText().trim();
        decimalFormat.parse(textValue, position);
        return position.getErrorIndex() == -1 && position.getIndex() == textValue.length();
    }

    private Optional<BigDecimal> getValueInternal() {
        return MathExpressionParser.parse(getText(), decimalFormat);
    }

    @Override
    public void setTooltipMessage(List<ITextComponent> tooltipMessage) {
        tooltipMessage.addFirst(new TextComponentTranslation("gui.ae2.requester." + name));
        super.setTooltipMessage(tooltipMessage);
        if (!isFocused() || (tooltipMessage.size() > 1 && !tooltipMessage.getFirst().getFormattedText().startsWith("="))) {
            return;
        }
        tooltipMessage.add(
            new TextComponentString("» ")
                .setStyle(new Style().setColor(TextFormatting.AQUA))
                .appendSibling(
                    new TextComponentTranslation("gui.ae2.requester.submit")
                        .setStyle(new Style().setColor(TextFormatting.GRAY))
                )
        );
    }

    @Override
    public void setFocused(boolean isFocusedIn) {
        if (isFocusedIn && !this.isEnabled()) {
            return;
        }
        super.setFocused(isFocusedIn);
    }

    void adjustToType(@Nullable AEKey key) {
        this.isFluid = key instanceof AEFluidKey;
        this.type = NumberEntryType.of(key);
        if (isFluid) {
            this.setMaxStringLength(5);
            resize(WIDTH - PADDING, HEIGHT);
        } else {
            resize(WIDTH, HEIGHT);
        }
    }
}
