package ae2.client.gui.me.requester;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.client.gui.MathExpressionParser;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ConfirmableTextField;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

public class NumberField extends ConfirmableTextField {

    private static final int WIDTH = 50;
    private static final int HEIGHT = 12;
    private static final int PREVIEW_WIDTH = 96;
    private static final int TEXT_FIELD_PADDING = 2;
    private static final int MAX_TEXT_LENGTH = 64;
    private static final String PREVIEW_ERROR = "error";

    private static final int TEXT_COLOR = 0xFF_FFFF;
    private static final int ERROR_COLOR = 0xFF_0000;

    private final GuiText label;
    private final DecimalFormat decimalFormat;
    private final AETextField previewField;
    private final long minValue;

    private NumberEntryType type = NumberEntryType.UNITLESS;
    @Nullable
    private String unitSymbol;
    private String previewCacheText = "";
    private NumberEntryType previewCacheType = this.type;
    private String previewCacheValue = PREVIEW_ERROR;
    private long lastSetValue;
    @Nullable
    private NumberEntryType lastSetType;
    @Nullable
    private String lastSetText;

    NumberField(int x, int y, GuiText label, GuiStyle style, long minValue, LongConsumer onConfirm) {
        super(style, Minecraft.getMinecraft().fontRenderer, x, y, WIDTH, HEIGHT);
        this.label = label;
        this.minValue = minValue;

        this.decimalFormat = new DecimalFormat("#.######", new DecimalFormatSymbols());
        this.decimalFormat.setParseBigDecimal(true);
        this.decimalFormat.setNegativePrefix("-");

        this.previewField = new AETextField(style, Minecraft.getMinecraft().fontRenderer, 0, 0, WIDTH, HEIGHT);
        this.previewField.setEnableBackgroundDrawing(false);
        this.previewField.setMaxStringLength(MAX_TEXT_LENGTH);
        this.previewField.setEnabled(false);
        this.previewField.setVisible(false);

        this.setVisible(true);
        this.setMaxStringLength(MAX_TEXT_LENGTH);
        this.setResponder(_ -> validate());
        this.setLongValue(0);
        this.setOnConfirm(() -> {
            var value = getLongValue();
            if (value.isPresent()) {
                onConfirm.accept(value.getAsLong());
                setFocused(false);
            }
        });
    }

    public void renderWidget(FontRenderer font) {
        super.drawTextBox();
        if (unitSymbol == null) {
            return;
        }
        font.drawString(unitSymbol, getX() + WIDTH - font.getStringWidth(unitSymbol), getY(), 0x54_5454);
    }

    void renderPreview(int guiLeft, int guiTop) {
        if (!isFocused()) {
            this.previewField.setVisible(false);
            return;
        }

        this.previewField.setVisible(true);
        this.previewField.move(getX() - guiLeft - TEXT_FIELD_PADDING, getY() - guiTop - TEXT_FIELD_PADDING + HEIGHT);
        this.previewField.resize(PREVIEW_WIDTH, HEIGHT);
        this.previewField.setText(getCachedPreviewValue());
        this.previewField.setCursorPositionEnd();
        this.previewField.setSelectionPos(this.previewField.getCursorPosition());
        this.previewField.drawTextBox();
    }

    private void validate() {
        List<ITextComponent> validationErrors = new ObjectArrayList<>(1);
        List<ITextComponent> infoMessages = new ObjectArrayList<>(1);

        var possibleValue = getValueInternal();
        if (possibleValue.isPresent()) {
            var internalValue = possibleValue.get();
            if (type.amountPerUnit() == 1 && internalValue.scale() > 0) {
                validationErrors.add(GuiText.NumberNonInteger.text());
            } else if (internalValue.compareTo(convertToInternalValue(minValue)) < 0) {
                var formatted = decimalFormat.format(convertToInternalValue(minValue));
                validationErrors.add(GuiText.NumberLessThanMinValue.text(formatted));
            } else if (internalValue.compareTo(convertToInternalValue(Long.MAX_VALUE)) > 0) {
                var formatted = decimalFormat.format(convertToInternalValue(Long.MAX_VALUE));
                validationErrors.add(GuiText.NumberGreaterThanMaxValue.text(formatted));
            } else {
                var externalValue = convertToExternalValue(internalValue);
                if (externalValue.isEmpty()) {
                    validationErrors.add(GuiText.InvalidNumber.text());
                } else if (!isNumber()) {
                    infoMessages.add(new TextComponentString("= " + decimalFormat.format(internalValue)));
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

    private OptionalLong convertToExternalValue(BigDecimal internalValue) {
        var multiplicand = BigDecimal.valueOf(type.amountPerUnit());
        try {
            var value = internalValue.multiply(multiplicand, MathContext.DECIMAL128);
            return OptionalLong.of(value.setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
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
        if (externalValue.isEmpty()) {
            return OptionalLong.empty();
        }

        long value = externalValue.getAsLong();
        if (value < minValue) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value);
    }

    void setLongValue(long value) {
        value = Math.max(value, this.minValue);
        if (value == this.lastSetValue && this.type.equals(this.lastSetType)
            && getText().equals(this.lastSetText)) {
            return;
        }

        var internalValue = convertToInternalValue(value);
        String text = this.decimalFormat.format(internalValue);
        boolean changed = !text.equals(getText());
        setText(text);
        setCursorPositionEnd();
        if (!changed) {
            validate();
        }
        this.lastSetValue = value;
        this.lastSetType = this.type;
        this.lastSetText = getText();
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

    private String getCachedPreviewValue() {
        String text = getText();
        if (!text.equals(this.previewCacheText) || !this.type.equals(this.previewCacheType)) {
            this.previewCacheText = text;
            this.previewCacheType = this.type;
            var v = getLongValue();
            this.previewCacheValue = v.isPresent() ? Long.toString(v.getAsLong()) : PREVIEW_ERROR;
        }
        return this.previewCacheValue;
    }

    @Override
    public void setTooltipMessage(List<ITextComponent> tooltipMessage) {
        tooltipMessage.addFirst(this.label.text());
        super.setTooltipMessage(tooltipMessage);
        if (!isFocused() || (tooltipMessage.size() > 1 && !tooltipMessage.getFirst().getFormattedText().startsWith("="))) {
            return;
        }
        tooltipMessage.add(
            new TextComponentString("» ")
                .setStyle(new Style().setColor(TextFormatting.AQUA))
                .appendSibling(
                    GuiText.RequesterSubmit.text()
                        .setStyle(new Style().setColor(TextFormatting.GRAY))
                )
        );
    }

    @Override
    public void setFocused(boolean isFocusedIn) {
        if (isFocusedIn && !this.isEnabled()) {
            return;
        }
        boolean wasFocused = isFocused();
        super.setFocused(isFocusedIn);
        if (wasFocused != isFocused() && this.decimalFormat != null) {
            validate();
        }
    }

    void adjustToType(@Nullable AEKey key) {
        AEKeyType keyType = key == null ? null : key.getType();
        int amountPerUnit = keyType == null ? 1 : keyType.getAmountPerUnit();
        String unit = keyType == null ? null : keyType.getUnitSymbol();
        if (this.type.amountPerUnit() != amountPerUnit || !Objects.equals(this.unitSymbol, unit)) {
            this.type = new NumberEntryType(amountPerUnit, unit);
            this.unitSymbol = unit;
        }
        if (unitSymbol != null) {
            int unitWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(unitSymbol);
            resize(WIDTH - unitWidth, HEIGHT);
        } else {
            resize(WIDTH, HEIGHT);
        }
    }
}
