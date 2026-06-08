package ae2.client.gui.widgets;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.MathExpressionParser;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.AEBaseContainer;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.IOException;
import java.util.OptionalLong;

public class GuiNumberEntryButtonSettings extends AEBaseGui<AEBaseContainer> {
    private static final int GROUP_COUNT = 4;
    private static final int BUTTON_COUNT = 4;
    private static final int VALID_COLOR = 0xFFFFFF;
    private static final int INVALID_COLOR = 0xFF5555;
    private static final String PREVIEW_ERROR = "error";

    private final AEBaseGui<?> parent;
    private final AE2Button saveButton;
    private final AETextField previewField;
    private final DecimalFormat decimalFormat;
    private final AETextField[][] fields = new AETextField[GROUP_COUNT][BUTTON_COUNT];
    private boolean fieldsValid;
    private AETextField previewCacheField;
    private String previewCacheText = "";
    private String previewCacheValue = PREVIEW_ERROR;
    private String previewDisplayText = "";

    public GuiNumberEntryButtonSettings(AEBaseGui<?> parent) {
        super(parent.getContainer(), parent.getContainer().getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/number_entry_button_settings.json"));
        this.parent = parent;
        this.decimalFormat = new DecimalFormat("#.######", new DecimalFormatSymbols());
        this.decimalFormat.setParseBigDecimal(true);
        this.decimalFormat.setNegativePrefix("-");
        this.previewField = new AETextField(getStyle(), Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 12);
        this.previewField.setEnableBackgroundDrawing(false);
        this.previewField.setMaxStringLength(64);
        this.previewField.setEnabled(false);
        this.previewField.setVisible(false);

        widgets.add("back", new TabButton(Icon.BACK, GuiText.NumberEntryButtonSettings.text(), this::returnToParent));
        this.saveButton = widgets.addButton("save", GuiText.Set.text(), this::saveAndReturn);
        widgets.addButton("resetDefaults", GuiText.NumberEntryButtonSettingsReset.text(), this::resetDefaults);

        addFields("default", 0);
        addFields("shift", 1);
        addFields("ctrl", 2);
        addFields("alt", 3);
        loadConfigToFields(NumberEntryButtonConfig.get());
        validateFields();
    }

    private void addFields(String prefix, int group) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            AETextField field = widgets.addTextField(prefix + i);
            field.setMaxStringLength(18);
            field.setResponder(ignored -> {
                validateFields();
                invalidatePreviewCache();
            });
            this.fields[group][i] = field;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        updatePreviewField();
        this.previewField.drawTextBox();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveAndReturn();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        clearFieldFocus();
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void saveAndReturn() {
        if (!save()) {
            return;
        }
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }

    private void returnToParent() {
        if (this.fieldsValid) {
            save();
        }
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }

    private void resetDefaults() {
        NumberEntryButtonConfig.resetToDefaults();
        loadConfigToFields(NumberEntryButtonConfig.get());
        validateFields();
    }

    private boolean save() {
        long[] defaultAddSteps = parseGroup(0);
        long[] shiftAddSteps = parseGroup(1);
        long[] ctrlMultiplyFactors = parseGroup(2);
        long[] altMultiplyFactors = parseGroup(3);
        if (!NumberEntryButtonConfig.isValidGroup(defaultAddSteps)
            || !NumberEntryButtonConfig.isValidGroup(shiftAddSteps)
            || !NumberEntryButtonConfig.isValidGroup(ctrlMultiplyFactors)
            || !NumberEntryButtonConfig.isValidGroup(altMultiplyFactors)) {
            validateFields();
            return false;
        }
        NumberEntryButtonConfig.save(new NumberEntryButtonConfig.Data(
            defaultAddSteps,
            shiftAddSteps,
            ctrlMultiplyFactors,
            altMultiplyFactors));
        return true;
    }

    private void loadConfigToFields(NumberEntryButtonConfig.Data config) {
        writeGroup(0, config.defaultAddSteps());
        writeGroup(1, config.shiftAddSteps());
        writeGroup(2, config.ctrlMultiplyFactors());
        writeGroup(3, config.altMultiplyFactors());
        invalidatePreviewCache();
    }

    private void writeGroup(int group, long[] values) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            this.fields[group][i].setText(Long.toString(values[i]));
        }
    }

    private void validateFields() {
        boolean valid = true;
        for (int group = 0; group < GROUP_COUNT; group++) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                boolean fieldValid = parseField(this.fields[group][i]).isPresent();
                this.fields[group][i].setTextColor(fieldValid ? VALID_COLOR : INVALID_COLOR);
                valid &= fieldValid;
            }
        }
        this.fieldsValid = valid;
        this.saveButton.enabled = valid;
    }

    private long[] parseGroup(int group) {
        long[] values = new long[BUTTON_COUNT];
        for (int i = 0; i < BUTTON_COUNT; i++) {
            values[i] = parseField(this.fields[group][i]).orElse(-1);
        }
        return values;
    }

    private OptionalLong parseField(AETextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            OptionalLong value = parseExpression(text);
            return value.isPresent() && value.getAsLong() > 0 ? value : OptionalLong.empty();
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    private OptionalLong parseExpression(String text) {
        var value = MathExpressionParser.parse(text, this.decimalFormat);
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }

        try {
            BigDecimal integerValue = value.get().setScale(0, RoundingMode.UNNECESSARY);
            return OptionalLong.of(integerValue.longValueExact());
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    private void updatePreviewField() {
        AETextField focusedField = getFocusedField();
        if (focusedField == null) {
            this.previewField.setVisible(false);
            return;
        }

        this.previewField.setVisible(true);
        this.previewField.move(this.guiLeft + 52, this.guiTop + this.ySize + 16);
        this.previewField.resize(72, 12);
        String previewText = getPreviewValue(focusedField);
        if (!previewText.equals(this.previewDisplayText)) {
            this.previewDisplayText = previewText;
            this.previewField.setText(previewText);
            this.previewField.setCursorPositionEnd();
            this.previewField.setSelectionPos(this.previewField.getCursorPosition());
        }
    }

    private String getPreviewValue(AETextField focusedField) {
        String text = focusedField.getText();
        if (focusedField != this.previewCacheField || !text.equals(this.previewCacheText)) {
            this.previewCacheField = focusedField;
            this.previewCacheText = text;
            var v = parseField(focusedField);
            this.previewCacheValue = v.isPresent() ? Long.toString(v.getAsLong()) : PREVIEW_ERROR;
        }
        return this.previewCacheValue;
    }

    private void invalidatePreviewCache() {
        this.previewCacheField = null;
        this.previewCacheText = "";
        this.previewCacheValue = null;
        this.previewDisplayText = "";
    }

    private AETextField getFocusedField() {
        for (int group = 0; group < GROUP_COUNT; group++) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                if (this.fields[group][i].isFocused()) {
                    return this.fields[group][i];
                }
            }
        }
        return null;
    }

    private void clearFieldFocus() {
        for (int group = 0; group < GROUP_COUNT; group++) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                this.fields[group][i].setFocused(false);
            }
        }
    }
}
