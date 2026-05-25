package appeng.container.me.common;

import appeng.api.implementations.guiobjects.IPortableTerminal;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageHelper;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.gui.locator.GuiHostLocators;
import appeng.helpers.WirelessTerminalActions;
import appeng.integration.modules.baubles.BaublesIntegration;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.ActionHostEnergySource;
import appeng.me.helpers.PlayerSource;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public final class MEIngredientActions {
    private MEIngredientActions() {
    }

    public static boolean handleContainer(ContainerMEStorage container, EntityPlayerMP player, MEIngredientAction action,
                                          GenericStack stack) {
        return switch (action) {
            case RETRIEVE -> stack.what() instanceof AEItemKey itemKey && container.retrieveItemToPlayer(itemKey);
            case CRAFT -> container.openCraftAmount(player, stack.what());
        };
    }

    public static void handleWirelessTerminals(EntityPlayerMP player, MEIngredientAction action,
                                               GenericStack stack) {
        int inventorySize = player.inventory.getSizeInventory();
        for (int slot = 0; slot < inventorySize; slot++) {
            if (!isWirelessTerminal(player.inventory.getStackInSlot(slot))) {
                continue;
            }
            if (handleWirelessTerminal(player, GuiHostLocators.forInventorySlot(slot), action, stack)) {
                return;
            }
        }

        int baubleSlots = BaublesIntegration.getSlots(player);
        for (int slot = 0; slot < baubleSlots; slot++) {
            if (!isWirelessTerminal(BaublesIntegration.getStackInSlot(player, slot))) {
                continue;
            }
            if (handleWirelessTerminal(player, GuiHostLocators.forBaubleSlot(slot), action, stack)) {
                return;
            }
        }

    }

    public static void retrieveToHotbarFromWirelessTerminals(EntityPlayerMP player, ItemStack target,
                                                             int hotbarSlot) {
        if (target.isEmpty() || !InventoryPlayer.isHotbar(hotbarSlot)) {
            return;
        }
        if (!WirelessTerminalActions.isPickBlockEnabled(player)) {
            return;
        }

        AEItemKey targetKey = AEItemKey.of(target);
        if (targetKey == null) {
            return;
        }

        int inventorySize = player.inventory.getSizeInventory();
        for (int slot = 0; slot < inventorySize; slot++) {
            if (!isWirelessTerminal(player.inventory.getStackInSlot(slot))) {
                continue;
            }
            if (retrieveToHotbarFromWirelessTerminal(player, GuiHostLocators.forInventorySlot(slot), targetKey, target,
                hotbarSlot)) {
                return;
            }
        }

        int baubleSlots = BaublesIntegration.getSlots(player);
        for (int slot = 0; slot < baubleSlots; slot++) {
            if (!isWirelessTerminal(BaublesIntegration.getStackInSlot(player, slot))) {
                continue;
            }
            if (retrieveToHotbarFromWirelessTerminal(player, GuiHostLocators.forBaubleSlot(slot), targetKey, target,
                hotbarSlot)) {
                return;
            }
        }

    }

    private static boolean handleWirelessTerminal(EntityPlayerMP player, GuiHostLocator locator,
                                                  MEIngredientAction action, GenericStack stack) {
        ITerminalHost host = locator.locate(player, ITerminalHost.class);
        if (!(host instanceof IPortableTerminal) || !host.getLinkStatus().connected()) {
            return false;
        }

        if (!(host instanceof IActionHost actionHost)) {
            return false;
        }

        IGridNode node = actionHost.getActionableNode();
        if (node == null || !node.isActive()) {
            return false;
        }

        return switch (action) {
            case RETRIEVE -> stack.what() instanceof AEItemKey itemKey
                && retrieveFromWirelessTerminal(player, host, actionHost, itemKey);
            case CRAFT -> openWirelessCraftAmount(player, locator, node, stack.what(), stack.amount(), true);
        };
    }

    private static boolean retrieveFromWirelessTerminal(EntityPlayerMP player, ITerminalHost host,
                                                        IActionHost actionHost, AEItemKey what) {
        int amount = Math.min(what.getMaxStackSize(), getInsertableAmount(player.inventory, what));
        if (amount <= 0) {
            return false;
        }

        IEnergySource energySource = host instanceof IEnergySource source
            ? source
            : new ActionHostEnergySource(actionHost);
        long extracted = StorageHelper.poweredExtraction(energySource, host.getInventory(), what, amount,
            new PlayerSource(player, actionHost));
        if (extracted <= 0) {
            return false;
        }

        ItemStack extractedStack = what.toStack((int) extracted);
        return player.inventory.addItemStackToInventory(extractedStack);
    }

    private static boolean openWirelessCraftAmount(EntityPlayerMP player, GuiHostLocator locator, IGridNode node,
                                                   AEKey what, long initialAmount,
                                                   boolean preserveReturnToContainerOverride) {
        if (!node.grid().getCraftingService().isCraftable(what)) {
            return false;
        }

        int amount = Math.clamp(initialAmount, what.getAmountPerUnit(), Integer.MAX_VALUE);
        if (preserveReturnToContainerOverride) {
            ContainerCraftAmount.open(player, locator, what, amount, player.openContainer);
        } else {
            ContainerCraftAmount.open(player, locator, what, amount);
        }
        return true;
    }

    private static boolean retrieveToHotbarFromWirelessTerminal(EntityPlayerMP player, GuiHostLocator locator,
                                                                AEItemKey targetKey, ItemStack target, int hotbarSlot) {
        ITerminalHost host = locator.locate(player, ITerminalHost.class);
        if (!(host instanceof IPortableTerminal) || !host.getLinkStatus().connected()) {
            return false;
        }

        if (!(host instanceof IActionHost actionHost)) {
            return false;
        }

        IGridNode node = actionHost.getActionableNode();
        if (node == null || !node.isActive()) {
            return false;
        }

        return retrieveToHotbar(player, locator, host, actionHost, node, targetKey, target, hotbarSlot);
    }

    private static boolean retrieveToHotbar(EntityPlayerMP player, GuiHostLocator locator, ITerminalHost host,
                                            IActionHost actionHost, IGridNode node, AEItemKey targetKey,
                                            ItemStack target, int hotbarSlot) {
        ItemStack hotbarStack = player.inventory.getStackInSlot(hotbarSlot);
        if (!hotbarStack.isEmpty()) {
            AEItemKey hotbarKey = AEItemKey.of(hotbarStack);
            if (hotbarKey == null || !hotbarKey.equals(targetKey)) {
                return false;
            }
        }

        int amount = getRetrievableHotbarAmount(target, hotbarStack, targetKey);
        if (amount <= 0) {
            return false;
        }

        IEnergySource energySource = host instanceof IEnergySource source
            ? source
            : new ActionHostEnergySource(actionHost);
        long extracted = StorageHelper.poweredExtraction(energySource, host.getInventory(), targetKey, amount,
            new PlayerSource(player, actionHost));
        if (extracted <= 0) {
            if (!WirelessTerminalActions.isCraftIfMissingEnabled(player)) {
                return false;
            }
            return openWirelessCraftAmount(player, locator, node, targetKey, target.getCount(), false);
        }

        if (hotbarStack.isEmpty()) {
            player.inventory.setInventorySlotContents(hotbarSlot, targetKey.toStack((int) extracted));
        } else {
            hotbarStack.grow((int) extracted);
            player.inventory.setInventorySlotContents(hotbarSlot, hotbarStack);
        }
        return true;
    }

    private static int getRetrievableHotbarAmount(ItemStack target, ItemStack hotbarStack, AEItemKey targetKey) {
        int maxStackSize = targetKey.getMaxStackSize();
        int requestedAmount = Math.min(target.getCount(), maxStackSize);
        if (hotbarStack.isEmpty()) {
            return requestedAmount;
        }

        int slotLimit = Math.min(hotbarStack.getMaxStackSize(), maxStackSize);
        return Math.clamp(slotLimit - hotbarStack.getCount(), 0, requestedAmount);
    }

    private static int getInsertableAmount(InventoryPlayer inventory, AEItemKey what) {
        int result = 0;
        int maxStackSize = what.getMaxStackSize();
        for (ItemStack stack : inventory.mainInventory) {
            if (stack.isEmpty()) {
                result += maxStackSize;
            } else if (what.matches(stack) && stack.getCount() < Math.min(stack.getMaxStackSize(), maxStackSize)) {
                result += Math.min(stack.getMaxStackSize(), maxStackSize) - stack.getCount();
            }

            if (result >= maxStackSize) {
                return maxStackSize;
            }
        }
        return result;
    }

    private static boolean isWirelessTerminal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof WirelessTerminalItem;
    }
}
