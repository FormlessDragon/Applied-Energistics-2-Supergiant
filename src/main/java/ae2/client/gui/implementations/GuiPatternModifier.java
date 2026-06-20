package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.GridSelectionPopup;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.IconButton;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerPatternModifier;
import ae2.core.AppEng;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.helpers.patternmodifier.PatternModifierActions;
import ae2.helpers.patternmodifier.PatternModifierLogic;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GuiPatternModifier extends AEBaseGui<ContainerPatternModifier> {
    private static final int TOOL_HEIGHT = 212;
    private static final int TOOL_PLAYER_INVENTORY_TOP = 128;
    private static final int PAGE_MULTIPLY = 0;
    private static final int PAGE_REPLACE = 1;
    private static final int PAGE_PROPERTY = 2;
    private static final int PAGE_CLONE = 3;
    private static final ResourceLocation MULTIPLY_TEXTURE = AppEng.makeId("textures/guis/pattern_editor_1.png");
    private static final ResourceLocation CLONE_TEXTURE = AppEng.makeId("textures/guis/pattern_editor_2.png");
    private static final ResourceLocation REPLACE_TEXTURE = AppEng.makeId("textures/guis/pattern_editor_3.png");
    private static final GuiText MODE_MULTIPLY = GuiText.PatternModifierMultiply;
    private static final GuiText MODE_REPLACE = GuiText.PatternModifierReplace;
    private static final GuiText MODE_PATTERN = GuiText.PatternModifierPatternMode;
    private static final GuiText MODE_CLONE = GuiText.PatternModifierClone;

    private final ModeButton modeButton;
    private final PageButton previousProviderPageButton;
    private final PageButton nextProviderPageButton;
    private final AE2Button replaceButton;
    private final IconButton cloneButton;
    private final AE2Button clearButton;
    private final InventoryButton inventoryButton;
    private final AE2Button[] multiplyButtons;
    private final AE2Button[] propertyButtons;

    public GuiPatternModifier(ContainerPatternModifier container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        this.xSize = 176;
        this.ySize = TOOL_HEIGHT;

        this.modeButton = new ModeButton(this.container::nextPage);
        this.previousProviderPageButton = new PageButton(Icon.ARROW_LEFT,
            () -> this.container.setProviderPage(this.container.getProviderPage() - 1));
        this.nextProviderPageButton = new PageButton(Icon.ARROW_RIGHT,
            () -> this.container.setProviderPage(this.container.getProviderPage() + 1));
        this.replaceButton = new TooltipButton(0, 0, 46, 20, GuiText.PatternModifierReplaceButton.text(),
            this.container::replace, GuiText.PatternModifierReplace.text());
        this.cloneButton = new CloneButton(this.container::clonePattern);
        this.clearButton = new TooltipButton(0, 0, 36, 20, GuiText.PatternModifierClear.text(),
            this.container::clearPatterns, GuiText.PatternModifierClearDescription.text());
        this.inventoryButton = new InventoryButton(this.container::togglePatternInventory);
        this.multiplyButtons = createAmountButtons();
        this.propertyButtons = new AE2Button[]{
            propertyButton(PatternModifierLogic.CraftingProperty.SUBSTITUTE, true),
            propertyButton(PatternModifierLogic.CraftingProperty.SUBSTITUTE, false),
            propertyButton(PatternModifierLogic.CraftingProperty.FLUID_SUBSTITUTE, true),
            propertyButton(PatternModifierLogic.CraftingProperty.FLUID_SUBSTITUTE, false)
        };
        this.widgets.add("mode", this.modeButton);
        this.widgets.add("previousProviderPage", this.previousProviderPageButton);
        this.widgets.add("nextProviderPage", this.nextProviderPageButton);
        this.widgets.add("replace", this.replaceButton);
        this.widgets.add("clone", this.cloneButton);
        this.widgets.add("clear", this.clearButton);
        this.widgets.add("inventory", this.inventoryButton);
        for (int index = 0; index < this.multiplyButtons.length; index++) {
            this.widgets.add("amount_" + index, this.multiplyButtons[index]);
        }
        for (int index = 0; index < this.propertyButtons.length; index++) {
            this.widgets.add("property_" + index, this.propertyButtons[index]);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    private static GridSelectionPopup.Entry<Integer> modeEntry(int page, Icon icon, GuiText label) {
        return GridSelectionPopup.Entry.icon(page, icon, Collections.singletonList(label.text()));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == this.modeButton && isHandlingRightClick()) {
            openModeSelectionPopup();
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        bindTexture(getBackgroundTexture());
        drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, TOOL_HEIGHT);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.container.updateVisibleSlots();
        this.repositionPatternSlots();
        this.repositionPlayerInventorySlots();
        int page = this.container.getPage();
        boolean providerLayout = this.isProviderPatternLayout();
        this.inventoryButton.setProviderMode(this.container.isProviderInventoryMode());
        this.inventoryButton.setVisibility(this.container.isProviderInventoryAvailable());
        this.previousProviderPageButton.setVisibility(providerLayout
            && this.container.getProviderPageCount() > 1 && this.container.getProviderPage() > 0);
        this.nextProviderPageButton.setVisibility(providerLayout
            && this.container.getProviderPageCount() > 1
            && this.container.getProviderPage() + 1 < this.container.getProviderPageCount());
        this.replaceButton.visible = page == PAGE_REPLACE;
        this.replaceButton.enabled = page == PAGE_REPLACE;
        this.cloneButton.visible = page == PAGE_CLONE;
        this.cloneButton.enabled = page == PAGE_CLONE;
        this.clearButton.visible = page == PAGE_MULTIPLY;
        this.clearButton.enabled = page == PAGE_MULTIPLY;
        for (AE2Button button : this.multiplyButtons) {
            button.visible = page == PAGE_MULTIPLY;
            button.enabled = page == PAGE_MULTIPLY;
        }
        for (AE2Button button : this.propertyButtons) {
            button.visible = page == PAGE_PROPERTY;
            button.enabled = page == PAGE_PROPERTY;
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(GuiText.PatternModifier.getLocal(getModeName().getLocal()), 8, 6,
            0x404040);
        if (this.container.getPage() == PAGE_PROPERTY) {
            this.fontRenderer.drawString(GuiText.PatternModifierSubstitute.getLocal(), 8, 22, 0x404040);
            this.fontRenderer.drawString(GuiText.PatternModifierFluidSubstitute.getLocal(), 8, 44, 0x404040);
        } else if (this.container.getPage() == PAGE_CLONE) {
            this.fontRenderer.drawString(GuiText.PatternModifierTarget.getLocal(), 51, 25, 0x404040);
            this.fontRenderer.drawString(GuiText.PatternModifierBlank.getLocal(), 52, 62, 0x404040);
        }
    }

    private void repositionPatternSlots() {
        int activeSlots = this.container.getActivePatternSlots();
        int index = 0;
        for (var slot : this.container.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            if (index < activeSlots) {
                slot.xPos = 8 + index % 9 * 18;
                slot.yPos = 61 + index / 9 * 18;
            }
            index++;
        }
    }

    private void repositionPlayerInventorySlots() {
        int index = 0;
        for (Slot slot : this.container.getSlots(SlotSemantics.PLAYER_INVENTORY)) {
            slot.xPos = 8 + index % 9 * 18;
            slot.yPos = TOOL_PLAYER_INVENTORY_TOP + index / 9 * 18;
            index++;
        }
        index = 0;
        for (Slot slot : this.container.getSlots(SlotSemantics.PLAYER_HOTBAR)) {
            slot.xPos = 8 + index * 18;
            slot.yPos = TOOL_PLAYER_INVENTORY_TOP + 58;
            index++;
        }
    }

    private ResourceLocation getBackgroundTexture() {
        return switch (this.container.getPage()) {
            case PAGE_REPLACE -> REPLACE_TEXTURE;
            case PAGE_CLONE -> CLONE_TEXTURE;
            default -> MULTIPLY_TEXTURE;
        };
    }

    private boolean isProviderPatternLayout() {
        return this.container.isProviderInventoryMode()
            && this.container.isProviderInventoryAvailable()
            && (this.container.getPage() == PAGE_MULTIPLY
            || this.container.getPage() == PAGE_REPLACE
            || this.container.getPage() == PAGE_PROPERTY);
    }

    private GuiText getModeName() {
        return switch (this.container.getPage()) {
            case PAGE_REPLACE -> MODE_REPLACE;
            case PAGE_PROPERTY -> MODE_PATTERN;
            case PAGE_CLONE -> MODE_CLONE;
            default -> MODE_MULTIPLY;
        };
    }

    private void openModeSelectionPopup() {
        var entries = GridSelectionPopup.<Integer>entries();
        entries.add(modeEntry(PAGE_MULTIPLY, Icon.CRAFT_HAMMER, MODE_MULTIPLY));
        entries.add(modeEntry(PAGE_REPLACE, Icon.COPY_MODE_ON, MODE_REPLACE));
        entries.add(modeEntry(PAGE_PROPERTY, Icon.COG, MODE_PATTERN));
        entries.add(modeEntry(PAGE_CLONE, Icon.ARROW_RIGHT, MODE_CLONE));
        var bounds = getBounds(false);
        this.openSelectionPopup(GridSelectionPopup.forButton(this.modeButton, this.guiLeft, this.guiTop, bounds.width,
            bounds.height, entries, this.container::setPage));
    }

    private AE2Button amountButton(int factor, boolean divide) {
        String prefix = divide ? "÷" : "x";
        ITextComponent tooltip = divide
            ? GuiText.PatternModifierDivideDescription.text(factor)
            : GuiText.PatternModifierMultiplyDescription.text(factor);
        return new TooltipButton(0, 0, 23, 20, new TextComponentString(prefix + factor),
            () -> this.container.modifyAmounts(factor, divide), tooltip);
    }

    private AE2Button[] createAmountButtons() {
        AE2Button[] buttons = new AE2Button[PatternModifierActions.STANDALONE_FACTORS.length * 2];
        for (int index = 0; index < PatternModifierActions.STANDALONE_FACTORS.length; index++) {
            int factor = PatternModifierActions.STANDALONE_FACTORS[index];
            buttons[index] = amountButton(factor, false);
            buttons[index + PatternModifierActions.STANDALONE_FACTORS.length] = amountButton(factor, true);
        }
        return buttons;
    }

    private AE2Button propertyButton(PatternModifierLogic.CraftingProperty property, boolean value) {
        ITextComponent label = value ? GuiText.PatternModifierEnable.text() : GuiText.PatternModifierDisable.text();
        return new AE2Button(0, 0, 23, 20, label,
            () -> this.container.setCraftingProperty(property, value));
    }

    private static class ModeButton extends IconButton {
        ModeButton(Runnable onLeftClick) {
            super(onLeftClick);
            setMessage(GuiText.PatternModifierChangeMode.text());
        }

        @Override
        protected Icon getIcon() {
            return Icon.SCHEDULING_DEFAULT;
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return List.of(
                getMessageComponent(),
                Tooltips.muted(ButtonToolTips.CycleModeAction.text(Tooltips.getMouseButtonText(0))),
                Tooltips.muted(ButtonToolTips.SelectModeAction.text(Tooltips.getMouseButtonText(1))));
        }
    }

    private static class PageButton extends IconButton {
        private final Icon icon;

        PageButton(Icon icon, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            this.setMessage((icon == Icon.ARROW_LEFT
                ? GuiText.PatternProviderPagePrevious
                : GuiText.PatternProviderPageNext).text());
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }

    private static class InventoryButton extends IconButton {
        private boolean providerMode;

        InventoryButton(Runnable onPress) {
            super(onPress);
            setMessage(GuiText.PatternModifierInventorySwitch.text());
        }

        void setProviderMode(boolean providerMode) {
            this.providerMode = providerMode;
        }

        @Override
        protected Icon getIcon() {
            return this.providerMode ? Icon.PATTERN_ACCESS_SHOW : Icon.PATTERN_ACCESS_HIDE;
        }
    }

    private static class TooltipButton extends AE2Button implements ITooltip {
        private final ITextComponent tooltip;

        TooltipButton(int x, int y, int width, int height, ITextComponent component, Runnable onPress,
                      ITextComponent tooltip) {
            super(x, y, width, height, component, onPress);
            this.tooltip = tooltip;
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return Collections.singletonList(this.tooltip);
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

    private static final class CloneButton extends IconButton {
        private CloneButton(Runnable onPress) {
            super(onPress);
            setMessage(GuiText.PatternModifierCloneDescription.text());
        }

        @Override
        protected Icon getIcon() {
            return Icon.ARROW_RIGHT;
        }
    }
}
