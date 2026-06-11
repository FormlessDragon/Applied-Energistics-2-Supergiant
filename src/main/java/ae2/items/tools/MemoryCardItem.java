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

package ae2.items.tools;

import ae2.api.implementations.items.IMemoryCard;
import ae2.api.implementations.items.MemoryCardColors;
import ae2.api.implementations.items.MemoryCardMessages;
import ae2.api.inventories.InternalInventory;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.AEColor;
import ae2.api.util.IConfigurableObject;
import ae2.block.AEBaseTileBlock;
import ae2.core.localization.InGameTooltip;
import ae2.core.localization.PlayerMessages;
import ae2.helpers.IConfigInvHost;
import ae2.helpers.IPriorityHost;
import ae2.items.AEBaseItem;
import ae2.items.contents.NetworkToolGuiHost;
import ae2.text.TextComponentItemStack;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import ae2.util.inv.PlayerInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MemoryCardItem extends AEBaseItem implements IMemoryCard {
    private static final int DEFAULT_BASE_COLOR = 0x9cd3ff;
    private static final String EXPORTED_SETTINGS_SOURCE = "exported_settings_source";
    private static final String EXPORTED_CUSTOM_NAME = "exported_custom_name";
    private static final String EXPORTED_UPGRADES = "exported_upgrades";
    private static final String EXPORTED_SETTINGS = "exported_settings";
    private static final String EXPORTED_PRIORITY = "exported_priority";
    private static final String EXPORTED_P2P_TYPE = "exported_p2p_type";
    private static final String EXPORTED_P2P_FREQUENCY = "exported_p2p_frequency";
    private static final String MEMORY_CARD_COLORS = "memory_card_colors";
    private static final String EXPORTED_CONFIG_INV = "exported_config_inv";
    private static final String EXPORTED_LEVEL_EMITTER_VALUE = "exported_level_emitter_value";
    private static final String EXPORTED_PATTERNS = "exported_patterns";
    private static final String EXPORTED_PUSH_DIRECTION = "pushDirection";
    private static final String[] EXPORTED_TAGS = {
        EXPORTED_SETTINGS_SOURCE,
        EXPORTED_CUSTOM_NAME,
        EXPORTED_UPGRADES,
        EXPORTED_SETTINGS,
        EXPORTED_PRIORITY,
        EXPORTED_P2P_TYPE,
        EXPORTED_P2P_FREQUENCY,
        MEMORY_CARD_COLORS,
        EXPORTED_CONFIG_INV,
        EXPORTED_LEVEL_EMITTER_VALUE,
        EXPORTED_PATTERNS,
        EXPORTED_PUSH_DIRECTION
    };
    private static final String[] IMPORTABLE_EXPORTED_TAGS = {
        EXPORTED_UPGRADES,
        EXPORTED_SETTINGS,
        EXPORTED_PRIORITY,
        EXPORTED_CONFIG_INV
    };

    public MemoryCardItem() {
        this.setMaxStackSize(1);
    }

    public static void exportGenericSettings(Object exportFrom, NBTTagCompound output) {
        if (exportFrom instanceof IUpgradeableObject) {
            writeUpgrades(((IUpgradeableObject) exportFrom).getUpgrades(), output);
        }

        if (exportFrom instanceof IConfigurableObject) {
            NBTTagCompound settings = new NBTTagCompound();
            Map<String, String> exported = ((IConfigurableObject) exportFrom).getConfigManager().exportSettings();
            for (Map.Entry<String, String> entry : exported.entrySet()) {
                settings.setString(entry.getKey(), entry.getValue());
            }
            if (!Platform.isNbtEmpty(settings)) {
                output.setTag(EXPORTED_SETTINGS, settings.copy());
            }
        }

        if (exportFrom instanceof IPriorityHost) {
            output.setInteger(EXPORTED_PRIORITY, ((IPriorityHost) exportFrom).getPriority());
        }

        if (exportFrom instanceof IConfigInvHost) {
            NBTTagList config = ((IConfigInvHost) exportFrom).getConfig().writeToTag();
            if (config.tagCount() > 0) {
                output.setTag(EXPORTED_CONFIG_INV, config.copy());
            }
        }
    }

    public static Set<String> importGenericSettings(Object importTo, NBTTagCompound input, @Nullable EntityPlayer player) {
        Set<String> imported = new ObjectOpenHashSet<>();

        if (player != null && importTo instanceof IUpgradeableObject) {
            if (input.hasKey(EXPORTED_UPGRADES, Constants.NBT.TAG_LIST)) {
                NBTTagList upgrades = input.getTagList(EXPORTED_UPGRADES, Constants.NBT.TAG_COMPOUND);
                restoreUpgrades(player, upgrades, (IUpgradeableObject) importTo);
                imported.add(EXPORTED_UPGRADES);
            }
        }

        if (importTo instanceof IConfigurableObject && input.hasKey(EXPORTED_SETTINGS, Constants.NBT.TAG_COMPOUND)) {
            Map<String, String> settings = new Object2ObjectOpenHashMap<>();
            NBTTagCompound settingsTag = input.getCompoundTag(EXPORTED_SETTINGS);
            for (String key : settingsTag.getKeySet()) {
                settings.put(key, settingsTag.getString(key));
            }
            if (((IConfigurableObject) importTo).getConfigManager().importSettings(settings)) {
                imported.add(EXPORTED_SETTINGS);
            }
        }

        if (importTo instanceof IPriorityHost && input.hasKey(EXPORTED_PRIORITY, Constants.NBT.TAG_INT)) {
            ((IPriorityHost) importTo).setPriority(input.getInteger(EXPORTED_PRIORITY));
            imported.add(EXPORTED_PRIORITY);
        } else if (importTo instanceof IPriorityHost && input.hasKey(EXPORTED_PRIORITY, Constants.NBT.TAG_ANY_NUMERIC)) {
            NBTBase priorityBase = input.getTag(EXPORTED_PRIORITY);
            if (priorityBase instanceof NBTTagInt priorityTag) {
                ((IPriorityHost) importTo).setPriority(priorityTag.getInt());
                imported.add(EXPORTED_PRIORITY);
            }
        }

        if (importTo instanceof IConfigInvHost && input.hasKey(EXPORTED_CONFIG_INV, Constants.NBT.TAG_LIST)) {
            NBTTagList configTag = input.getTagList(EXPORTED_CONFIG_INV, Constants.NBT.TAG_COMPOUND);
            if (configTag.tagCount() > 0) {
                ((IConfigInvHost) importTo).getConfig().readFromTag(configTag);
                imported.add(EXPORTED_CONFIG_INV);
            }
        }

        return imported;
    }

    public static void importGenericSettingsAndNotify(Object importTo, NBTTagCompound input, @Nullable EntityPlayer player) {
        List<String> exportedSettings = getExportedSettings(input);
        Set<String> imported = importGenericSettings(importTo, input, player);
        if (player != null && !player.world.isRemote) {
            if (imported.isEmpty()) {
                player.sendStatusMessage(PlayerMessages.InvalidMachine.text(), true);
            } else {
                player.sendStatusMessage(
                    PlayerMessages.InvalidMachinePartiallyRestored.text(formatImportedSettings(exportedSettings, imported)),
                    true);
            }
        }
    }

    private static List<String> getExportedSettings(NBTTagCompound input) {
        List<String> exportedSettings = new ObjectArrayList<>(IMPORTABLE_EXPORTED_TAGS.length);
        for (String exportedTag : IMPORTABLE_EXPORTED_TAGS) {
            if (input.hasKey(exportedTag)) {
                exportedSettings.add(exportedTag);
            }
        }
        return exportedSettings;
    }

    private static String getSettingTranslationKey(String exportedSetting) {
        return "exported_setting.ae2." + exportedSetting;
    }

    private static ITextComponent getSettingComponent(String exportedSetting) {
        return new TextComponentTranslation(getSettingTranslationKey(exportedSetting));
    }

    private static ITextComponent formatImportedSettings(List<String> exportedSettings, Set<String> importedSettings) {
        ITextComponent text = new TextComponentString("");
        boolean first = true;
        for (String exportedSetting : exportedSettings) {
            if (!importedSettings.contains(exportedSetting)) {
                continue;
            }

            if (!first) {
                text.appendText(", ");
            }
            text.appendSibling(getSettingComponent(exportedSetting));
            first = false;
        }
        return text;
    }

    private static void writeUpgrades(IUpgradeInventory upgrades, NBTTagCompound output) {
        NBTTagList list = new NBTTagList();
        Reference2IntOpenHashMap<Item> counts = new Reference2IntOpenHashMap<>();
        counts.defaultReturnValue(0);

        for (ItemStack upgrade : upgrades) {
            if (upgrade.isEmpty()) {
                continue;
            }
            counts.addTo(upgrade.getItem(), upgrade.getCount());
        }

        for (Reference2IntMap.Entry<Item> entry : counts.reference2IntEntrySet()) {
            ItemStack stack = new ItemStack(entry.getKey(), entry.getIntValue());
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            list.appendTag(stackTag);
        }

        if (list.tagCount() > 0) {
            output.setTag(EXPORTED_UPGRADES, list.copy());
        }
    }

    private static void restoreUpgrades(EntityPlayer player, NBTTagList desiredUpgrades, IUpgradeableObject upgradeableObject) {
        IUpgradeInventory upgrades = upgradeableObject.getUpgrades();
        if (player.capabilities.isCreativeMode) {
            for (int i = 0; i < upgrades.size(); i++) {
                upgrades.setItemDirect(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < desiredUpgrades.tagCount(); i++) {
                ItemStack desired = readStack(desiredUpgrades.getCompoundTagAt(i));
                if (!desired.isEmpty()) {
                    upgrades.addItems(desired);
                }
            }
            return;
        }

        List<InternalInventory> upgradeSources = new ObjectArrayList<>();
        upgradeSources.add(new PlayerInternalInventory(player.inventory));
        NetworkToolGuiHost<?> networkTool = NetworkToolItem.findNetworkToolInv(player);
        if (networkTool != null) {
            upgradeSources.add(networkTool.getInventory());
        }

        Reference2IntOpenHashMap<Item> desiredCounts = new Reference2IntOpenHashMap<>();
        desiredCounts.defaultReturnValue(0);
        for (int i = 0; i < desiredUpgrades.tagCount(); i++) {
            ItemStack desired = readStack(desiredUpgrades.getCompoundTagAt(i));
            if (!desired.isEmpty()) {
                desiredCounts.addTo(desired.getItem(), desired.getCount());
            }
        }

        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack current = upgrades.getStackInSlot(i);
            if (current.isEmpty()) {
                continue;
            }

            int target = desiredCounts.getInt(current.getItem());
            int installed = upgradeableObject.getInstalledUpgrades(current.getItem());
            int toRemove = installed - target;
            if (toRemove > 0) {
                ItemStack removed = upgrades.extractItem(i, toRemove, false);
                for (InternalInventory source : upgradeSources) {
                    if (!removed.isEmpty()) {
                        removed = source.addItems(removed);
                    }
                }
                if (!removed.isEmpty()) {
                    player.dropItem(removed, false);
                }
            }
        }

        for (Reference2IntMap.Entry<Item> entry : desiredCounts.reference2IntEntrySet()) {
            int missing = entry.getIntValue() - upgradeableObject.getInstalledUpgrades(entry.getKey());
            if (missing <= 0) {
                continue;
            }

            ItemStack desired = new ItemStack(entry.getKey(), missing);
            ItemStack overflow = upgrades.addItems(desired, true);
            if (!overflow.isEmpty()) {
                missing -= overflow.getCount();
            }

            for (InternalInventory source : upgradeSources) {
                if (missing <= 0) {
                    break;
                }
                ItemStack extracted = source.removeItems(missing, desired, null);
                if (!extracted.isEmpty()) {
                    overflow = upgrades.addItems(extracted);
                    if (!overflow.isEmpty()) {
                        player.inventory.placeItemBackInInventory(player.world, overflow);
                    }
                    missing -= extracted.getCount();
                }
            }

            if (missing > 0 && !player.world.isRemote) {
                player.sendStatusMessage(PlayerMessages.MissingUpgrades.text(TextComponentItemStack.of(new ItemStack(entry.getKey())), missing),
                    true);
            }
        }
    }

    private static ItemStack readStack(NBTTagCompound tag) {
        try {
            return new ItemStack(tag);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static void clearCard(ItemStack card) {
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null) {
            return;
        }

        for (String exportedTag : EXPORTED_TAGS) {
            tag.removeTag(exportedTag);
        }

        if (Platform.isNbtEmpty(tag)) {
            card.setTagCompound(null);
        }
    }

    public static void setMemoryCardColors(NBTTagCompound tag, MemoryCardColors colors) {
        tag.setIntArray(MEMORY_CARD_COLORS, colors.toArray());
    }

    public static MemoryCardColors getMemoryCardColors(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return MemoryCardColors.DEFAULT;
        }

        NBTBase frequencyBase = tag.getTag(EXPORTED_P2P_FREQUENCY);
        if (frequencyBase instanceof NBTTagShort frequencyTag && frequencyTag.getShort() != 0) {
            AEColor[] colors = Platform.p2p().toColors(frequencyTag.getShort());
            return MemoryCardColors.repeatedPairs(colors[0], colors[1], colors[2], colors[3]);
        }

        return MemoryCardColors.fromTag(tag, MEMORY_CARD_COLORS);
    }

    public static int getTintColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 1 && stack.getItem() instanceof MemoryCardItem) {
            return ((MemoryCardItem) stack.getItem()).getColor(stack);
        }
        return 0xFFFFFF;
    }

    static boolean shouldClearOnShiftRightClick(@Nullable RayTraceResult hitResult) {
        return hitResult == null || hitResult.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add(InGameTooltip.MemoryCardOffhandP2PInput.getLocal());

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }

        if (tag.hasKey(EXPORTED_SETTINGS_SOURCE, Constants.NBT.TAG_STRING)) {
            String source = tag.getString(EXPORTED_SETTINGS_SOURCE);
            lines.add(source.endsWith(".name") ? I18n.format(source) : source);
        }

        NBTBase frequencyBase = tag.getTag(EXPORTED_P2P_FREQUENCY);
        NBTTagShort frequencyTag = frequencyBase instanceof NBTTagShort ? (NBTTagShort) frequencyBase : null;
        if (frequencyTag != null) {
            ITextComponent freq = Platform.p2p().toColoredHexString(frequencyTag.getShort());
            lines.add(InGameTooltip.P2PFrequency.getLocal(freq.getFormattedText()));
        }
    }

    @Override
    public void notifyUser(EntityPlayer player, MemoryCardMessages msg) {
        if (player.world.isRemote) {
            return;
        }

        switch (msg) {
            case SETTINGS_CLEARED -> player.sendStatusMessage(PlayerMessages.SettingCleared.text(), true);
            case INVALID_MACHINE -> player.sendStatusMessage(PlayerMessages.InvalidMachine.text(), true);
            case SETTINGS_LOADED -> player.sendStatusMessage(PlayerMessages.LoadedSettings.text(), true);
            case SETTINGS_SAVED -> player.sendStatusMessage(PlayerMessages.SavedSettings.text(), true);
            case SETTINGS_RESET -> player.sendStatusMessage(PlayerMessages.ResetSettings.text(), true);
            default -> {
            }
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            Block block = world.getBlockState(pos).getBlock();
            if (block instanceof AEBaseTileBlock<?>) {
                return EnumActionResult.PASS;
            }
            if (!world.isRemote) {
                clearCard(player, hand);
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (InteractionUtil.isInAlternateUseMode(player) && shouldClearOnShiftRightClick(rayTrace(world, player, false))
            && !world.isRemote) {
            clearCard(player, hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess level, BlockPos pos, EntityPlayer player) {
        return true;
    }

    private void clearCard(EntityPlayer player, EnumHand hand) {
        IMemoryCard memoryCard = (IMemoryCard) player.getHeldItem(hand).getItem();
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
        clearCard(player.getHeldItem(hand));
    }

    public int getColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("display", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("color", Constants.NBT.TAG_ANY_NUMERIC)) {
                return display.getInteger("color");
            }
        }
        return DEFAULT_BASE_COLOR;
    }
}
