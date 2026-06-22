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

package ae2.client.gui.implementations;

import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.storage.ISubGuiHost;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GeneratedBackground;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AECheckbox;
import ae2.client.gui.widgets.TabButton;
import ae2.container.AEBaseContainer;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class GuiKeyTypeSelection<C extends AEBaseContainer & IKeyTypeSelectionContainer> extends AEBaseGui<C> {
    private final AEBaseGui<C> parent;
    private final KeyTypeCheckboxes keyTypesWidget = new KeyTypeCheckboxes();

    public GuiKeyTypeSelection(AEBaseGui<C> parent, ISubGuiHost subGuiHost, ITextComponent dialogTitle) {
        super(parent.getContainer(), parent.getContainer().getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/key_type_selection.json"));
        this.parent = parent;

        widgets.add("back", new TabButton(Icon.BACK, TextComponentItemStack.of(subGuiHost.getMainContainerIcon()), this::returnToParent));
        widgets.add("keytypes", keyTypesWidget);
        setTextContent("dialog_title", dialogTitle);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        int selectedEntryCount = 0;
        AECheckbox selectedEntry = null;

        for (Map.Entry<AEKeyType, AECheckbox> entry : keyTypesWidget.checkboxes.entrySet()) {
            boolean selected = getContainer().getClientKeyTypeSelection().keyTypes().getBoolean(entry.getKey());
            entry.getValue().setSelected(selected);
            entry.getValue().enabled = true;

            if (selected) {
                selectedEntryCount++;
                selectedEntry = entry.getValue();
            }
        }

        if (selectedEntryCount == 1 && selectedEntry != null) {
            selectedEntry.enabled = false;
        }
    }

    private void returnToParent() {
        switchToScreen(parent);
        parent.returnFromSubScreen(this);
    }

    private void setHeight(int height) {
        if (this.style == null) {
            throw new IllegalStateException("GUI style has not been initialized");
        }
        GeneratedBackground generatedBackground = this.style.getGeneratedBackground();
        if (generatedBackground == null) {
            throw new IllegalStateException("GUI style is missing generated background");
        }
        generatedBackground.setHeight(height);
        this.ySize = height;
    }

    private class KeyTypeCheckboxes implements ICompositeWidget {
        private static final int PADDING = 6;
        private static final int KEY_TYPE_SPACING = AECheckbox.SIZE + PADDING;
        private final Object2ObjectLinkedOpenHashMap<AEKeyType, AECheckbox> checkboxes = new Object2ObjectLinkedOpenHashMap<>();
        private Rectangle bounds = new Rectangle(0, 0, 0, 0);

        @Override
        public void setPosition(Point position) {
            bounds = new Rectangle(position.x(), position.y(), bounds.width, bounds.height);
        }

        @Override
        public void setSize(int width, int height) {
            bounds = new Rectangle(bounds.x, bounds.y, width, height);
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        @Override
        public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
            int xPos = this.bounds.x + bounds.x;
            int yPos = this.bounds.y + bounds.y;

            checkboxes.clear();

            var keyTypes = getContainer().getClientKeyTypeSelection().keyTypes().keySet();
            checkboxes.ensureCapacity(keyTypes.size());

            for (AEKeyType keyType : keyTypes) {
                ITextComponent text = keyType.getDescription();
                int textboxWidth = 24 + Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getFormattedText());

                AECheckbox checkbox = new AECheckbox(xPos, yPos, textboxWidth, AECheckbox.SIZE,
                    Objects.requireNonNull(screen.getStyle()), text);
                checkbox.setChangeListener(() -> getContainer().selectKeyType(getContainer().windowId, keyType,
                    checkbox.isSelected()));
                addWidget.accept(checkbox);
                checkboxes.put(keyType, checkbox);

                yPos += KEY_TYPE_SPACING;
            }

            int height = this.bounds.y + AEKeyTypes.getAll().size() * KEY_TYPE_SPACING + PADDING;
            GuiKeyTypeSelection.this.setHeight(height);
        }
    }
}
