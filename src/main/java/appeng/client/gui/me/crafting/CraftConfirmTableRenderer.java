/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseGui;
import appeng.container.me.crafting.CraftingPlanSummaryEntry;
import appeng.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class CraftConfirmTableRenderer extends AbstractTableRenderer<CraftingPlanSummaryEntry> {

    public CraftConfirmTableRenderer(AEBaseGui<?> screen, int x, int y) {
        super(screen, x, y, 5);
    }

    private static void addInventoryUsageLine(CraftingPlanSummaryEntry entry, List<ITextComponent> lines) {
        if (entry.finalOutput() || entry.inventoryAmount() <= 0) {
            return;
        }

        var usage = getInventoryUsage(entry);
        var line = GuiText.InventoryUsage.text(usage.text);
        line.getStyle().setColor(usage.color);
        lines.add(line);
    }

    private static InventoryUsage getInventoryUsage(CraftingPlanSummaryEntry entry) {
        double percent = entry.inventoryUsageAmount() * 100.0D / entry.inventoryAmount();
        TextFormatting color = percent > 80.0D ? TextFormatting.RED : TextFormatting.GREEN;

        if (percent > 1000.0D) {
            return new InventoryUsage("> 1000", color);
        }
        if (percent > 0.0D && percent < 0.01D) {
            return new InventoryUsage("< 0.01", color);
        }
        return new InventoryUsage(formatPercent(percent), color);
    }

    private static String formatPercent(double percent) {
        long scaled = Math.round(percent * 100.0D);
        return scaled / 100 + "." + scaled / 10 % 10 + scaled % 10;
    }

    @Override
    protected List<ITextComponent> getEntryDescription(CraftingPlanSummaryEntry entry) {
        List<ITextComponent> lines = new ObjectArrayList<>(4);
        if (entry.storedAmount() > 0) {
            String amount = entry.what().getType().formatAmount(entry.storedAmount(), AmountFormat.SLOT);
            lines.add(GuiText.FromStorage.text(amount));
        }

        addInventoryUsageLine(entry, lines);

        if (entry.missingAmount() > 0) {
            String amount = entry.what().getType().formatAmount(entry.missingAmount(), AmountFormat.SLOT);
            lines.add(GuiText.Missing.text(amount));
        }

        if (entry.craftAmount() > 0) {
            String amount = entry.what().getType().formatAmount(entry.craftAmount(), AmountFormat.SLOT);
            lines.add(GuiText.ToCraft.text(amount));
        }
        if (entry.intermediateCraftAmount() > 0) {
            String amount = entry.what().getType().formatAmount(entry.intermediateCraftAmount(), AmountFormat.SLOT);
            lines.add(GuiText.IntermediateCraft.text(amount));
        }
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CraftingPlanSummaryEntry entry) {
        return entry.what();
    }

    @Override
    protected List<ITextComponent> getEntryTooltip(CraftingPlanSummaryEntry entry) {
        List<ITextComponent> lines = AEKeyRendering.getTooltip(entry.what());

        if (entry.storedAmount() > 0) {
            lines.add(GuiText.FromStorage
                .text(entry.what().getType().formatAmount(entry.storedAmount(), AmountFormat.FULL)));
        }
        addInventoryUsageLine(entry, lines);
        if (entry.missingAmount() > 0) {
            lines.add(GuiText.Missing.text(
                entry.what().getType().formatAmount(entry.missingAmount(), AmountFormat.FULL)));
        }
        if (entry.craftAmount() > 0) {
            lines.add(GuiText.ToCraft
                .text(entry.what().getType().formatAmount(entry.craftAmount(), AmountFormat.FULL)));
        }
        if (entry.intermediateCraftAmount() > 0) {
            lines.add(GuiText.IntermediateCraft
                .text(entry.what().getType().formatAmount(entry.intermediateCraftAmount(), AmountFormat.FULL)));
        }

        return lines;
    }

    @Override
    protected int getEntryBackgroundColor(CraftingPlanSummaryEntry entry) {
        return entry.missingAmount() > 0 ? 0x1AFF0000 : 0;
    }

    private record InventoryUsage(String text, TextFormatting color) {
    }
}
