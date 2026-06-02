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
import net.minecraft.util.text.TextComponentString;

import java.awt.Rectangle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
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

    private static final long[] STEPS_1000 = new long[]{1, 10, 100, 1000};
    private static final long[] STEPS_64 = new long[]{1, 16, 32, 64};
    private static final ITextComponent PLUS = new TextComponentString("+");
    private static final ITextComponent MINUS = new TextComponentString("-");
    private static final int UNIT_PADDING = 3;
    private static final int UNIT_COLOR = 0x555555;
    private final ITextComponent[] components1000;
    private final ITextComponent[] components64;
    private final int errorTextColor;
    private final int normalTextColor;

    private final ConfirmableTextField textField;
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
    private Point currentScreenOrigin = Point.ZERO;

    private List<AE2Button> amountButtons = ObjectLists.emptyList();

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
            if (this.onConfirm != null && getLongValue().isPresent()) {
                this.onConfirm.run();
            }
        });
        validate();

        components1000 = new ITextComponent[]{
            makeLabel(PLUS, 0, true),
            makeLabel(PLUS, 1, true),
            makeLabel(PLUS, 2, true),
            makeLabel(PLUS, 3, true),
            makeLabel(MINUS, 0, true),
            makeLabel(MINUS, 1, true),
            makeLabel(MINUS, 2, true),
            makeLabel(MINUS, 3, true),
        };
        components64 = new ITextComponent[]{
            makeLabel(PLUS, 0, false),
            makeLabel(PLUS, 1, false),
            makeLabel(PLUS, 2, false),
            makeLabel(PLUS, 3, false),
            makeLabel(MINUS, 0, false),
            makeLabel(MINUS, 1, false),
            makeLabel(MINUS, 2, false),
            makeLabel(MINUS, 3, false),
        };
    }

    private static boolean hasShiftOrControlDown() {
        return GuiScreen.isShiftKeyDown() || GuiScreen.isCtrlKeyDown();
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

    public void setMinValue(long minValue) {
        this.minValue = minValue;
        refreshBoundsCache();
        validate();
    }

    public void setMaxValue(long maxValue) {
        this.maxValue = maxValue;
        refreshBoundsCache();
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

        amountButtons.add(new AE2Button(left, top, 22, 20, components1000[0],
            () -> addQty(hasShiftOrControlDown() ? STEPS_64[0] : STEPS_1000[0])));
        amountButtons.add(new AE2Button(left + 28, top, 28, 20, components1000[1],
            () -> addQty(hasShiftOrControlDown() ? STEPS_64[1] : STEPS_1000[1])));
        amountButtons.add(new AE2Button(left + 62, top, 32, 20, components1000[2],
            () -> addQty(hasShiftOrControlDown() ? STEPS_64[2] : STEPS_1000[2])));
        amountButtons.add(new AE2Button(left + 100, top, 38, 20, components1000[3],
            () -> addQty(hasShiftOrControlDown() ? STEPS_64[3] : STEPS_1000[3])));

        buttons.addAll(amountButtons);
        amountButtons.forEach(addWidget);

        this.currentScreenOrigin = Point.fromTopLeft(bounds);
        setTextFieldBounds(this.textFieldBounds);
        screen.setInitialFocus(this.textField);

        amountButtons.add(new AE2Button(left, top + 42, 22, 20, components1000[4],
            () -> addQty(hasShiftOrControlDown() ? -STEPS_64[0] : -STEPS_1000[0])));
        amountButtons.add(new AE2Button(left + 28, top + 42, 28, 20, components1000[5],
            () -> addQty(hasShiftOrControlDown() ? -STEPS_64[1] : -STEPS_1000[1])));
        amountButtons.add(new AE2Button(left + 62, top + 42, 32, 20, components1000[6],
            () -> addQty(hasShiftOrControlDown() ? -STEPS_64[2] : -STEPS_1000[2])));
        amountButtons.add(new AE2Button(left + 100, top + 42, 38, 20, components1000[7],
            () -> addQty(hasShiftOrControlDown() ? -STEPS_64[3] : -STEPS_1000[3])));

        this.amountButtons = List.copyOf(amountButtons);

        buttons.addAll(amountButtons.subList(4, amountButtons.size()));

        if (!hideValidationIcon) {
            this.validationIcon = new ValidationIcon();
            this.validationIcon.x = left + 104;
            this.validationIcon.y = top + 27;
            buttons.add(this.validationIcon);
        }

        buttons.subList(4, buttons.size()).forEach(addWidget);

        this.buttons = buttons;

        this.validate();
    }

    @Override
    public void updateBeforeRender() {
        ITextComponent[] messages = hasShiftOrControlDown() ? components64 : components1000;
        for (int i = 0; i < amountButtons.size(); i++) {
            amountButtons.get(i).setMessage(messages[i]);
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
        var value = validateValue().externalValue();
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
        var currentValue = getValueInternal().orElse(BigDecimal.ZERO);
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

    /**
     * Retrieves the numeric representation of the value entered by the user, if it is convertible.
     */
    private Optional<BigDecimal> getValueInternal() {
        var textValue = textField.getText();
        if (textValue.startsWith("=")) {
            textValue = textValue.substring(1);
        }
        return MathExpressionParser.parse(textValue, decimalFormat);
    }

    /**
     * Changes the value displayed to the user.
     */
    private void setValueInternal(BigDecimal value) {
        textField.setText(decimalFormat.format(value));
    }

    /*
     * Return true if the value entered by the user is a single numeric number and not a mathematical expression
     */
    private boolean isNumber() {
        var position = new ParsePosition(0);
        var textValue = textField.getText().trim();
        decimalFormat.parse(textValue, position);
        return position.getErrorIndex() == -1 && position.getIndex() == textValue.length();
    }

    private void validate() {
        List<ITextComponent> validationErrors = new ObjectArrayList<>();
        List<ITextComponent> infoMessages = new ObjectArrayList<>();

        var possibleValue = validateValue();
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
                } else if (!isNumber()) {
                    infoMessages.add(new TextComponentString("= " + decimalFormat.format(internalValue)));
                }
            }
        } else {
            validationErrors.add(GuiText.InvalidNumber.text());
        }

        boolean valid = validationErrors.isEmpty();
        var tooltip = valid ? infoMessages : validationErrors;
        this.textField.setTextColor(valid ? normalTextColor : errorTextColor);
        this.textField.setTooltipMessage(tooltip);

        if (this.validationIcon != null) {
            this.validationIcon.setValid(valid);
            this.validationIcon.setTooltip(tooltip);
        }
    }

    private ITextComponent makeLabel(ITextComponent prefix, int amountIndex, boolean useDecimalSteps) {
        return new TextComponentString(prefix.getFormattedText()
            + decimalFormat.format(useDecimalSteps ? STEPS_1000[amountIndex] : STEPS_64[amountIndex]));
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

    private ValidatedValue validateValue() {
        var internalValue = getValueInternal();
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
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        return this.textField.mouseClicked(
            this.currentScreenOrigin.x() + mousePos.x(),
            this.currentScreenOrigin.y() + mousePos.y(),
            button);
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

    private record ValidatedValue(Optional<BigDecimal> internalValue, OptionalLong externalValue,
                                  boolean notAnInteger) {
    }
}
