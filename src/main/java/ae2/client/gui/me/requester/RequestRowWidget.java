package ae2.client.gui.me.requester;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ToggleButton;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.RequesterUpdatePacket;
import ae2.tile.crafting.requester.Request;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public final class RequestRowWidget {

    private static final int ROW_X = 8;
    private static final int ENABLED_X = ROW_X + 1;
    private static final int AMOUNT_X = ROW_X + 38;
    private static final int BATCH_X = ROW_X + 92;
    private static final int FORCE_START_X = ROW_X + 143;
    private static final int STATUS_X = ROW_X + 39;
    private static final int STATUS_Y = 15;

    private final AEBaseGui<?> gui;
    private final FontRenderer font;
    private final int currentY;
    private final List<ToggleButton> buttons = new ObjectArrayList<>(2);

    private final ToggleButton enableBtn;
    private final ToggleButton forceStartBtn;
    private final NumberField amountField;
    private final NumberField batchField;
    private final StatusDisplay statusDisplay;

    private Request request;
    private long requesterId;
    private int requestIndex;
    private boolean visible = true;
    private int offsetX = Integer.MIN_VALUE;
    private int offsetY = Integer.MIN_VALUE;

    public RequestRowWidget(AEBaseGui<?> gui, GuiStyle style, Request request, int currentY) {
        this.gui = gui;
        this.font = gui.mc.fontRenderer;
        this.currentY = currentY;
        this.request = request;

        this.amountField = new NumberField(this.offsetX, this.offsetY, GuiText.RequesterAmount, style, 0,
            this::amountFieldSubmitted);
        this.batchField = new NumberField(this.offsetX, this.offsetY, GuiText.RequesterBatch, style, 1,
            this::batchFieldSubmitted);

        this.enableBtn = new ToggleButton(Icon.ENABLED, Icon.DISABLED, this::enableBtnChanged);
        this.enableBtn.setDisableBackground(true);
        this.enableBtn.setTooltipOn(List.of(GuiText.RequesterToggleEnabled.text()));
        this.enableBtn.setTooltipOff(List.of(GuiText.RequesterToggleDisabled.text()));

        this.forceStartBtn = new ToggleButton(Icon.ENABLED, Icon.DISABLED, this::forceStartBtnChanged);
        this.forceStartBtn.setDisableBackground(true);
        this.forceStartBtn.setTooltipOn(List.of(
            GuiText.ForceStart.text(),
            GuiText.RequesterForceStartDetailOn.text()
                .setStyle(new Style().setColor(TextFormatting.GRAY))));
        this.forceStartBtn.setTooltipOff(List.of(
            GuiText.ForceStart.text(),
            GuiText.RequesterForceStartDetailOff.text()
                .setStyle(new Style().setColor(TextFormatting.GRAY))));

        this.buttons.add(this.enableBtn);
        this.buttons.add(this.forceStartBtn);

        this.statusDisplay = new StatusDisplay(this.offsetX + STATUS_X, this.offsetY + STATUS_Y + this.currentY, this::isInactive);
        setRequester(request);
    }

    public void updateFromRequest(Request request) {
        this.request = request;

        var status = this.request.getClientStatus();
        this.statusDisplay.setStatus(status);

        this.amountField.adjustToType(request.getKey());
        this.batchField.adjustToType(request.getKey());
        if (status.locksRequest()) {
            this.amountField.setEnabled(false);
            this.batchField.setEnabled(false);
            this.enableBtn.enabled = request.isEnabled();
            this.forceStartBtn.enabled = false;
        } else {
            this.amountField.setEnabled(true);
            this.batchField.setEnabled(true);
            this.enableBtn.enabled = true;
            this.forceStartBtn.enabled = true;
        }
        if (!this.amountField.isFocused() && !this.batchField.isFocused()) {
            this.amountField.setLongValue(request.getAmount());
            this.batchField.setLongValue(request.getBatchSize());
        }
        this.enableBtn.setState(request.isEnabled());
        this.forceStartBtn.setState(request.isForceStart());
    }

    public void setRequester(Request request) {
        this.requesterId = request.getRequesterId();
        this.requestIndex = request.getIndex();
        updateFromRequest(request);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        for (GuiButton button : this.buttons) {
            button.visible = visible;
            if (!visible) {
                button.enabled = false;
            }
        }
        this.amountField.setVisible(visible);
        this.batchField.setVisible(visible);
        this.statusDisplay.setVisible(visible);
    }

    public void addButtons(List<GuiButton> target) {
        target.addAll(this.buttons);
    }

    public void removeButtons(List<GuiButton> target) {
        target.removeAll(this.buttons);
    }

    public void setOrigin(int offsetX, int offsetY) {
        if (this.offsetX == offsetX && this.offsetY == offsetY) {
            return;
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.amountField.move(new Point(offsetX + AMOUNT_X, offsetY + this.currentY));
        this.batchField.move(new Point(offsetX + BATCH_X, offsetY + this.currentY));
        this.enableBtn.x = offsetX + ENABLED_X;
        this.enableBtn.y = offsetY + this.currentY;
        this.forceStartBtn.x = offsetX + FORCE_START_X;
        this.forceStartBtn.y = offsetY + this.currentY;
        this.statusDisplay.move(offsetX + STATUS_X, offsetY + this.currentY + STATUS_Y);
    }

    public void draw(int offsetX, int offsetY) {
        if (!this.visible) {
            return;
        }
        setOrigin(offsetX, offsetY);
        this.amountField.renderWidget(this.font);
        this.batchField.renderWidget(this.font);
        this.statusDisplay.renderWidget();
    }

    public void drawPreview(int guiLeft, int guiTop) {
        if (!this.visible) {
            return;
        }
        this.amountField.renderPreview(guiLeft, guiTop);
        this.batchField.renderPreview(guiLeft, guiTop);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!this.visible) {
            return false;
        }

        if (this.amountField.isMouseOver(mouseX, mouseY)) {
            this.batchField.setFocused(false);
            return this.amountField.mouseClicked(mouseX, mouseY, button);
        }

        if (this.batchField.isMouseOver(mouseX, mouseY)) {
            this.amountField.setFocused(false);
            return this.batchField.mouseClicked(mouseX, mouseY, button);
        }

        return false;
    }

    public boolean isMouseOverInput(int mouseX, int mouseY) {
        return this.visible && (this.amountField.isMouseOver(mouseX, mouseY)
            || this.batchField.isMouseOver(mouseX, mouseY));
    }

    public void clearFocus() {
        this.amountField.setFocused(false);
        this.batchField.setFocused(false);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.visible) {
            return false;
        }

        if (this.amountField.isFocused()) {
            return this.amountField.textboxKeyTyped(typedChar, keyCode);
        }

        if (this.batchField.isFocused()) {
            return this.batchField.textboxKeyTyped(typedChar, keyCode);
        }

        return false;
    }

    public void confirmFocusedInput() {
        if (!this.visible) {
            return;
        }

        if (this.amountField.isFocused()) {
            this.amountFieldSubmitted(this.amountField.getLongValue().orElse(0));
        } else if (this.batchField.isFocused()) {
            this.batchFieldSubmitted(this.batchField.getLongValue().orElse(1));
        }
    }

    public boolean drawTooltip(AEBaseGui<?> gui, int mouseX, int mouseY) {
        if (!this.visible) {
            return false;
        }

        for (ToggleButton btn : this.buttons) {
            if (btn.isMouseOver()) {
                gui.drawTooltipWithHeader(mouseX, mouseY, btn.getTooltipMessage());
                return true;
            }
        }

        if (this.amountField.isMouseOver(mouseX, mouseY)) {
            gui.drawTooltipWithHeader(mouseX, mouseY, this.amountField.getTooltipMessage());
            return true;
        }

        if (this.batchField.isMouseOver(mouseX, mouseY)) {
            gui.drawTooltipWithHeader(mouseX, mouseY, this.batchField.getTooltipMessage());
            return true;
        }

        if (this.statusDisplay.isMouseOver(mouseX, mouseY)) {
            gui.drawTooltipWithHeader(mouseX, mouseY, this.statusDisplay.getTooltipMessage());
            return true;
        }

        return false;
    }

    private void amountFieldSubmitted(long amount) {
        if (this.request == null) {
            return;
        }

        long oldValue = this.request.getAmount();
        this.request.updateAmount(amount);
        if (oldValue == this.request.getAmount()) {
            this.amountField.setLongValue(oldValue);
        } else {
            submit();
        }
    }

    private void batchFieldSubmitted(long batchSize) {
        if (this.request == null) {
            return;
        }

        long oldValue = this.request.getBatchSize();
        this.request.updateBatchSize(batchSize);
        if (oldValue == this.request.getBatchSize()) {
            batchField.setLongValue(oldValue);
        } else {
            submit();
        }
    }

    private void submit() {
        if (this.request == null) {
            return;
        }
        long amount = amountField.getLongValue().orElse(0);
        long batch = batchField.getLongValue().orElse(1);

        InitNetwork.sendToServer(new RequesterUpdatePacket(this.gui.getContainer().windowId, this.requesterId, this.requestIndex, amount, batch));
    }

    private void enableBtnChanged(boolean changed) {
        if (this.request == null) {
            return;
        }

        this.request.setEnabled(changed);
        this.enableBtn.setState(changed);
        InitNetwork.sendToServer(new RequesterUpdatePacket(this.gui.getContainer().windowId, this.requesterId, this.requestIndex, changed, this.request.isForceStart()));
    }

    private void forceStartBtnChanged(boolean changed) {
        if (this.request == null) {
            return;
        }

        this.request.setForceStart(changed);
        this.forceStartBtn.setState(changed);
        InitNetwork.sendToServer(new RequesterUpdatePacket(this.gui.getContainer().windowId, this.requesterId, this.requestIndex, this.request.isEnabled(), changed));
    }

    private boolean isInactive() {
        return this.request == null || this.request.getKey() == null || this.request.getAmount() == 0;
    }

}
