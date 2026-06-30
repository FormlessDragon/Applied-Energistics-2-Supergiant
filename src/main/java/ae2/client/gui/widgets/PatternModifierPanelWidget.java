package ae2.client.gui.widgets;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Rects;
import ae2.client.gui.style.Blitter;
import ae2.container.implementations.PatternModifierPanel;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.helpers.patternmodifier.PatternModifierToolboxLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public final class PatternModifierPanelWidget implements ICompositeWidget {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/pattern_modifier_toolbox.png");

    private final AEBaseGui<?> gui;
    private final PanelHost host;
    private final AE2Button[] amountButtons;
    private final IconButton clearButton;
    private final ItemStackButton toggleButton;
    private boolean expanded;

    public PatternModifierPanelWidget(AEBaseGui<?> gui, PanelHost host) {
        this.gui = gui;
        this.host = host;
        this.amountButtons = new AE2Button[]{
            amountButton(2, false),
            amountButton(2, true),
            amountButton(3, false),
            amountButton(3, true),
            amountButton(5, false),
            amountButton(5, true)
        };
        this.clearButton = new ClearButton();
        this.toggleButton = new ToggleButton();
    }

    @Override
    public boolean isVisible() {
        return this.expanded && this.host.isPatternModifierPanelAvailable();
    }

    @Override
    public void setPosition(Point position) {
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        if (!isVisible()) {
            return Rects.ZERO;
        }
        Point panelOffset = getPanelOffset();
        return new Rectangle(
            panelOffset.x(),
            panelOffset.y(),
            PatternModifierToolboxLayout.PANEL_WIDTH,
            PatternModifierToolboxLayout.PANEL_HEIGHT);
    }

    @Override
    public boolean hitTest(Point mousePos) {
        return mousePos.isIn(getBounds()) && !isHoveringPanelSlot(mousePos.x(), mousePos.y());
    }

    @Override
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        if (isVisible()) {
            Point panelOffset = getPanelOffset();
            exclusionZones.add(Rects.expand(new Rectangle(
                screenBounds.x + panelOffset.x(),
                screenBounds.y + panelOffset.y(),
                PatternModifierToolboxLayout.PANEL_WIDTH,
                PatternModifierToolboxLayout.PANEL_HEIGHT), 2));
        }
    }

    public void addButtons() {
        this.gui.getWidgets().add("patternModifierPanel", this);
        for (int index = 0; index < this.amountButtons.length; index++) {
            this.gui.getWidgets().add("patternModifierPanelAmount" + index, this.amountButtons[index]);
        }
        this.gui.getWidgets().add("patternModifierPanelClear", this.clearButton);
    }

    public GuiButton getToolbarButton() {
        return this.toggleButton;
    }

    public void update() {
        boolean available = this.host.isPatternModifierPanelAvailable();
        if (!available && this.expanded) {
            this.expanded = false;
            this.gui.requestExclusionZonesUpdate();
        }

        boolean visible = this.expanded && available;
        this.host.updatePatternModifierPanelVisibleSlots(visible);
        positionButtons();
        positionSlots(visible);
        for (AE2Button button : this.amountButtons) {
            button.visible = visible;
            button.enabled = visible;
        }
        this.clearButton.visible = visible;
        this.clearButton.enabled = visible;
        this.toggleButton.visible = available;
        this.toggleButton.enabled = available;
    }

    public void drawBackground() {
        if (!isVisible()) {
            return;
        }
        Point panelOffset = getPanelOffset();
        Blitter.texture(TEXTURE, 256, 256)
               .src(0, 0, PatternModifierToolboxLayout.PANEL_WIDTH, PatternModifierToolboxLayout.PANEL_HEIGHT)
               .dest(this.gui.getGuiLeft() + panelOffset.x(),
                   this.gui.getGuiTop() + panelOffset.y(),
                   PatternModifierToolboxLayout.PANEL_WIDTH, PatternModifierToolboxLayout.PANEL_HEIGHT)
               .blit();
    }

    private void positionButtons() {
        Point panelOffset = getPanelOffset();
        for (int index = 0; index < this.amountButtons.length; index++) {
            this.amountButtons[index].x = this.gui.getGuiLeft()
                + PatternModifierToolboxLayout.getButtonX(index)
                + panelOffset.x()
                - PatternModifierToolboxLayout.PANEL_LEFT_OFFSET;
            this.amountButtons[index].y = this.gui.getGuiTop()
                + PatternModifierToolboxLayout.getButtonY(index)
                + panelOffset.y()
                - PatternModifierToolboxLayout.PANEL_TOP_OFFSET;
        }
        this.clearButton.x = this.gui.getGuiLeft()
            + PatternModifierToolboxLayout.getButtonX(this.amountButtons.length)
            + panelOffset.x()
            - PatternModifierToolboxLayout.PANEL_LEFT_OFFSET
            + 1;
        this.clearButton.y = this.gui.getGuiTop()
            + PatternModifierToolboxLayout.getButtonY(this.amountButtons.length)
            + panelOffset.y()
            - PatternModifierToolboxLayout.PANEL_TOP_OFFSET;
    }

    private void positionSlots(boolean visible) {
        Point panelOffset = getPanelOffset();
        int index = 0;
        for (var slot : this.gui.getContainer().getSlots(PatternModifierPanel.PATTERN_MODIFIER_PANEL)) {
            if (visible) {
                slot.xPos = PatternModifierToolboxLayout.getSlotX(index)
                    + panelOffset.x()
                    - PatternModifierToolboxLayout.PANEL_LEFT_OFFSET;
                slot.yPos = PatternModifierToolboxLayout.getSlotY(index)
                    + panelOffset.y()
                    - PatternModifierToolboxLayout.PANEL_TOP_OFFSET;
            }
            index++;
        }
    }

    private Point getPanelOffset() {
        return this.host.getPatternModifierPanelOffset();
    }

    private void toggleExpanded() {
        if (!this.host.isPatternModifierPanelAvailable()) {
            if (this.expanded) {
                this.expanded = false;
                this.gui.requestExclusionZonesUpdate();
            }
            return;
        }
        this.expanded = !this.expanded;
        this.gui.requestExclusionZonesUpdate();
    }

    private AE2Button amountButton(int factor, boolean divide) {
        return new TooltipAmountButton(factor, divide);
    }

    private boolean isHoveringPanelSlot(int mouseX, int mouseY) {
        for (Slot slot : this.gui.getContainer().getSlots(PatternModifierPanel.PATTERN_MODIFIER_PANEL)) {
            if (mouseX >= slot.xPos && mouseX < slot.xPos + 18
                && mouseY >= slot.yPos && mouseY < slot.yPos + 18) {
                return true;
            }
        }

        return false;
    }

    public interface PanelHost {
        boolean isPatternModifierPanelAvailable();

        default Point getPatternModifierPanelOffset() {
            return new Point(
                PatternModifierToolboxLayout.PANEL_LEFT_OFFSET,
                PatternModifierToolboxLayout.PANEL_TOP_OFFSET);
        }

        void updatePatternModifierPanelVisibleSlots(boolean visible);

        void clearPatternModifierPanel();

        void modifyPatternModifierPanelAmounts(int factor, boolean divide);
    }

    private final class TooltipAmountButton extends AE2Button implements ITooltip {
        private final int factor;
        private final boolean divide;

        private TooltipAmountButton(int factor, boolean divide) {
            super(0, 0, PatternModifierToolboxLayout.BUTTON_WIDTH, PatternModifierToolboxLayout.BUTTON_HEIGHT,
                new TextComponentString((divide ? "/" : "x") + factor), () -> {
                });
            this.factor = factor;
            this.divide = divide;
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
                host.modifyPatternModifierPanelAmounts(this.factor, this.divide);
            }
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            setMessage(getMessageComponent());
            try {
                super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            } finally {
                GlStateManager.disableDepth();
            }
        }

        @Override
        public ITextComponent getMessageComponent() {
            return new TextComponentString((this.divide ? "/" : "x") + this.factor);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return Collections.singletonList(this.divide
                ? GuiText.PatternModifierDivideDescription.text(this.factor)
                : GuiText.PatternModifierMultiplyDescription.text(this.factor));
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

    private final class ClearButton extends IconButton implements ITooltip {
        private ClearButton() {
            super(host::clearPatternModifierPanel);
            this.width = PatternModifierToolboxLayout.BUTTON_WIDTH;
            this.height = PatternModifierToolboxLayout.BUTTON_HEIGHT;
            setDisableClickSound(false);
        }

        @Override
        protected Icon getIcon() {
            return Icon.CLEAR;
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return Collections.singletonList(GuiText.PatternModifierClearDescription.text());
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

    private final class ToggleButton extends ItemStackButton {
        private ToggleButton() {
            super(() -> AEItems.PATTERN_MODIFIER.stack(1), GuiText.PatternModifierShowPanel.text(),
                PatternModifierPanelWidget.this::toggleExpanded);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return List.of(
                new TextComponentString(AEItems.PATTERN_MODIFIER.stack(1).getDisplayName()),
                PatternModifierPanelWidget.this.expanded
                    ? GuiText.PatternModifierHidePanel.text()
                    : GuiText.PatternModifierShowPanel.text());
        }
    }
}
