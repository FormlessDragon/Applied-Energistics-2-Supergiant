package ae2.hooks;

import ae2.helpers.WirelessTerminalActions;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
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
        if (hand == null || original == null || original.isEmpty()) {
            return;
        }
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

    private static boolean isServerPlayer(EntityPlayer player) {
        World world = player != null ? player.world : null;
        return world != null && !world.isRemote && player instanceof EntityPlayerMP;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer playerEntity = event.player;
        if (event.phase != TickEvent.Phase.END || !isServerPlayer(playerEntity)
            || !(playerEntity instanceof EntityPlayerMP player)) {
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PortableItemCellAutoPickup.clearTickCaches();
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        EntityPlayer playerEntity = event.getEntityPlayer();
        if (!isServerPlayer(playerEntity) || !(playerEntity instanceof EntityPlayerMP player)
            || event.getItem() == null) {
            return;
        }
        if (WirelessTerminalActions.tryPickupToME(player, event.getItem()) && event.getItem().isDead) {
            event.setCanceled(true);
            return;
        }
        PortableItemCellAutoPickup.PickupResult result = PortableItemCellAutoPickup.tryPickup(player, event.getItem());
        if (result == PortableItemCellAutoPickup.PickupResult.COMPLETE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP player) || !isServerPlayer(player)) {
            return;
        }
        ItemStack original = event.getItem().copy();
        WirelessTerminalActions.restockStack(player, original, event.getResultStack(), event::setResultStack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer playerEntity = event.getEntityPlayer();
        World world = event.getWorld();
        if (event.isCanceled() || world == null || world.isRemote || !isServerPlayer(playerEntity)
            || !(playerEntity instanceof EntityPlayerMP player)) {
            return;
        }
        restockHeldItem(player, event.getHand(), event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        EntityPlayer playerEntity = event.getEntityPlayer();
        World world = event.getWorld();
        if (event.isCanceled() || world == null || world.isRemote || !isServerPlayer(playerEntity)
            || !(playerEntity instanceof EntityPlayerMP player)) {
            return;
        }
        restockHeldItem(player, event.getHand(), event.getItemStack());
    }

    @SubscribeEvent
    public void onArrowNock(ArrowNockEvent event) {
        EntityPlayer playerEntity = event.getEntityPlayer();
        if (!event.hasAmmo() || !isServerPlayer(playerEntity) || !(playerEntity instanceof EntityPlayerMP player)) {
            return;
        }
        restockProjectile(player);
    }

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        EntityPlayer playerEntity = event.getEntityPlayer();
        if (!event.hasAmmo() || !isServerPlayer(playerEntity) || !(playerEntity instanceof EntityPlayerMP player)) {
            return;
        }
        restockProjectile(player);
    }
}
