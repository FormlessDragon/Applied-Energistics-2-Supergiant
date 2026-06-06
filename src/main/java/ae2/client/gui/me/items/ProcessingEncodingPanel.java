package ae2.client.gui.me.items;

import ae2.api.config.ActionItems;
import ae2.client.Point;
import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.Scrollbar;
import ae2.container.SlotSemantics;
import ae2.core.localization.GuiText;
import ae2.parts.encoding.ProcessingPatternAmountHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public class ProcessingEncodingPanel extends EncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);
    private static final Rectangle MOUSE_WHEEL_AREA = new Rectangle(-1, 1, 124, 66);

    private final ActionButton clearBtn;
    private final ActionButton cycleOutputBtn;
    private final Scrollbar scrollbar;
    private final ProcessingAmountButton[] amountButtons;

    public ProcessingEncodingPanel(GuiPatternEncodingTerm screen, WidgetContainer widgets) {
        super(screen, widgets);
        this.clearBtn = new ActionButton(ActionItems.S_CLOSE, this.container::clear);
        this.clearBtn.setHalfSize(true);
        this.clearBtn.setDisableBackground(true);
        widgets.add("processingClearPattern", this.clearBtn);

        this.cycleOutputBtn = new ActionButton(ActionItems.S_CYCLE_PROCESSING_OUTPUT,
            this.container::cycleProcessingOutput);
        this.cycleOutputBtn.setHalfSize(true);
        this.cycleOutputBtn.setDisableBackground(true);
        widgets.add("processingCycleOutput", this.cycleOutputBtn);

        this.scrollbar = widgets.addScrollBar("processingPatternModeScrollbar", Scrollbar.SMALL);
        this.scrollbar.setRange(0, Math.max(0, this.container.getProcessingInputSlots().length / 3 - 3), 3);
        this.scrollbar.setCaptureMouseWheel(false);

        this.amountButtons = new ProcessingAmountButton[]{
            amountButton("/2", ProcessingPatternAmountHelper.Operation.DIVIDE_2, GuiText.ProcessingPatternDivideDescription),
            amountButton("x2", ProcessingPatternAmountHelper.Operation.MULTIPLY_2,
                GuiText.ProcessingPatternMultiplyDescription),
            amountButton("/3", ProcessingPatternAmountHelper.Operation.DIVIDE_3, GuiText.ProcessingPatternDivideDescription),
            amountButton("x3", ProcessingPatternAmountHelper.Operation.MULTIPLY_3,
                GuiText.ProcessingPatternMultiplyDescription),
            amountButton("/5", ProcessingPatternAmountHelper.Operation.DIVIDE_5, GuiText.ProcessingPatternDivideDescription),
            amountButton("x5", ProcessingPatternAmountHelper.Operation.MULTIPLY_5,
                GuiText.ProcessingPatternMultiplyDescription)
        };
        for (int index = 0; index < this.amountButtons.length; index++) {
            widgets.add("processingAmountButton" + index, this.amountButtons[index]);
        }
    }

    @Override
    public void updateBeforeRender() {
        this.screen.repositionSlots(SlotSemantics.PROCESSING_INPUTS);
        this.screen.repositionSlots(SlotSemantics.PROCESSING_OUTPUTS);

        int scroll = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.container.getProcessingInputSlots().length; i++) {
            var slot = this.container.getProcessingInputSlots()[i];
            int effectiveRow = i / 3 - scroll;
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.yPos -= scroll * 18;
        }
        for (int i = 0; i < this.container.getProcessingOutputSlots().length; i++) {
            var slot = this.container.getProcessingOutputSlots()[i];
            int effectiveRow = i - scroll;
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.yPos -= scroll * 18;
        }

        this.cycleOutputBtn.setVisibility(this.visible && this.container.canCycleProcessingOutputs());
        updateTooltipVisibility();
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        BG.dest(bounds.x + this.position.x() - 1, bounds.y + this.position.y() + 1).blit();
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (!getMouseWheelArea().contains(mousePos.x(), mousePos.y())) {
            return false;
        }
        return this.scrollbar.onMouseWheel(mousePos, delta);
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return true;
    }

    private Rectangle getMouseWheelArea() {
        return new Rectangle(
            this.position.x() + MOUSE_WHEEL_AREA.x,
            this.position.y() + MOUSE_WHEEL_AREA.y,
            MOUSE_WHEEL_AREA.width,
            MOUSE_WHEEL_AREA.height);
    }

    private void updateTooltipVisibility() {
        int scroll = this.scrollbar.getCurrentScroll();
        this.widgets.setTooltipAreaEnabled("processing-primary-output",
            this.visible && scroll == 0 && isVisibleOutputSlotEmpty(0));
        this.widgets.setTooltipAreaEnabled("processing-optional-output1",
            this.visible && scroll > 0 && isVisibleOutputSlotEmpty(0));
        this.widgets.setTooltipAreaEnabled("processing-optional-output2",
            this.visible && isVisibleOutputSlotEmpty(1));
        this.widgets.setTooltipAreaEnabled("processing-optional-output3",
            this.visible && isVisibleOutputSlotEmpty(2));
    }

    private boolean isVisibleOutputSlotEmpty(int visibleIndex) {
        int slotIndex = this.scrollbar.getCurrentScroll() + visibleIndex;
        var slots = this.container.getProcessingOutputSlots();
        return slotIndex >= 0
            && slotIndex < slots.length
            && slots[slotIndex].getStack().isEmpty();
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_PROCESSING;
    }

    @Override
    public ITextComponent getTabTooltip() {
        return GuiText.ProcessingPattern.text();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        this.scrollbar.setVisible(visible);
        this.clearBtn.setVisibility(visible);
        this.cycleOutputBtn.setVisibility(visible && this.container.canCycleProcessingOutputs());
        for (ProcessingAmountButton button : this.amountButtons) {
            button.visible = visible;
            button.enabled = visible;
        }
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, !visible);
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, !visible);
        updateTooltipVisibility();
    }

    private ProcessingAmountButton amountButton(String label, ProcessingPatternAmountHelper.Operation operation,
                                                GuiText tooltip) {
        return new ProcessingAmountButton(label, operation, tooltip);
    }

    private final class ProcessingAmountButton extends GuiButton implements ITooltip {
        private static final float TEXT_SCALE = 0.5F;
        private final String label;
        private final ProcessingPatternAmountHelper.Operation operation;
        private final GuiText tooltip;

        private ProcessingAmountButton(String label, ProcessingPatternAmountHelper.Operation operation,
                                       GuiText tooltip) {
            super(0, 0, 0, 8, 8, label);
            this.label = label;
            this.operation = operation;
            this.tooltip = tooltip;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            Icon background = this.hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
            background.getBlitter()
                      .dest(this.x, this.y, this.width, this.height)
                      .zOffset(2)
                      .blit();

            int color = this.enabled ? 0xFFFFFFFF : 0xFF413F54;
            renderScaledText(minecraft.fontRenderer, this.label, color, this.hovered ? 1 : 0);
        }

        private void renderScaledText(FontRenderer fontRenderer, String text, int color, int yOffset) {
            float textWidth = fontRenderer.getStringWidth(text) * TEXT_SCALE;
            float textHeight = 8.0F * TEXT_SCALE;
            float textX = this.x + (this.width - textWidth) / 2.0F;
            float textY = this.y + (this.height - textHeight) / 2.0F - yOffset * 0.5F;

            GlStateManager.pushMatrix();
            GlStateManager.translate(textX, textY, 0);
            GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            fontRenderer.drawString(text, 0, 0, color, false);
            GlStateManager.popMatrix();
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            boolean releasedInside = this.enabled && this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            super.mouseReleased(mouseX, mouseY);
            if (releasedInside) {
                container.modifyProcessingPatternAmounts(this.operation);
            }
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            int amount = switch (this.operation) {
                case MULTIPLY_2, DIVIDE_2 -> 2;
                case MULTIPLY_3, DIVIDE_3 -> 3;
                case MULTIPLY_5, DIVIDE_5 -> 5;
            };
            return Collections.singletonList(this.tooltip.text(amount));
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
