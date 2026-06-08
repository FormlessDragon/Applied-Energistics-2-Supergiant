/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.widgets;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.MathExpressionParser;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.Rects;
import ae2.client.gui.Tooltip;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.style.WidgetStyle;
import ae2.core.localization.GuiText;
import com.google.common.primitives.Longs;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.awt.Rectangle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * A utility widget that consists of a text-field to enter a number with attached buttons to increment/decrement the
 * number in fixed intervals.
 */
public class NumberEntryWidget implements ICompositeWidget {

    private static final ITextComponent PLUS = new TextComponentString("+");
    private static final ITextComponent MINUS = new TextComponentString("-");
    private static final ITextComponent MULTIPLY = new TextComponentString("*");
    private static final ITextComponent DIVIDE = new TextComponentString("/");
    private static final ITextComponent CEIL = new TextComponentString("C");
    private static final ITextComponent FLOOR = new TextComponentString("F");
    private static final String PREVIEW_ERROR = "error";
    private static final int UNIT_PADDING = 3;
    private static final int UNIT_COLOR = 0x555555;
    private final int errorTextColor;
    private final int normalTextColor;

    private final ConfirmableTextField textField;
    private final AETextField previewField;
    private final DecimalFormat decimalFormat;
    private NumberEntryType type;
    private BigDecimal amountPerUnit;
    private BigDecimal minInternalValue;
    private BigDecimal maxInternalValue;
    private BigDecimal minButtonValue;
    private BigDecimal maxButtonValue;
    private List<GuiButton> buttons = ObjectLists.emptyList();
    private long minValue;
    private long maxValue = Long.MAX_VALUE;
    private ValidationIcon validationIcon;

    private Runnable onChange;
    private Runnable onConfirm;

    private boolean hideValidationIcon;

    private Rectangle bounds = new Rectangle(0, 0, 0, 0);

    private Rectangle textFieldBounds = Rects.ZERO;
    private Rectangle previewFieldBounds = Rects.ZERO;
    private Point currentScreenOrigin = Point.ZERO;

    private List<AE2Button> amountButtons = ObjectLists.emptyList();
    private AE2Button ceilButton;
    private AE2Button floorButton;
    private Rectangle ceilButtonBounds = Rects.ZERO;
    private Rectangle floorButtonBounds = Rects.ZERO;
    private ButtonMode lastButtonMode;
    private long[] lastButtonValues = new long[0];
    private boolean previewFieldConfigured;
    private boolean validationCacheDirty = true;
    private String validationCacheText = "";
    private NumberEntryType validationCacheType;
    private long validationCacheMinValue;
    private long validationCacheMaxValue;
    private CachedValidation validationCache;

    public NumberEntryWidget(GuiStyle style, NumberEntryType type) {
        this.errorTextColor = style.getColor(PaletteColor.TEXTFIELD_ERROR).toARGB();
        this.normalTextColor = style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB();

        this.type = Objects.requireNonNull(type, "type");
        refreshUnitCache();
        this.decimalFormat = new DecimalFormat("#.######", new DecimalFormatSymbols());
        this.decimalFormat.setParseBigDecimal(true);
        this.decimalFormat.setNegativePrefix("-");

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        this.textField = new ConfirmableTextField(style, font, 0, 0, 0, font.FONT_HEIGHT);
        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setMaxStringLength(16);
        this.textField.setTextColor(normalTextColor);
        this.textField.setVisible(true);
        this.textField.setResponder(ignored -> {
            validate();
            if (onChange != null) {
                this.onChange.run();
            }
        });
        this.textField.setOnConfirm(() -> {
            if (this.onConfirm != null && getCachedValidation().value().externalValue().isPresent()) {
                this.onConfirm.run();
            }
        });

        this.previewField = new AETextField(style, font, 0, 0, 0, font.FONT_HEIGHT);
        this.previewField.setEnableBackgroundDrawing(false);
        this.previewField.setMaxStringLength(64);
        this.previewField.setTextColor(normalTextColor);
        this.previewField.setEnabled(false);
        this.previewField.setVisible(false);

        validate();

    }

    private static ButtonMode getButtonMode() {
        if (GuiScreen.isCtrlKeyDown()) {
            return ButtonMode.MULTIPLY;
        }
        if (GuiScreen.isAltKeyDown()) {
            return ButtonMode.ALT_MULTIPLY;
        }
        return GuiScreen.isShiftKeyDown() ? ButtonMode.SHIFT_ADD : ButtonMode.ADD;
    }

    static OptionalLong toExternalValue(BigDecimal internalValue, int amountPerUnit) {
        if (amountPerUnit == 1 && internalValue.scale() > 0) {
            return OptionalLong.empty();
        }

        return toExternalValue(internalValue, BigDecimal.valueOf(amountPerUnit));
    }

    private static OptionalLong toExternalValue(BigDecimal internalValue, BigDecimal amountPerUnit) {
        try {
            var value = internalValue.multiply(amountPerUnit);
            return OptionalLong.of(value.setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    public void setOnConfirm(Runnable callback) {
        this.onConfirm = callback;
    }

    public void setOnChange(Runnable callback) {
        this.onChange = callback;
    }

    public void setActive(boolean active) {
        this.textField.setEnabled(active);
        this.buttons.forEach(b -> b.enabled = active);
    }

    /**
     * Sets the bounds of the text field on the screen. This may seem insane, but the text-field background is actually
     * baked into the screens background image, which necessitates setting it precisely.
     */
    public void setTextFieldBounds(Rectangle bounds) {
        this.textFieldBounds = bounds;

        this.textField.move(currentScreenOrigin.move(bounds.x, bounds.y));
        int unitWidth = 0;
        if (this.type.unit() != null) {
            unitWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(this.type.unit()) + UNIT_PADDING;
        }
        this.textField.resize(bounds.width - unitWidth, bounds.height);
    }

    public void setTextFieldStyle(WidgetStyle style) {
        int left = 0;
        if (style.getLeft() != null) {
            left = style.getLeft();
        }
        int top = 0;
        if (style.getTop() != null) {
            top = style.getTop();
        }
        setTextFieldBounds(new Rectangle(
            left,
            top,
            style.getWidth(),
            style.getHeight()));
    }

    public void setPreviewFieldStyle(WidgetStyle style) {
        int left = 0;
        if (style.getLeft() != null) {
            left = style.getLeft();
        }
        int top = 0;
        if (style.getTop() != null) {
            top = style.getTop();
        }
        setPreviewFieldBounds(new Rectangle(
            left,
            top,
            style.getWidth(),
            style.getHeight()));
    }

    private void setPreviewFieldBounds(Rectangle bounds) {
        this.previewFieldConfigured = true;
        this.previewFieldBounds = bounds;
        this.previewField.move(currentScreenOrigin.move(bounds.x, bounds.y));
        this.previewField.resize(bounds.width, bounds.height);
        updatePreviewField();
    }

    public void setMinValue(long minValue) {
        this.minValue = minValue;
        refreshBoundsCache();
        invalidateValidationCache();
        validate();
    }

    public void setMaxValue(long maxValue) {
        this.maxValue = maxValue;
        refreshBoundsCache();
        invalidateValidationCache();
        validate();
    }

    @Override
    public void setPosition(Point position) {
        bounds = new Rectangle(position.x(), position.y(), bounds.width, bounds.height);
    }

    @Override
    public void setSize(int width, int height) {

        bounds = new Rectangle(bounds.x, bounds.y, width, height);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        int left = bounds.x + this.bounds.x;

        int top = bounds.y + this.bounds.y;

        List<GuiButton> buttons = new ObjectArrayList<>(9);
        List<AE2Button> amountButtons = new ObjectArrayList<>(8);

        amountButtons.add(new AE2Button(left, top, 22, 20, makeButtonLabel(PLUS, getCurrentButtonValues()[0]),
            () -> applyButton(0, true)));
        amountButtons.add(new AE2Button(left + 25, top, 26, 20, makeButtonLabel(PLUS, getCurrentButtonValues()[1]),
            () -> applyButton(1, true)));
        amountButtons.add(new AE2Button(left + 54, top, 30, 20, makeButtonLabel(PLUS, getCurrentButtonValues()[2]),
            () -> applyButton(2, true)));
        amountButtons.add(new AE2Button(left + 86, top, 34, 20, makeButtonLabel(PLUS, getCurrentButtonValues()[3]),
            () -> applyButton(3, true)));

        buttons.addAll(amountButtons);
        amountButtons.forEach(addWidget);

        this.currentScreenOrigin = Point.fromTopLeft(bounds);
        setTextFieldBounds(this.textFieldBounds);
        this.textField.setFocused(true);
        if (this.previewFieldConfigured) {
            setPreviewFieldBounds(this.previewFieldBounds);
        }

        amountButtons.add(new AE2Button(left, top + 42, 22, 20, makeButtonLabel(MINUS, getCurrentButtonValues()[0]),
            () -> applyButton(0, false)));
        amountButtons.add(new AE2Button(left + 25, top + 42, 26, 20, makeButtonLabel(MINUS, getCurrentButtonValues()[1]),
            () -> applyButton(1, false)));
        amountButtons.add(new AE2Button(left + 54, top + 42, 30, 20, makeButtonLabel(MINUS, getCurrentButtonValues()[2]),
            () -> applyButton(2, false)));
        amountButtons.add(new AE2Button(left + 86, top + 42, 34, 20, makeButtonLabel(MINUS, getCurrentButtonValues()[3]),
            () -> applyButton(3, false)));

        this.amountButtons = List.copyOf(amountButtons);

        buttons.addAll(amountButtons.subList(4, amountButtons.size()));

        if (!hideValidationIcon) {
            this.validationIcon = new ValidationIcon();
            this.validationIcon.x = left + 104;
            this.validationIcon.y = top + 27;
            buttons.add(this.validationIcon);
        }
        this.ceilButtonBounds = new Rectangle(this.bounds.x + 124, this.bounds.y, 14, 20);
        this.floorButtonBounds = new Rectangle(this.bounds.x + 124, this.bounds.y + 42, 14, 20);
        this.ceilButton = new AE2Button(left + 124, top, 14, 20, CEIL, () -> roundValue(RoundingMode.CEILING));
        this.floorButton = new AE2Button(left + 124, top + 42, 14, 20, FLOOR, () -> roundValue(RoundingMode.FLOOR));
        buttons.add(this.ceilButton);
        buttons.add(this.floorButton);

        buttons.subList(4, buttons.size()).forEach(addWidget);

        this.buttons = buttons;

        this.validate();
    }

    @Override
    public void updateBeforeRender() {
        ButtonMode mode = getButtonMode();
        long[] values = getValues(mode);
        if (mode == this.lastButtonMode && Arrays.equals(values, this.lastButtonValues)) {
            return;
        }
        this.lastButtonMode = mode;
        this.lastButtonValues = Arrays.copyOf(values, values.length);
        for (int i = 0; i < amountButtons.size(); i++) {
            boolean positive = i < 4;
            ITextComponent prefix = switch (mode) {
                case ADD, SHIFT_ADD -> positive ? PLUS : MINUS;
                case MULTIPLY, ALT_MULTIPLY -> positive ? MULTIPLY : DIVIDE;
            };
            amountButtons.get(i).setMessage(makeButtonLabel(prefix, values[i % 4]));
        }
    }

    @Override
    public void tick() {
        if (this.textField.getVisible()) {
            this.textField.updateCursorCounter();
            this.textField.tickKeyRepeat();
        }
    }

    /**
     * Returns whether the text field begins with an equals sign. This is used by crafting request screens in order to
     * request just enough of an item to bring the total stored amount to the input amount, rather than requesting the
     * input amount itself.
     */
    public boolean startsWithEquals() {
        return textField.getText().startsWith("=");
    }

    /**
     * Returns the integer value currently in the text-field, if it is a valid number and is within the allowed min/max
     * value.
     */
    public OptionalInt getIntValue() {
        var value = getLongValue();
        if (value.isPresent()) {
            var longValue = value.getAsLong();
            if (longValue > Integer.MAX_VALUE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) longValue);
        }
        return OptionalInt.empty();
    }

    /**
     * Returns the long value currently in the text-field, if it is a valid number and is within the allowed min/max
     * value.
     */
    public OptionalLong getLongValue() {
        var value = getCachedValidation().value().externalValue();
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }

        var externalValue = value.getAsLong();
        if (externalValue < minValue || externalValue > maxValue) {
            return OptionalLong.empty();
        }
        return value;
    }

    public void setLongValue(long value) {
        var internalValue = convertToInternalValue(Longs.constrainToRange(value, minValue, maxValue));
        this.textField.setText(decimalFormat.format(internalValue));
        this.textField.setCursorPositionEnd();
        this.textField.setSelectionPos(0);
        validate();
    }

    private void addQty(long delta) {
        var operableValue = getOperableValue();
        if (operableValue.isEmpty()) {
            return;
        }
        var currentValue = operableValue.get();
        var newValue = currentValue.add(BigDecimal.valueOf(delta));
        if (newValue.compareTo(minButtonValue) < 0) {
            newValue = minButtonValue;
        } else if (newValue.compareTo(maxButtonValue) > 0) {
            newValue = maxButtonValue;
        } else if (currentValue.compareTo(BigDecimal.ONE) == 0 && delta > 1) {
            newValue = newValue.subtract(BigDecimal.ONE);
        }
        setValueInternal(newValue);
    }

    private void applyButton(int amountIndex, boolean positive) {
        ButtonMode mode = getButtonMode();
        long value = getValues(mode)[amountIndex];
        switch (mode) {
            case ADD, SHIFT_ADD -> addQty(positive ? value : -value);
            case MULTIPLY, ALT_MULTIPLY -> multiplyQty(BigDecimal.valueOf(value), !positive);
        }
    }

    private void multiplyQty(BigDecimal factor, boolean divide) {
        var operableValue = getOperableValue();
        if (operableValue.isEmpty()) {
            return;
        }
        var currentValue = operableValue.get();
        var newValue = divide
            ? currentValue.divide(factor, MathContext.DECIMAL128)
            : currentValue.multiply(factor, MathContext.DECIMAL128);
        if (newValue.compareTo(minInternalValue) < 0) {
            newValue = minInternalValue;
        } else if (newValue.compareTo(maxInternalValue) > 0) {
            newValue = maxInternalValue;
        }
        setValueInternal(newValue);
    }

    private void roundValue(RoundingMode roundingMode) {
        var currentValue = getOperableValue();
        if (currentValue.isEmpty()) {
            return;
        }
        setValueInternal(currentValue.get().setScale(0, roundingMode));
    }

    /**
     * Retrieves the numeric representation of the value entered by the user, if it is convertible.
     */
    private Optional<BigDecimal> getValueInternal() {
        return getCachedValidation().value().internalValue();
    }

    private Optional<BigDecimal> getOperableValue() {
        if (textField.getText().trim().isEmpty()) {
            return Optional.of(BigDecimal.ZERO);
        }
        return getValueInternal();
    }

    /**
     * Changes the value displayed to the user.
     */
    private void setValueInternal(BigDecimal value) {
        invalidateValidationCache();
        textField.setText(decimalFormat.format(value.stripTrailingZeros()));
    }

    private Optional<BigDecimal> getValueInternal(String textValue) {
        if (textValue.startsWith("=")) {
            textValue = textValue.substring(1);
        }
        return MathExpressionParser.parse(textValue, decimalFormat);
    }

    /*
     * Return true if the value entered by the user is a single numeric number and not a mathematical expression
     */
    private boolean isNumber(String textValue) {
        var position = new ParsePosition(0);
        textValue = textValue.trim();
        decimalFormat.parse(textValue, position);
        return position.getErrorIndex() == -1 && position.getIndex() == textValue.length();
    }

    private void validate() {
        var validation = getCachedValidation();
        boolean valid = validation.valid();
        var tooltip = validation.tooltip();
        this.textField.setTextColor(valid ? normalTextColor : errorTextColor);
        this.textField.setTooltipMessage(tooltip);

        if (this.validationIcon != null) {
            this.validationIcon.setValid(valid);
            this.validationIcon.setTooltip(tooltip);
        }
    }

    private CachedValidation getCachedValidation() {
        String text = this.textField.getText();
        if (this.validationCacheDirty
            || this.validationCache == null
            || !text.equals(this.validationCacheText)
            || !this.type.equals(this.validationCacheType)
            || this.minValue != this.validationCacheMinValue
            || this.maxValue != this.validationCacheMaxValue) {
            this.validationCacheText = text;
            this.validationCacheType = this.type;
            this.validationCacheMinValue = this.minValue;
            this.validationCacheMaxValue = this.maxValue;
            this.validationCache = computeValidation(text);
            this.validationCacheDirty = false;
            updatePreviewField();
        }
        return this.validationCache;
    }

    private CachedValidation computeValidation(String text) {
        List<ITextComponent> validationErrors = new ObjectArrayList<>();
        List<ITextComponent> infoMessages = new ObjectArrayList<>();

        var possibleValue = validateValue(text);
        if (possibleValue.internalValue().isPresent()) {
            var internalValue = possibleValue.internalValue().get();
            if (possibleValue.notAnInteger()) {
                validationErrors.add(GuiText.NumberNonInteger.text());
            } else if (possibleValue.externalValue().isEmpty()) {
                validationErrors.add(GuiText.InvalidNumber.text());
            } else {
                var value = possibleValue.externalValue().getAsLong();
                if (value < minValue) {
                    var formatted = decimalFormat.format(minInternalValue);
                    validationErrors.add(GuiText.NumberLessThanMinValue.text(formatted));
                } else if (value > maxValue) {
                    var formatted = decimalFormat.format(maxInternalValue);
                    validationErrors.add(GuiText.NumberGreaterThanMaxValue.text(formatted));
                } else if (!isNumber(text)) {
                    infoMessages.add(new TextComponentString("= " + decimalFormat.format(internalValue)));
                }
            }
        } else {
            validationErrors.add(GuiText.InvalidNumber.text());
        }

        boolean valid = validationErrors.isEmpty();
        var tooltip = valid ? infoMessages : validationErrors;
        var preview = valid && possibleValue.externalValue().isPresent()
            ? Long.toString(possibleValue.externalValue().getAsLong())
            : PREVIEW_ERROR;
        return new CachedValidation(possibleValue, valid, List.copyOf(tooltip), preview);
    }

    private void invalidateValidationCache() {
        this.validationCacheDirty = true;
    }

    private void updatePreviewField() {
        if (this.previewFieldConfigured && this.validationCache != null) {
            this.previewField.setText(this.validationCache.previewText());
            this.previewField.setCursorPositionEnd();
            this.previewField.setSelectionPos(this.previewField.getCursorPosition());
        }
    }

    private ITextComponent makeButtonLabel(ITextComponent prefix, long amount) {
        return new TextComponentString(prefix.getFormattedText() + decimalFormat.format(amount));
    }

    private long[] getCurrentButtonValues() {
        return getValues(getButtonMode());
    }

    private long[] getValues(ButtonMode mode) {
        NumberEntryButtonConfig.Data config = NumberEntryButtonConfig.get();
        return switch (mode) {
            case ADD -> config.defaultAddSteps();
            case SHIFT_ADD -> config.shiftAddSteps();
            case MULTIPLY -> config.ctrlMultiplyFactors();
            case ALT_MULTIPLY -> config.altMultiplyFactors();
        };
    }

    public void setHideValidationIcon(boolean hideValidationIcon) {
        this.hideValidationIcon = hideValidationIcon;
    }

    public NumberEntryType getType() {
        return type;
    }

    public void setType(NumberEntryType type) {
        if (this.type.equals(type)) {
            return;
        }
        this.type = type;
        refreshUnitCache();
        setTextFieldBounds(this.textFieldBounds);
        if (this.previewFieldConfigured) {
            setPreviewFieldBounds(this.previewFieldBounds);
        }
        invalidateValidationCache();
        if (onChange != null) {
            onChange.run();
        }

        validate();
    }

    private OptionalLong convertToExternalValue(BigDecimal internalValue) {
        return toExternalValue(internalValue, amountPerUnit);
    }

    private BigDecimal convertToInternalValue(long externalValue) {
        return BigDecimal.valueOf(externalValue).divide(amountPerUnit, MathContext.DECIMAL128);
    }

    private void refreshUnitCache() {
        this.amountPerUnit = BigDecimal.valueOf(type.amountPerUnit());
        refreshBoundsCache();
    }

    private void refreshBoundsCache() {
        this.minInternalValue = convertToInternalValue(this.minValue);
        this.maxInternalValue = convertToInternalValue(this.maxValue);
        this.minButtonValue = minInternalValue.setScale(0, RoundingMode.CEILING);
        this.maxButtonValue = maxInternalValue.setScale(0, RoundingMode.FLOOR);
    }

    private ValidatedValue validateValue(String text) {
        var internalValue = getValueInternal(text);
        if (internalValue.isEmpty()) {
            return new ValidatedValue(Optional.empty(), OptionalLong.empty(), false);
        }

        var internal = internalValue.get();
        var notAnInteger = type.amountPerUnit() == 1 && internal.scale() > 0;
        return new ValidatedValue(internalValue, notAnInteger ? OptionalLong.empty() : convertToExternalValue(internal),
            notAnInteger);
    }

    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        if (type.unit() != null) {
            var font = Minecraft.getMinecraft().fontRenderer;
            var x = bounds.x + textFieldBounds.x + textFieldBounds.width
                - font.getStringWidth(type.unit());

            var y = (int) (bounds.y + textFieldBounds.y + (textFieldBounds.height - font.FONT_HEIGHT) / 2f
                + 1);
            font.drawString(type.unit(), x, y, UNIT_COLOR);
        }
    }

    @Override
    public void drawAbsoluteLayer(Rectangle bounds, Point mouse) {
        this.textField.drawTextBox();
        this.previewField.setVisible(this.previewFieldConfigured);
        this.previewField.setFocused(false);
        this.previewField.drawTextBox();
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (button == 1 && this.textFieldBounds.contains(mousePos.x(), mousePos.y())) {
            this.textField.setText("");
            this.textField.setFocused(true);
            return true;
        }

        if (!this.textFieldBounds.contains(mousePos.x(), mousePos.y())) {
            return false;
        }

        return this.textField.mouseClicked(
            this.currentScreenOrigin.x() + mousePos.x(),
            this.currentScreenOrigin.y() + mousePos.y(),
            button);
    }

    @Override
    public boolean wantsAllMouseDownEvents() {
        return this.previewFieldConfigured;
    }

    @Override
    public boolean onKeyTyped(char typedChar, int keyCode) {
        return this.textField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (textFieldBounds.contains(mousePos.x(), mousePos.y())) {
            if (getValueInternal().isPresent()) {
                if (delta < 0) {
                    addQty(-1);
                } else if (delta > 0) {
                    addQty(1);
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (this.ceilButtonBounds.contains(mouseX, mouseY)) {
            return new Tooltip(Collections.singletonList(GuiText.NumberEntryCeil.text()));
        }
        if (this.floorButtonBounds.contains(mouseX, mouseY)) {
            return new Tooltip(Collections.singletonList(GuiText.NumberEntryFloor.text()));
        }
        if (this.textFieldBounds.contains(mouseX, mouseY)) {
            return new Tooltip(getTextFieldTooltip());
        }
        return null;
    }

    private List<ITextComponent> getTextFieldTooltip() {
        CachedValidation validation = getCachedValidation();
        List<ITextComponent> tooltip = new ObjectArrayList<>();
        tooltip.add(GuiText.SelectAmount.text());
        if (validation.valid()) {
            tooltip.add(new TextComponentString("= " + validation.previewText()));
            if (this.textField.isFocused()) {
                tooltip.add(
                    new TextComponentString("» ")
                        .setStyle(new Style().setColor(TextFormatting.AQUA))
                        .appendSibling(
                            GuiText.Set.text()
                                .setStyle(new Style().setColor(TextFormatting.GRAY))
                        )
                );
            }
        } else {
            tooltip.addAll(validation.tooltip());
        }
        return tooltip;
    }

    private record ValidatedValue(Optional<BigDecimal> internalValue, OptionalLong externalValue,
                                  boolean notAnInteger) {
    }

    private record CachedValidation(ValidatedValue value, boolean valid, List<ITextComponent> tooltip,
                                    String previewText) {
    }

    private enum ButtonMode {
        ADD,
        SHIFT_ADD,
        MULTIPLY,
        ALT_MULTIPLY
    }

}
