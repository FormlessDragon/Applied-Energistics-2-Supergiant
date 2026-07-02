/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.cellterminal.widget;

import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class CardsDisplay extends AbstractWidget {
    private static final int CARD_ICON_SIZE = 8;
    private static final int CARD_STRIDE = 9;
    private static final int COLUMNS = 2;
    private final Supplier<List<CardEntry>> cardsSupplier;
    private final RenderItem itemRender;
    private CardClickCallback clickCallback;

    public CardsDisplay(int x, int y, Supplier<List<CardEntry>> cardsSupplier, RenderItem itemRender) {
        super(x, y, 0, CARD_ICON_SIZE);
        this.cardsSupplier = cardsSupplier;
        this.itemRender = itemRender;
    }

    public void setClickCallback(CardClickCallback callback) {
        this.clickCallback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        List<CardEntry> cards = cardsSupplier.get();
        if (cards.isEmpty()) {
            return;
        }
        int rows = (cards.size() + COLUMNS - 1) / COLUMNS;
        this.width = COLUMNS * CARD_STRIDE;
        this.height = rows * CARD_STRIDE;
        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;
            if (!entry.stack().isEmpty()) {
                renderSmallItemStack(entry.stack(), iconX, iconY);
            }
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        CardEntry hovered = cardAt(mouseX, mouseY);
        if (!visible || button != 0 || hovered == null || clickCallback == null) {
            return false;
        }
        clickCallback.onCardClicked(hovered.slotIndex(),
            Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        return true;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) {
            return false;
        }
        return cardAt(mouseX, mouseY) != null;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        CardEntry hovered = cardAt(mouseX, mouseY);
        if (hovered == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ObjectArrayList<>();
        lines.add("§6" + hovered.stack().getDisplayName());
        lines.add("");
        lines.add("§b" + GuiText.CellTerminalUpgradeClickExtract.getLocal());
        lines.add("§b" + GuiText.CellTerminalUpgradeShiftClickInventory.getLocal());
        return lines;
    }

    private CardEntry cardAt(int mouseX, int mouseY) {
        if (!visible) {
            return null;
        }
        List<CardEntry> cards = cardsSupplier.get();
        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            if (entry.stack().isEmpty()) {
                continue;
            }
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;
            if (mouseX >= iconX && mouseX < iconX + CARD_ICON_SIZE
                && mouseY >= iconY && mouseY < iconY + CARD_ICON_SIZE) {
                return entry;
            }
        }
        return null;
    }

    private void renderSmallItemStack(ItemStack stack, int renderX, int renderY) {
        if (stack.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, 0);
        GlStateManager.scale(0.5f, 0.5f, 1.0f);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @FunctionalInterface
    public interface CardClickCallback {
        void onCardClicked(int slotIndex, boolean quickMove);
    }

    public record CardEntry(ItemStack stack, int slotIndex) {
    }
}
