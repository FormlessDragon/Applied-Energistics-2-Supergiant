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

package ae2.parts.crafting;

import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.extensions.GridLogicExtensions;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AECableType;
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.core.definitions.AEParts;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.items.parts.PartModels;
import ae2.parts.AEBasePart;
import ae2.parts.PartModel;
import ae2.util.SettingsFrom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;
import java.util.List;

public class PatternProviderPart extends AEBasePart implements PatternProviderLogicHost {

    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/pattern_provider_base");
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/interface_off");
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/interface_on");
    public static final ResourceLocation MODEL_HAS_CHANNEL = AppEng.makeId("part/interface_has_channel");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    protected final PatternProviderLogic logic = this.createLogic();

    public PatternProviderPart(IPartItem<?> partItem) {
        super(partItem);
    }

    protected PatternProviderLogic createLogic() {
        return new PatternProviderLogic(this.getMainNode(), this, AEParts.PATTERN_PROVIDER.item(),
            PatternProviderCapacity.getMaxPatternSlots(AEConfig.instance().getPatternProviderExpansionCardLimit()));
    }

    @Override
    public void saveChanges() {
        getHost().markForSave();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(getPartItem().asItem());
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.logic.onMainNodeStateChanged();
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.logic.updatePatterns();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.clearContent();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);

        if (mode == SettingsFrom.MEMORY_CARD) {
            this.logic.exportSettings(output);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);

        if (mode == SettingsFrom.MEMORY_CARD) {
            this.logic.importSettings(input, player);
        }
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        this.logic.invalidateTargetCaches();
        this.logic.updateRedstoneState();
        var side = GridLogicExtensions.getNeighborSide(pos, neighbor);
        if (side != null && side == getSide() && !isClientSide()) {
            this.logic.onNeighborChanged(side);
        }
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!player.world.isRemote) {
            openGui(player, GuiHostLocators.forPart(this));
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

    @Override
    public PatternProviderLogic getLogic() {
        return this.logic;
    }

    @Override
    public EnumSet<EnumFacing> getTargets() {
        EnumFacing side = this.getSide();
        return side != null ? EnumSet.of(side) : EnumSet.noneOf(EnumFacing.class);
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEParts.PATTERN_PROVIDER.stack();
    }
}



