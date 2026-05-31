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

package ae2.client.gui.me.crafting;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.util.AEColor;
import ae2.client.gui.AEBaseGui;
import ae2.container.me.crafting.CraftingStatusEntry;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Objects;

public class CraftingStatusTableRenderer extends AbstractTableRenderer<CraftingStatusEntry> {
    private static final int BACKGROUND_ALPHA = 0x5A000000;

    public CraftingStatusTableRenderer(AEBaseGui<?> screen, int x, int y) {
        super(screen, x, y, 6);
    }

    @Override
    protected List<ITextComponent> getEntryDescription(CraftingStatusEntry entry) {
        AEKey what = Objects.requireNonNull(entry.what());
        List<ITextComponent> lines = new ObjectArrayList<>(3);
        if (entry.storedAmount() > 0) {
            String amount = what.getType().formatAmount(entry.storedAmount(), AmountFormat.SLOT);
            lines.add(GuiText.FromStorage.text(amount));
        }
        if (entry.activeAmount() > 0) {
            String amount = what.getType().formatAmount(entry.activeAmount(), AmountFormat.SLOT);
            lines.add(GuiText.Crafting.text(amount));
        }
        if (entry.pendingAmount() > 0) {
            String amount = what.getType().formatAmount(entry.pendingAmount(), AmountFormat.SLOT);
            lines.add(GuiText.Scheduled.text(amount));
        }
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CraftingStatusEntry entry) {
        return Objects.requireNonNull(entry.what());
    }

    @Override
    protected List<ITextComponent> getEntryTooltip(CraftingStatusEntry entry) {
        AEKey what = Objects.requireNonNull(entry.what());
        List<ITextComponent> lines = AEKeyRendering.getTooltip(what);
        if (entry.storedAmount() > 0) {
            lines.add(GuiText.FromStorage.text(
                what.getType().formatAmount(entry.storedAmount(), AmountFormat.FULL)));
        }
        if (entry.activeAmount() > 0) {
            lines.add(GuiText.Crafting.text(
                what.getType().formatAmount(entry.activeAmount(), AmountFormat.FULL)));
        }
        if (entry.pendingAmount() > 0) {
            lines.add(GuiText.Scheduled.text(
                what.getType().formatAmount(entry.pendingAmount(), AmountFormat.FULL)));
        }
        if (entry.activeAmount() > 0 || entry.pendingAmount() > 0) {
            lines.add(new TextComponentString("")
                .appendSibling(Tooltips.of(new TextComponentString("Shift + ")
                    .setStyle(new Style().setColor(TextFormatting.GRAY))))
                .appendSibling(Tooltips.of(ButtonToolTips.LeftClick.text()))
                .appendSibling(Tooltips.of(new TextComponentString(" 定位供应器")
                    .setStyle(new Style().setColor(TextFormatting.GRAY)))));
        }
        return lines;
    }

    @Override
    protected int getEntryBackgroundColor(CraftingStatusEntry entry) {
        if (entry.activeAmount() > 0) {
            return AEColor.GREEN.blackVariant | BACKGROUND_ALPHA;
        } else if (entry.pendingAmount() > 0) {
            return AEColor.YELLOW.blackVariant | BACKGROUND_ALPHA;
        }
        return 0;
    }
}
