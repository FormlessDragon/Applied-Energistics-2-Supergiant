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
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AECheckbox;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.IconButton;
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
import java.util.List;

public class GuiTerminalSettings extends AEBaseGui<AEBaseContainer> {
    private final AEBaseGui<?> parent;
    private final Runnable beforeReturn;
    private final boolean wirelessOnly;
    private final AECheckbox pinAutoCraftedItemsCheckbox;
    private final AECheckbox notifyForFinishedCraftingJobsCheckbox;
    private final AECheckbox clearGridOnCloseCheckbox;
    private final AECheckbox useInternalSearchRadio;
    private final AECheckbox useExternalSearchRadio;
    private final AECheckbox rememberCheckbox;
    private final AECheckbox autoFocusCheckbox;
    private final AECheckbox syncWithExternalCheckbox;
    private final AECheckbox clearExternalCheckbox;
    private final AETextField recursiveReserveField;
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
    private long displayedRecursiveReserveAmount = -1;
    private boolean recursiveReserveFieldFocused;

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
        super(container, container.getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/terminals/terminal_settings.json"));
        this.parent = parent;
        this.beforeReturn = beforeReturn;
        this.wirelessOnly = wirelessOnly;
        this.wirelessHost = wirelessHost;

        ITextComponent externalSearchName = new TextComponentString(
            ItemListMod.isEnabled() ? ItemListMod.getShortName() : "HEI");

        widgets.add("back", new TabButton(
            Icon.BACK,
            TextComponentItemStack.of(parentIcon),
            this::returnToParent));
        this.generalPageButton = addToLeftToolbar(
            new ActionButton(ActionItems.TERMINAL_SETTINGS, () -> switchPage(Page.GENERAL)));
        this.wirelessPageButton = new WirelessSettingsPageButton(() -> switchPage(Page.WIRELESS));
        addToLeftToolbar(this.wirelessPageButton);

        this.pinAutoCraftedItemsCheckbox = widgets.addCheckbox("pinAutoCraftedItemsCheckbox",
            GuiText.TerminalSettingsPinAutoCraftedItems.text(), this::save);
        this.notifyForFinishedCraftingJobsCheckbox = widgets.addCheckbox("notifyForFinishedCraftingJobsCheckbox",
            GuiText.TerminalSettingsNotifyForFinishedJobs.text(), this::save);
        this.clearGridOnCloseCheckbox = widgets.addCheckbox("clearGridOnCloseCheckbox",
            GuiText.TerminalSettingsClearGridOnClose.text(), this::save);
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
        this.recursiveReserveField.setMaxStringLength(18);
        this.recursiveReserveField.setResponder(ignored -> validateRecursiveReserveField());
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
        setTextContent("wireless_settings_title", GuiText.WirelessTerminalSettingsTitle.text());
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

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateState();
        submitRecursiveReserveOnFocusLoss();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.recursiveReserveField.isFocused()
            && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            submitRecursiveReserveField();
            this.recursiveReserveField.setFocused(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void returnToParent() {
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

    private void openMagnetSettings() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.WIRELESS_MAGNET));
    }

    private void save() {
        AEConfig config = AEConfig.instance();
        config.setPinAutoCraftedItems(pinAutoCraftedItemsCheckbox.isSelected());
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
        pinAutoCraftedItemsCheckbox.setSelected(config.isPinAutoCraftedItems());
        notifyForFinishedCraftingJobsCheckbox.setSelected(config.isNotifyForFinishedCraftingJobs());
        clearGridOnCloseCheckbox.setSelected(config.isClearGridOnClose());
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
            return amount >= 0 ? amount : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
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

    private void updateVisibility() {
        boolean general = !this.wirelessOnly && this.page == Page.GENERAL;
        boolean wireless = this.page == Page.WIRELESS && this.wirelessHost != null;
        setTextContent(TEXT_ID_DIALOG_TITLE,
            wireless ? GuiText.WirelessTerminalSettingsTitle.text() : GuiText.TerminalSettingsTitle.text());

        this.generalPageButton.visible = !this.wirelessOnly;
        this.generalPageButton.setFocused(general);
        this.wirelessPageButton.setFocused(wireless);
        this.wirelessPageButton.visible = !this.wirelessOnly && this.wirelessHost != null;

        setTextHidden("search_settings_title", !general);
        setTextHidden("wireless_settings_title", true);
        this.pinAutoCraftedItemsCheckbox.visible = general;
        this.notifyForFinishedCraftingJobsCheckbox.visible = general;
        this.clearGridOnCloseCheckbox.visible = general;
        this.useInternalSearchRadio.visible = general;
        this.useExternalSearchRadio.visible = general;
        this.rememberCheckbox.visible = general && this.useInternalSearchRadio.isSelected();
        this.autoFocusCheckbox.visible = general && this.useInternalSearchRadio.isSelected();
        this.syncWithExternalCheckbox.visible = general && this.useInternalSearchRadio.isSelected();
        this.clearExternalCheckbox.visible = general && this.useExternalSearchRadio.isSelected();
        setTextHidden("recursive_reserve_label", !general);
        this.recursiveReserveField.setVisible(general);

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

    private boolean hasExternalSearch() {
        return ItemListMod.isEnabled();
    }

    private enum Page {
        GENERAL,
        WIRELESS
    }

    private static class WirelessSettingsPageButton extends IconButton {
        private WirelessSettingsPageButton(Runnable onPress) {
            super(onPress);
            setMessage(GuiText.WirelessTerminalSettingsTitle.text());
        }

        @Override
        protected Icon getIcon() {
            return Icon.WIRELESS_SETTINGS_PAGE;
        }
    }
}
