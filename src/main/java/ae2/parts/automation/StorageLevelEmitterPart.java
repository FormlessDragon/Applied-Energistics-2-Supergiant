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

package ae2.parts.automation;

import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.IStackWatcher;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.helpers.IConfigInvHost;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.util.ConfigInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Abstract level emitter logic for storage-based level emitters (item and fluid).
 */
public class StorageLevelEmitterPart extends AbstractLevelEmitterPart
    implements IConfigInvHost, ICraftingProvider {
    @PartModels
    public static final ResourceLocation MODEL_BASE_OFF = AppEng.makeId(
        "part/level_emitter_base_off");
    @PartModels
    public static final ResourceLocation MODEL_BASE_ON = AppEng.makeId(
        "part/level_emitter_base_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = AppEng.makeId(
        "part/level_emitter_status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = AppEng.makeId(
        "part/level_emitter_status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = AppEng.makeId(
        "part/level_emitter_status_has_channel");
    public static final PartModel MODEL_OFF_OFF = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    public static final PartModel MODEL_OFF_ON = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    public static final PartModel MODEL_OFF_HAS_CHANNEL = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    public static final PartModel MODEL_ON_OFF = new PartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    public static final PartModel MODEL_ON_ON = new PartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    public static final PartModel MODEL_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);
    private IStackWatcher storageWatcher;
    private IStackWatcher craftingWatcher;
    private final ConfigInventory config = ConfigInventory.configTypes(1).changeListener(this::configureWatchers)
                                                          .build();
    private boolean reportingUpdatePending;
    private BigInteger aggregateOverflow;

    public StorageLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);

        // Aggregate modes initialize once, then track the difference between the old and new network amounts.
        IStorageWatcherNode stackWatcherNode = new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                storageWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                if (what.equals(getConfiguredKey()) && !isUpgradedWith(AEItems.FUZZY_CARD)) {
                    lastReportedValue = amount;
                    updateState();
                } else {
                    scheduleReportingUpdate();
                }
            }

            @Override
            public void onStackChange(AEKey what, long amount, long previousAmount) {
                if (reportingUpdatePending) {
                    return;
                }
                var configured = getConfiguredKey();
                if (configured != null && !isUpgradedWith(AEItems.FUZZY_CARD)) {
                    onStackChange(what, amount);
                } else if (configured == null || configured.fuzzyEquals(what,
                    getConfigManager().getSetting(Settings.FUZZY_MODE))) {
                    addReportedAmount(amount - previousAmount);
                    updateState();
                }
            }
        };
        getMainNode().addService(IStorageWatcherNode.class, stackWatcherNode);
        ICraftingWatcherNode craftingWatcherNode = new ICraftingWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                craftingWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onRequestChange(AEKey what) {
                updateState();
            }

            @Override
            public void onCraftableChange(AEKey what) {
            }
        };
        getMainNode().addService(ICraftingWatcherNode.class, craftingWatcherNode);
        getMainNode().addService(ICraftingProvider.class, this);
    }


    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.CRAFT_VIA_REDSTONE, YesNo.NO);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Nullable
    private AEKey getConfiguredKey() {
        return config.getKey(0);
    }

    @Override
    protected final int getUpgradeSlots() {
        return 1;
    }

    @Override
    public final void upgradesChanged() {
        this.configureWatchers();
    }

    @Override
    protected boolean hasDirectOutput() {
        return isUpgradedWith(AEItems.CRAFTING_CARD);
    }

    @Override
    protected boolean getDirectOutput() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            if (getConfiguredKey() != null) {
                return grid.getCraftingService().isRequesting(getConfiguredKey());
            } else {
                return grid.getCraftingService().isRequestingAny();
            }
        }

        return false;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return true;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        if (isUpgradedWith(AEItems.CRAFTING_CARD)
            && getConfigManager().getSetting(Settings.CRAFT_VIA_REDSTONE) == YesNo.YES) {
            if (getConfiguredKey() != null) {
                return Set.of(getConfiguredKey());
            }
        }
        return Set.of();
    }

    @Override
    protected void onReportingValueChanged() {
        updateState();
    }

    @Override
    protected void configureWatchers() {
        var myStack = getConfiguredKey();

        if (this.storageWatcher != null) {
            this.storageWatcher.reset();
        }

        if (this.craftingWatcher != null) {
            this.craftingWatcher.reset();
        }

        ICraftingProvider.requestUpdate(getMainNode());

        if (isUpgradedWith(AEItems.CRAFTING_CARD)) {
            if (this.craftingWatcher != null) {
                if (myStack == null) {
                    this.craftingWatcher.setWatchAll(true);
                } else {
                    this.craftingWatcher.add(myStack);
                }
            }
        } else {
            if (this.storageWatcher != null) {
                if (isUpgradedWith(AEItems.FUZZY_CARD) || myStack == null) {
                    this.storageWatcher.setWatchAll(true);
                } else {
                    this.storageWatcher.add(myStack);
                }
            }

            scheduleReportingUpdate();
        }

        updateState();
    }

    private void updateReportingValue(IGrid grid) {
        var stacks = grid.getStorageService().getCachedInventory();
        var myStack = getConfiguredKey();

        if (myStack == null) {
            this.aggregateOverflow = null;
            this.lastReportedValue = 0;
            for (var st : stacks) {
                addReportedAmount(st.getLongValue());
            }
        } else if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            this.aggregateOverflow = null;
            this.lastReportedValue = 0;
            var fzMode = this.getConfigManager().getSetting(Settings.FUZZY_MODE);
            var fuzzyList = stacks.findFuzzy(myStack, fzMode);
            for (var st : fuzzyList) {
                addReportedAmount(st.getLongValue());
            }
        } else {
            this.lastReportedValue = stacks.get(myStack);
        }

        this.updateState();
    }

    private void scheduleReportingUpdate() {
        if (this.reportingUpdatePending || isClientSide() || getLevel() == null) {
            return;
        }
        this.reportingUpdatePending = true;
        try {
            getMainNode().ifPresent(this::updateReportingValue);
        } finally {
            this.reportingUpdatePending = false;
        }
    }

    private void addReportedAmount(long delta) {
        if (this.aggregateOverflow == null && (delta <= 0 || this.lastReportedValue <= Long.MAX_VALUE - delta)) {
            this.lastReportedValue += delta;
            return;
        }
        if (this.aggregateOverflow == null) {
            this.aggregateOverflow = BigInteger.valueOf(this.lastReportedValue);
        }
        this.aggregateOverflow = this.aggregateOverflow.add(BigInteger.valueOf(delta));
        if (this.aggregateOverflow.bitLength() < 64) {
            this.lastReportedValue = this.aggregateOverflow.longValue();
            this.aggregateOverflow = null;
        } else {
            this.lastReportedValue = Long.MAX_VALUE;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        config.readFromChildTag(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        config.writeToChildTag(data, "config");
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.STORAGE_LEVEL_EMITTER, this);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    public ConfigInventory getConfig() {
        return config;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_ON : MODEL_OFF_ON;
        } else {
            return this.isLevelEmitterOn() ? MODEL_ON_OFF : MODEL_OFF_OFF;
        }
    }


}
