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

package appeng.client.gui.me.crafting;

import appeng.api.config.CpuSelectionMode;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.GuiStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.me.crafting.CraftingStatus;
import appeng.container.me.crafting.CraftingStatusEntry;
import appeng.core.localization.GuiText;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.TraceCraftingSupplierPacket;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class GuiCraftingCPU<T extends ContainerCraftingCPU> extends AEBaseGui<T> {

    private final CraftingStatusTableRenderer table;
    private final AE2Button cancel;
    private final AE2Button suspend;
    private final Scrollbar scrollbar;
    private final SettingToggleButton<CpuSelectionMode> schedulingModeButton;
    @Nullable
    private CraftingStatus status;

    public GuiCraftingCPU(T container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);

        this.table = new CraftingStatusTableRenderer(this, 9, 19);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.cancel = widgets.addButton("cancel", GuiText.Cancel.text(), container::cancelCrafting);
        this.suspend = widgets.addButton("suspend", GuiText.Suspend.text(), container::toggleScheduling);
        this.schedulingModeButton = new ServerSettingToggleButton<>(Settings.CPU_SELECTION_MODE, CpuSelectionMode.ANY);

        if (container.allowConfiguration()) {
            this.addToLeftToolbar(this.schedulingModeButton);
        }

        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        List<CraftingStatusEntry> entries = getVisualEntries();
        this.cancel.enabled = !entries.isEmpty();
        this.suspend.enabled = this.cancel.enabled;

        ITextComponent title = this.getGuiDisplayName(GuiText.CraftingStatus.text());
        String elapsedTimeSuffix = CraftingTimeDisplay.getElapsedTimeTitleSuffix(status, entries.size());
        if (elapsedTimeSuffix != null) {
            title = title.createCopy().appendText(" - " + elapsedTimeSuffix);
        }
        if (container.isCantStoreItems()) {
            ITextComponent suffix = new TextComponentString(" - ");
            suffix.appendSibling(GuiText.CantStoreItems.text().setStyle(new Style().setColor(TextFormatting.RED)));
            title = title.createCopy().appendSibling(suffix);
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, title);

        this.scrollbar.setRange(0, this.table.getScrollableRows(entries.size()), 1);
        this.schedulingModeButton.set(this.container.getSchedulingMode());
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.status != null) {
            this.table.render(mouseX - offsetX, mouseY - offsetY, status.entries(), scrollbar.getCurrentScroll());
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        List<ITextComponent> hoveredTooltip = this.table.getHoveredTooltip();
        if (hoveredTooltip != null) {
            drawTooltipWithHeader(mouseX, mouseY, hoveredTooltip);
        }
    }

    @Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table.getHoveredStack();
        if (hovered != null) {
            return hovered;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && isShiftKeyDown()) {
            var hoveredEntry = this.table.getHoveredEntry();
            if (hoveredEntry != null && (hoveredEntry.activeAmount() > 0 || hoveredEntry.pendingAmount() > 0)) {
                InitNetwork.sendToServer(new TraceCraftingSupplierPacket(this.container.windowId, hoveredEntry.serial()));
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void postUpdate(CraftingStatus status) {
        Long2ObjectMap<CraftingStatusEntry> entries;
        if (this.status == null || status.fullStatus()) {
            entries = new Long2ObjectLinkedOpenHashMap<>();
        } else {
            entries = new Long2ObjectLinkedOpenHashMap<>(this.status.entries().size());
            for (CraftingStatusEntry entry : this.status.entries()) {
                entries.put(entry.serial(), entry);
            }
        }

        for (CraftingStatusEntry entry : status.entries()) {
            if (entry.isDeleted()) {
                entries.remove(entry.serial());
                continue;
            }

            CraftingStatusEntry existingEntry = entries.get(entry.serial());
            if (existingEntry != null) {
                entries.put(entry.serial(), new CraftingStatusEntry(
                    existingEntry.serial(),
                    existingEntry.what(),
                    entry.storedAmount(),
                    entry.activeAmount(),
                    entry.pendingAmount()));
            } else if (entry.what() != null) {
                entries.put(entry.serial(), entry);
            }
        }

        List<CraftingStatusEntry> sortedEntries = new ObjectArrayList<>(entries.values());
        Collections.sort(sortedEntries);
        this.status = new CraftingStatus(
            true,
            status.elapsedTime(),
            status.remainingItemCount(),
            status.startItemCount(),
            sortedEntries,
            status.suspended());
        this.suspend.setMessage(status.suspended() ? GuiText.Resume.text() : GuiText.Suspend.text());
    }

    protected ITextComponent getGuiDisplayName(ITextComponent in) {
        return super.getGuiDisplayName(in);
    }

    private List<CraftingStatusEntry> getVisualEntries() {
        return this.status != null ? status.entries() : Collections.emptyList();
    }
}
