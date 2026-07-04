package ae2.client.gui.me.patternencode;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.config.ActionItems;
import ae2.api.config.YesNo;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import ae2.client.gui.me.patternaccess.AbstractPatternAccessTerm;
import ae2.client.gui.me.patternaccess.GuiPatternSlot;
import ae2.client.gui.me.patternaccess.GuiProviderSelect;
import ae2.client.gui.me.common.GuiTerminalSettings;
import ae2.client.gui.me.common.GuiTerminalSettings.GeneralSetting;
import ae2.client.gui.me.common.RepoSlot;
import ae2.client.gui.me.items.GuiPatternImportPrioritySettings;
import ae2.client.gui.me.items.GuiPatternItemRenamer;
import ae2.client.gui.me.items.GuiSetProcessingPatternAmount;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.DynamicIconButton;
import ae2.client.gui.widgets.TabButton;
import ae2.container.SlotSemantics;
import ae2.container.me.patternencode.ContainerPEATerm;
import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.slot.AppEngSlot;
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.IProviderSelectPageReceiver;
import ae2.container.me.patternencode.ProviderMappingPage;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.Integrations;
import ae2.parts.encoding.EncodingMode;
import ae2.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuiPEATerm extends AbstractPatternAccessTerm<ContainerPEATerm>
    implements IProviderSelectPageReceiver {

    private static final ResourceLocation ACCESS_TEXTURE = AppEng.makeId("textures/guis/ex_pattern_access_terminal.png");
    private static final ResourceLocation ENCODING_TEXTURE = AppEng.makeId("textures/guis/ex_pattern.png");
    private static final String PROVIDER_SELECT_OVERLAY_WIDGET = GuiProviderSelect.WIDGET_ID;
    private static final int GUI_FOOTER_HEIGHT = 178;
    private static final int GUI_FOOTER_TEXTURE_Y = 73;
    private static final Set<GeneralSetting> PEAT_GENERAL_SETTINGS = Set.of(
        GeneralSetting.CLEAR_GRID_ON_CLOSE,
        GeneralSetting.PATTERN_AUTO_FILL);

    private static final EncodingMode[] ENCODING_MODES = EncodingMode.values();

    private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);
    private final DynamicIconButton uploadPatternButton;
    private final GuiProviderSelect<?> providerSelectOverlay;
    private int providerSelectOverlayRequestNonce;

    public GuiPEATerm(ContainerPEATerm container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                      GuiStyle style) {
        super(container, playerInventory, title, GuiText.PatternEncodingAccessTerminalShort.text(), style,
            "pattern encoding access terminal", GUI_FOOTER_HEIGHT);

        this.providerSelectOverlay = new GuiProviderSelect<>(this);
        addMode(EncodingMode.CRAFTING, new CraftingEncodingPanel(this, widgets), 0);
        addMode(EncodingMode.PROCESSING, new ProcessingEncodingPanel(this, widgets), 1);
        this.uploadPatternButton = new DynamicIconButton(
            () -> Icon.PATTERN_UPLOAD,
            ButtonToolTips.PatternUpload.text(),
            this::uploadPatternTooltip,
            this::uploadPattern);
        this.uploadPatternButton.setHalfSize(true);
        this.uploadPatternButton.setIconScale(0.5F);
        this.uploadPatternButton.setVisibility(false);
        widgets.add(PROVIDER_SELECT_OVERLAY_WIDGET, this.providerSelectOverlay);
        widgets.add("uploadPattern", this.uploadPatternButton);
        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE,
            () -> container.encode(isShiftDown())));
        if (Integrations.hei().isEnabled()) {
            addToLeftToolbar(new ActionButton(ActionItems.PATTERN_IMPORT_PRIORITIES,
                this::openImportPrioritySettings));
        }
        this.setSlotsHidden(SlotSemantics.VIEW_CELL, true);
    }

    private void addMode(EncodingMode mode, EncodingModePanel panel, int index) {
        TabButton tabButton = new TabButton(panel.getIcon(), panel.getTabTooltip(), () -> this.container.setMode(mode));
        tabButton.setStyle(TabButton.Style.HORIZONTAL);
        widgets.add("modePanel" + index, panel);
        widgets.add("modeTabButton" + index, tabButton);
        this.modePanels.put(mode, panel);
        this.modeTabButtons.put(mode, tabButton);
    }

    private List<ITextComponent> uploadPatternTooltip() {
        if (container.mode == EncodingMode.PROCESSING) {
            return List.of(
                ButtonToolTips.PatternUpload.text(),
                ButtonToolTips.PatternUploadProcessingHint.text(),
                ButtonToolTips.PatternUploadProcessingShiftHint.text());
        }
        return List.of(
            ButtonToolTips.PatternUpload.text(),
            ButtonToolTips.PatternUploadHint.text(),
            ButtonToolTips.PatternUploadShiftHint.text());
    }

    private void openImportPrioritySettings() {
        switchToScreen(new GuiPatternImportPrioritySettings<>(this));
    }

    private void uploadPattern() {
        this.container.uploadPattern(isShiftDown());
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    @Override
    protected void addSettingsButton() {
        this.addToLeftToolbar(new ActionButton(ActionItems.TERMINAL_SETTINGS,
            () -> switchToScreen(new GuiTerminalSettings(
                this,
                this.container,
                this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> host
                    ? host
                    : null,
                this.container.getHost().getMainContainerIcon(),
                () -> {
                },
                false,
                PEAT_GENERAL_SETTINGS))));
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

    @Override
    protected void blitPatternAccess(int x, int y, int u, int v, int width, int height) {
        bindTexture(ACCESS_TEXTURE);
        drawTexturedModalRect(x, y, u, v, width, height);
    }

    @Override
    protected void drawPatternAccessFooter(int x, int y) {
        bindTexture(ENCODING_TEXTURE);
        drawTexturedModalRect(x, y, 0, GUI_FOOTER_TEXTURE_Y, GUI_WIDTH, GUI_FOOTER_HEIGHT);
    }

    @Override
    protected void beforePatternAccessUpdate() {
        syncProviderSelectOverlayOpenRequest();
        for (EncodingMode mode : ENCODING_MODES) {
            boolean selected = this.container.getMode() == mode;
            TabButton tabButton = this.modeTabButtons.get(mode);
            EncodingModePanel panel = this.modePanels.get(mode);
            if (tabButton != null) {
                tabButton.setSelected(selected);
            }
            if (panel != null) {
                panel.setVisible(selected);
            }
        }
        this.uploadPatternButton.setVisibility(true);
    }

    private void syncProviderSelectOverlayOpenRequest() {
        int requestNonce = this.container.getProviderSelectOverlayRequestNonce();
        if (requestNonce == this.providerSelectOverlayRequestNonce) {
            return;
        }

        this.providerSelectOverlayRequestNonce = requestNonce;
        if (requestNonce == 0) {
            return;
        }

        this.providerSelectOverlay.open(
            this.container.getProviderSelectOverlaySearchText(),
            this.container.getProviderSelectOverlayMappingText());
    }

    @Override
    protected boolean handlePatternAccessExtraMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 2) {
            return false;
        }

        Slot slot = findSlot(mouseX, mouseY);
        if (isAltDown() && slot != null && this.container.isProcessingPatternItemSlot(slot)) {
            switchToScreen(new GuiPatternItemRenamer<>(this, slot,
                TextComponentItemStack.of(this.container.getHost().getMainContainerIcon())));
            return true;
        }
        if (slot != null && this.container.canModifyAmountForSlot(slot)) {
            GenericStack currentStack = GenericStack.fromItemStack(slot.getStack());
            if (currentStack != null) {
                switchToScreen(new GuiSetProcessingPatternAmount(this, currentStack,
                    newStack -> InitNetwork.sendToServer(new InventoryActionPacket(
                        this.container.windowId,
                        InventoryAction.SET_FILTER,
                        slot.slotNumber,
                        GenericStack.wrapInItemStack(newStack))),
                    TextComponentItemStack.of(this.container.getHost().getMainContainerIcon())));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isQuickMovePatternSource(Slot slot) {
        return PatternDetailsHelper.isEncodedPattern(slot.getStack())
            && (this.container.isPlayerSideSlot(slot) || isEncodedPatternSlot(slot));
    }

    @Override
    @Nullable
    protected Slot findPatternAccessSlot(int mouseX, int mouseY) {
        for (Slot slot : this.container.inventorySlots) {
            if (slot instanceof GuiPatternSlot patternSlot
                && isVisiblePatternSlot(patternSlot)
                && isHovering(slot, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    protected boolean isHiddenFromPatternAccessSlotLookup(Slot slot) {
        return slot instanceof RepoSlot && isProviderListSlot(slot);
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
    protected void drawSlot(Slot slot) {
        if (slot instanceof RepoSlot && isProviderListSlot(slot)) {
            return;
        }

        super.drawSlot(slot);
    }

    @Override
    protected void afterPatternAccessInitGui() {
        this.container.setClearOnClose(AEConfig.instance().isClearGridOnClose());
    }

    private boolean isEncodedPatternSlot(Slot slot) {
        return this.container.getSlots(SlotSemantics.ENCODED_PATTERN).contains(slot);
    }

    @Override
    public void receiveProviderDirectoryPage(ProviderDirectoryPage page) {
        this.providerSelectOverlay.receiveProviderDirectoryPage(page);
    }

    @Override
    public void receiveProviderMappingPage(ProviderMappingPage page) {
        this.providerSelectOverlay.receiveProviderMappingPage(page);
    }

}
