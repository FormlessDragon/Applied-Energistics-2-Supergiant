/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.me.common;

import ae2.api.config.ActionItems;
import ae2.api.config.PinDisplayMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AECheckbox;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.SimpleIconButton;
import ae2.client.gui.widgets.TabButton;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ConfigValueServerPacket;
import ae2.core.network.serverbound.SetRecursiveIngredientReserveAmountPacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.core.network.serverbound.WirelessTerminalSettingsPacket;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.abstraction.ItemListMod;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalMagnetMode;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.text.TextComponentItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GuiTerminalSettings extends AEBaseGui<AEBaseContainer> {

    public enum GeneralSetting {
        PINNED_ITEMS,
        CRAFTING_JOB_NOTIFICATIONS,
        CLEAR_GRID_ON_CLOSE,
        PATTERN_AUTO_FILL,
        RECURSIVE_INGREDIENT_RESERVE,
        PLAYER_PIN_ROWS,
        SEARCH
    }

    private static final long MAX_RECURSIVE_INGREDIENT_RESERVE = 1000;
    private final AEBaseGui<?> parent;
    private final Runnable beforeReturn;
    private final boolean wirelessOnly;
    private final boolean supportsPlayerPinRows;
    private final EnumSet<GeneralSetting> generalSettings;
    private final AECheckbox pinAutoCraftedItemsCheckbox;
    private final AECheckbox pinDisplaySortTopRadio;
    private final AECheckbox pinDisplayLockedGridRadio;
    private final AECheckbox notifyForFinishedCraftingJobsCheckbox;
    private final AECheckbox clearGridOnCloseCheckbox;
    @Nullable
    private final AECheckbox autoFillPatternsCheckbox;
    private final AECheckbox useInternalSearchRadio;
    private final AECheckbox useExternalSearchRadio;
    private final AECheckbox rememberCheckbox;
    private final AECheckbox autoFocusCheckbox;
    private final AECheckbox syncWithExternalCheckbox;
    private final AECheckbox clearExternalCheckbox;
    private final AETextField recursiveReserveField;
    private final AETextField playerPinRowsField;
    private final AE2Button previousPageButton;
    private final AE2Button nextPageButton;
    private final IconButton generalPageButton;
    private final IconButton wirelessPageButton;
    @Nullable
    private final WirelessTerminalGuiHost<?> wirelessHost;
    @Nullable
    private final AECheckbox pickBlockCheckbox;
    @Nullable
    private final AECheckbox craftIfMissingCheckbox;
    @Nullable
    private final AECheckbox restockCheckbox;
    @Nullable
    private final AECheckbox magnetCheckbox;
    @Nullable
    private final AECheckbox pickupToMECheckbox;
    @Nullable
    private final AE2Button magnetSettingsButton;
    private Page page = Page.GENERAL;
    private int generalPage;
    private long displayedRecursiveReserveAmount = -1;
    private boolean recursiveReserveFieldFocused;
    private boolean playerPinRowsDirty;
    private boolean closing;
    private int pendingPlayerPinRows;

    public GuiTerminalSettings(GuiMEStorage<? extends ContainerMEStorage> parent) {
        this(parent, parent.getContainer(),
            parent.getContainer().getHost() instanceof WirelessTerminalGuiHost<?> host ? host : null,
            parent.getContainer().getHost().getMainContainerIcon(),
            parent::onCloseTerminalSettings,
            false);
    }

    public GuiTerminalSettings(AEBaseGui<?> parent, AEBaseContainer container,
                               @Nullable WirelessTerminalGuiHost<?> wirelessHost,
                               ItemStack parentIcon, Runnable beforeReturn, boolean wirelessOnly) {
        this(parent, container, wirelessHost, parentIcon, beforeReturn, wirelessOnly,
            EnumSet.allOf(GeneralSetting.class));
    }

    public GuiTerminalSettings(AEBaseGui<?> parent, AEBaseContainer container,
                               @Nullable WirelessTerminalGuiHost<?> wirelessHost,
                               ItemStack parentIcon, Runnable beforeReturn, boolean wirelessOnly,
                               Set<GeneralSetting> generalSettings) {
        super(container, container.getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/terminals/terminal_settings.json"));
        this.parent = parent;
        this.beforeReturn = beforeReturn;
        this.wirelessOnly = wirelessOnly;
        this.wirelessHost = wirelessHost;
        this.supportsPlayerPinRows = parent instanceof GuiMEStorage<?>;
        this.generalSettings = copyGeneralSettings(generalSettings);

        ITextComponent externalSearchName = new TextComponentString(
            ItemListMod.isEnabled() ? ItemListMod.getShortName() : "HEI");

        widgets.add("back", new TabButton(
            Icon.BACK,
            TextComponentItemStack.of(parentIcon),
            this::returnToParent));
        this.generalPageButton = addToLeftToolbar(
            new ActionButton(ActionItems.TERMINAL_SETTINGS, () -> switchPage(Page.GENERAL)));
        this.wirelessPageButton = new SimpleIconButton(Icon.WIRELESS_SETTINGS_PAGE,
            GuiText.WirelessTerminalSettingsTitle.text(), () -> switchPage(Page.WIRELESS));
        addToLeftToolbar(this.wirelessPageButton);
        this.previousPageButton = widgets.addButton("previousPage", new TextComponentString("<"), this::previousGeneralPage);
        this.nextPageButton = widgets.addButton("nextPage", new TextComponentString(">"), this::nextGeneralPage);

        this.pinAutoCraftedItemsCheckbox = widgets.addCheckbox("pinAutoCraftedItemsCheckbox",
            GuiText.TerminalSettingsPinAutoCraftedItems.text(), this::save);
        this.pinDisplaySortTopRadio = widgets.addCheckbox("pinDisplaySortTopRadio",
            GuiText.TerminalSettingsPinDisplaySortTop.text(), this::switchToSortTopPins);
        this.pinDisplaySortTopRadio.setRadio(true);
        this.pinDisplaySortTopRadio.setTooltipMessage(List.of(GuiText.TerminalSettingsPinDisplaySortTopTooltip.text()));
        this.pinDisplayLockedGridRadio = widgets.addCheckbox("pinDisplayLockedGridRadio",
            GuiText.TerminalSettingsPinDisplayLockedGrid.text(), this::switchToLockedGridPins);
        this.pinDisplayLockedGridRadio.setRadio(true);
        this.pinDisplayLockedGridRadio.setTooltipMessage(List.of(
            GuiText.TerminalSettingsPinDisplayLockedGridTooltip.text()));
        this.notifyForFinishedCraftingJobsCheckbox = widgets.addCheckbox("notifyForFinishedCraftingJobsCheckbox",
            GuiText.TerminalSettingsNotifyForFinishedJobs.text(), this::save);
        this.clearGridOnCloseCheckbox = widgets.addCheckbox("clearGridOnCloseCheckbox",
            GuiText.TerminalSettingsClearGridOnClose.text(), this::save);
        if (this.container instanceof ContainerPatternEncodingTerm) {
            this.autoFillPatternsCheckbox = widgets.addCheckbox("autoFillPatternsCheckbox",
                GuiText.TerminalSettingsAutoFillBlankPatterns.text(), this::savePatternAutoFill);
        } else {
            this.autoFillPatternsCheckbox = null;
        }
        this.useInternalSearchRadio = widgets.addCheckbox("useInternalSearchRadio",
            GuiText.SearchSettingsUseInternalSearch.text(), this::switchToAeSearch);
        this.useInternalSearchRadio.setRadio(true);
        this.useExternalSearchRadio = widgets.addCheckbox("useExternalSearchRadio",
            GuiText.SearchSettingsUseExternalSearch.text(externalSearchName), this::switchToExternalSearch);
        this.useExternalSearchRadio.setRadio(true);
        this.rememberCheckbox = widgets.addCheckbox("rememberCheckbox", GuiText.SearchSettingsRememberSearch.text(),
            this::save);
        this.autoFocusCheckbox = widgets.addCheckbox("autoFocusCheckbox", GuiText.SearchSettingsAutoFocus.text(),
            this::save);
        this.syncWithExternalCheckbox = widgets.addCheckbox("syncWithExternalCheckbox",
            GuiText.SearchSettingsSyncWithExternal.text(externalSearchName), this::save);
        this.clearExternalCheckbox = widgets.addCheckbox("clearExternalCheckbox",
            GuiText.SearchSettingsClearExternal.text(externalSearchName), this::save);
        this.recursiveReserveField = widgets.addTextField("recursiveReserveField");
        this.recursiveReserveField.setMaxStringLength(4);
        this.recursiveReserveField.setKeyFilter(this::isRecursiveReserveInput);
        this.recursiveReserveField.setResponder(ignored -> sanitizeRecursiveReserveField());
        this.playerPinRowsField = widgets.addTextField("playerPinRowsField");
        this.playerPinRowsField.setMaxStringLength(2);
        this.playerPinRowsField.setKeyFilter(this::isPlayerPinRowsInput);
        this.playerPinRowsField.setResponder(ignored -> sanitizePlayerPinRowsField());
        if (this.wirelessHost != null) {
            this.pickBlockCheckbox = widgets.addCheckbox("pickBlockCheckbox",
                GuiText.WirelessTerminalSettingsPickBlock.text(), this::save);
            this.craftIfMissingCheckbox = widgets.addCheckbox("craftIfMissingCheckbox",
                GuiText.WirelessTerminalSettingsCraftIfMissing.text(), this::save);
            this.restockCheckbox = widgets.addCheckbox("restockCheckbox",
                GuiText.WirelessTerminalSettingsRestock.text(), this::save);
            this.magnetCheckbox = widgets.addCheckbox("magnetCheckbox",
                GuiText.WirelessTerminalSettingsMagnet.text(), this::save);
            this.pickupToMECheckbox = widgets.addCheckbox("pickupToMECheckbox",
                GuiText.WirelessTerminalSettingsPickupToME.text(), this::save);
            this.magnetSettingsButton = new AE2Button(GuiText.WirelessMagnetConfigure.text(), this::openMagnetSettings);
            widgets.add("magnetSettingsButton", this.magnetSettingsButton);
        } else {
            this.pickBlockCheckbox = null;
            this.craftIfMissingCheckbox = null;
            this.restockCheckbox = null;
            this.magnetCheckbox = null;
            this.pickupToMECheckbox = null;
            this.magnetSettingsButton = null;
        }

        setTextContent(TEXT_ID_DIALOG_TITLE,
            wirelessOnly ? GuiText.WirelessTerminalSettingsTitle.text() : GuiText.TerminalSettingsTitle.text());
        setTextContent("search_settings_title", GuiText.SearchSettingsTitle.text());
        setTextContent("recursive_reserve_label", GuiText.TerminalSettingsRecursiveIngredientReserve.text());
        setTextContent("player_pin_rows_label", GuiText.TerminalSettingsPlayerPinRows.text());
        setTextContent("wireless_settings_title", GuiText.WirelessTerminalSettingsTitle.text());
        this.pendingPlayerPinRows = PinnedKeys.getPlayerPinRows();
        if (wirelessOnly) {
            this.page = Page.WIRELESS;
        } else {
            this.wirelessPageButton.visible = this.wirelessHost != null;
            if (this.wirelessHost == null) {
                this.page = Page.GENERAL;
            }
        }
        updateState();
    }

    private static EnumSet<GeneralSetting> copyGeneralSettings(Set<GeneralSetting> generalSettings) {
        Set<GeneralSetting> checkedSettings = Objects.requireNonNull(generalSettings, "generalSettings");
        if (checkedSettings.isEmpty()) {
            throw new IllegalArgumentException("generalSettings must not be empty");
        }
        return EnumSet.copyOf(checkedSettings);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateState();
        submitRecursiveReserveOnFocusLoss();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.playerPinRowsField.isFocused() && handlePlayerPinRowsKeyTyped(typedChar, keyCode)) {
            return;
        }
        if ((this.recursiveReserveField.isFocused() || this.playerPinRowsField.isFocused())
            && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            if (this.recursiveReserveField.isFocused()) {
                submitRecursiveReserveField();
            } else {
                sanitizePlayerPinRowsField();
                this.playerPinRowsField.setFocused(false);
            }
            this.recursiveReserveField.setFocused(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean handlePlayerPinRowsKeyTyped(char typedChar, int keyCode) {
        if (Character.isDigit(typedChar)) {
            this.playerPinRowsField.setText(Character.toString(typedChar));
            sanitizePlayerPinRowsField();
            this.playerPinRowsField.setCursorPositionEnd();
            this.playerPinRowsField.setSelectionPos(this.playerPinRowsField.getCursorPosition());
            return true;
        }

        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            this.playerPinRowsField.setText("0");
            sanitizePlayerPinRowsField();
            this.playerPinRowsField.setCursorPositionEnd();
            this.playerPinRowsField.setSelectionPos(this.playerPinRowsField.getCursorPosition());
            return true;
        }

        return false;
    }

    private void returnToParent() {
        commitPendingPlayerPinRowsOnce();
        this.beforeReturn.run();
        switchToScreen(parent);
        parent.returnFromSubScreen(this);
    }

    private void switchPage(Page page) {
        if (page == Page.WIRELESS && this.wirelessHost == null) {
            return;
        }
        this.page = page;
        updateState();
    }

    private void previousGeneralPage() {
        if (this.generalPage > 0) {
            this.generalPage--;
            updateVisibility();
        }
    }

    private void nextGeneralPage() {
        if (this.generalPage + 1 < getGeneralPageCount()) {
            this.generalPage++;
            updateVisibility();
        }
    }

    private void switchToAeSearch() {
        this.useInternalSearchRadio.setSelected(true);
        this.useExternalSearchRadio.setSelected(false);
        save();
    }

    private void switchToExternalSearch() {
        if (!hasExternalSearch()) {
            this.useInternalSearchRadio.setSelected(true);
            this.useExternalSearchRadio.setSelected(false);
            updateState();
            return;
        }
        this.useInternalSearchRadio.setSelected(false);
        this.useExternalSearchRadio.setSelected(true);
        save();
    }

    private void switchToSortTopPins() {
        this.pinDisplaySortTopRadio.setSelected(true);
        this.pinDisplayLockedGridRadio.setSelected(false);
        save();
    }

    private void switchToLockedGridPins() {
        this.pinDisplaySortTopRadio.setSelected(false);
        this.pinDisplayLockedGridRadio.setSelected(true);
        save();
    }

    private void openMagnetSettings() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.WIRELESS_MAGNET));
    }

    private void save() {
        AEConfig config = AEConfig.instance();
        config.setPinAutoCraftedItems(pinAutoCraftedItemsCheckbox.isSelected());
        config.setPinDisplayMode(this.pinDisplayLockedGridRadio.isSelected()
            ? PinDisplayMode.LOCKED_GRID
            : PinDisplayMode.SORT_TOP);
        config.setNotifyForFinishedCraftingJobs(notifyForFinishedCraftingJobsCheckbox.isSelected());
        config.setClearGridOnClose(clearGridOnCloseCheckbox.isSelected());
        syncClearGridOnClose();
        config.setUseExternalSearch(hasExternalSearch() && this.useExternalSearchRadio.isSelected());
        config.setRememberLastSearch(this.rememberCheckbox.isSelected());
        config.setAutoFocusSearch(this.autoFocusCheckbox.isSelected());
        config.setSyncWithExternalSearch(this.syncWithExternalCheckbox.isSelected());
        config.setClearExternalSearchOnOpen(this.clearExternalCheckbox.isSelected());
        saveWirelessSettings();
        updateVisibility();
    }

    private void syncClearGridOnClose() {
        if (this.container instanceof ContainerCraftingTerm craftingTerm) {
            craftingTerm.setClearGridOnClose(this.clearGridOnCloseCheckbox.isSelected());
        } else if (this.container instanceof ContainerPatternEncodingTerm patternEncodingTerm) {
            patternEncodingTerm.setClearOnClose(this.clearGridOnCloseCheckbox.isSelected());
        }
    }

    private void saveWirelessSettings() {
        if (this.wirelessHost == null || this.pickBlockCheckbox == null || this.craftIfMissingCheckbox == null
            || this.restockCheckbox == null || this.magnetCheckbox == null || this.pickupToMECheckbox == null
            || this.magnetSettingsButton == null) {
            return;
        }

        WirelessTerminalItem terminal = this.wirelessHost.getTerminalItem();
        var stack = this.wirelessHost.getItemStack();
        WirelessTerminals.setPickBlockEnabled(stack, terminal, this.pickBlockCheckbox.isSelected());
        WirelessTerminals.setCraftIfMissingEnabled(stack, terminal, this.craftIfMissingCheckbox.isSelected());
        WirelessTerminals.setRestockEnabled(stack, terminal, this.restockCheckbox.isSelected());
        WirelessTerminals.setMagnetMode(stack, terminal,
            WirelessTerminalMagnetMode.from(this.magnetCheckbox.isSelected(), this.pickupToMECheckbox.isSelected()));
        InitNetwork.sendToServer(new WirelessTerminalSettingsPacket(
            this.container.windowId,
            this.pickBlockCheckbox.isSelected(),
            this.craftIfMissingCheckbox.isSelected(),
            this.restockCheckbox.isSelected(),
            this.magnetCheckbox.isSelected(),
            this.pickupToMECheckbox.isSelected()));
    }

    private void updateState() {
        AEConfig config = AEConfig.instance();
        updateRecursiveReserveFromContainer();
        if (this.supportsPlayerPinRows && !this.playerPinRowsField.isFocused()) {
            this.playerPinRowsField.setText(Integer.toString(this.pendingPlayerPinRows));
        }
        this.playerPinRowsField.setTooltipMessage(List.of(GuiText.TerminalSettingsPlayerPinRowsTooltip.text()));
        pinAutoCraftedItemsCheckbox.setSelected(config.isPinAutoCraftedItems());
        PinDisplayMode pinDisplayMode = config.getPinDisplayMode();
        pinDisplaySortTopRadio.setSelected(pinDisplayMode == PinDisplayMode.SORT_TOP);
        pinDisplayLockedGridRadio.setSelected(pinDisplayMode == PinDisplayMode.LOCKED_GRID);
        notifyForFinishedCraftingJobsCheckbox.setSelected(config.isNotifyForFinishedCraftingJobs());
        clearGridOnCloseCheckbox.setSelected(config.isClearGridOnClose());
        if (this.autoFillPatternsCheckbox != null && this.container instanceof ContainerPatternEncodingTerm patternEncodingTerm) {
            this.autoFillPatternsCheckbox.setSelected(patternEncodingTerm.getAutoFillPatterns() == YesNo.YES);
        }
        boolean hasExternalSearch = hasExternalSearch();
        if (!hasExternalSearch && config.isUseExternalSearch()) {
            config.setUseExternalSearch(false);
        }
        boolean useExternalSearch = hasExternalSearch && config.isUseExternalSearch();
        this.useInternalSearchRadio.setSelected(!useExternalSearch);
        this.useExternalSearchRadio.setSelected(useExternalSearch);
        this.useExternalSearchRadio.enabled = hasExternalSearch;
        this.rememberCheckbox.setSelected(config.isRememberLastSearch());
        this.autoFocusCheckbox.setSelected(config.isAutoFocusSearch());
        this.syncWithExternalCheckbox.setSelected(config.isSyncWithExternalSearch());
        this.syncWithExternalCheckbox.enabled = hasExternalSearch;
        this.clearExternalCheckbox.setSelected(config.isClearExternalSearchOnOpen());
        this.clearExternalCheckbox.enabled = hasExternalSearch;
        this.rememberCheckbox.visible = this.useInternalSearchRadio.isSelected();
        this.autoFocusCheckbox.visible = this.useInternalSearchRadio.isSelected();
        this.syncWithExternalCheckbox.visible = this.useInternalSearchRadio.isSelected();
        this.clearExternalCheckbox.visible = this.useExternalSearchRadio.isSelected();
        updateWirelessState();
        updateVisibility();
    }

    private void updateRecursiveReserveFromContainer() {
        if (!(this.container instanceof ContainerMEStorage meStorage)) {
            return;
        }

        long amount = meStorage.getRecursiveIngredientReserveAmount();
        if (amount == this.displayedRecursiveReserveAmount) {
            return;
        }

        this.displayedRecursiveReserveAmount = amount;
        if (!this.recursiveReserveField.isFocused()) {
            this.recursiveReserveField.setText(Long.toString(amount));
            validateRecursiveReserveField();
        }
    }

    private void validateRecursiveReserveField() {
        if (parseRecursiveReserveField() >= 0) {
            this.recursiveReserveField.setTextColor(0xFFFFFF);
            this.recursiveReserveField.setTooltipMessage(List.of(
                GuiText.TerminalSettingsRecursiveIngredientReserveTooltip.text()));
        } else {
            this.recursiveReserveField.setTextColor(0xFF5555);
            this.recursiveReserveField.setTooltipMessage(List.of(GuiText.InvalidNumber.text()));
        }
    }

    private long parseRecursiveReserveField() {
        String text = this.recursiveReserveField.getText().trim();
        if (text.isEmpty()) {
            return -1;
        }
        try {
            long amount = Long.parseLong(text);
            return amount >= 0 && amount <= MAX_RECURSIVE_INGREDIENT_RESERVE ? amount : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean isRecursiveReserveInput(char typedChar, int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END
            || Character.isDigit(typedChar);
    }

    private void sanitizeRecursiveReserveField() {
        String text = this.recursiveReserveField.getText().trim();
        if (!text.isEmpty()) {
            long amount = Long.parseLong(text);
            if (amount > MAX_RECURSIVE_INGREDIENT_RESERVE) {
                this.recursiveReserveField.setText(Long.toString(MAX_RECURSIVE_INGREDIENT_RESERVE));
            }
        }
        validateRecursiveReserveField();
    }

    private void submitRecursiveReserveOnFocusLoss() {
        boolean focused = this.recursiveReserveField.isFocused();
        if (this.recursiveReserveFieldFocused && !focused) {
            submitRecursiveReserveField();
        }
        this.recursiveReserveFieldFocused = focused;
    }

    private void submitRecursiveReserveField() {
        long amount = parseRecursiveReserveField();
        if (amount < 0 || amount == this.displayedRecursiveReserveAmount) {
            validateRecursiveReserveField();
            return;
        }

        this.displayedRecursiveReserveAmount = amount;
        if (this.container instanceof ContainerMEStorage meStorage) {
            meStorage.setRecursiveIngredientReserveAmount(amount);
        }
        InitNetwork.sendToServer(new SetRecursiveIngredientReserveAmountPacket(this.container.windowId, amount));
        validateRecursiveReserveField();
    }

    private void updateWirelessState() {
        AE2Button magnetSettingsButton = this.magnetSettingsButton;
        if (this.wirelessHost == null || this.pickBlockCheckbox == null || this.craftIfMissingCheckbox == null
            || this.restockCheckbox == null || this.magnetCheckbox == null || this.pickupToMECheckbox == null
            || magnetSettingsButton == null) {
            return;
        }

        WirelessTerminalItem terminal = this.wirelessHost.getTerminalItem();
        var stack = this.wirelessHost.getItemStack();
        WirelessTerminalMagnetMode magnetMode = WirelessTerminals.getMagnetMode(stack, terminal);
        this.pickBlockCheckbox.setSelected(WirelessTerminals.isPickBlockEnabled(stack, terminal));
        this.craftIfMissingCheckbox.setSelected(WirelessTerminals.isCraftIfMissingEnabled(stack, terminal));
        this.craftIfMissingCheckbox.enabled = this.pickBlockCheckbox.isSelected();
        this.restockCheckbox.setSelected(WirelessTerminals.isRestockEnabled(stack, terminal));
        this.magnetCheckbox.setSelected(magnetMode.magnet());
        this.pickupToMECheckbox.setSelected(magnetMode.pickupToME());
        boolean magnetCardInstalled = this.wirelessHost.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
        this.magnetCheckbox.enabled = magnetCardInstalled;
        this.pickupToMECheckbox.enabled = magnetCardInstalled;
        magnetSettingsButton.enabled = magnetCardInstalled;
        magnetSettingsButton.visible = magnetCardInstalled;
    }

    private boolean hasGeneralSetting(GeneralSetting setting) {
        return this.generalSettings.contains(setting);
    }

    private boolean hasGeneralTerminalPage() {
        return hasGeneralSetting(GeneralSetting.PINNED_ITEMS)
            || hasGeneralSetting(GeneralSetting.CRAFTING_JOB_NOTIFICATIONS)
            || hasGeneralSetting(GeneralSetting.CLEAR_GRID_ON_CLOSE)
            || hasGeneralSetting(GeneralSetting.PATTERN_AUTO_FILL)
            || hasGeneralSetting(GeneralSetting.RECURSIVE_INGREDIENT_RESERVE)
            || hasGeneralSetting(GeneralSetting.PLAYER_PIN_ROWS);
    }

    private int getGeneralPageCount() {
        int pageCount = 0;
        if (hasGeneralTerminalPage()) {
            pageCount++;
        }
        if (hasGeneralSetting(GeneralSetting.SEARCH)) {
            pageCount++;
        }
        return Math.max(1, pageCount);
    }

    private void updateVisibility() {
        int generalPageCount = getGeneralPageCount();
        if (this.generalPage >= generalPageCount) {
            this.generalPage = generalPageCount - 1;
        }
        boolean general = !this.wirelessOnly && this.page == Page.GENERAL;
        boolean wireless = this.page == Page.WIRELESS && this.wirelessHost != null;
        boolean hasGeneralTerminalPage = hasGeneralTerminalPage();
        boolean generalTerminalPage = general && hasGeneralTerminalPage && this.generalPage == 0;
        boolean generalSearchPage = general && hasGeneralSetting(GeneralSetting.SEARCH)
            && this.generalPage == (hasGeneralTerminalPage ? 1 : 0);
        boolean showGeneralPageControls = general && generalPageCount > 1;
        setTextContent(TEXT_ID_DIALOG_TITLE,
            wireless ? GuiText.WirelessTerminalSettingsTitle.text() : GuiText.TerminalSettingsTitle.text());
        setTextContent("page_info", new TextComponentString((this.generalPage + 1) + " / " + generalPageCount));

        this.generalPageButton.visible = !this.wirelessOnly;
        this.generalPageButton.setFocused(general);
        this.wirelessPageButton.setFocused(wireless);
        this.wirelessPageButton.visible = !this.wirelessOnly && this.wirelessHost != null;
        this.previousPageButton.visible = showGeneralPageControls;
        this.nextPageButton.visible = showGeneralPageControls;
        this.previousPageButton.enabled = showGeneralPageControls && this.generalPage > 0;
        this.nextPageButton.enabled = showGeneralPageControls && this.generalPage + 1 < generalPageCount;

        setTextHidden("search_settings_title", !generalSearchPage);
        setTextHidden("page_info", !showGeneralPageControls);
        setTextHidden("wireless_settings_title", true);
        this.pinAutoCraftedItemsCheckbox.visible = generalTerminalPage
            && hasGeneralSetting(GeneralSetting.PINNED_ITEMS);
        this.pinDisplaySortTopRadio.visible = generalTerminalPage
            && hasGeneralSetting(GeneralSetting.PINNED_ITEMS);
        this.pinDisplayLockedGridRadio.visible = generalTerminalPage
            && hasGeneralSetting(GeneralSetting.PINNED_ITEMS);
        this.notifyForFinishedCraftingJobsCheckbox.visible = generalTerminalPage
            && hasGeneralSetting(GeneralSetting.CRAFTING_JOB_NOTIFICATIONS);
        this.clearGridOnCloseCheckbox.visible = generalTerminalPage
            && hasGeneralSetting(GeneralSetting.CLEAR_GRID_ON_CLOSE);
        if (this.autoFillPatternsCheckbox != null) {
            this.autoFillPatternsCheckbox.visible = generalTerminalPage
                && hasGeneralSetting(GeneralSetting.PATTERN_AUTO_FILL);
        }
        this.useInternalSearchRadio.visible = generalSearchPage;
        this.useExternalSearchRadio.visible = generalSearchPage;
        this.rememberCheckbox.visible = generalSearchPage && this.useInternalSearchRadio.isSelected();
        this.autoFocusCheckbox.visible = generalSearchPage && this.useInternalSearchRadio.isSelected();
        this.syncWithExternalCheckbox.visible = generalSearchPage && this.useInternalSearchRadio.isSelected();
        this.clearExternalCheckbox.visible = generalSearchPage && this.useExternalSearchRadio.isSelected();
        setTextHidden("recursive_reserve_label",
            !(generalTerminalPage && hasGeneralSetting(GeneralSetting.RECURSIVE_INGREDIENT_RESERVE)));
        setTextHidden("player_pin_rows_label",
            !(generalTerminalPage && this.supportsPlayerPinRows
                && hasGeneralSetting(GeneralSetting.PLAYER_PIN_ROWS)));
        this.recursiveReserveField.setVisible(generalTerminalPage
            && hasGeneralSetting(GeneralSetting.RECURSIVE_INGREDIENT_RESERVE));
        this.playerPinRowsField.setVisible(generalTerminalPage && this.supportsPlayerPinRows
            && hasGeneralSetting(GeneralSetting.PLAYER_PIN_ROWS));

        if (this.pickBlockCheckbox != null) {
            this.pickBlockCheckbox.visible = wireless;
        }
        if (this.craftIfMissingCheckbox != null) {
            this.craftIfMissingCheckbox.visible = wireless;
        }
        if (this.restockCheckbox != null) {
            this.restockCheckbox.visible = wireless;
        }
        if (this.magnetCheckbox != null) {
            this.magnetCheckbox.visible = wireless;
        }
        if (this.pickupToMECheckbox != null) {
            this.pickupToMECheckbox.visible = wireless;
        }
        if (this.magnetSettingsButton != null) {
            this.magnetSettingsButton.visible = wireless
                && this.wirelessHost.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
        }
    }

    private void savePatternAutoFill() {
        if (this.autoFillPatternsCheckbox == null) {
            return;
        }

        YesNo value = this.autoFillPatternsCheckbox.isSelected() ? YesNo.YES : YesNo.NO;
        if (this.container instanceof ContainerPatternEncodingTerm patternEncodingTerm) {
            patternEncodingTerm.getConfigManager().putSetting(Settings.PATTERN_AUTO_FILL, value);
        }
        InitNetwork.sendToServer(new ConfigValueServerPacket(this.container.windowId, Settings.PATTERN_AUTO_FILL, value));
    }

    private boolean hasExternalSearch() {
        return ItemListMod.isEnabled();
    }

    @Override
    public void onGuiClosed() {
        commitPendingPlayerPinRowsOnce();
        super.onGuiClosed();
    }

    private boolean isPlayerPinRowsInput(char typedChar, int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END
            || Character.isDigit(typedChar);
    }

    private void sanitizePlayerPinRowsField() {
        updatePendingPlayerPinRows(true);
    }

    private void updatePendingPlayerPinRows(boolean markDirty) {
        String text = this.playerPinRowsField.getText().trim();
        int rows = 0;
        if (!text.isEmpty()) {
            rows = Math.clamp(Integer.parseInt(text), 0, PinnedKeys.MAX_PLAYER_PIN_ROWS);
        }
        String sanitized = Integer.toString(rows);
        if (!sanitized.equals(this.playerPinRowsField.getText())) {
            this.playerPinRowsField.setText(sanitized);
        }
        this.pendingPlayerPinRows = rows;
        if (markDirty) {
            this.playerPinRowsDirty = true;
        }
    }

    private void commitPendingPlayerPinRowsOnce() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        commitPendingPlayerPinRows();
    }

    private void commitPendingPlayerPinRows() {
        if (!this.supportsPlayerPinRows) {
            this.playerPinRowsDirty = false;
            return;
        }
        updatePendingPlayerPinRows(false);
        if (!(this.parent instanceof GuiMEStorage<?> meStorage)) {
            this.playerPinRowsDirty = false;
            return;
        }
        if (this.playerPinRowsDirty) {
            GuiStyle style = meStorage.getStyle();
            if (style == null || style.getTerminalStyle() == null) {
                throw new IllegalStateException("ME terminal settings require a terminal style");
            }
            PinnedKeys.setPlayerPinRows(this.pendingPlayerPinRows, style.getTerminalStyle().getSlotsPerRow());
            this.playerPinRowsDirty = false;
        }
    }

    private enum Page {
        GENERAL,
        WIRELESS
    }

}
