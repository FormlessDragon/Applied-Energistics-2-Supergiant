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

package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.implementations.blockentities.IColorableBlockEntity;
import ae2.api.implementations.tiles.IColorableTile;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.AEColor;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.helpers.IMouseWheelItem;
import ae2.hooks.IBlockTool;
import ae2.items.contents.CellConfig;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import ae2.me.helpers.BaseActionSource;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ColorApplicatorItem extends AEBasePoweredItem implements IBasicCellItem, IBlockTool, IMouseWheelItem {

    private static final double POWER_PER_USE = 100;
    private static final String FUZZY_MODE_TAG = "fuzzyMode";
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";
    private static final String SELECTED_COLOR = "selected_color";
    private static final Int2ObjectOpenHashMap<AEColor> ORE_TO_COLOR = new Int2ObjectOpenHashMap<>();

    static {
        for (final AEColor color : AEColor.VALID_COLORS) {
            final String dyeName = color.dye.getTranslationKey();
            final String oreDictName = "dye" + capitalize(dyeName);
            final int oreDictId = OreDictionary.getOreID(oreDictName);
            ORE_TO_COLOR.put(oreDictId, color);
        }
    }

    public ColorApplicatorItem() {
        super(AEConfig.instance().getColorApplicatorBattery());
    }

    public static ItemStack createFullColorApplicator() {
        var item = AEItems.COLOR_APPLICATOR.get();
        var applicator = new ItemStack(item);
        var dyeStorage = StorageCells.getCellInventory(applicator, null);
        if (dyeStorage != null) {
            for (var color : AEColor.VALID_COLORS) {
                dyeStorage.insert(AEItemKey.of(new ItemStack(Items.DYE, 1, color.dye.getDyeDamage())),
                    128, Actionable.MODULATE, new BaseActionSource());
            }
            dyeStorage.insert(AEItemKey.of(Items.SNOWBALL), 128, Actionable.MODULATE, new BaseActionSource());
        }

        var upgrades = item.getUpgrades(applicator);
        upgrades.addItems(AEItems.ENERGY_CARD.stack());
        upgrades.addItems(AEItems.ENERGY_CARD.stack());

        item.injectAEPower(applicator, item.getAEMaxPower(applicator), Actionable.MODULATE);
        return applicator;
    }

    private static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    @Override
    protected void getCheckedSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        super.getCheckedSubItems(creativeTab, itemStacks);
        itemStacks.add(createFullColorApplicator());
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 80.0 + 80.0 * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hitX,
                                      float hitY, float hitZ) {
        return this.onItemUse(p.getHeldItem(hand), p, w, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack stack = p.getHeldItem(hand);
        if (p.isSneaking()) {
            if (!w.isRemote) {
                cycleColors(stack, getColor(stack), 1);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public EnumActionResult onItemUse(ItemStack is, EntityPlayer p, World w, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        if (p.isSneaking()) {
            return EnumActionResult.PASS;
        }

        ItemStack selectedColorStack = this.getColor(is);
        AEColor color = this.getColorFromItem(selectedColorStack);
        if (color == null || !consumeColor(is, color, true)) {
            return EnumActionResult.FAIL;
        }

        Block block = w.getBlockState(pos).getBlock();
        Object tile = w.getTileEntity(pos);

        if (color == AEColor.TRANSPARENT) {
            if (tile instanceof IColorableBlockEntity colorableBlockEntity && this.getAECurrentPower(is) > POWER_PER_USE) {
                if (colorableBlockEntity.getColor() != AEColor.TRANSPARENT
                    && colorableBlockEntity.recolourBlock(side, AEColor.TRANSPARENT, p)) {
                    consumeColor(is, color, false);
                    return EnumActionResult.SUCCESS;
                }
            }

            if (tile instanceof IColorableTile colorableTile && this.getAECurrentPower(is) > POWER_PER_USE) {
                if (colorableTile.getColor() != AEColor.TRANSPARENT
                    && colorableTile.recolourBlock(side, AEColor.TRANSPARENT, p)) {
                    consumeColor(is, color, false);
                    return EnumActionResult.SUCCESS;
                }
            }
        } else if (this.getAECurrentPower(is) > POWER_PER_USE && this.recolourBlock(block, side, w, pos, color, p)) {
            consumeColor(is, color, false);
            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.FAIL;
    }

    public boolean consumeColor(ItemStack applicator, AEColor color, boolean simulate) {
        ae2.api.storage.cells.StorageCell inv = StorageCells.getCellInventory(applicator, null);
        if (inv == null) {
            return false;
        }

        ae2.api.stacks.KeyCounter availableItems = inv.getAvailableStacks();
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : availableItems) {
            if (getColorFromKey(entry.getKey()) == color) {
                return consumeItem(applicator, entry.getKey(), simulate);
            }
        }

        return false;
    }

    public boolean consumeItem(ItemStack applicator, AEKey key, boolean simulate) {
        ae2.api.storage.cells.StorageCell inv = StorageCells.getCellInventory(applicator, null);
        if (inv == null) {
            return false;
        }

        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        boolean success = inv.extract(key, 1, mode, new BaseActionSource()) >= 1
            && this.extractAEPower(applicator, POWER_PER_USE, mode) >= POWER_PER_USE;

        if (success && !simulate && getColorFromKey(key) == getActiveColor(applicator)
            && inv.getAvailableStacks().get(key) == 0) {
            setColor(applicator, ItemStack.EMPTY);
        }

        return success;
    }

    public AEColor getActiveColor(final ItemStack tol) {
        return this.getSelectedColor(tol);
    }

    private @Nullable AEColor getColorFromItem(final ItemStack paintBall) {
        if (paintBall.isEmpty()) {
            return null;
        }

        if (paintBall.getItem() instanceof ItemSnowball) {
            return AEColor.TRANSPARENT;
        }

        final int[] ids = OreDictionary.getOreIDs(paintBall);
        for (final int oreID : ids) {
            if (ORE_TO_COLOR.containsKey(oreID)) {
                return ORE_TO_COLOR.get(oreID);
            }
        }

        return null;
    }

    private @Nullable AEColor getColorFromKey(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return getColorFromItem(itemKey.toStack());
        }
        return null;
    }

    public ItemStack getColor(final ItemStack is) {
        final AEColor selectedColor = getSelectedColor(is);
        if (selectedColor != null) {
            ItemStack selectedColorStack = findColorStack(is, selectedColor);
            if (!selectedColorStack.isEmpty()) {
                return selectedColorStack;
            }
        }

        return this.findNextColor(is, ItemStack.EMPTY, 0);
    }

    private ItemStack findNextColor(final ItemStack is, final ItemStack anchor, final int scrollOffset) {
        ItemStack newColor = ItemStack.EMPTY;

        ae2.api.storage.cells.StorageCell inv = StorageCells.getCellInventory(is, null);
        if (inv != null) {
            ae2.api.stacks.KeyCounter keyList = inv.getAvailableStacks();
            if (anchor.isEmpty()) {
                AEKey firstItem = keyList.getFirstKey();
                if (firstItem instanceof AEItemKey itemKey) {
                    newColor = itemKey.toStack();
                }
            } else {
                final ObjectList<AEItemKey> list = new ObjectArrayList<>();
                for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : keyList) {
                    if (entry.getKey() instanceof AEItemKey itemKey) {
                        list.add(itemKey);
                    }
                }

                list.sort(Comparator.comparingInt(key -> key.toStack().getItemDamage()));
                if (list.isEmpty()) {
                    return ItemStack.EMPTY;
                }

                AEItemKey where = list.getFirst();
                int cycles = 1 + list.size();
                AEItemKey anchorKey = AEItemKey.of(anchor);

                while (cycles > 0 && !where.equals(anchorKey)) {
                    list.add(list.removeFirst());
                    cycles--;
                    where = list.getFirst();
                }

                if (scrollOffset > 0) {
                    list.add(list.removeFirst());
                }

                if (scrollOffset < 0) {
                    list.addFirst(list.removeLast());
                }

                return list.getFirst().toStack();
            }
        }

        if (!newColor.isEmpty()) {
            this.setColor(is, newColor);
        }

        return newColor;
    }

    private void setColor(final ItemStack is, final ItemStack newColor) {
        setSelectedColor(is, getColorFromItem(newColor));
    }

    private boolean recolourBlock(final Block blk, final EnumFacing side, final World w, final BlockPos pos,
                                  final AEColor newColor, final EntityPlayer p) {
        final IBlockState state = w.getBlockState(pos);

        if (blk instanceof BlockColored) {
            if (newColor.dye == state.getValue(BlockColored.COLOR)) {
                return false;
            }
            return w.setBlockState(pos, state.withProperty(BlockColored.COLOR, newColor.dye));
        }

        if (blk == Blocks.GLASS) {
            return w.setBlockState(pos,
                Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockStainedGlass.COLOR, newColor.dye));
        }

        if (blk == Blocks.STAINED_GLASS) {
            if (newColor.dye == state.getValue(BlockStainedGlass.COLOR)) {
                return false;
            }
            return w.setBlockState(pos, state.withProperty(BlockStainedGlass.COLOR, newColor.dye));
        }

        if (blk == Blocks.GLASS_PANE) {
            return w.setBlockState(pos, Blocks.STAINED_GLASS_PANE.getDefaultState()
                                                                 .withProperty(BlockStainedGlassPane.COLOR, newColor.dye));
        }

        if (blk == Blocks.STAINED_GLASS_PANE) {
            if (newColor.dye == state.getValue(BlockStainedGlassPane.COLOR)) {
                return false;
            }
            return w.setBlockState(pos, state.withProperty(BlockStainedGlassPane.COLOR, newColor.dye));
        }

        Object tile = w.getTileEntity(pos);
        if (tile instanceof IColorableBlockEntity colorableBlockEntity) {
            return colorableBlockEntity.recolourBlock(side, newColor, p);
        }

        if (tile instanceof IColorableTile colorableTile) {
            return colorableTile.recolourBlock(side, newColor, p);
        }

        return blk.recolorBlock(w, pos, side, newColor.dye);
    }

    public void cycleColors(final ItemStack is, final ItemStack paintBall, final int i) {
        if (paintBall.isEmpty()) {
            this.setColor(is, this.getColor(is));
        } else {
            this.setColor(is, this.findNextColor(is, paintBall, i));
        }
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        addCellInformationToTooltip(stack, lines);
    }

    @Override
    public int getBytes(final ItemStack cellItem) {
        return 512;
    }

    @Override
    public int getBytesPerType(final ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return 27;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        return getColorFromKey(requestedAddition) == null;
    }

    @Override
    public boolean storableInStorageCell() {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag != null && tag.hasKey(FUZZY_MODE_TAG, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(FUZZY_MODE_TAG));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        final NBTTagCompound tag = openNbtData(is);
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
        tag.removeTag(FUZZY_MODE_TAG);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 2, this::onUpgradesChanged);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Collections.singleton(AEKeyType.items()), is);
    }

    @Override
    public void onWheel(ItemStack is, boolean up) {
        cycleColors(is, getColor(is), up ? 1 : -1);
    }

    public void setActiveColor(ItemStack applicator, @Nullable AEColor color) {
        if (color == null) {
            setSelectedColor(applicator, null);
            return;
        }

        ae2.api.storage.cells.StorageCell inv = StorageCells.getCellInventory(applicator, null);
        if (inv == null) {
            return;
        }

        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : inv.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                if (getColorFromKey(itemKey) == color) {
                    setSelectedColor(applicator, color);
                    return;
                }
            }
        }
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPower(stack, AEConfig.instance().getColorApplicatorBattery()
            * (1 + Upgrades.getEnergyCardMultiplier(upgrades) * 8));
    }

    @Nullable
    private AEColor getSelectedColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }

        if (tag.hasKey(SELECTED_COLOR, 8)) {
            try {
                return AEColor.valueOf(tag.getString(SELECTED_COLOR));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (tag.hasKey("color", 10)) {
            ItemStack legacyColor = new ItemStack(tag.getCompoundTag("color"));
            return getColorFromItem(legacyColor);
        }

        return null;
    }

    private void setSelectedColor(ItemStack stack, @Nullable AEColor color) {
        final NBTTagCompound tag = openNbtData(stack);
        if (color == null) {
            tag.removeTag(SELECTED_COLOR);
            tag.removeTag("color");
            return;
        }

        tag.setString(SELECTED_COLOR, color.name());
        tag.removeTag("color");
    }

    private ItemStack findColorStack(ItemStack applicator, AEColor color) {
        ae2.api.storage.cells.StorageCell inv = StorageCells.getCellInventory(applicator, null);
        if (inv == null) {
            return ItemStack.EMPTY;
        }

        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : inv.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey && getColorFromKey(itemKey) == color) {
                return itemKey.toStack();
            }
        }

        return ItemStack.EMPTY;
    }
}
