package ae2.helpers;

import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.StorageHelper;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.PlayerMessages;
import ae2.integration.modules.baubles.BaublesIntegration;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalMagnetMode;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.me.helpers.ActionHostEnergySource;
import ae2.me.helpers.PlayerSource;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class WirelessTerminalActions {
    private static final ExecutorService RESTOCK_CHECK_POOL;
    private static final ConcurrentMap<RestockPlanKey, PendingRestockPlan> PENDING_RESTOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<RestockPlanKey, Boolean> RESTOCKS_IN_FLIGHT =
        ConcurrentHashMap.newKeySet();

    static {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "AE Wireless Restock Checker");
            thread.setDaemon(true);
            return thread;
        };
        RESTOCK_CHECK_POOL = Executors.newSingleThreadExecutor(factory);
    }

    private WirelessTerminalActions() {
    }

    public static void clear() {
        PENDING_RESTOCKS.clear();
        RESTOCKS_IN_FLIGHT.clear();
    }

    public static void clear(UUID playerId) {
        PENDING_RESTOCKS.keySet().removeIf(key -> key.playerId.equals(playerId));
        RESTOCKS_IN_FLIGHT.removeIf(key -> key.playerId.equals(playerId));
    }

    public static boolean toggleRestock(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player, ignored -> true);
        if (context == null) {
            return false;
        }
        boolean enabled = !WirelessTerminals.isRestockEnabled(context.stack, context.terminal);
        WirelessTerminals.setRestockEnabled(context.stack, context.terminal, enabled);
        player.sendStatusMessage((enabled ? PlayerMessages.WirelessRestockEnabled
            : PlayerMessages.WirelessRestockDisabled).text(), true);
        return true;
    }

    public static boolean cycleMagnetMode(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player, WirelessTerminalActions::hasMagnetCard);
        if (context == null) {
            return false;
        }
        WirelessTerminalMagnetMode next = WirelessTerminals.getMagnetMode(context.stack, context.terminal).next();
        WirelessTerminals.setMagnetMode(context.stack, context.terminal, next);
        player.sendStatusMessage(getMagnetModeMessage(next).text(), true);
        return true;
    }

    public static boolean stowInventory(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player, ignored -> true);
        if (context == null) {
            return false;
        }
        int slot = player.inventory.currentItem;
        Integer terminalSlot = context.locator.getPlayerInventorySlot();
        if (terminalSlot != null && terminalSlot == slot) {
            return false;
        }
        ItemStack stack = player.inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return false;
        }
        if (!insertStack(context, stack)) {
            return false;
        }
        player.inventory.setInventorySlotContents(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        return true;
    }

    public static void restock(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player,
            terminal -> WirelessTerminals.isRestockEnabled(terminal.stack, terminal.terminal));
        if (context == null) {
            return;
        }

        applyPendingRestocks(player, context);
        scheduleRestockCheck(player, context);
    }

    private static void scheduleRestockCheck(EntityPlayerMP player, TerminalContext context) {
        RestockPlanKey planKey = new RestockPlanKey(player.getUniqueID(), createTerminalKey(context.stack));
        if (!RESTOCKS_IN_FLIGHT.add(planKey)) {
            return;
        }

        RestockSlotSnapshot[] snapshots = new RestockSlotSnapshot[player.inventory.getSizeInventory()];
        for (int slot = 0; slot < snapshots.length; slot++) {
            snapshots[slot] = RestockSlotSnapshot.of(slot, player.inventory.getStackInSlot(slot));
        }

        RESTOCK_CHECK_POOL.execute(() -> {
            try {
                PENDING_RESTOCKS.put(planKey, computePendingRestockPlan(snapshots));
            } finally {
                RESTOCKS_IN_FLIGHT.remove(planKey);
            }
        });
    }

    private static void applyPendingRestocks(EntityPlayerMP player, TerminalContext context) {
        RestockPlanKey planKey = new RestockPlanKey(player.getUniqueID(), createTerminalKey(context.stack));
        PendingRestockPlan plan = PENDING_RESTOCKS.remove(planKey);
        if (plan == null) {
            return;
        }

        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            RestockRequest request = plan.requestForSlot(slot);
            if (request == null) {
                continue;
            }

            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            if (!request.matches(stack, key)) {
                continue;
            }

            int targetAmount = request.amount();
            long extracted = StorageHelper.poweredExtraction(context.energySource, context.host.getInventory(),
                key, targetAmount, new PlayerSource(player, context.actionHost));
            if (extracted > 0) {
                stack.grow((int) extracted);
                player.inventory.setInventorySlotContents(slot, stack);
            }
        }
    }

    public static void restockStack(EntityPlayerMP player, ItemStack original, ItemStack current,
                                    Consumer<ItemStack> setter) {
        if (player.capabilities.isCreativeMode || original.isEmpty()) {
            return;
        }
        int currentCount = current.isEmpty() ? 0 : current.getCount();
        if (currentCount >= original.getMaxStackSize()) {
            return;
        }
        if (!current.isEmpty() && (!ItemStack.areItemsEqual(original, current)
            || !ItemStack.areItemStackTagsEqual(original, current))) {
            return;
        }
        AEItemKey key = AEItemKey.of(original);
        if (key == null) {
            return;
        }
        TerminalContext context = findUsableTerminal(player,
            terminal -> WirelessTerminals.isRestockEnabled(terminal.stack, terminal.terminal));
        if (context == null) {
            return;
        }

        int targetAmount = WirelessTerminalRestockLogic.getRestockAmount(current.isEmpty() ? original : current, key);
        if (targetAmount <= 0) {
            return;
        }
        long extracted = StorageHelper.poweredExtraction(context.energySource, context.host.getInventory(),
            key, targetAmount, new PlayerSource(player, context.actionHost));
        if (extracted <= 0) {
            return;
        }

        ItemStack result = current.isEmpty() ? original.copy() : current.copy();
        result.setCount(currentCount + (int) extracted);
        setter.accept(result);
        player.inventoryContainer.detectAndSendChanges();
    }

    public static ItemStack extractStack(EntityPlayerMP player, ItemStack template, int amount) {
        if (amount <= 0 || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        AEItemKey key = AEItemKey.of(template);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        TerminalContext context = findUsableTerminal(player, ignored -> true);
        if (context == null) {
            return ItemStack.EMPTY;
        }

        long extracted = StorageHelper.poweredExtraction(context.energySource, context.host.getInventory(), key,
            amount, new PlayerSource(player, context.actionHost));
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = template.copy();
        result.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
        return result;
    }

    public static void tryMagnetPickup(EntityPlayerMP player, EntityItem entityItem) {
        tryPickup(player, entityItem, true);
    }

    public static boolean tryPickupToME(EntityPlayerMP player, EntityItem entityItem) {
        return tryPickup(player, entityItem, false);
    }

    private static boolean tryPickup(EntityPlayerMP player, EntityItem entityItem, boolean allowRemoteMagnet) {
        ItemStack itemStack = entityItem.getItem();
        if (itemStack.isEmpty()) {
            return false;
        }
        if (player.isSneaking()) {
            return false;
        }
        AEItemKey key = AEItemKey.of(itemStack);
        if (key == null) {
            return false;
        }

        TerminalContext context = findUsableTerminal(player, terminal -> {
            if (!hasMagnetCard(terminal)) {
                return false;
            }
            WirelessTerminalMagnetMode mode = WirelessTerminals.getMagnetMode(terminal.stack, terminal.terminal);
            return allowRemoteMagnet ? mode.magnet() : mode.pickupToME();
        });
        if (context == null) {
            return false;
        }

        WirelessTerminalMagnetHost magnetHost = context.host.getMagnetHost();
        if (!magnetHost.matchesPickup(key)) {
            return false;
        }
        WirelessTerminalMagnetMode mode = WirelessTerminals.getMagnetMode(context.stack, context.terminal);
        if (mode.pickupToME() && magnetHost.matchesInsert(key) && insertStack(context, itemStack)) {
            int pickedUp = entityItem.getItem().getCount() - itemStack.getCount();
            if (pickedUp > 0) {
                player.addStat(StatList.getObjectsPickedUpStats(key.getItem()), pickedUp);
                player.onItemPickup(entityItem, pickedUp);
            }
            entityItem.setItem(itemStack);
            if (itemStack.isEmpty()) {
                entityItem.setDead();
            }
            return true;
        }

        if (allowRemoteMagnet && mode.magnet()) {
            if (entityItem.getEntityData().hasKey("PreventRemoteMovement")) {
                return false;
            }
            int before = itemStack.getCount();
            boolean pickedUp = player.inventory.addItemStackToInventory(itemStack);
            if (itemStack.isEmpty()) {
                entityItem.setDead();
                return true;
            }
            if (pickedUp || itemStack.getCount() != before) {
                entityItem.setItem(itemStack);
                return true;
            }
        }
        return false;
    }

    public static boolean isPickBlockEnabled(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player,
            terminal -> WirelessTerminals.isPickBlockEnabled(terminal.stack, terminal.terminal));
        return context != null;
    }

    public static boolean isCraftIfMissingEnabled(EntityPlayerMP player) {
        TerminalContext context = findUsableTerminal(player,
            terminal -> WirelessTerminals.isCraftIfMissingEnabled(terminal.stack, terminal.terminal));
        return context != null;
    }

    private static boolean insertStack(TerminalContext context, ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }
        long inserted = StorageHelper.poweredInsert(context.energySource, context.host.getInventory(), key,
            stack.getCount(), new PlayerSource(context.player, context.actionHost));
        if (inserted <= 0) {
            return false;
        }
        stack.shrink((int) inserted);
        return true;
    }

    private static boolean hasMagnetCard(TerminalContext context) {
        return context.host.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
    }

    private static PendingRestockPlan computePendingRestockPlan(RestockSlotSnapshot[] snapshots) {
        RestockRequest[] requests = new RestockRequest[snapshots.length];
        for (RestockSlotSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.empty() || snapshot.maxStackSize() <= 1) {
                continue;
            }

            int amount = WirelessTerminalRestockLogic.getRestockAmount(snapshot.count(), snapshot.maxStackSize());
            if (amount <= 0) {
                continue;
            }

            requests[snapshot.slot()] = new RestockRequest(snapshot.itemId(), snapshot.damage(), snapshot.tag(), amount);
        }
        return new PendingRestockPlan(requests);
    }

    private static String createTerminalKey(ItemStack stack) {
        String itemId = stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "";
        NBTTagCompound tagCompound = stack.getTagCompound();
        String tag = tagCompound != null ? tagCompound.toString() : "";
        return itemId + "|" + tag;
    }

    private static PlayerMessages getMagnetModeMessage(WirelessTerminalMagnetMode mode) {
        return switch (mode) {
            case OFF -> PlayerMessages.WirelessMagnetOff;
            case PICKUP_INVENTORY -> PlayerMessages.WirelessMagnetInventory;
            case PICKUP_ME -> PlayerMessages.WirelessMagnetME;
            case PICKUP_ME_NO_MAGNET -> PlayerMessages.WirelessMagnetMENoMagnet;
        };
    }

    @Nullable
    private static TerminalContext findUsableTerminal(EntityPlayerMP player, Predicate<TerminalContext> predicate) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            TerminalContext context = getTerminal(player, GuiHostLocators.forInventorySlot(slot));
            if (context != null && predicate.test(context)) {
                return context;
            }
        }

        int baubleSlots = BaublesIntegration.getSlots(player);
        for (int slot = 0; slot < baubleSlots; slot++) {
            TerminalContext context = getTerminal(player, GuiHostLocators.forBaubleSlot(slot));
            if (context != null && predicate.test(context)) {
                return context;
            }
        }
        return null;
    }

    @Nullable
    private static TerminalContext getTerminal(EntityPlayerMP player, ItemGuiHostLocator locator) {
        ItemStack stack = locator.locateItem(player);
        WirelessTerminalItem terminal = WirelessTerminalRegistry.ofStack(stack);
        if (terminal == null) {
            return null;
        }

        WirelessTerminalGuiHost<?> host = locator.locate(player, WirelessTerminalGuiHost.class);
        if (host == null || !host.getLinkStatus().connected()) {
            return null;
        }
        IGridNode node = host.getActionableNode();
        if (node == null || !node.isActive()) {
            return null;
        }
        IEnergySource energySource = host instanceof IEnergySource source
            ? source
            : new ActionHostEnergySource(host);
        return new TerminalContext(player, locator, stack, terminal, host, host, node, energySource);
    }

    private record TerminalContext(EntityPlayerMP player, ItemGuiHostLocator locator, ItemStack stack,
                                   WirelessTerminalItem terminal, WirelessTerminalGuiHost<?> host,
                                   IActionHost actionHost,
                                   IGridNode node, IEnergySource energySource) {
    }

    private record RestockPlanKey(UUID playerId, String terminalKey) {
    }

    private record PendingRestockPlan(RestockRequest[] requests) {
        @Nullable
        private RestockRequest requestForSlot(int slot) {
            return slot >= 0 && slot < this.requests.length ? this.requests[slot] : null;
        }
    }

    private record RestockRequest(String itemId, int damage, @Nullable NBTTagCompound tag, int amount) {
        private boolean matches(ItemStack stack, AEItemKey key) {
            String registryName = key.getItem().getRegistryName() != null ? key.getItem().getRegistryName().toString() : "";
            if (!this.itemId.equals(registryName)) {
                return false;
            }
            if (stack.getItemDamage() != this.damage) {
                return false;
            }

            if (this.tag == null && !stack.hasTagCompound()) {
                return true;
            }
            return this.tag != null && stack.hasTagCompound() && this.tag.equals(stack.getTagCompound());
        }
    }

    private record RestockSlotSnapshot(int slot, boolean empty, int count, int maxStackSize, String itemId, int damage,
                                       @Nullable NBTTagCompound tag) {
        private static RestockSlotSnapshot of(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return new RestockSlotSnapshot(slot, true, 0, 0, "", 0, null);
            }

            String itemId = stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "";
            NBTTagCompound tag = stack.getTagCompound();
            return new RestockSlotSnapshot(slot, false, stack.getCount(), stack.getMaxStackSize(), itemId,
                stack.getItemDamage(), tag != null ? tag.copy() : null);
        }
    }
}
