package ae2.hooks;

import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.WirelessTerminalPickBlockPacket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public final class WirelessTerminalPickBlockHook {
    private static final int COOLDOWN_TICKS = 5;
    private static int cooldownTicks;

    private WirelessTerminalPickBlockHook() {
    }

    public static void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public static void onPickBlock(EntityPlayerSP player, RayTraceResult hitResult) {
        if (cooldownTicks > 0 || player.isCreative() || hitResult.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        World world = player.world;
        BlockPos pos = hitResult.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos)) {
            return;
        }

        ItemStack result = state.getBlock().getPickBlock(state, hitResult, world, pos, player).copy();
        if (result.isEmpty()) {
            return;
        }

        result.setCount(player.isSneaking() ? 1 : result.getItem().getItemStackLimit(result));

        InventoryPlayer inventory = player.inventory;
        ItemStack currentStack = inventory.getCurrentItem();
        if (canTopOffCurrentStack(currentStack, result)) {
            InitNetwork.sendToServer(new WirelessTerminalPickBlockPacket(result, inventory.currentItem));
            cooldownTicks = COOLDOWN_TICKS;
            return;
        }

        var decision = WirelessTerminalPickBlockLogic.findTargetSlot(
            findMatchingInventorySlot(inventory, result),
            inventory.currentItem,
            currentStack.isEmpty(),
            findFirstEmptyHotbarSlot(inventory));

        if (decision.shouldSelectHotbarSlot()) {
            inventory.currentItem = decision.hotbarSlot();
            return;
        }

        if (!decision.shouldRequestFromNetwork()) {
            return;
        }

        inventory.currentItem = decision.hotbarSlot();
        InitNetwork.sendToServer(new WirelessTerminalPickBlockPacket(result, decision.hotbarSlot()));
        cooldownTicks = COOLDOWN_TICKS;
    }

    private static int findMatchingInventorySlot(InventoryPlayer inventory, ItemStack target) {
        for (int slot = 0; slot < inventory.mainInventory.size(); slot++) {
            if (stacksMatch(target, inventory.mainInventory.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static int findFirstEmptyHotbarSlot(InventoryPlayer inventory) {
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack expected, ItemStack actual) {
        return !expected.isEmpty()
            && !actual.isEmpty()
            && ItemStack.areItemsEqual(expected, actual)
            && ItemStack.areItemStackTagsEqual(expected, actual);
    }

    private static boolean canTopOffCurrentStack(ItemStack currentStack, ItemStack target) {
        if (!stacksMatch(target, currentStack)) {
            return false;
        }

        int stackLimit = Math.min(currentStack.getMaxStackSize(), target.getItem().getItemStackLimit(target));
        return currentStack.getCount() < stackLimit;
    }
}
