package ae2.client.gui.me.requester;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.ToggleButton;
import ae2.core.localization.GuiText;
import ae2.tile.crafting.requester.Request;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.ForgeModContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberFieldUpdateTest {
    private static boolean previousImeBlocking;
    private static Object previousMinecraft;

    @BeforeAll
    static void disableNativeIme() throws Exception {
        previousImeBlocking = ForgeModContainer.inputMethodBlockingEnabled;
        ForgeModContainer.inputMethodBlockingEnabled = false;
        var instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousMinecraft = instance.get(null);
        if (previousMinecraft == null) instance.set(null, allocate(Minecraft.class));
    }

    @AfterAll
    static void restoreNativeIme() throws Exception {
        ForgeModContainer.inputMethodBlockingEnabled = previousImeBlocking;
        set(Minecraft.class, null, "instance", previousMinecraft);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
    }

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

    @Test
    void expressionsAndInvalidAmountsAreCheckedWithoutClientInput() throws Exception {
        var field = field();
        for (var expression : List.of("(2+3)*4", "1k/50", "2^4+4")) {
            field.setText(expression);
            assertEquals(20, field.getLongValue().orElseThrow(), expression);
        }
        for (var expression : List.of("", "bad", "1/0", "(2+3", "-1", "1.5",
            "9223372036854775808")) {
            field.setText(expression);
            assertTrue(field.getLongValue().isEmpty(), expression);
        }
    }

    @Test
    void exactLongMaximumIsAcceptedAndOverflowIsRejectedWithUnitConversion() throws Exception {
        var field = field();
        for (long amount : new long[]{(long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
            field.setLongValue(amount);
            assertEquals(amount, field.getLongValue().orElseThrow());
        }
        field.setText("9223372036854775808");
        assertTrue(field.getLongValue().isEmpty());

        set(NumberField.class, field, "type", new NumberEntryType(1000, null));
        field.setLongValue(Long.MAX_VALUE);
        assertEquals("9223372036854775.807", field.getText());
        assertEquals(Long.MAX_VALUE, field.getLongValue().orElseThrow());
        field.setText("9223372036854775.808");
        assertTrue(field.getLongValue().isEmpty());
    }

    @Test
    void rowRebindingAndKeyChangesDiscardOldEditsButStableUpdatesKeepThem() throws Exception {
        Bootstrap.register();
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var row = (RequestRowWidget) unsafe.allocateInstance(RequestRowWidget.class);
        var amount = field();
        var batch = field();
        var enable = (ToggleButton) unsafe.allocateInstance(ToggleButton.class);
        var force = (ToggleButton) unsafe.allocateInstance(ToggleButton.class);
        set(RequestRowWidget.class, row, "amountField", amount);
        set(RequestRowWidget.class, row, "batchField", batch);
        set(RequestRowWidget.class, row, "enableBtn", enable);
        set(RequestRowWidget.class, row, "forceStartBtn", force);
        set(RequestRowWidget.class, row, "buttons", List.of(enable, force));
        set(RequestRowWidget.class, row, "statusDisplay", new StatusDisplay(0, 0, () -> false));

        var first = new Request();
        first.setRequesterLocation(11, 0);
        first.updateConfiguredStack(new GenericStack(AEItemKey.of(Items.APPLE), 10));
        row.setRequester(first);
        amount.setFocused(true);
        amount.setText("777");
        row.updateFromRequest(first);
        assertTrue(amount.isFocused());
        assertEquals("777", amount.getText());

        var second = new Request();
        second.setRequesterLocation(22, 1);
        second.updateConfiguredStack(new GenericStack(AEItemKey.of(Items.APPLE), 20));
        row.setRequester(second);
        assertFalse(amount.isFocused());
        assertFalse(batch.isFocused());
        assertEquals(20, amount.getLongValue().orElseThrow());
        amount.setFocused(true);
        amount.setText("999");
        second.updateConfiguredStack(new GenericStack(AEItemKey.of(Items.CARROT), 30));
        row.updateFromRequest(second);
        assertFalse(amount.isFocused());
        assertEquals(30, amount.getLongValue().orElseThrow());

        amount.setFocused(true);
        amount.setText("123");
        row.setVisible(false);
        row.confirmFocusedInput();
        assertEquals(30, second.getAmount(), "hidden rows cannot submit an edit");
        row.setVisible(true);
        row.setRequester(first);
        assertFalse(amount.isFocused());
        assertEquals(10, amount.getLongValue().orElseThrow());
    }

    @Test
    void requesterSearchFiltersByNameOrItemAndClearingRestoresSortedRows() throws Exception {
        Bootstrap.register();
        var gui = allocate(GuiRequesterTerm.class);
        var first = new ClientRequester(1, new TextComponentString("Beta"), 0, 1);
        var second = new ClientRequester(2, new TextComponentString("Alpha"), 0, 1);
        var namedItem = new ItemStack(Items.APPLE).setStackDisplayName("SearchToken");
        first.getRequests().get(0).updateConfiguredStack(new GenericStack(AEItemKey.of(namedItem), 10));
        var byId = new Long2ObjectOpenHashMap<ClientRequester>();
        byId.put(1, first);
        byId.put(2, second);
        var search = field();
        var lines = new ArrayList<>();
        set(GuiRequesterTerm.class, gui, "byId", byId);
        set(GuiRequesterTerm.class, gui, "byName", HashMultimap.create());
        set(GuiRequesterTerm.class, gui, "requesterNames", new ArrayList<>());
        set(GuiRequesterTerm.class, gui, "searchField", search);
        set(AbstractGuiRequester.class, gui, "lines", lines);
        set(AbstractGuiRequester.class, gui, "scrollbar", new Scrollbar());
        set(AbstractGuiRequester.class, gui, "requestWidgets", new ObjectArrayList<>());
        set(AbstractGuiRequester.class, gui, "rowAmount", 3);

        search.setText("BETA");
        gui.refreshList();
        assertEquals(List.of(first.getDisplayName().getFormattedText(), first.getRequests().get(0)), lines);
        search.setText("SEARCHTOKEN");
        gui.refreshList();
        assertEquals(List.of(first.getDisplayName().getFormattedText(), first.getRequests().get(0)), lines);
        search.setText("missing");
        gui.refreshList();
        assertTrue(lines.isEmpty());
        search.setText("");
        gui.refreshList();
        assertEquals(List.of(second.getDisplayName().getFormattedText(), second.getRequests().get(0),
            first.getDisplayName().getFormattedText(), first.getRequests().get(0)), lines);
        gui.clearData();
        gui.refreshList();
        assertTrue(lines.isEmpty());
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
