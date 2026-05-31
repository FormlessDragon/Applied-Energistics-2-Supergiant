package ae2.hooks;

import ae2.helpers.WirelessTerminalActions;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

public class WirelessTerminalEventHandler {
    private static final double MAGNET_RANGE = 8.0;

    private static void restockHeldItem(EntityPlayerMP player, EnumHand hand, ItemStack original) {
        ItemStack item = original.copy();
        WirelessTerminalActions.restockStack(player, item, player.getHeldItem(hand),
            stack -> player.setHeldItem(hand, stack));
    }

    private static void restockProjectile(EntityPlayerMP player) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack held = player.getHeldItem(hand);
            if (held.getItem() instanceof ItemArrow) {
                restockHeldItem(player, hand, held);
                return;
            }
        }

        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.getItem() instanceof ItemArrow) {
                ItemStack original = stack.copy();
                final int targetSlot = slot;
                WirelessTerminalActions.restockStack(player, original, stack,
                    restocked -> player.inventory.setInventorySlotContents(targetSlot, restocked));
                return;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
            || !(event.player instanceof EntityPlayerMP player)) {
            return;
        }

        if (player.ticksExisted % 20 == 0) {
            WirelessTerminalActions.restock(player);
        }

        if (player.ticksExisted % 5 != 0) {
            return;
        }

        AxisAlignedBB box = player.getEntityBoundingBox().grow(MAGNET_RANGE);
        List<EntityItem> items = player.world.getEntitiesWithinAABB(EntityItem.class, box,
            item -> item != null && !item.isDead && !item.getItem().isEmpty());
        for (EntityItem item : items) {
            WirelessTerminalActions.tryMagnetPickup(player, item);
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntityPlayer().world.isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        if (WirelessTerminalActions.tryPickupToME(player, event.getItem()) && event.getItem().isDead) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntityLiving().world.isRemote || !(event.getEntityLiving() instanceof EntityPlayerMP player)) {
            return;
        }
        ItemStack original = event.getItem().copy();
        WirelessTerminalActions.restockStack(player, original, event.getResultStack(), event::setResultStack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getWorld().isRemote
            || !(event.getEntityPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        restockHeldItem(player, event.getHand(), event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.isCanceled() || event.getWorld().isRemote
            || !(event.getEntityPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        restockHeldItem(player, event.getHand(), event.getItemStack());
    }

    @SubscribeEvent
    public void onArrowNock(ArrowNockEvent event) {
        if (!event.hasAmmo() || event.getEntityPlayer().world.isRemote
            || !(event.getEntityPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        restockProjectile(player);
    }

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        if (!event.hasAmmo() || event.getEntityPlayer().world.isRemote
            || !(event.getEntityPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        restockProjectile(player);
    }
}
