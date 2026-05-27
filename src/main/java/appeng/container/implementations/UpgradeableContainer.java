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

package appeng.container.implementations;

import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.ToolboxInventory;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.FakeSlot;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalFakeSlot;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;

public abstract class UpgradeableContainer<T extends IUpgradeableObject> extends AEBaseContainer implements IOptionalSlotHost {

    private final T host;
    private final ToolboxInventory toolbox;
    @GuiSync(0)
    public RedstoneMode rsMode = RedstoneMode.IGNORE;
    @GuiSync(1)
    public FuzzyMode fzMode = FuzzyMode.IGNORE_ALL;
    @GuiSync(5)
    public YesNo cMode = YesNo.NO;
    @GuiSync(6)
    public SchedulingMode schedulingMode = SchedulingMode.DEFAULT;

    protected UpgradeableContainer(InventoryPlayer ip, T host) {
        super(ip, host);
        this.host = host;
        this.toolbox = new ToolboxInventory(this);
        this.layoutToolboxSlots();
        this.setupInventorySlots();
        this.setupUpgrades();
        this.layoutUpgradeSlots();
        this.setupConfig();
        this.addPlayerInventorySlots(getPlayerInventoryLeft(), getPlayerInventoryTop());
    }

    protected void setupInventorySlots() {
    }

    protected void setupConfig() {
    }

    protected void setupUpgrades() {
        setupUpgrades(this.getHost().getUpgrades());
    }

    protected int getPlayerInventoryLeft() {
        return 8;
    }

    protected int getPlayerInventoryTop() {
        return 102;
    }

    protected final void addExpandableConfigSlots(GenericStackInv config) {
        var inv = config.createGuiWrapper();
        int originX = 8;
        int originY = 29;

        for (int y = 0; y < 2 + 5; y++) {
            for (int x = 0; x < 9; x++) {
                int slotIndex = y * 9 + x;
                int slotX = originX + x * 18;
                int slotY = originY + y * 18;

                if (y < 2) {
                    this.addSlot(new FakeSlot(inv, slotIndex, slotX, slotY), SlotSemantics.CONFIG);
                } else {
                    this.addSlot(new OptionalFakeSlot(inv, this, slotIndex, slotX, slotY, y - 2),
                        SlotSemantics.CONFIG);
                }
            }
        }
    }

    public ToolboxInventory getToolbox() {
        return this.toolbox;
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide() && this.host instanceof IConfigurableObject configurableObject) {
            this.loadSettingsFromHost(configurableObject.getConfigManager());
        }

        this.toolbox.tick();

        for (var slot : this.inventorySlots) {
            if (slot instanceof OptionalFakeSlot fs) {
                if (!fs.isSlotEnabled() && !fs.getDisplayStack().isEmpty()) {
                    fs.clearStack();
                }
            }
        }

        this.standardDetectAndSendChanges();
    }

    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_CONTROLLED));
        if (cm.hasSetting(Settings.CRAFT_ONLY)) {
            this.setCraftingMode(cm.getSetting(Settings.CRAFT_ONLY));
        }
        if (cm.hasSetting(Settings.SCHEDULING_MODE)) {
            this.setSchedulingMode(cm.getSetting(Settings.SCHEDULING_MODE));
        }
    }

    protected void standardDetectAndSendChanges() {
        super.broadcastChanges();
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int capacityUpgrades = this.getHost().getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD.item());
        return idx == 1 && capacityUpgrades >= 1 || idx == 2 && capacityUpgrades >= 2;
    }

    public FuzzyMode getFuzzyMode() {
        return this.fzMode;
    }

    public void setFuzzyMode(FuzzyMode fzMode) {
        this.fzMode = fzMode;
    }

    public YesNo getCraftingMode() {
        return this.cMode;
    }

    public void setCraftingMode(YesNo cMode) {
        this.cMode = cMode;
    }

    public RedstoneMode getRedStoneMode() {
        return this.rsMode;
    }

    public void setRedStoneMode(RedstoneMode rsMode) {
        this.rsMode = rsMode;
    }

    public SchedulingMode getSchedulingMode() {
        return this.schedulingMode;
    }

    private void setSchedulingMode(SchedulingMode schedulingMode) {
        this.schedulingMode = schedulingMode;
    }

    public final T getHost() {
        return this.host;
    }

    public final IUpgradeInventory getUpgrades() {
        return this.getHost().getUpgrades();
    }

    public final boolean hasUpgrade(Item upgradeCard) {
        return this.getUpgrades().isInstalled(upgradeCard);
    }

    private void layoutToolboxSlots() {
        var slots = this.getSlots(SlotSemantics.TOOLBOX);
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            int col = index % 3;
            int row = index / 3;
            slot.xPos = 186 + col * 18;
            slot.yPos = this.getPlayerInventoryTop() + row * 18;
        }
    }

    private void layoutUpgradeSlots() {
        var slots = this.getSlots(SlotSemantics.UPGRADE);
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            slot.xPos = 187;
            slot.yPos = 8 + index * 18;
        }
    }
}
