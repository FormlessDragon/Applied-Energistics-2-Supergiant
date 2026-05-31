package ae2.client.gui.me.items;

import ae2.api.config.ActionItems;
import ae2.client.Point;
import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.container.SlotSemantics;
import ae2.core.localization.GuiText;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;

public class ProcessingEncodingPanel extends EncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);

    private final ActionButton clearBtn;
    private final ActionButton cycleOutputBtn;
    private final Scrollbar scrollbar;

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
        return this.scrollbar.onMouseWheel(mousePos, delta);
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
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, !visible);
        this.screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, !visible);
        updateTooltipVisibility();
    }
}
