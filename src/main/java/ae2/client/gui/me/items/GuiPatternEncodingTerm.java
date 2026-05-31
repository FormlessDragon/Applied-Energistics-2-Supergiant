package ae2.client.gui.me.items;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.config.ActionItems;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.PatternModifierPanelWidget;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.TabButton;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.core.AEConfig;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import ae2.integration.Integrations;
import ae2.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class GuiPatternEncodingTerm extends GuiMEStorage<ContainerPatternEncodingTerm> {
    private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);
    private final SettingToggleButton<YesNo> autoFillPatternsButton;
    private final PatternModifierPanelWidget patternModifierPanel;

    public GuiPatternEncodingTerm(ContainerPatternEncodingTerm container, InventoryPlayer playerInventory,
                                  @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory, resolveTitle(container, title), style);
        this.autoFillPatternsButton = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.PATTERN_AUTO_FILL, YesNo.NO));
        addMode(EncodingMode.CRAFTING, new CraftingEncodingPanel(this, widgets), 0);
        addMode(EncodingMode.PROCESSING, new ProcessingEncodingPanel(this, widgets), 1);
        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE, container::encode));
        if (Integrations.hei().isEnabled()) {
            addToLeftToolbar(new IconButton(this::openImportPrioritySettings) {
                {
                    setMessage(GuiText.PatternImportPrioritiesTitle.text());
                }

                @Override
                protected Icon getIcon() {
                    return Icon.PRIORITY;
                }
            });
        }
        this.patternModifierPanel = new PatternModifierPanelWidget(this, new EncodingTerminalPanelHost());
        this.patternModifierPanel.addButtons();
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
        this.autoFillPatternsButton.set(this.container.getAutoFillPatterns());
        for (var mode : EncodingMode.values()) {
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
        this.patternModifierPanel.update();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY, partialTicks);
        this.patternModifierPanel.drawBackground();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 2) {
            Slot slot = findSlot(mouseX, mouseY);
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
                itemTooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped).getFormattedText());
            }
            itemTooltip.add(Tooltips.getSetAmountTooltip().getFormattedText());
            drawTooltipLines(mouseX, mouseY, itemTooltip);
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
