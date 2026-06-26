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

package ae2.client.gui.widgets;

import ae2.api.config.AccessRestriction;
import ae2.api.config.AdvancedMemoryCardStatusFilter;
import ae2.api.config.BlockingMode;
import ae2.api.config.CondenserOutput;
import ae2.api.config.CpuSelectionMode;
import ae2.api.config.CraftingPlanSortMode;
import ae2.api.config.FormationPlaneMode;
import ae2.api.config.FullnessMode;
import ae2.api.config.FuzzyMode;
import ae2.api.config.InscriberInputCapacity;
import ae2.api.config.LockCraftingMode;
import ae2.api.config.OperationMode;
import ae2.api.config.PatternProviderBlockingType;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.config.PatternProviderOutputSideMode;
import ae2.api.config.PowerUnit;
import ae2.api.config.RedstoneMode;
import ae2.api.config.RelativeDirection;
import ae2.api.config.SchedulingMode;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.StorageFilter;
import ae2.api.config.TerminalStyle;
import ae2.api.config.ViewItems;
import ae2.api.config.YesNo;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.core.AELog;
import ae2.core.definitions.AEParts;
import ae2.core.definitions.ItemDefinition;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.LocalizationEnum;
import ae2.core.localization.Tooltips;
import ae2.util.EnumCycler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class SettingToggleButton<T extends Enum<T>> extends IconButton {
    private static Map<EnumPair<?>, ButtonAppearance> appearances;

    private final Setting<T> buttonSetting;
    private final IHandler<SettingToggleButton<T>> onPress;
    private final IValueHandler<T, SettingToggleButton<T>> onDirectSelection;
    private final EnumSet<T> validValues;
    private T currentValue;
    private boolean pressedBackwards;
    private boolean triggeredOnPress;

    public SettingToggleButton(Setting<T> setting, T val, IHandler<SettingToggleButton<T>> onPress) {
        this(setting, val, ignored -> true, onPress);
    }

    public SettingToggleButton(Setting<T> setting, T val, Predicate<T> isValidValue,
                               IHandler<SettingToggleButton<T>> onPress) {
        this(setting, val, isValidValue, onPress, SettingToggleButton::selectWithExistingHandler);
    }

    public SettingToggleButton(Setting<T> setting, T val, Predicate<T> isValidValue,
                               IHandler<SettingToggleButton<T>> onPress,
                               IValueHandler<T, SettingToggleButton<T>> onDirectSelection) {
        super(() -> {
        });
        this.onPress = onPress;
        this.onDirectSelection = onDirectSelection;

        EnumSet<T> validValues = EnumSet.allOf(val.getDeclaringClass());
        validValues.removeIf(isValidValue.negate());
        validValues.removeIf(s -> !setting.getValues().contains(s));
        this.validValues = validValues;

        this.buttonSetting = setting;
        this.currentValue = val;

        if (appearances == null) {
            appearances = new Object2ObjectOpenHashMap<>();

            registerApp(Icon.CONDENSER_OUTPUT_TRASH, Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH,
                ButtonToolTips.CondenserOutput, ButtonToolTips.Trash);
            registerApp(Icon.CONDENSER_OUTPUT_MATTER_BALL, Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS,
                ButtonToolTips.CondenserOutput,
                ButtonToolTips.MatterBalls.text(CondenserOutput.MATTER_BALLS.requiredPower));
            registerApp(Icon.CONDENSER_OUTPUT_SINGULARITY, Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY,
                ButtonToolTips.CondenserOutput,
                ButtonToolTips.Singularity.text(CondenserOutput.SINGULARITY.requiredPower));

            registerApp(Icon.ACCESS_READ, Settings.ACCESS, AccessRestriction.READ, ButtonToolTips.IOMode,
                ButtonToolTips.Read);
            registerApp(Icon.ACCESS_WRITE, Settings.ACCESS, AccessRestriction.WRITE, ButtonToolTips.IOMode,
                ButtonToolTips.Write);
            registerApp(Icon.ACCESS_READ_WRITE, Settings.ACCESS, AccessRestriction.READ_WRITE, ButtonToolTips.IOMode,
                ButtonToolTips.ReadWrite);

            registerApp(Icon.POWER_UNIT_AE, Settings.POWER_UNITS, PowerUnit.AE, ButtonToolTips.PowerUnits,
                PowerUnit.AE.textComponent());
            registerApp(Icon.POWER_UNIT_RF, Settings.POWER_UNITS, PowerUnit.FE, ButtonToolTips.PowerUnits,
                PowerUnit.FE.textComponent());
            registerApp(Icon.POWER_UNIT_EU, Settings.POWER_UNITS, PowerUnit.EU, ButtonToolTips.PowerUnits,
                PowerUnit.EU.textComponent());

            registerApp(Icon.REDSTONE_IGNORE, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE,
                ButtonToolTips.RedstoneMode, ButtonToolTips.AlwaysActive);
            registerApp(Icon.REDSTONE_LOW, Settings.REDSTONE_CONTROLLED, RedstoneMode.LOW_SIGNAL,
                ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveWithoutSignal);
            registerApp(Icon.REDSTONE_HIGH, Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL,
                ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveWithSignal);
            registerApp(Icon.REDSTONE_PULSE, Settings.REDSTONE_CONTROLLED, RedstoneMode.SIGNAL_PULSE,
                ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveOnPulse);

            registerApp(Icon.REDSTONE_BELOW, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL,
                ButtonToolTips.RedstoneMode, ButtonToolTips.EmitLevelsBelow);
            registerApp(Icon.REDSTONE_ABOVE_EQUAL, Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL,
                ButtonToolTips.RedstoneMode, ButtonToolTips.EmitLevelAbove);

            registerApp(Icon.ARROW_LEFT, Settings.OPERATION_MODE, OperationMode.FILL,
                ButtonToolTips.TransferDirection, ButtonToolTips.TransferToStorageCell);
            registerApp(Icon.ARROW_RIGHT, Settings.OPERATION_MODE, OperationMode.EMPTY,
                ButtonToolTips.TransferDirection, ButtonToolTips.TransferToNetwork);

            registerApp(Icon.ARROW_LEFT, Settings.IO_DIRECTION, RelativeDirection.LEFT,
                ButtonToolTips.TransferDirection, ButtonToolTips.TransferToStorageCell);
            registerApp(Icon.ARROW_RIGHT, Settings.IO_DIRECTION, RelativeDirection.RIGHT,
                ButtonToolTips.TransferDirection, ButtonToolTips.TransferToNetwork);

            registerApp(Icon.ARROW_UP, Settings.SORT_DIRECTION, SortDir.ASCENDING,
                ButtonToolTips.SortOrder, ButtonToolTips.Ascending);
            registerApp(Icon.ARROW_DOWN, Settings.SORT_DIRECTION, SortDir.DESCENDING,
                ButtonToolTips.SortOrder, ButtonToolTips.Descending);

            registerApp(Icon.SORT_BY_NAME, Settings.SORT_BY, SortOrder.NAME,
                ButtonToolTips.SortBy, ButtonToolTips.ItemName);
            registerApp(Icon.SORT_BY_AMOUNT, Settings.SORT_BY, SortOrder.AMOUNT,
                ButtonToolTips.SortBy, ButtonToolTips.NumberOfItems);
            registerApp(Icon.SORT_BY_MOD, Settings.SORT_BY, SortOrder.MOD,
                ButtonToolTips.SortBy, ButtonToolTips.Mod);
            registerApp(Icon.SORT_BY_INVENTORY_TWEAKS, Settings.SORT_BY, SortOrder.INVTWEAKS,
                ButtonToolTips.SortBy, ButtonToolTips.InventoryTweaks);
            registerApp(Icon.SORT_BY_HEI, Settings.SORT_BY, SortOrder.HEI,
                ButtonToolTips.SortBy, ButtonToolTips.HEI);

            registerAppWithoutTitle(Icon.CRAFTING_PLAN_SORT_AVAILABILITY, Settings.CRAFTING_PLAN_SORT_MODE,
                CraftingPlanSortMode.AVAILABILITY,
                ButtonToolTips.AvailabilityDescription.text());
            registerAppWithoutTitle(Icon.CRAFTING_PLAN_SORT_USED_COUNT, Settings.CRAFTING_PLAN_SORT_MODE,
                CraftingPlanSortMode.USED_COUNT,
                ButtonToolTips.UsedResourceCountDescription.text());
            registerAppWithoutTitle(Icon.CRAFTING_PLAN_SORT_USED_PERCENT, Settings.CRAFTING_PLAN_SORT_MODE,
                CraftingPlanSortMode.USED_PERCENT,
                ButtonToolTips.UsedResourcePercentDescription.text());
            registerAppWithoutTitle(Icon.CRAFTING_PLAN_SORT_ASCENDING, Settings.CRAFTING_PLAN_SORT_DIRECTION,
                SortDir.ASCENDING,
                ButtonToolTips.SortOrderAscendingDescription.text());
            registerAppWithoutTitle(Icon.CRAFTING_PLAN_SORT_DESCENDING, Settings.CRAFTING_PLAN_SORT_DIRECTION,
                SortDir.DESCENDING,
                ButtonToolTips.SortOrderDescendingDescription.text());

            registerApp(Icon.VIEW_MODE_STORED, Settings.VIEW_MODE, ViewItems.STORED,
                ButtonToolTips.View, ButtonToolTips.StoredItems);
            registerApp(Icon.VIEW_MODE_ALL, Settings.VIEW_MODE, ViewItems.ALL,
                ButtonToolTips.View, ButtonToolTips.StoredCraftable);
            registerApp(Icon.VIEW_MODE_CRAFTING, Settings.VIEW_MODE, ViewItems.CRAFTABLE,
                ButtonToolTips.View, ButtonToolTips.Craftable);

            registerApp(Icon.TERMINAL_STYLE_SMALL, Settings.TERMINAL_STYLE, TerminalStyle.SMALL,
                ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Small);
            registerApp(Icon.TERMINAL_STYLE_MEDIUM, Settings.TERMINAL_STYLE, TerminalStyle.MEDIUM,
                ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Medium);
            registerApp(Icon.TERMINAL_STYLE_TALL, Settings.TERMINAL_STYLE, TerminalStyle.TALL,
                ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Tall);
            registerApp(Icon.TERMINAL_STYLE_FULL, Settings.TERMINAL_STYLE, TerminalStyle.FULL,
                ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Full);
            registerAppWithoutTitle(Icon.TYPE_FILTER_ALL, Settings.ADVANCED_MEMORY_CARD_STATUS_FILTER,
                AdvancedMemoryCardStatusFilter.ANY,
                GuiText.AdvancedMemoryCardStatus.text(GuiText.AdvancedMemoryCardStatusAny.text()));
            registerAppWithoutTitle(Icon.ADVANCED_MEMORY_CARD_STATUS_INPUT, Settings.ADVANCED_MEMORY_CARD_STATUS_FILTER,
                AdvancedMemoryCardStatusFilter.INPUT,
                GuiText.AdvancedMemoryCardStatus.text(GuiText.AdvancedMemoryCardStatusInput.text()));
            registerAppWithoutTitle(Icon.ADVANCED_MEMORY_CARD_STATUS_OUTPUT, Settings.ADVANCED_MEMORY_CARD_STATUS_FILTER,
                AdvancedMemoryCardStatusFilter.OUTPUT,
                GuiText.AdvancedMemoryCardStatus.text(GuiText.AdvancedMemoryCardStatusOutput.text()));
            registerAppWithoutTitle(Icon.ADVANCED_MEMORY_CARD_STATUS_UNBOUND, Settings.ADVANCED_MEMORY_CARD_STATUS_FILTER,
                AdvancedMemoryCardStatusFilter.UNBOUND,
                GuiText.AdvancedMemoryCardStatus.text(GuiText.AdvancedMemoryCardStatusUnbound.text()));

            registerApp(Icon.PATTERN_TERMINAL_ALL, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.ALL,
                ButtonToolTips.InterfaceTerminalDisplayMode, ButtonToolTips.ShowAllProviders);
            registerApp(Icon.PATTERN_TERMINAL_VISIBLE, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.VISIBLE,
                ButtonToolTips.InterfaceTerminalDisplayMode, ButtonToolTips.ShowVisibleProviders);
            registerApp(Icon.PATTERN_TERMINAL_HIDDEN, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.HIDDEN,
                ButtonToolTips.InterfaceTerminalDisplayMode, ButtonToolTips.ShowHiddenProviders);
            registerApp(Icon.PATTERN_TERMINAL_NOT_FULL, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.NOT_FULL,
                ButtonToolTips.InterfaceTerminalDisplayMode, ButtonToolTips.ShowNonFullProviders);

            registerApp(Icon.FUZZY_PERCENT_25, Settings.FUZZY_MODE, FuzzyMode.PERCENT_25,
                ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_25);
            registerApp(Icon.FUZZY_PERCENT_50, Settings.FUZZY_MODE, FuzzyMode.PERCENT_50,
                ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_50);
            registerApp(Icon.FUZZY_PERCENT_75, Settings.FUZZY_MODE, FuzzyMode.PERCENT_75,
                ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_75);
            registerApp(Icon.FUZZY_PERCENT_99, Settings.FUZZY_MODE, FuzzyMode.PERCENT_99,
                ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_99);
            registerApp(Icon.FUZZY_IGNORE, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL,
                ButtonToolTips.FuzzyMode, ButtonToolTips.FZIgnoreAll);

            registerApp(Icon.FULLNESS_EMPTY, Settings.FULLNESS_MODE, FullnessMode.EMPTY,
                ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenEmpty);
            registerApp(Icon.FULLNESS_HALF, Settings.FULLNESS_MODE, FullnessMode.HALF,
                ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenWorkIsDone);
            registerApp(Icon.FULLNESS_FULL, Settings.FULLNESS_MODE, FullnessMode.FULL,
                ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenFull);

            registerApp(Icon.INSCRIBER_COMBINED_SIDES, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO,
                ButtonToolTips.InscriberSideness, ButtonToolTips.InscriberSidenessCombined);
            registerApp(Icon.INSCRIBER_SEPARATE_SIDES, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.YES,
                ButtonToolTips.InscriberSideness, ButtonToolTips.InscriberSidenessSeparate);
            registerApp(Icon.AUTO_EXPORT_OFF, Settings.AUTO_EXPORT, YesNo.NO,
                ButtonToolTips.AutoExport, ButtonToolTips.AutoExportOff);
            registerApp(Icon.AUTO_EXPORT_ON, Settings.AUTO_EXPORT, YesNo.YES,
                ButtonToolTips.AutoExport, ButtonToolTips.AutoExportOn);
            registerApp(Icon.INSCRIBER_BUFFER_1, Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.ONE,
                ButtonToolTips.InscriberBufferSize, ButtonToolTips.InscriberBufferVeryLow);
            registerApp(Icon.INSCRIBER_BUFFER_4, Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.FOUR,
                ButtonToolTips.InscriberBufferSize, ButtonToolTips.InscriberBufferLow);
            registerApp(Icon.INSCRIBER_BUFFER_64, Settings.INSCRIBER_INPUT_CAPACITY,
                InscriberInputCapacity.SIXTY_FOUR, ButtonToolTips.InscriberBufferSize,
                ButtonToolTips.InscriberBufferHigh);

            registerApp(Icon.BLOCKING_MODE_YES, Settings.BLOCKING_MODE, BlockingMode.YES,
                ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.Blocking);
            registerApp(Icon.BLOCKING_MODE_NO, Settings.BLOCKING_MODE, BlockingMode.NO,
                ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.NonBlocking);
            registerApp(Icon.BLOCKING_MODE_STRONG, Settings.BLOCKING_MODE, BlockingMode.STRONG,
                ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.StrongBlocking);

            registerApp(Icon.BLOCKING_MODE_TYPE_NORMAL, Settings.PATTERN_PROVIDER_BLOCKING_TYPE,
                PatternProviderBlockingType.NORMAL,
                ButtonToolTips.PatternProviderBlockingType,
                ButtonToolTips.NormalBlockingDescription.text());
            registerApp(Icon.BLOCKING_MODE_TYPE_SMART, Settings.PATTERN_PROVIDER_BLOCKING_TYPE,
                PatternProviderBlockingType.SMART,
                ButtonToolTips.PatternProviderBlockingType,
                ButtonToolTips.SmartBlockingDescription.text());

            registerApp(Icon.PATTERN_PROVIDER_INSERTION_DEFAULT, Settings.PATTERN_PROVIDER_INSERTION_MODE,
                PatternProviderInsertionMode.DEFAULT,
                ButtonToolTips.PatternProviderInsertionModeDefault,
                ButtonToolTips.PatternProviderInsertionModeDefaultDescription.text());
            registerApp(Icon.PATTERN_PROVIDER_INSERTION_PREFER_EMPTY, Settings.PATTERN_PROVIDER_INSERTION_MODE,
                PatternProviderInsertionMode.PREFER_EMPTY,
                ButtonToolTips.PatternProviderInsertionModePreferEmpty,
                ButtonToolTips.PatternProviderInsertionModePreferEmptyDescription.text());
            registerApp(Icon.PATTERN_PROVIDER_INSERTION_EMPTY_ONLY, Settings.PATTERN_PROVIDER_INSERTION_MODE,
                PatternProviderInsertionMode.EMPTY_ONLY,
                ButtonToolTips.PatternProviderInsertionModeEmptyOnly,
                ButtonToolTips.PatternProviderInsertionModeEmptyOnlyDescription.text());

            registerApp(Icon.PATTERN_PROVIDER_OUTPUT_SIDE_SINGLE, Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE,
                PatternProviderOutputSideMode.SINGLE_SIDE,
                ButtonToolTips.PatternProviderOutputSideModeSingleSide,
                ButtonToolTips.PatternProviderOutputSideModeSingleSideDescription.text());
            registerApp(Icon.PATTERN_PROVIDER_OUTPUT_SIDE_SPLIT_BY_INGREDIENTS_TYPE,
                Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE,
                PatternProviderOutputSideMode.SPLIT_BY_INGREDIENTS_TYPE,
                ButtonToolTips.PatternProviderOutputSideModeSplitByIngredientsType,
                ButtonToolTips.PatternProviderOutputSideModeSplitByIngredientsTypeDescription.text());

            registerApp(Icon.UNLOCKED, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE,
                ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingModeNone);
            registerApp(Icon.REDSTONE_ON, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH,
                ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingWhileRedstoneHigh);
            registerApp(Icon.REDSTONE_OFF, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW,
                ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingWhileRedstoneLow);
            registerApp(Icon.REDSTONE_PULSE, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_PULSE,
                ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingUntilRedstonePulse);
            registerApp(Icon.ENTER, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT,
                ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingUntilResultReturned);

            registerApp(Icon.VIEW_MODE_CRAFTING, Settings.CRAFT_ONLY, YesNo.YES,
                ButtonToolTips.Craft, ButtonToolTips.CraftOnly);
            registerApp(Icon.VIEW_MODE_ALL, Settings.CRAFT_ONLY, YesNo.NO,
                ButtonToolTips.Craft, ButtonToolTips.CraftEither);

            registerApp(Icon.CRAFT_HAMMER, Settings.CRAFT_VIA_REDSTONE, YesNo.YES,
                ButtonToolTips.EmitterMode, ButtonToolTips.CraftViaRedstone);
            registerApp(Icon.ACCESS_READ, Settings.CRAFT_VIA_REDSTONE, YesNo.NO,
                ButtonToolTips.EmitterMode, ButtonToolTips.EmitWhenCrafting);

            registerApp(Icon.STORAGE_FILTER_EXTRACTABLE_ONLY, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY,
                ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsNo);
            registerApp(Icon.STORAGE_FILTER_EXTRACTABLE_NONE, Settings.STORAGE_FILTER, StorageFilter.NONE,
                ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsYes);

            registerApp(Icon.PLACEMENT_BLOCK, Settings.PLACE_BLOCK, YesNo.YES,
                ButtonToolTips.BlockPlacement, ButtonToolTips.BlockPlacementYes);
            registerApp(Icon.PLACEMENT_ITEM, Settings.PLACE_BLOCK, YesNo.NO,
                ButtonToolTips.BlockPlacement, ButtonToolTips.BlockPlacementNo);

            registerApp(Icon.AUTO_EXPORT_OFF, Settings.FORMATION_PLANE_MODE, FormationPlaneMode.PASSIVE,
                ButtonToolTips.FormationPlaneMode, ButtonToolTips.FormationPlanePassive);
            registerApp(Icon.AUTO_EXPORT_ON, Settings.FORMATION_PLANE_MODE, FormationPlaneMode.ACTIVE,
                ButtonToolTips.FormationPlaneMode, ButtonToolTips.FormationPlaneActive);

            registerApp(Icon.SCHEDULING_DEFAULT, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT,
                ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeDefault);
            registerApp(Icon.SCHEDULING_ROUND_ROBIN, Settings.SCHEDULING_MODE, SchedulingMode.ROUNDROBIN,
                ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRoundRobin);
            registerApp(Icon.SCHEDULING_RANDOM, Settings.SCHEDULING_MODE, SchedulingMode.RANDOM,
                ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRandom);

            registerApp(Icon.OVERLAY_OFF, Settings.OVERLAY_MODE, YesNo.NO, ButtonToolTips.OverlayMode,
                ButtonToolTips.OverlayModeNo);
            registerApp(Icon.OVERLAY_ON, Settings.OVERLAY_MODE, YesNo.YES, ButtonToolTips.OverlayMode,
                ButtonToolTips.OverlayModeYes);
            registerApp(Icon.REGULATE_STOCK_ON, Settings.REGULATE_STOCK, YesNo.YES,
                ButtonToolTips.RegulateStock, ButtonToolTips.RegulateStockOn);
            registerApp(Icon.REGULATE_STOCK_OFF, Settings.REGULATE_STOCK, YesNo.NO,
                ButtonToolTips.RegulateStock, ButtonToolTips.RegulateStockOff);

            registerApp(Icon.FILTER_ON_EXTRACT_ENABLED, Settings.FILTER_ON_EXTRACT, YesNo.YES,
                ButtonToolTips.FilterOnExtract, ButtonToolTips.FilterOnExtractEnabled);
            registerApp(Icon.FILTER_ON_EXTRACT_DISABLED, Settings.FILTER_ON_EXTRACT, YesNo.NO,
                ButtonToolTips.FilterOnExtract, ButtonToolTips.FilterOnExtractDisabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_NBT_ENABLED, Settings.PORTABLE_CELL_PICKUP_MATCH_NBT, YesNo.YES,
                ButtonToolTips.PortableCellPickupMatchNbt, ButtonToolTips.PortableCellPickupMatchNbtEnabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_NBT_DISABLED, Settings.PORTABLE_CELL_PICKUP_MATCH_NBT, YesNo.NO,
                ButtonToolTips.PortableCellPickupMatchNbt, ButtonToolTips.PortableCellPickupMatchNbtDisabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_DAMAGE_ENABLED, Settings.PORTABLE_CELL_PICKUP_MATCH_DAMAGE,
                YesNo.YES,
                ButtonToolTips.PortableCellPickupMatchDamage, ButtonToolTips.PortableCellPickupMatchDamageEnabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_DAMAGE_DISABLED, Settings.PORTABLE_CELL_PICKUP_MATCH_DAMAGE,
                YesNo.NO,
                ButtonToolTips.PortableCellPickupMatchDamage, ButtonToolTips.PortableCellPickupMatchDamageDisabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY_ENABLED,
                Settings.PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY, YesNo.YES,
                ButtonToolTips.PortableCellPickupMatchOreDictionary,
                ButtonToolTips.PortableCellPickupMatchOreDictionaryEnabled);
            registerApp(Icon.PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY_DISABLED,
                Settings.PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY, YesNo.NO,
                ButtonToolTips.PortableCellPickupMatchOreDictionary,
                ButtonToolTips.PortableCellPickupMatchOreDictionaryDisabled);

            registerApp(Icon.CRAFT_HAMMER, Settings.CPU_SELECTION_MODE, CpuSelectionMode.ANY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModeAny);
            registerApp(AEParts.TERMINAL, Settings.CPU_SELECTION_MODE, CpuSelectionMode.PLAYER_ONLY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModePlayersOnly);
            registerApp(AEParts.EXPORT_BUS, Settings.CPU_SELECTION_MODE, CpuSelectionMode.MACHINE_ONLY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModeAutomationOnly);
        }
    }

    private static <T extends Enum<T>> void registerApp(Icon icon, Setting<T> setting, T val,
                                                        LocalizationEnum title, LocalizationEnum hint) {
        registerApp(icon, setting, val, title, hint.text());
    }

    private static <T extends Enum<T>> void registerApp(Icon icon, Setting<T> setting, T val,
                                                        LocalizationEnum title, ITextComponent... tooltipLines) {
        var lines = new ObjectArrayList<ITextComponent>(tooltipLines.length + 1);
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(new EnumPair<>(setting, val), new ButtonAppearance(icon, null, lines));
    }

    private static <T extends Enum<T>> void registerAppWithoutTitle(Icon icon, Setting<T> setting, T val,
                                                                    ITextComponent... tooltipLines) {
        var lines = new ObjectArrayList<ITextComponent>(tooltipLines.length);
        Collections.addAll(lines, tooltipLines);

        appearances.put(new EnumPair<>(setting, val), new ButtonAppearance(icon, null, lines));
    }

    private static <T extends Enum<T>> void registerApp(ItemDefinition<?> item, Setting<T> setting, T val,
                                                        LocalizationEnum title, LocalizationEnum hint) {
        registerApp(item, setting, val, title, hint.text());
    }

    private static <T extends Enum<T>> void registerApp(ItemDefinition<?> item, Setting<T> setting, T val,
                                                        LocalizationEnum title, ITextComponent... tooltipLines) {
        var lines = new ObjectArrayList<ITextComponent>(tooltipLines.length + 1);
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(new EnumPair<>(setting, val), new ButtonAppearance(null, item.item(), lines));
    }

    static <T extends Enum<T>> void registerAppearance(Setting<T> setting, T value, ButtonAppearance appearance) {
        if (appearances == null) {
            String message = "Setting toggle appearances must be initialized before registering custom appearances";
            AELog.error(message);
            throw new IllegalStateException(message);
        }

        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "Value");
        Objects.requireNonNull(appearance, "appearance");

        var key = new EnumPair<>(setting, value);
        var existing = appearances.putIfAbsent(key, appearance);
        if (existing != null && !hasSameStableAppearance(existing, appearance)) {
            String message = "Conflicting setting toggle appearance registration for setting "
                + setting.getName() + " Value " + value.name();
            AELog.error(message);
            throw new IllegalStateException(message);
        }
    }

    private static boolean hasSameStableAppearance(ButtonAppearance existing, ButtonAppearance appearance) {
        return Objects.equals(existing.icon(), appearance.icon())
            && Objects.equals(existing.item(), appearance.item());
    }

    private static <T extends Enum<T>> void selectWithExistingHandler(SettingToggleButton<T> button, T value) {
        T previousValue = EnumCycler.rotateEnum(value, true, button.validValues);
        button.set(previousValue);
        button.triggerPress(false);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean releasedInside = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        if (releasedInside && !this.triggeredOnPress) {
            triggerPress(this.pressedBackwards);
        }
        this.pressedBackwards = false;
        this.triggeredOnPress = false;
    }

    private void triggerPress(boolean backwards) {
        onPress.handle(this, backwards);
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        var currentScreen = Minecraft.getMinecraft().currentScreen;
        if (currentScreen instanceof AEBaseGui<?> baseGui && baseGui.isHandlingRightClick()) {
            return mousePressed(minecraft, mouseX, mouseY, true, baseGui);
        }
        return mousePressed(minecraft, mouseX, mouseY, false, null);
    }

    protected boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY, int mouseButton, AEBaseGui<?> gui) {
        if (mouseButton != 0 && mouseButton != 1) {
            return false;
        }
        return mousePressed(minecraft, mouseX, mouseY, mouseButton == 1, gui);
    }

    private boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY, boolean backwards, AEBaseGui<?> gui) {
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        if (pressed) {
            this.pressedBackwards = backwards;
            if (this.pressedBackwards) {
                if (gui == null) {
                    throw new IllegalStateException("Right-click setting popup requires an AEBaseGui host");
                }
                if (shouldOpenSelectionPopup(gui)) {
                    openSelectionPopup(gui);
                } else {
                    triggerPress(true);
                }
                this.triggeredOnPress = true;
            } else {
                this.triggeredOnPress = false;
            }
        } else {
            this.pressedBackwards = false;
            this.triggeredOnPress = false;
        }
        return pressed;
    }

    protected boolean shouldOpenSelectionPopup(AEBaseGui<?> gui) {
        return this.validValues.size() >= 3;
    }

    protected void openSelectionPopup(AEBaseGui<?> gui) {
        List<GridSelectionPopup.Entry<T>> entries = new ObjectArrayList<>(this.validValues.size());
        for (T value : this.validValues) {
            ButtonAppearance appearance = getAppearance(value);
            if (appearance == null) {
                entries.add(GridSelectionPopup.Entry.icon(value, Icon.INVALID,
                    Collections.singletonList(ButtonToolTips.NoSuchMessage.text())));
            } else if (appearance.item != null) {
                entries.add(GridSelectionPopup.Entry.item(value, new ItemStack(appearance.item),
                    appearance.tooltipLines));
            } else {
                Icon icon = appearance.icon != null ? appearance.icon : Icon.TOOLBAR_BUTTON_BACKGROUND;
                entries.add(GridSelectionPopup.Entry.icon(value, icon, appearance.tooltipLines));
            }
        }

        var bounds = gui.getBounds(false);
        gui.openSelectionPopup(GridSelectionPopup.forButton(this, gui.getGuiLeft(), gui.getGuiTop(), bounds.width,
            bounds.height, entries, this::setValueDirect));
    }

    protected void setValueDirect(T value) {
        if (value == this.currentValue) {
            return;
        }
        this.onDirectSelection.handle(this, value);
    }

    @Override
    protected Icon getIcon() {
        var appearance = getAppearance();
        if (appearance != null && appearance.icon != null) {
            return appearance.icon;
        }
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        var appearance = getAppearance();
        return appearance != null ? appearance.item : null;
    }

    public Setting<T> getSetting() {
        return this.buttonSetting;
    }

    public T getCurrentValue() {
        return this.currentValue;
    }

    public void set(T value) {
        this.currentValue = value;
    }

    public T getNextValue(boolean backwards) {
        return EnumCycler.rotateEnum(currentValue, backwards, validValues);
    }

    public List<T> getValidValues() {
        return List.copyOf(this.validValues);
    }

    public ButtonAppearance getAppearance(T value) {
        return appearances.get(new EnumPair<>(this.buttonSetting, value));
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        if (this.buttonSetting == null || this.currentValue == null) {
            return Collections.emptyList();
        }

        var appearance = getAppearance();
        if (appearance == null) {
            return Collections.singletonList(ButtonToolTips.NoSuchMessage.text());
        }

        if (this.validValues.size() < 3) {
            return appearance.tooltipLines;
        }

        List<ITextComponent> lines = new ObjectArrayList<>(appearance.tooltipLines.size() + 2);
        lines.addAll(appearance.tooltipLines);
        lines.add(Tooltips.muted(ButtonToolTips.CycleModeAction.text(Tooltips.getMouseButtonText(0))));
        lines.add(Tooltips.muted(ButtonToolTips.SelectModeAction.text(Tooltips.getMouseButtonText(1))));
        return lines;
    }

    private ButtonAppearance getAppearance() {
        return appearances.get(new EnumPair<>(this.buttonSetting, this.currentValue));
    }

    @FunctionalInterface
    public interface IHandler<T extends SettingToggleButton<?>> {
        void handle(T button, boolean backwards);
    }

    @FunctionalInterface
    public interface IValueHandler<T extends Enum<T>, B extends SettingToggleButton<T>> {
        void handle(B button, T value);
    }

    private record EnumPair<T extends Enum<T>>(Setting<T> setting, T value) {

        @Override
        public int hashCode() {
            return this.setting.hashCode() ^ this.value.hashCode();
        }

    }

    public record ButtonAppearance(Icon icon, Item item, List<ITextComponent> tooltipLines) {
    }
}
