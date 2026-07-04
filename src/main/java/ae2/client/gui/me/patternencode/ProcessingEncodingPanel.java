package ae2.client.gui.me.patternencode;

import ae2.api.config.ActionItems;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SmallTextTooltipButton;
import ae2.container.SlotSemantics;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.core.localization.GuiText;
import ae2.parts.encoding.ProcessingPatternAmountHelper;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.List;

public class ProcessingEncodingPanel extends EncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);
    private static final Rectangle MOUSE_WHEEL_AREA = new Rectangle(-1, 1, 124, 66);

    private final ActionButton clearBtn;
    private final ActionButton cycleOutputBtn;
    private final ActionButton clearSecondaryOutputsBtn;
    private final Scrollbar scrollbar;
    private final SmallTextTooltipButton[] amountButtons;

    public ProcessingEncodingPanel(AEBaseGui<? extends ContainerPatternEncodingTerm> screen, WidgetContainer widgets) {
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

        this.clearSecondaryOutputsBtn = new ActionButton(ActionItems.S_CLEAR_PROCESSING_SECONDARY_OUTPUTS,
            this.container::clearProcessingSecondaryOutputs);
        this.clearSecondaryOutputsBtn.setHalfSize(true);
        this.clearSecondaryOutputsBtn.setDisableBackground(true);
        widgets.add("processingClearSecondaryOutputs", this.clearSecondaryOutputsBtn);

        this.scrollbar = widgets.addScrollBar("processingPatternModeScrollbar", Scrollbar.SMALL);
        this.scrollbar.setRange(0, Math.max(0, this.container.getProcessingInputSlots().length / 3 - 3), 3);
        this.scrollbar.setCaptureMouseWheel(false);

        this.amountButtons = new SmallTextTooltipButton[]{
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
        this.clearSecondaryOutputsBtn.setVisibility(this.visible && this.container.canCycleProcessingOutputs());
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
        this.clearSecondaryOutputsBtn.setVisibility(visible && this.container.canCycleProcessingOutputs());
        for (SmallTextTooltipButton button : this.amountButtons) {
            button.visible = visible;
            button.enabled = visible;
        }
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, !visible);
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, !visible);
        updateTooltipVisibility();
    }

    private SmallTextTooltipButton amountButton(String label, ProcessingPatternAmountHelper.Operation operation,
                                                GuiText tooltip) {
        return new SmallTextTooltipButton(8, 8, label,
            () -> List.of(tooltip.text(amountForOperation(operation))),
            () -> this.container.modifyProcessingPatternAmounts(operation),
            SmallTextTooltipButton.BackgroundStyle.TOOLBAR);
    }

    private static int amountForOperation(ProcessingPatternAmountHelper.Operation operation) {
        return switch (operation) {
            case MULTIPLY_2, DIVIDE_2 -> 2;
            case MULTIPLY_3, DIVIDE_3 -> 3;
            case MULTIPLY_5, DIVIDE_5 -> 5;
        };
    }
}
