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

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ISubGuiHost;
import appeng.container.AEBaseContainer;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.InaccessibleSlot;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.ConfirmAutoCraftPacket;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ContainerCraftAmount extends AEBaseContainer implements ISubGui {

    private final AppEngSlot craftingItem;
    private final ISubGuiHost host;
    @Nullable
    private AEKey whatToCraft;
    @GuiSync(4)
    public int initialAmount = 1;

    public ContainerCraftAmount( InventoryPlayer ip, ISubGuiHost host) {
        super(ip, host);
        this.host = host;
        this.craftingItem = new InaccessibleSlot(new AppEngInternalInventory(1), 0);
        this.craftingItem.setHideAmount(true);
        this.addSlot(this.craftingItem, SlotSemantics.MACHINE_OUTPUT);
    }

    public static void open(EntityPlayerMP player, GuiHostLocator locator, AEKey whatToCraft, int initialAmount) {
        open(player, locator, whatToCraft, initialAmount, null);
    }

    public static void open(EntityPlayerMP player, GuiHostLocator locator, AEKey whatToCraft, int initialAmount,
                            @Nullable Container returnToContainerOverride) {
        SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.CRAFT_AMOUNT, returnToContainerOverride);

        if (player.openContainer instanceof ContainerCraftAmount container) {
            container.setWhatToCraft(whatToCraft, initialAmount);
            container.detectAndSendChanges();
        }
    }

    @Override
    public ISubGuiHost getHost() {
        return this.host;
    }

    public World getLevel() {
        return this.getPlayerInventory().player.world;
    }

    private void setWhatToCraft(AEKey whatToCraft, int initialAmount) {
        this.whatToCraft = Objects.requireNonNull(whatToCraft, "whatToCraft");
        this.initialAmount = Math.max(1, initialAmount);
        this.craftingItem.putStack(GenericStack.wrapInItemStack(whatToCraft, 1));
    }

    public void confirm(int amount, boolean craftMissingAmount, boolean autoStart) {
        if (!isServerSide()) {
            InitNetwork.sendToServer(new ConfirmAutoCraftPacket(amount, craftMissingAmount, autoStart));
            return;
        }

        if (this.whatToCraft == null) {
            return;
        }

        if (craftMissingAmount) {
            IActionHost actionHost = getActionHost();
            if (actionHost != null) {
                IGridNode node = actionHost.getActionableNode();
                if (node != null) {
                    IStorageService storage = node.grid().getStorageService();
                    int existingAmount = (int) Math.min(storage.getCachedInventory().get(this.whatToCraft),
                        Integer.MAX_VALUE);
                    if (existingAmount > amount) {
                        amount = 0;
                    } else {
                        amount -= existingAmount;
                    }
                }
            }
        }

        GuiHostLocator locator = getLocator();
        if (locator != null) {
            EntityPlayer player = getPlayer();
            if (player instanceof EntityPlayerMP serverPlayer) {
                if (amount > 0) {
                    SwitchGuisPacket.openSubGui(serverPlayer, locator, GuiIds.GuiKey.CRAFT_CONFIRM,
                        getReturnToContainerOverride());

                    if (serverPlayer.openContainer instanceof ContainerCraftConfirm container) {
                        container.setAutoStart(autoStart);
                        container.planJob(this.whatToCraft, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
                        container.detectAndSendChanges();
                    }
                } else {
                    this.host.returnToMainContainer(serverPlayer, this);
                }
            }
        }
    }

    @Nullable
    public GenericStack getWhatToCraft() {
        return GenericStack.unwrapItemStack(this.craftingItem.getDisplayStack());
    }

    public int getInitialAmount() {
        return this.initialAmount;
    }
}
