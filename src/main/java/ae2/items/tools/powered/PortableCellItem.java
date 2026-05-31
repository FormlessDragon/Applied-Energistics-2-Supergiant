/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 */
package ae2.items.tools.powered;

import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.core.AppEng;
import ae2.items.contents.CellConfig;
import ae2.items.storage.StorageTier;
import ae2.util.ConfigInventory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;

public class PortableCellItem extends AbstractPortableCell implements IBasicCellItem {
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";

    private final StorageTier tier;
    private final AEKeyType keyType;
    private final int totalTypes;

    public PortableCellItem(AEKeyType keyType, int totalTypes, ae2.container.GuiIds.GuiKey guiKey, StorageTier tier,
                            double powerCapacity, int defaultColor) {
        super(guiKey, powerCapacity, defaultColor);
        this.setMaxStackSize(1);
        this.tier = tier;
        this.keyType = keyType;
        this.totalTypes = totalTypes;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 80d + 80d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    @Override
    public ResourceLocation getRecipeId() {
        return AppEng.makeId("tools/" + getRegistryName().getPath());
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        addCellInformationToTooltip(stack, lines);
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return this.tier.bytes() / 2;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return this.tier.bytes() / 128;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return this.totalTypes;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 3, super::onUpgradesChanged);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(keyType), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        var tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
    }

    @Override
    public AEKeyType getKeyType() {
        return keyType;
    }

    public StorageTier getTier() {
        return tier;
    }
}
