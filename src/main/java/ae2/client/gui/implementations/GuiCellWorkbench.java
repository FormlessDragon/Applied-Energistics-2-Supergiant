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
package ae2.client.gui.implementations;

import ae2.api.config.ActionItems;
import ae2.api.config.CopyMode;
import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.AEKeySlotFilter;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.ToggleButton;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerCellWorkbench;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GuiCellWorkbench extends GuiUpgradeable<ContainerCellWorkbench> {
    private final ToggleButton copyMode;
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final CellRestrictionButton cellRestrictionButton;
    private final PageButton previousPageButton;
    private final PageButton nextPageButton;
    private ItemStack cachedWorkbenchItem = ItemStack.EMPTY;
    private boolean cachedFuzzyVisible;
    private boolean cachedRestrictionVisible;
    private boolean cachedPreviousPageVisible;
    private boolean cachedNextPageVisible;

    public GuiCellWorkbench(ContainerCellWorkbench container, InventoryPlayer playerInventory, ITextComponent title,
                            GuiStyle style) {
        super(container, playerInventory, title, style);

        this.fuzzyMode = new SettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL, this::toggleFuzzyMode);
        this.addToLeftToolbar(new ActionButton(ActionItems.COG, container::partition));
        this.addToLeftToolbar(new ActionButton(ActionItems.CLOSE, container::clear));
        this.cellRestrictionButton = new CellRestrictionButton(this::openCellRestriction);
        this.copyMode = this.addToLeftToolbar(new ToggleButton(Icon.COPY_MODE_ON, Icon.COPY_MODE_OFF,
            GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(), act -> container.nextWorkBenchCopyMode()));
        this.addToLeftToolbar(this.fuzzyMode);
        this.addToLeftToolbar(this.cellRestrictionButton);
        this.previousPageButton = new PageButton(Icon.ARROW_LEFT, () -> container.setPage(container.getCurrentPage() - 1));
        this.nextPageButton = new PageButton(Icon.ARROW_RIGHT, () -> container.setPage(container.getCurrentPage() + 1));
        this.widgets.add("previousPage", this.previousPageButton);
        this.widgets.add("nextPage", this.nextPageButton);
    }

    private static void addIncompatibleWithCellTooltip(List<String> lines) {
        lines.add(TextFormatting.RED + GuiText.IncompatibleWithCell.getLocal());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.copyMode.setState(this.container.getCopyMode() == CopyMode.CLEAR_ON_REMOVE);
        this.fuzzyMode.set(this.container.getFuzzyMode());
        boolean fuzzyVisible = container.getUpgrades().isInstalled(AEItems.FUZZY_CARD.item());
        boolean restrictionVisible = canRestrictCell();
        boolean previousPageVisible = this.container.getPageCount() > 1 && this.container.getCurrentPage() > 0;
        boolean nextPageVisible = this.container.getPageCount() > 1
            && this.container.getCurrentPage() + 1 < this.container.getPageCount();

        this.fuzzyMode.setVisibility(fuzzyVisible);
        this.cellRestrictionButton.setVisibility(restrictionVisible);
        this.previousPageButton.setVisibility(previousPageVisible);
        this.nextPageButton.setVisibility(nextPageVisible);

        ItemStack workbenchItem = this.container.getWorkbenchItem();
        if (!ItemStack.areItemStacksEqual(this.cachedWorkbenchItem, workbenchItem)
            || !ItemStack.areItemStackTagsEqual(this.cachedWorkbenchItem, workbenchItem)
            || this.cachedFuzzyVisible != fuzzyVisible
            || this.cachedRestrictionVisible != restrictionVisible
            || this.cachedPreviousPageVisible != previousPageVisible
            || this.cachedNextPageVisible != nextPageVisible) {
            this.cachedWorkbenchItem = workbenchItem.isEmpty() ? ItemStack.EMPTY : workbenchItem.copy();
            this.cachedFuzzyVisible = fuzzyVisible;
            this.cachedRestrictionVisible = restrictionVisible;
            this.cachedPreviousPageVisible = previousPageVisible;
            this.cachedNextPageVisible = nextPageVisible;
            requestExclusionZonesUpdate();
        }
    }

    private void toggleFuzzyMode(SettingToggleButton<FuzzyMode> button, boolean backwards) {
        container.setCellFuzzyMode(button.getNextValue(backwards));
    }

    private void openCellRestriction() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.CELL_RESTRICTION));
    }

    private boolean canRestrictCell() {
        ItemStack stack = this.container.getWorkbenchItem();
        return !stack.isEmpty()
            && stack.getItem() instanceof IBasicCellItem basicCellItem
            && basicCellItem.isStorageCell(stack);
    }

    @Override
    public List<String> getItemToolTip(ItemStack stack) {
        List<String> lines = new ObjectArrayList<>(super.getItemToolTip(stack));
        ItemStack cell = this.container.getWorkbenchItem();
        if (cell.isEmpty() || cell == stack) {
            return lines;
        }

        ICellWorkbenchItem workbenchItem = this.container.getHost().getCell();
        if (workbenchItem == null) {
            return lines;
        }

        AEKey what;
        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack != null) {
            what = genericStack.what();
        } else {
            what = AEItemKey.of(stack);
        }

        if (what == null) {
            return lines;
        }

        ConfigInventory configInventory = workbenchItem.getConfigInventory(cell);
        if (!configInventory.isSupportedType(what.getType())) {
            addIncompatibleWithCellTooltip(lines);
            return lines;
        }

        AEKeySlotFilter filter = configInventory.getFilter();
        if (filter != null) {
            boolean anySlotMatches = false;
            for (int i = 0; i < configInventory.size(); i++) {
                if (configInventory.isAllowedIn(i, what)) {
                    anySlotMatches = true;
                    break;
                }
            }

            if (!anySlotMatches) {
                addIncompatibleWithCellTooltip(lines);
            }
        }

        return lines;
    }

    private static class PageButton extends IconButton {
        private final Icon icon;

        PageButton(Icon icon, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            this.setMessage((icon == Icon.ARROW_LEFT
                ? GuiText.InterfacePagePrevious
                : GuiText.InterfacePageNext).text());
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }

    private static class CellRestrictionButton extends IconButton {
        CellRestrictionButton(Runnable onPress) {
            super(onPress);
            this.setMessage(GuiText.CellRestriction.text());
        }

        @Override
        protected Icon getIcon() {
            return Icon.CELL_RESTRICTION;
        }
    }

}
