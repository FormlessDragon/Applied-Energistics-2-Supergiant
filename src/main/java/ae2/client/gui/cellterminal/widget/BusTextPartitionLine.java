package ae2.client.gui.cellterminal.widget;

import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Row widget used by Cell Terminal for storage-bus filters that are native text expressions.
 */
public class BusTextPartitionLine extends AbstractLine {
    public static final int FIELD_X = CellTerminalLayout.CELL_INDENT + 4;
    public static final int FIELD_WIDTH = CellTerminalLayout.CONTENT_RIGHT_EDGE - FIELD_X - 4;
    public static final int FIELD_HEIGHT = 12;
    public static final int FIELD_Y_OFFSET = 3;

    private final AETextField textField;
    private final String fieldId;
    private final Consumer<String> submitter;
    private int guiLeft;
    private int guiTop;

    public BusTextPartitionLine(int y,
                                String fieldId,
                                String label,
                                String text,
                                String placeholder,
                                FontRenderer fontRenderer,
                                GuiStyle guiStyle,
                                Consumer<String> submitter,
                                @Nullable AETextField existingField) {
        super(0, y, CellTerminalLayout.CONTENT_RIGHT_EDGE);
        this.fieldId = Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(fontRenderer, "fontRenderer");
        this.submitter = Objects.requireNonNull(submitter, "submitter");
        this.textField = existingField != null
            ? existingField
            : new AETextField(Objects.requireNonNull(guiStyle, "guiStyle"), fontRenderer, 0, 0,
            FIELD_WIDTH, FIELD_HEIGHT);
        this.textField.setMaxStringLength(1024);
        this.textField.setPlaceholder(placeholder);
        this.textField.setResponder(ignored -> {
        });
        if (!this.textField.isFocused() && !this.textField.getText().equals(text)) {
            this.textField.setText(text);
        }
    }

    public String fieldId() {
        return this.fieldId;
    }

    public AETextField textField() {
        return this.textField;
    }

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.textField.move(guiLeft + FIELD_X, guiTop + this.y + FIELD_Y_OFFSET);
        this.textField.resize(FIELD_WIDTH, FIELD_HEIGHT);
        this.textField.setVisible(true);
    }

    public boolean isFocused() {
        return this.textField.isFocused();
    }

    public void tickKeyRepeat() {
        this.textField.tickKeyRepeat();
    }

    public boolean handleKeyTyped(char typedChar, int keyCode) {
        return this.textField.textboxKeyTyped(typedChar, keyCode);
    }

    public void submitIfChanged(String snapshotText) {
        String current = this.textField.getText();
        if (!current.equals(snapshotText)) {
            this.submitter.accept(current);
        }
    }

    public void clearFocus() {
        this.textField.setFocused(false);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        drawTreeLines(mouseX, mouseY);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-this.guiLeft, -this.guiTop, 0);
        this.textField.drawTextBox();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (super.handleClick(mouseX, mouseY, button)) {
            return true;
        }
        if (!visible) {
            return false;
        }
        int screenX = this.guiLeft + mouseX;
        int screenY = this.guiTop + mouseY;
        if (this.textField.isMouseOver(screenX, screenY)) {
            this.textField.mouseClicked(screenX, screenY, button);
            return true;
        }
        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!this.textField.isMouseOver(this.guiLeft + mouseX, this.guiTop + mouseY)) {
            return super.getTooltip(mouseX, mouseY);
        }
        List<String> lines = new ObjectArrayList<>();
        lines.add(switch (this.fieldId) {
            case "odWhite" -> GuiText.CellTerminalBusTextPartitionTooltipOdWhite.getLocal();
            case "odBlack" -> GuiText.CellTerminalBusTextPartitionTooltipOdBlack.getLocal();
            default -> GuiText.CellTerminalBusTextPartitionTooltipMod.getLocal();
        });
        return lines;
    }
}
