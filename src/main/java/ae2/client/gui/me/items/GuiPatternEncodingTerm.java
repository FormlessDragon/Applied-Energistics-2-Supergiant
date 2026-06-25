package ae2.client.gui.me.items;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.config.ActionItems;
import ae2.api.config.YesNo;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.PatternModifierPanelWidget;
import ae2.client.gui.widgets.TabButton;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.container.slot.AppEngSlot;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import ae2.integration.Integrations;
import ae2.parts.encoding.EncodingMode;
import ae2.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GuiPatternEncodingTerm extends GuiMEStorage<ContainerPatternEncodingTerm> {
    private static final EncodingMode[] ENCODING_MODES = EncodingMode.values();
    private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);
    private final PatternModifierPanelWidget patternModifierPanel;
    private final IconButton uploadPatternButton;

    public GuiPatternEncodingTerm(ContainerPatternEncodingTerm container, InventoryPlayer playerInventory,
                                  @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory, resolveTitle(container, title), style);
        addMode(EncodingMode.CRAFTING, new CraftingEncodingPanel(this, widgets), 0);
        addMode(EncodingMode.PROCESSING, new ProcessingEncodingPanel(this, widgets), 1);
        this.uploadPatternButton = new IconButton(this::uploadPattern) {
            {
                setHalfSize(true);
                setIconScale(0.5F);
                setMessage(ButtonToolTips.PatternUpload.text());
                setVisibility(false);
            }

            @Override
            protected Icon getIcon() {
                return Icon.PATTERN_UPLOAD;
            }

            @Override
            public List<ITextComponent> getTooltipMessage() {
                return Arrays.asList(
                    ButtonToolTips.PatternUpload.text(),
                    ButtonToolTips.PatternUploadHint.text(),
                    ButtonToolTips.PatternUploadShiftHint.text());
            }
        };
        widgets.add("uploadPattern", this.uploadPatternButton);
        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE,
            () -> container.encode(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))));
        if (Integrations.hei().isEnabled()) {
            addToLeftToolbar(new ActionButton(ActionItems.PATTERN_IMPORT_PRIORITIES,
                this::openImportPrioritySettings));
        }
        this.patternModifierPanel = new PatternModifierPanelWidget(this, new EncodingTerminalPanelHost());
        this.patternModifierPanel.addButtons();
        addToLeftToolbar(this.patternModifierPanel.getToolbarButton());
    }

    private static ITextComponent resolveTitle(ContainerPatternEncodingTerm container, @Nullable ITextComponent title) {
        if (title != null) {
            return title;
        }
        if (container.getGuiTitle() != null) {
            return container.getGuiTitle();
        }
        return new TextComponentString("");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.container.setClearOnClose(AEConfig.instance().isClearGridOnClose());
    }

    private void openImportPrioritySettings() {
        switchToScreen(new GuiPatternImportPrioritySettings(this));
    }

    private void addMode(EncodingMode mode, EncodingModePanel panel, int index) {
        TabButton tabButton = new TabButton(panel.getIcon(), panel.getTabTooltip(), () -> this.container.setMode(mode));
        tabButton.setStyle(TabButton.Style.HORIZONTAL);
        widgets.add("modePanel" + index, panel);
        widgets.add("modeTabButton" + index, tabButton);
        this.modePanels.put(mode, panel);
        this.modeTabButtons.put(mode, tabButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        for (var mode : ENCODING_MODES) {
            boolean selected = this.container.getMode() == mode;
            var tabButton = this.modeTabButtons.get(mode);
            var panel = this.modePanels.get(mode);
            if (tabButton != null) {
                tabButton.setSelected(selected);
            }
            if (panel != null) {
                panel.setVisible(selected);
            }
        }
        updateUploadPatternButton();
        this.patternModifierPanel.update();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY, partialTicks);
        this.patternModifierPanel.drawBackground();
    }

    @Override
    @Nullable
    protected String getSlotAmountText(Slot slot, AppEngSlot appEngSlot, ItemStack rawStack, ItemStack displayStack) {
        if (this.container.isBlankPatternSlot(slot)
            && this.container.getAutoFillPatterns() == YesNo.YES
            && AEItems.BLANK_PATTERN.is(rawStack)) {
            long total = this.container.getSyncedNetworkBlankPatternCount() + rawStack.getCount();
            if (total <= 1) {
                return null;
            }

            AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack(1));
            if (blankPatternKey == null) {
                return Long.toString(total);
            }
            return blankPatternKey.formatAmount(total, AmountFormat.SLOT);
        }

        return super.getSlotAmountText(slot, appEngSlot, rawStack, displayStack);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    private void uploadPattern() {
        if (this.container.getMode() != EncodingMode.CRAFTING) {
            return;
        }
        this.container.uploadPattern(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
    }

    private void updateUploadPatternButton() {
        boolean visible = this.container.getMode() == EncodingMode.CRAFTING;
        this.uploadPatternButton.setVisibility(visible);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        if (mouseButton == 2) {
            Slot slot = findSlot(mouseX, mouseY);
            if (isAltDown() && slot != null && this.container.isProcessingPatternItemSlot(slot)) {
                switchToScreen(new GuiPatternItemRenamer(this, slot,
                    TextComponentItemStack.of(this.container.getHost().getMainContainerIcon())));
                return;
            }
            if (slot != null && this.container.canModifyAmountForSlot(slot)) {
                GenericStack currentStack = GenericStack.fromItemStack(slot.getStack());
                if (currentStack != null) {
                    switchToScreen(new GuiSetProcessingPatternAmount(this, currentStack,
                        newStack -> InitNetwork.sendToServer(new InventoryActionPacket(
                            this.container.windowId,
                            InventoryAction.SET_FILTER,
                            slot.slotNumber,
                            GenericStack.wrapInItemStack(newStack)))));
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    @Override
    protected EmptyingAction getEmptyingAction(@Nullable Slot slot, ItemStack carried) {
        if (this.container.isProcessingPatternSlot(slot)) {
            EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptyingAction != null) {
                return emptyingAction;
            }
        }

        return super.getEmptyingAction(slot, carried);
    }

    @Override
    protected void handleMouseClick(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (mouseButton == 2 && this.container.canModifyAmountForSlot(slot)) {
            return;
        }

        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = getSlotUnderMouse();
        if (this.playerInventory.getItemStack().isEmpty() && this.container.canModifyAmountForSlot(slot)) {
            assert slot != null;
            var itemTooltip = new ObjectArrayList<>(getItemToolTip(slot.getStack()));
            GenericStack unwrapped = GenericStack.fromItemStack(slot.getStack());
            if (unwrapped != null) {
                itemTooltip.add(Tooltips.getAmountTooltipLocal(ButtonToolTips.Amount, unwrapped));
            }
            itemTooltip.add(Tooltips.getSetAmountTooltipLocal());
            if (this.container.isProcessingPatternItemSlot(slot)) {
                itemTooltip.add(Tooltips.getRenameTooltipLocal());
            }
            drawItemTooltipWithImages(mouseX, mouseY, slot.getStack(), itemTooltip);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private final class EncodingTerminalPanelHost implements PatternModifierPanelWidget.PanelHost {
        @Override
        public boolean isPatternModifierPanelAvailable() {
            return container.isPatternModifierPanelAvailable();
        }

        @Override
        public ae2.client.Point getPatternModifierPanelOffset() {
            return new ae2.client.Point(
                ae2.helpers.patternmodifier.PatternModifierToolboxLayout.PANEL_LEFT_OFFSET - 4,
                ae2.helpers.patternmodifier.PatternModifierToolboxLayout.PANEL_TOP_OFFSET - 50);
        }

        @Override
        public void updatePatternModifierPanelVisibleSlots(boolean visible) {
            container.updatePatternModifierPanelVisibleSlots(visible);
        }

        @Override
        public void clearPatternModifierPanel() {
            container.getPatternModifierPanel().clearPatterns();
        }

        @Override
        public void modifyPatternModifierPanelAmounts(int factor, boolean divide) {
            container.getPatternModifierPanel().modifyAmounts(factor, divide);
        }
    }
}
