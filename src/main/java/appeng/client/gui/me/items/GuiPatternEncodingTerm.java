package appeng.client.gui.me.items;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.GuiMEStorage;
import appeng.client.gui.style.GuiStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.TabButton;
import appeng.container.me.items.ContainerPatternEncodingTerm;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.Integrations;
import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class GuiPatternEncodingTerm extends GuiMEStorage<ContainerPatternEncodingTerm> {
    private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);
    private final SettingToggleButton<YesNo> autoFillPatternsButton;

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
                    setMessage(new TextComponentTranslation("gui.ae2.PatternImportPrioritiesTitle"));
                }

                @Override
                protected Icon getIcon() {
                    return Icon.PRIORITY;
                }
            });
        }
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
    }

    @Override
    public void onGuiClosed() {
        if (AEConfig.instance().isClearGridOnClose()) {
            this.container.clear();
        }
        super.onGuiClosed();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 2) {
            Slot slot = findSlot(mouseX, mouseY);
            if (this.container.canModifyAmountForSlot(slot)) {
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
}
