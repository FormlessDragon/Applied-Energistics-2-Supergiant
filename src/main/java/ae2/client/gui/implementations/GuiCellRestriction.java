package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.MathExpressionParser;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITooltip;
import ae2.container.implementations.ContainerCellRestriction;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.OptionalLong;

public class GuiCellRestriction extends AEBaseGui<ContainerCellRestriction> {
    private final DecimalFormat decimalFormat = new DecimalFormat("#.######", new DecimalFormatSymbols());
    private final AETextField amount;
    private final AETextField types;
    private final AE2Button setButton;
    private final AE2Button releaseButton;
    private boolean initialized;
    private String cachedAmountText = "";
    private String cachedTypesText = "";
    private long cachedAmount;
    private int cachedTypes;
    private long cachedAllocatedBytes;
    private boolean cachedValidInput;
    private boolean cachedRestrictionValid;
    private boolean cachedSameAsCurrent;
    private long cachedMaxBytes = Long.MIN_VALUE;
    private int cachedMaxTypes = Integer.MIN_VALUE;
    private int cachedAmountPerByte = Integer.MIN_VALUE;
    private int cachedTypePerByte = Integer.MIN_VALUE;
    private long cachedRestrictionAmount = Long.MIN_VALUE;
    private int cachedRestrictionTypes = Integer.MIN_VALUE;

    public GuiCellRestriction(ContainerCellRestriction container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        this.decimalFormat.setParseBigDecimal(true);

        AESubGui.addBackButton(container, "back", widgets);

        this.amount = widgets.addTextField("amountInput");
        this.amount.setMaxStringLength(32);
        this.amount.setResponder(ignored -> updateInputCache());

        this.types = widgets.addTextField("typesInput");
        this.types.setMaxStringLength(32);
        this.types.setResponder(ignored -> updateInputCache());

        this.setButton = widgets.addButton("set", GuiText.Set.text(), this::setRestriction);
        this.releaseButton = new ResetRestrictionButton(this::releaseRestriction);
        widgets.add("release", this.releaseButton);
    }

    @Override
    public void initGui() {
        super.initGui();
        setInitialFocus(this.amount);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!this.initialized && this.container.maxBytes > 0) {
            this.amount.setText(Long.toString(this.container.getCurrentAmountLimit()));
            this.types.setText(Integer.toString(this.container.getCurrentTypeLimit()));
            this.initialized = true;
        }
        updateInputCache();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && this.setButton.enabled) {
            setRestriction();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 1) {
            if (this.amount.isMouseOver(mouseX, mouseY)) {
                this.amount.setTextFromClient("");
                this.amount.setFocused(true);
                this.types.setFocused(false);
                return;
            }
            if (this.types.isMouseOver(mouseX, mouseY)) {
                this.types.setTextFromClient("");
                this.types.setFocused(true);
                this.amount.setFocused(false);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.amount.isMouseOver(mouseX, mouseY)) {
            this.types.setFocused(false);
        } else if (this.types.isMouseOver(mouseX, mouseY)) {
            this.amount.setFocused(false);
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        drawInfo(GuiText.CellRestrictionMaxByte, this.container.maxBytes * this.container.amountPerByte, 62);
        drawInfo(GuiText.CellRestrictionTotalByte, this.container.maxBytes, 72);
        drawInfo(GuiText.CellRestrictionAmountPerByte, 82);
        drawInfo(GuiText.CellRestrictionTypePerByte, 92);

        if (this.cachedValidInput) {
            drawInfo(GuiText.CellRestrictionAllocatedByte, this.cachedAllocatedBytes, 102);
        } else {
            drawInfo(GuiText.CellRestrictionAllocatedByte, 102);
        }
        if (this.cachedValidInput) {
            drawInfo(GuiText.CellRestrictionFreeByte, this.container.maxBytes - this.cachedAllocatedBytes, 112);
        } else {
            drawInfo(GuiText.CellRestrictionFreeByte, 112);
        }
    }

    private void drawInfo(GuiText label, long value, int y) {
        this.fontRenderer.drawString(label.text(value).getFormattedText(), 12, y, 0x404040);
    }

    private void drawInfo(GuiText label, int y) {
        this.fontRenderer.drawString(label.text("error").getFormattedText(), 12, y, 0x404040);
    }

    private void setRestriction() {
        updateInputCache();
        if (this.cachedRestrictionValid) {
            this.container.setRestriction(this.cachedAmount, this.cachedTypes);
            updateInputCache();
        }
    }

    private void releaseRestriction() {
        this.container.releaseRestriction();
        this.amount.setText(Long.toString(this.container.maxBytes * this.container.amountPerByte));
        this.types.setText(Integer.toString(this.container.maxTypes));
        updateInputCache();
    }

    private void updateInputCache() {
        String amountText = this.amount.getText();
        String typesText = this.types.getText();
        if (amountText.equals(this.cachedAmountText)
            && typesText.equals(this.cachedTypesText)
            && this.cachedMaxBytes == this.container.maxBytes
            && this.cachedMaxTypes == this.container.maxTypes
            && this.cachedAmountPerByte == this.container.amountPerByte
            && this.cachedTypePerByte == this.container.typePerByte
            && this.cachedRestrictionAmount == this.container.restrictionAmount
            && this.cachedRestrictionTypes == this.container.restrictionTypes) {
            applyButtonState();
            return;
        }

        this.cachedAmountText = amountText;
        this.cachedTypesText = typesText;
        this.cachedMaxBytes = this.container.maxBytes;
        this.cachedMaxTypes = this.container.maxTypes;
        this.cachedAmountPerByte = this.container.amountPerByte;
        this.cachedTypePerByte = this.container.typePerByte;
        this.cachedRestrictionAmount = this.container.restrictionAmount;
        this.cachedRestrictionTypes = this.container.restrictionTypes;
        this.cachedValidInput = false;
        this.cachedRestrictionValid = false;
        this.cachedSameAsCurrent = false;

        OptionalLong amountValue = parseLong(amountText);
        OptionalLong typeValue = parseLong(typesText);
        if (amountValue.isPresent() && typeValue.isPresent() && typeValue.getAsLong() <= Integer.MAX_VALUE) {
            this.cachedAmount = amountValue.getAsLong();
            this.cachedTypes = (int) typeValue.getAsLong();
            this.cachedAllocatedBytes = this.container.getAllocatedBytes(this.cachedAmount, this.cachedTypes);
            this.cachedValidInput = true;
            this.cachedRestrictionValid = this.container.maxBytes > 0
                && this.container.isValidRestriction(this.cachedAmount, this.cachedTypes);
            this.cachedSameAsCurrent = this.container.isSameAsCurrent(this.cachedAmount, this.cachedTypes);
        }
        applyButtonState();
    }

    private void applyButtonState() {
        this.setButton.enabled = this.cachedRestrictionValid && !this.cachedSameAsCurrent;
        this.releaseButton.enabled = this.container.hasRestriction();
    }

    private OptionalLong parseLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return OptionalLong.empty();
        }

        return MathExpressionParser.parse(text.trim(), this.decimalFormat)
                                   .flatMap(this::toLong)
                                   .map(OptionalLong::of)
                                   .orElseGet(OptionalLong::empty);
    }

    private java.util.Optional<Long> toLong(BigDecimal value) {
        try {
            return java.util.Optional.of(value.setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (ArithmeticException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static class ResetRestrictionButton extends AE2Button implements ITooltip {
        private ResetRestrictionButton(Runnable onPress) {
            super(GuiText.ResetRestriction.text(), onPress);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return List.of(GuiText.ResetRestrictionHint.text());
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }

}
