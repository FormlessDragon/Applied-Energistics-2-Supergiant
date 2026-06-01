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
package ae2.tile.crafting;

import ae2.api.AECapabilities;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.block.crafting.PatternProviderBlock;
import ae2.block.crafting.PushDirection;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.items.tools.MemoryCardItem;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.SettingsFrom;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class TilePatternProvider extends AENetworkedTile implements PatternProviderLogicHost, IConfigurableObject {
    private static final String MEMORY_CARD_PUSH_DIRECTION = "pushDirection";

    private final PatternProviderLogic logic = new PatternProviderLogic(this.getMainNode(), this,
        AEBlocks.PATTERN_PROVIDER.item(),
        PatternProviderCapacity.getMaxPatternSlots(AEConfig.instance().getPatternProviderExpansionCardLimit()));

    @Override
    public void onReady() {
        super.onReady();
        this.logic.updatePatterns();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.logic.onMainNodeStateChanged();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.logic.writeToNBT(data);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        this.logic.invalidateTargetCaches();
        this.onGridConnectableSidesChanged();
    }

    public void onPushDirectionChanged() {
        this.logic.invalidateTargetCaches();
        this.onGridConnectableSidesChanged();
    }

    @Override
    public ItemStack getItemFromTile() {
        return new ItemStack(AEBlocks.PATTERN_PROVIDER.block());
    }

    @Override
    public PatternProviderLogic getLogic() {
        return this.logic;
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public EnumSet<EnumFacing> getTargets() {
        EnumFacing pushDirection = this.getPushDirection().getDirection();
        return pushDirection == null ? EnumSet.allOf(EnumFacing.class) : EnumSet.of(pushDirection);
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        EnumFacing pushDirection = this.getPushDirection().getDirection();
        return pushDirection == null ? EnumSet.allOf(EnumFacing.class)
            : EnumSet.complementOf(EnumSet.of(pushDirection));
    }

    public void addAdditionalDrops(List<ItemStack> drops) {
        this.logic.addDrops(drops);
    }

    public void clearContent() {
        this.logic.clearContent();
    }

    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        MemoryCardItem.exportGenericSettings(this, output);
        this.logic.exportSettings(output);
        output.setInteger(MEMORY_CARD_PUSH_DIRECTION, this.getPushDirection().ordinal());
    }

    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        MemoryCardItem.importGenericSettings(this, input, player);
        this.logic.importSettings(input, player);

        if (input.hasKey(MEMORY_CARD_PUSH_DIRECTION, Constants.NBT.TAG_INT)) {
            PushDirection[] values = PushDirection.values();
            int ordinal = input.getInteger(MEMORY_CARD_PUSH_DIRECTION);
            if (ordinal >= 0 && ordinal < values.length) {
                IBlockState state = this.getBlockState();
                if (state != null && state.getBlock() instanceof PatternProviderBlock) {
                    this.getWorld().setBlockState(this.getPos(),
                        state.withProperty(PatternProviderBlock.PUSH_DIRECTION, values[ordinal]), 3);
                    this.onGridConnectableSidesChanged();
                }
            }
        }
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return new ItemStack(AEBlocks.PATTERN_PROVIDER.block());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(new ItemStack(AEBlocks.PATTERN_PROVIDER.block()));
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return AECapabilities.GENERIC_INTERNAL_INV.cast(this.logic.getReturnInv());
        }
        return super.getCapability(capability, facing);
    }

    private PushDirection getPushDirection() {
        IBlockState state = this.getBlockState();
        if (state == null || !(state.getBlock() instanceof PatternProviderBlock)) {
            return PushDirection.ALL;
        }
        return state.getValue(PatternProviderBlock.PUSH_DIRECTION);
    }
}
