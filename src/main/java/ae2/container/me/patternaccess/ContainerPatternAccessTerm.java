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

package ae2.container.me.patternaccess;

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.IPatternAccessTermContainerHost;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.ILinkStatusAwareContainer;
import ae2.container.implementations.PatternModifierPanel;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.network.clientbound.SetLinkStatusPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ContainerPatternAccessTerm extends AEBaseContainer
    implements ILinkStatusAwareContainer, PatternModifierPanel.Host, IPatternAccess {

    private static final String ACTION_OPEN_PROVIDER = "openProvider";
    private static final String ACTION_TOGGLE_PROVIDER_VISIBILITY = "toggleProviderVisibility";
    private static final String ACTION_RENAME_GROUP = "renameGroup";
    private static final String ACTION_RENAME_PROVIDER = "renameProvider";

    private final IPatternAccessTermContainerHost host;
    private final PatternAccessSupport<ContainerPatternAccessTerm> patternAccessSupport;
    private final PatternModifierPanel patternModifierPanel;
    @GuiSync(1)
    public ShowPatternProviders showPatternProviders = ShowPatternProviders.VISIBLE;
    @GuiSync(2)
    public boolean patternModifierPanelAvailable;
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public ContainerPatternAccessTerm(InventoryPlayer playerInventory, IPatternAccessTermContainerHost host) {
        super(playerInventory, host);
        this.host = host;
        this.patternAccessSupport = new PatternAccessSupport<>(
            this::getGrid,
            this::getShownProviders,
            () -> getPlayer().world,
            this::isPlayerSideSlot,
            this::sendPacketToClient,
            new PatternAccessSupport.PlayerHandAccess() {
                @Override
                public ItemStack getCarried() {
                    return ContainerPatternAccessTerm.this.getCarried();
                }

                @Override
                public void setCarried(ItemStack stack) {
                    ContainerPatternAccessTerm.this.setCarried(stack);
                }
            },
            this);
        registerClientAction(ACTION_OPEN_PROVIDER, Long.class, this::openPatternProvider);
        registerClientAction(ACTION_TOGGLE_PROVIDER_VISIBILITY, Long.class, this::togglePatternProviderVisibility);
        registerClientAction(ACTION_RENAME_GROUP, PatternAccessSupport.RenamePatternGroupPayload.class,
            this::renamePatternGroup);
        registerClientAction(ACTION_RENAME_PROVIDER, PatternAccessSupport.RenamePatternProviderPayload.class,
            this::renamePatternProvider);
        if (host instanceof WirelessTerminalGuiHost<?> wirelessHost) {
            setupUpgrades(wirelessHost.getUpgrades());
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                wirelessHost.getSingularityStorage(), 0, 0, 0);
            slot.setStackLimit(1);
            this.addSlot(slot, SlotSemantics.WIRELESS_SINGULARITY);
        }
        this.addPlayerInventorySlots(0, 0);
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
    }

    @Override
    public void openPatternProvider(long inventoryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_PROVIDER, inventoryId);
            return;
        }

        this.patternAccessSupport.openProvider(getPlayer(), inventoryId);
    }

    @Override
    public void renamePatternProvider(long inventoryId, String name) {
        renamePatternProvider(new PatternAccessSupport.RenamePatternProviderPayload(inventoryId, name));
    }

    @Override
    public void renamePatternGroup(long[] inventoryIds, String name) {
        renamePatternGroup(new PatternAccessSupport.RenamePatternGroupPayload(inventoryIds, name));
    }

    @Override
    public void togglePatternProviderVisibility(long inventoryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PROVIDER_VISIBILITY, inventoryId);
            return;
        }

        this.patternAccessSupport.toggleProviderVisibility(inventoryId);
    }

    private void renamePatternGroup(PatternAccessSupport.RenamePatternGroupPayload payload) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_GROUP, payload);
            return;
        }

        this.patternAccessSupport.renameGroup(payload);
    }

    private void renamePatternProvider(PatternAccessSupport.RenamePatternProviderPayload payload) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_PROVIDER, payload);
            return;
        }

        this.patternAccessSupport.renameProvider(payload);
    }

    @Override
    public ShowPatternProviders getShownProviders() {
        return this.showPatternProviders;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return this.linkStatus;
    }

    @Override
    public void setLinkStatus(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Override
    public boolean isPatternModifierPanelAvailable() {
        return this.patternModifierPanelAvailable;
    }

    @Override
    public PatternModifierPanel getPatternModifierPanel() {
        return this.patternModifierPanel;
    }

    @Nullable
    private IGrid getGrid() {
        if (!this.host.getLinkStatus().connected()) {
            return null;
        }
        IGridNode node = this.host.getGridNode();
        if (node != null && node.isActive()) {
            return node.grid();
        }
        return null;
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }

        this.showPatternProviders = this.host.getConfigManager().getSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();

        super.broadcastChanges();

        updateLinkStatus();
        this.patternAccessSupport.updateProviderVisibility();
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        this.patternAccessSupport.doAction(player, action, slot, id);
    }

    @Override
    public void quickMovePattern(EntityPlayerMP player, Slot sourceSlot, LongList allowedPatternContainerIds,
                                 LongList allowedPatternSlots) {
        this.patternAccessSupport.quickMovePattern(player, sourceSlot, allowedPatternContainerIds,
            allowedPatternSlots);
    }

    protected void updateLinkStatus() {
        ILinkStatus linkStatus = this.host.getLinkStatus();
        if (!Objects.equals(this.linkStatus, linkStatus)) {
            this.linkStatus = linkStatus;
            sendPacketToClient(new SetLinkStatusPacket(linkStatus));
        }
    }

    @Override
    public void updatePatternModifierPanelVisibleSlots(boolean visible) {
        this.patternModifierPanel.updateSlotState(visible && this.patternModifierPanelAvailable);
    }

    @Override
    public void registerPatternModifierPanelAction(String action, Runnable runnable) {
        registerClientAction(action, runnable);
    }

    @Override
    public void sendPatternModifierPanelAction(String action) {
        sendClientAction(action);
    }

    @Override
    public void lockPatternModifierPlayerInventorySlot(int slot) {
        lockPlayerInventorySlot(slot);
    }

}
