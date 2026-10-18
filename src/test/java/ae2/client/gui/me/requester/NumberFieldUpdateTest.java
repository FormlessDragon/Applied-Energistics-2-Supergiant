package ae2.client.gui.me.requester;

import ae2.client.gui.NumberEntryType;
import ae2.core.localization.GuiText;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NumberFieldUpdateTest {
    private static HeadlessField field() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var field = (HeadlessField) ((Unsafe) unsafeField.get(null)).allocateInstance(HeadlessField.class);
        var format = new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setParseBigDecimal(true);
        set(NumberField.class, field, "decimalFormat", format);
        set(NumberField.class, field, "type", NumberEntryType.UNITLESS);
        set(GuiTextField.class, field, "text", "");
        field.setValidator(_ -> true);
        field.setMaxStringLength(64);
        field.setEnabled(true);
        var validate = NumberField.class.getDeclaredMethod("validate");
        validate.setAccessible(true);
        field.setResponder(_ -> {
            try {
                validate.invoke(field);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        return field;
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void stableUpdatesSkipFormattingAndValidationButEditedTextStillNormalizes() throws Exception {
        var field = field();
        field.setLongValue(10);
        String originalText = field.getText();
        assertEquals(1, field.writes);
        assertEquals(1, field.validations);
        for (int i = 0; i < 50; i++) {
            field.adjustToType(null);
            field.setLongValue(10);
        }
        assertSame(originalText, field.getText());
        assertEquals(1, field.writes);
        assertEquals(1, field.validations);

        field.setText("5*2");
        assertEquals(10, field.getLongValue().orElseThrow());
        field.setLongValue(10);
        assertEquals("10", field.getText());
        assertEquals(3, field.writes);
        assertEquals(3, field.validations);
        field.setText("bad");
        field.setLongValue(10);
        assertEquals("10", field.getText());
        assertEquals(10, field.getLongValue().orElseThrow());
    }

    @Test
    void typeChangesInvalidateFormattedTextWithoutRepeatingStableValidation() throws Exception {
        var field = field();
        field.setLongValue(1000);
        set(NumberField.class, field, "type", new NumberEntryType(1000, null));
        field.setLongValue(1000);
        assertEquals("1", field.getText());
        assertEquals(1000, field.getLongValue().orElseThrow());
        field.adjustToType(null);
        field.setLongValue(1000);
        assertEquals("1000", field.getText());

        int validations = field.validations;
        field.adjustToType(null);
        field.setLongValue(1000);
        assertEquals(validations, field.validations);
    }

    // Skip Minecraft/GL construction; use real text mutation, parsing and responder methods.
    private static final class HeadlessField extends NumberField {
        private int writes;
        private int validations;

        private HeadlessField() {
            super(0, 0, GuiText.RequesterAmount, null, 0, _ -> {
            });
        }

        @Override
        public void setText(String text) {
            this.writes++;
            super.setText(text);
        }

        @Override
        public void setCursorPositionEnd() {
        }

        @Override
        public void setTooltipMessage(List<ITextComponent> tooltip) {
            this.validations++;
        }
    }
}
