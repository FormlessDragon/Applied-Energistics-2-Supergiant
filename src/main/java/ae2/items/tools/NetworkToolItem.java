/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.IStackTooltipDataProvider;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.INetworkToolAware;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.AEBaseItem;
import ae2.items.contents.NetworkToolGuiHost;
import ae2.items.storage.StorageCellTooltipComponent;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class NetworkToolItem extends AEBaseItem implements IGuiItem, IStackTooltipDataProvider {
    private static final String INVENTORY_TAG = "inv";

    public NetworkToolItem() {
        this.setMaxStackSize(1);
    }

    @Nullable
    public static NetworkToolGuiHost<?> findNetworkToolInv(EntityPlayer player) {
        InventoryPlayer inventory = player.inventory;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof NetworkToolItem networkToolItem) {
                return networkToolItem.getGuiHost(player, GuiHostLocators.forInventorySlot(slot), null);
            }
        }
        return null;
    }

    public static InternalInventory getInventory(ItemStack stack) {
        AppEngInternalInventory inventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    stack.setTagCompound(tag);
                }
                inv.writeToNBT(tag, INVENTORY_TAG);
            }

            @Override
            public boolean isClientSide() {
                return false;
            }
        }, 9);
        inventory.setEnableClientEvents(true);
        inventory.setFilter(new NetworkToolInventoryFilter());
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            inventory.readFromNBT(tag, INVENTORY_TAG);
        }
        return inventory;
    }

    private static Object2IntLinkedOpenHashMap<Item> getUpgradeCounts(ItemStack stack) {
        var result = new Object2IntLinkedOpenHashMap<Item>();
        for (var upgrade : getInventory(stack)) {
            if (!upgrade.isEmpty()) {
                result.addTo(upgrade.getItem(), upgrade.getCount());
            }
        }
        return result;
    }

    private static List<Object2IntMap.Entry<Item>> getSortedUpgradeEntries(ItemStack stack) {
        List<Object2IntMap.Entry<Item>> result = new ObjectArrayList<>();
        result.addAll(getUpgradeCounts(stack).object2IntEntrySet());
        result.sort(Comparator.<Object2IntMap.Entry<Item>>comparingInt(Object2IntMap.Entry::getIntValue)
                              .reversed()
                              .thenComparing(entry -> TextComponentItemStack.of(new ItemStack(entry.getKey())).getFormattedText()));
        return result;
    }

    @Override
    public NetworkToolGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                            @Nullable RayTraceResult hitResult) {
        if (hitResult == null || hitResult.getBlockPos() == null) {
            return new NetworkToolGuiHost<>(this, player, locator, null);
        }

        IInWorldGridNodeHost host = GridHelper.getNodeHost(player.world, hitResult.getBlockPos());
        return new NetworkToolGuiHost<>(this, player, locator, host);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        var held = player.getHeldItem(hand);
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.NETWORK_TOOL, GuiHostLocators.forHand(player, hand));
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        RayTraceResult hitResult = GuiHostLocators.createItemUseHitResult(pos, side, hitX, hitY, hitZ);
        TileEntity tileEntity = world.getTileEntity(pos);

        if (tileEntity instanceof IPartHost partHost) {
            SelectedPart selectedPart = partHost.selectPartLocal(new Vec3d(hitX, hitY, hitZ));
            if ((selectedPart.part != null || selectedPart.facade != null)
                && selectedPart.part instanceof INetworkToolAware
                && !((INetworkToolAware) selectedPart.part).showNetworkInfo(player, world, pos, hand,
                player.getHeldItem(hand), hitResult)) {
                return EnumActionResult.PASS;
            }
        } else if (tileEntity instanceof INetworkToolAware toolAgent) {
            if (!toolAgent.showNetworkInfo(player, world, pos, hand, player.getHeldItem(hand), hitResult)) {
                return EnumActionResult.PASS;
            }
        }

        if (!world.isRemote && !showNetworkToolGui(player, world, pos, side, hitX, hitY, hitZ, hand)) {
            return EnumActionResult.FAIL;
        }

        return EnumActionResult.SUCCESS;
    }

    private boolean showNetworkToolGui(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                       float hitY, float hitZ, EnumHand hand) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            return false;
        }

        IInWorldGridNodeHost nodeHost = GridHelper.getNodeHost(world, pos);
        if (nodeHost != null) {
            return GuiOpener.openItemGui(player, GuiIds.GuiKey.NETWORK_STATUS,
                GuiHostLocators.forItemUseContext(player, hand, pos, side, hitX, hitY, hitZ));
        }

        return GuiOpener.openItemGui(player, GuiIds.GuiKey.NETWORK_TOOL, GuiHostLocators.forHand(player, hand));
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
    }

    @Override
    public boolean onStackedOnOther(ItemStack toolStack, Slot slot, EntityPlayer player) {
        if (!slot.canTakeStack(player)) {
            return false;
        }

        var other = slot.getStack();
        if (other.isEmpty()) {
            return true;
        }

        insertIntoTool(toolStack, other, player);
        return true;
    }

    @Override
    public boolean onOtherStackedOnMe(ItemStack toolStack, ItemStack otherStack, Slot slot, EntityPlayer player) {
        if (!slot.canTakeStack(player)) {
            return false;
        }

        if (otherStack.isEmpty()) {
            return false;
        }

        insertIntoTool(toolStack, otherStack, player);
        return true;
    }

    private void insertIntoTool(ItemStack tool, ItemStack upgrade, EntityPlayer player) {
        NetworkToolGuiHost<?> toolHost = new NetworkToolGuiHost<>(this, player, GuiHostLocators.forStack(tool), null);
        int amount = upgrade.getCount();
        ItemStack overflow = toolHost.getInventory().addItems(upgrade);
        upgrade.shrink(amount - overflow.getCount());
    }

    @Override
    public Optional<StorageCellTooltipComponent> getStackTooltipData(ItemStack stack) {
        var upgrades = new ObjectArrayList<GenericStack>();
        for (Object2IntMap.Entry<Item> entry : getSortedUpgradeEntries(stack)) {
            upgrades.add(new GenericStack(AEItemKey.of(new ItemStack(entry.getKey())),
                entry.getIntValue()));
        }
        return upgrades.isEmpty() ? Optional.empty()
            : Optional.of(new StorageCellTooltipComponent(Collections.emptyList(), upgrades,
            false, true));
    }

    private static class NetworkToolInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return Upgrades.isUpgradeCardItem(stack.getItem());
        }
    }
}
