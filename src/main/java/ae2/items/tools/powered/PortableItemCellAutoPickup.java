package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.config.IncludeExclude;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.StorageHelper;
import ae2.api.storage.cells.StorageCell;
import ae2.client.Hotkeys;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.integration.modules.baubles.BaublesIntegration;
import ae2.items.storage.PortableVoidCellItem;
import ae2.me.helpers.PlayerSource;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import ae2.util.helpers.ItemComparisonHelper;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.Nullable;

/**
 * Owns automatic pickup state and insertion behavior for item-based portable cells.
 */
public final class PortableItemCellAutoPickup {
    public static final String HOTKEY_ID = "portable_item_cell_auto_pickup";

    private static final String AUTO_PICKUP_TAG = "portable_item_cell_auto_pickup";
    private static final String PICKUP_CONFIG_TAG = "portable_item_cell_pickup_config";
    private static final String PICKUP_MODE_TAG = "portable_item_cell_pickup_mode";
    private static final String PICKUP_MATCH_NBT_TAG = "portable_item_cell_pickup_match_nbt";
    private static final String PICKUP_MATCH_DAMAGE_TAG = "portable_item_cell_pickup_match_damage";
    private static final String PICKUP_MATCH_ORE_TAG = "portable_item_cell_pickup_match_ore";
    private static final int PICKUP_CONFIG_SIZE = 63;
    private static final Reference2ObjectMap<ItemStack, StorageCell> CELL_CACHE = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectMap<ItemStack, ConfigInventory> PICKUP_CONFIG_CACHE =
        new Reference2ObjectOpenHashMap<>();

    private PortableItemCellAutoPickup() {
    }

    public static boolean isSupported(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof PortableVoidCellItem
            || stack.getItem() instanceof PortableCellItem cellItem && cellItem.getKeyType() == AEKeyType.items();
    }

    public static boolean isEnabled(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(AUTO_PICKUP_TAG);
    }

    public static void setEnabled(ItemStack stack, boolean enabled) {
        if (!isSupported(stack)) {
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(AUTO_PICKUP_TAG, enabled);
    }

    public static boolean toggle(ItemStack stack) {
        if (!isSupported(stack)) {
            return false;
        }
        setEnabled(stack, !isEnabled(stack));
        return true;
    }

    public static void invalidateCachedCell(ItemStack stack) {
        invalidateCachedState(stack);
    }

    public static void invalidateCachedState(ItemStack stack) {
        synchronized (CELL_CACHE) {
            CELL_CACHE.remove(stack);
        }
        synchronized (PICKUP_CONFIG_CACHE) {
            PICKUP_CONFIG_CACHE.remove(stack);
        }
    }

    public static void clearTickCaches() {
        synchronized (CELL_CACHE) {
            CELL_CACHE.clear();
        }
        synchronized (PICKUP_CONFIG_CACHE) {
            PICKUP_CONFIG_CACHE.clear();
        }
    }

    public static boolean toggleFirstAvailable(EntityPlayerMP player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (toggleAndNotify(player, stack)) {
                return true;
            }
        }

        for (int slot = 0; slot < BaublesIntegration.getSlots(player); slot++) {
            if (toggleAndNotify(player, BaublesIntegration.getStackInSlot(player, slot))) {
                return true;
            }
        }
        return false;
    }

    public static PickupResult tryPickup(EntityPlayerMP player, EntityItem entityItem) {
        ItemStack dropped = entityItem.getItem();
        AEItemKey key = AEItemKey.of(dropped);
        if (dropped.isEmpty() || key == null) {
            return PickupResult.NONE;
        }

        PickupResult bestResult = PickupResult.NONE;
        IActionSource source = new PlayerSource(player);
        for (ItemStack stack : player.inventory.mainInventory) {
            PickupResult result = tryPickupToCell(player, entityItem, dropped, key, stack, source);
            if (result == PickupResult.COMPLETE) {
                return PickupResult.COMPLETE;
            }
            if (result == PickupResult.PARTIAL) {
                bestResult = PickupResult.PARTIAL;
            }
        }

        for (int slot = 0; slot < BaublesIntegration.getSlots(player); slot++) {
            PickupResult result = tryPickupToCell(player, entityItem, dropped, key,
                BaublesIntegration.getStackInSlot(player, slot), source);
            if (result == PickupResult.COMPLETE) {
                return PickupResult.COMPLETE;
            }
            if (result == PickupResult.PARTIAL) {
                bestResult = PickupResult.PARTIAL;
            }
        }
        return bestResult;
    }

    public static ConfigInventory getPickupConfig(ItemStack stack) {
        assertSupportedCell(stack);
        synchronized (PICKUP_CONFIG_CACHE) {
            ConfigInventory cached = PICKUP_CONFIG_CACHE.get(stack);
            if (cached != null) {
                return cached;
            }

            var holder = new PickupConfigHolder(stack);
            holder.load();
            PICKUP_CONFIG_CACHE.put(stack, holder.config);
            return holder.config;
        }
    }

    public static ConfigInventory getConfigInventory(ItemStack stack) {
        return getPickupConfig(stack);
    }

    public static IncludeExclude getPickupMode(ItemStack stack) {
        assertSupportedCell(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PICKUP_MODE_TAG)) {
            return IncludeExclude.WHITELIST;
        }
        return tag.getBoolean(PICKUP_MODE_TAG) ? IncludeExclude.WHITELIST : IncludeExclude.BLACKLIST;
    }

    public static IncludeExclude getMode(ItemStack stack) {
        return getPickupMode(stack);
    }

    public static void togglePickupMode(ItemStack stack) {
        setPickupMode(stack, getPickupMode(stack) == IncludeExclude.WHITELIST
            ? IncludeExclude.BLACKLIST
            : IncludeExclude.WHITELIST);
    }

    public static boolean isMatchNbt(ItemStack stack) {
        return getBooleanSetting(stack, PICKUP_MATCH_NBT_TAG, true);
    }

    public static boolean isMatchDamage(ItemStack stack) {
        return getBooleanSetting(stack, PICKUP_MATCH_DAMAGE_TAG, true);
    }

    public static boolean isMatchOreDictionary(ItemStack stack) {
        return getBooleanSetting(stack, PICKUP_MATCH_ORE_TAG, false);
    }

    public static void toggleMatchNbt(ItemStack stack) {
        setBooleanSetting(stack, PICKUP_MATCH_NBT_TAG, !isMatchNbt(stack));
    }

    public static void toggleMatchDamage(ItemStack stack) {
        setBooleanSetting(stack, PICKUP_MATCH_DAMAGE_TAG, !isMatchDamage(stack));
    }

    public static void toggleMatchOreDictionary(ItemStack stack) {
        setBooleanSetting(stack, PICKUP_MATCH_ORE_TAG, !isMatchOreDictionary(stack));
    }

    public static void clearPickupConfig(ItemStack stack) {
        getPickupConfig(stack).clear();
    }

    public static boolean matches(ItemStack stack, AEKey key) {
        assertSupportedCell(stack);
        ConfigInventory config = getPickupConfigIfPresent(stack);
        if (config.isEmpty()) {
            return true;
        }

        boolean matched = false;
        if (key instanceof AEItemKey itemKey) {
            ItemStack candidate = itemKey.getReadOnlyStack();
            boolean matchNbt = isMatchNbt(stack);
            boolean matchDamage = isMatchDamage(stack);
            boolean matchOreDictionary = isMatchOreDictionary(stack);
            int[] candidateOreIds = matchOreDictionary ? OreDictionary.getOreIDs(candidate) : null;

            for (int slot = 0; slot < config.size(); slot++) {
                AEKey filterKey = config.getKey(slot);
                if (filterKey instanceof AEItemKey filterItemKey
                    && matchesFilter(candidate, filterItemKey.getReadOnlyStack(), matchNbt, matchDamage,
                    matchOreDictionary, candidateOreIds)) {
                    matched = true;
                    break;
                }
            }
        }
        return (getPickupMode(stack) == IncludeExclude.WHITELIST) == matched;
    }

    private static ConfigInventory getPickupConfigIfPresent(ItemStack stack) {
        synchronized (PICKUP_CONFIG_CACHE) {
            ConfigInventory cached = PICKUP_CONFIG_CACHE.get(stack);
            if (cached != null) {
                return cached;
            }
        }

        if (!hasPickupConfig(stack)) {
            return ConfigInventory.emptyTypes();
        }
        return getPickupConfig(stack);
    }

    private static boolean hasPickupConfig(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null
            && tag.hasKey(PICKUP_CONFIG_TAG, net.minecraftforge.common.util.Constants.NBT.TAG_LIST)
            && tag.getTagList(PICKUP_CONFIG_TAG, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND).tagCount() > 0;
    }

    private static boolean matchesFilter(ItemStack candidate, ItemStack filter, boolean matchNbt, boolean matchDamage,
                                         boolean matchOreDictionary, int @Nullable [] candidateOreIds) {
        if (candidate.isEmpty() || filter.isEmpty()) {
            return false;
        }

        if (candidate.getItem() == filter.getItem()) {
            boolean damageMatches = !matchDamage || candidate.getItemDamage() == filter.getItemDamage();
            boolean nbtMatches = !matchNbt
                || ItemComparisonHelper.isNbtTagEqual(candidate.getTagCompound(), filter.getTagCompound());
            if (damageMatches && nbtMatches) {
                return true;
            }
        }

        return matchOreDictionary && sharesOreDictionary(candidateOreIds, filter);
    }

    private static boolean sharesOreDictionary(int @Nullable [] candidateOreIds, ItemStack filter) {
        if (candidateOreIds == null || candidateOreIds.length == 0) {
            return false;
        }
        int[] filterOreIds = OreDictionary.getOreIDs(filter);
        if (filterOreIds.length == 0) {
            return false;
        }

        for (int candidateOreId : candidateOreIds) {
            for (int filterOreId : filterOreIds) {
                if (candidateOreId == filterOreId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean getBooleanSetting(ItemStack stack, String tagName, boolean defaultValue) {
        assertSupportedCell(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(tagName)) {
            return defaultValue;
        }
        return tag.getBoolean(tagName);
    }

    private static void setBooleanSetting(ItemStack stack, String tagName, boolean enabled) {
        assertSupportedCell(stack);
        Platform.openNbtData(stack).setBoolean(tagName, enabled);
    }

    @SideOnly(Side.CLIENT)
    public static void addInformationToTooltip(ItemStack stack, java.util.List<String> lines) {
        if (!isSupported(stack)) {
            return;
        }

        String hotkeyText = Hotkeys.getHotkeyDisplayName(HOTKEY_ID);
        if (hotkeyText == null || hotkeyText.isEmpty()) {
            hotkeyText = GuiText.PortableItemCellAutoPickupHotkeyUnbound.getLocal();
        }
        String status = isEnabled(stack) ? GuiText.Yes.getLocal() : GuiText.No.getLocal();
        lines.add(GuiText.PortableItemCellAutoPickupTooltip.getLocal(status, hotkeyText));
    }

    private static boolean toggleAndNotify(EntityPlayerMP player, ItemStack stack) {
        if (!toggle(stack)) {
            return false;
        }

        player.sendStatusMessage((isEnabled(stack)
            ? PlayerMessages.PortableItemCellAutoPickupEnabled
            : PlayerMessages.PortableItemCellAutoPickupDisabled).text(), true);
        return true;
    }

    private static PickupResult tryPickupToCell(EntityPlayerMP player, EntityItem entityItem, ItemStack dropped,
                                                AEItemKey key, ItemStack cellStack, IActionSource source) {
        if (cellStack.isEmpty() || !isSupported(cellStack) || !isEnabled(cellStack) || !matches(cellStack, key)) {
            return PickupResult.NONE;
        }

        StorageCell cell = getCellInventory(cellStack);
        if (cell == null) {
            return PickupResult.NONE;
        }

        int before = dropped.getCount();
        long inserted = insertIntoCell(cellStack, cell, key, before, source);
        if (inserted <= 0) {
            return PickupResult.NONE;
        }

        cell.persist();
        int insertedCount = Math.toIntExact(Math.min(inserted, before));
        dropped.shrink(insertedCount);
        player.addStat(StatList.getObjectsPickedUpStats(key.getItem()), insertedCount);
        player.onItemPickup(entityItem, insertedCount);
        entityItem.setItem(dropped);
        if (dropped.isEmpty()) {
            entityItem.setDead();
            return PickupResult.COMPLETE;
        }
        return PickupResult.PARTIAL;
    }

    private static long insertIntoCell(ItemStack cellStack, StorageCell cell, AEItemKey key, int amount,
                                       IActionSource source) {
        if (cellStack.getItem() instanceof PortableVoidCellItem) {
            return cell.insert(key, amount, Actionable.MODULATE, source);
        }
        if (cellStack.getItem() instanceof PortableCellItem portableCellItem) {
            return StorageHelper.poweredInsert(new PortableCellEnergySource(cellStack, portableCellItem), cell, key,
                amount, source, Actionable.MODULATE);
        }
        throw new IllegalArgumentException("Auto pickup requires a supported portable cell");
    }

    @Nullable
    private static StorageCell getCellInventory(ItemStack stack) {
        synchronized (CELL_CACHE) {
            StorageCell cached = CELL_CACHE.get(stack);
            if (cached != null) {
                return cached;
            }

            MEStorage storage = StorageCells.getCellInventory(stack, null);
            if (storage instanceof StorageCell cell) {
                CELL_CACHE.put(stack, cell);
                return cell;
            }
            return null;
        }
    }

    private static void assertSupportedCell(ItemStack stack) {
        if (isSupported(stack)) {
            return;
        }
        throw new IllegalArgumentException("Auto pickup only supports item portable cells");
    }

    private static void setPickupMode(ItemStack stack, IncludeExclude mode) {
        assertSupportedCell(stack);
        Platform.openNbtData(stack).setBoolean(PICKUP_MODE_TAG, mode == IncludeExclude.WHITELIST);
    }

    public enum PickupResult {
        NONE,
        PARTIAL,
        COMPLETE
    }

    private record PortableCellEnergySource(ItemStack stack, PortableCellItem cellItem) implements IEnergySource {

        @Override
        public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
            return usePowerMultiplier.divide(
                this.cellItem.extractAEPower(this.stack, usePowerMultiplier.multiply(amt), mode));
        }
    }

    private static final class PickupConfigHolder {
        private final ItemStack stack;
        private final ConfigInventory config;

        private PickupConfigHolder(ItemStack stack) {
            this.stack = stack;
            this.config = ConfigInventory.configTypes(PICKUP_CONFIG_SIZE)
                                         .supportedType(AEKeyType.items())
                                         .changeListener(this::save)
                                         .build();
        }

        private void load() {
            NBTTagCompound tag = this.stack.getTagCompound();
            if (tag == null) {
                return;
            }

            this.config.beginBatch();
            try {
                this.config.readFromChildTag(tag, PICKUP_CONFIG_TAG);
            } finally {
                this.config.endBatchSuppressed();
            }
        }

        private void save() {
            NBTTagCompound tag = Platform.openNbtData(this.stack);
            this.config.writeToChildTag(tag, PICKUP_CONFIG_TAG);
            this.stack.setTagCompound(Platform.isNbtEmpty(tag) ? null : tag);
        }
    }
}
