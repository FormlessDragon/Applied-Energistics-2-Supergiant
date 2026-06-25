/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.core;

import ae2.api.behaviors.GenericSlotCapacities;
import ae2.api.config.CondenserOutput;
import ae2.api.config.CraftingPlanSortMode;
import ae2.api.config.PinDisplayMode;
import ae2.api.config.PowerUnit;
import ae2.api.config.SortDir;
import ae2.api.config.TerminalStyle;
import ae2.api.networking.pathing.ChannelMode;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEKeyType;
import ae2.core.settings.TickRates;
import ae2.me.service.TickManagerService;
import ae2.util.EmptyArrays;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.List;

@Config(modid = Tags.MOD_ID, name = Tags.MOD_ID, category = "")
public class AEConfig {

    @Config.Name("general")
    public static final General GENERAL = new General();

    @Config.Name("debug")
    public static final Debug DEBUG = new Debug();

    @Config.Name("battery")
    public static final Battery BATTERY = new Battery();

    @Config.Name("condenser")
    public static final Condenser CONDENSER = new Condenser();

    @Config.Name("automation")
    public static final Automation AUTOMATION = new Automation();

    @Config.Name("crafting")
    public static final Crafting CRAFTING = new Crafting();

    @Config.Name("craftingCPU")
    public static final CraftingCPU CRAFTING_CPU = new CraftingCPU();

    @Config.Name("tooltip")
    public static final Tooltip TOOLTIP = new Tooltip();

    @Config.Name("wireless")
    public static final Wireless WIRELESS = new Wireless();

    @Config.Name("powerRatios")
    public static final PowerRatios POWER_RATIOS = new PowerRatios();

    @Config.Name("spatial")
    public static final Spatial SPATIAL = new Spatial();

    @Config.Name("vibrationChamber")
    public static final VibrationChamber VIBRATION_CHAMBER = new VibrationChamber();

    @Config.Name("client")
    public static final Client CLIENT = new Client();

    @Config.Name("terminals")
    public static final Terminals TERMINALS = new Terminals();

    @Config.Name("search")
    public static final Search SEARCH = new Search();

    @Config.Name("worldGen")
    public static final WorldGen WORLD_GEN = new WorldGen();

    @Config.Name("tick_rates")
    public static final TickRateConfig TICK_RATES = new TickRateConfig();

    @Config.Name("requester")
    public static final Requester REQUESTER = new Requester();

    private static AEConfig INSTANCE;

    private AEConfig() {
    }

    public static synchronized void init() {
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        INSTANCE = new AEConfig();
        INSTANCE.applyRuntimeValues();
    }

    public static AEConfig instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("AEConfig has not been initialized yet");
        }
        return INSTANCE;
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Tags.MOD_ID.equals(event.getModID())) {
            return;
        }

        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        this.applyRuntimeValues();
        GenericSlotCapacities.modifyValue(AEKeyType.items(), getInterfaceItemSlotCapacity());
        GenericSlotCapacities.modifyValue(AEKeyType.fluids(), getInterfaceFluidSlotCapacity());
    }

    private void applyRuntimeValues() {
        CondenserOutput.MATTER_BALLS.requiredPower = CONDENSER.matterBallsPower;
        CondenserOutput.SINGULARITY.requiredPower = CONDENSER.singularityPower;

        for (TickRates tickRate : TickRates.values()) {
            TickRateRange range = TICK_RATES.get(tickRate);
            tickRate.setMin(Math.max(1, range.min));
            tickRate.setMax(Math.max(tickRate.getMin(), range.max));
        }

        TickManagerService.MONITORING_ENABLED = DEBUG.tickMonitoring;
    }

    @SuppressWarnings("unused")
    public boolean isDebugLoggingEnabled() {
        return GENERAL.debugLogging;
    }

    public boolean isBlockUpdateLogEnabled() {
        return GENERAL.blockUpdateLog;
    }

    public boolean isChunkLoggerTraceEnabled() {
        return DEBUG.chunkLoggerTrace;
    }

    public boolean isDebugEnergyEnabled() {
        return DEBUG.debugEnergy;
    }

    public void setDebugEnergyEnabled(boolean enabled) {
        DEBUG.debugEnergy = enabled;
        this.save();
    }

    public boolean isCraftingPerformanceLogEnabled() {
        return DEBUG.craftingPerformanceLog;
    }

    public boolean isTickMonitoringEnabled() {
        return DEBUG.tickMonitoring;
    }

    public void setTickMonitoringEnabled(boolean enabled) {
        DEBUG.tickMonitoring = enabled;
        TickManagerService.MONITORING_ENABLED = enabled;
        this.save();
        TickManagerService.MONITORING_ENABLED = enabled;
    }

    public double getGridEnergyStoragePerNode() {
        return 25.0D;
    }

    public double getCrystalResonanceGeneratorRate() {
        return 20.0D;
    }

    public int getColorApplicatorBattery() {
        return BATTERY.colorApplicator;
    }

    public int getChargedStaffBattery() {
        return BATTERY.chargedStaff;
    }

    public int getMatterCannonBattery() {
        return BATTERY.matterCannon;
    }

    public int getEntropyManipulatorBattery() {
        return BATTERY.entropyManipulator;
    }

    public int getWirelessTerminalBattery() {
        return BATTERY.wirelessTerminal;
    }

    public int getPortableCellBattery() {
        return BATTERY.portableCell;
    }

    public int getFormationPlaneEntityLimit() {
        return AUTOMATION.formationPlaneEntityLimit;
    }

    public long getInterfaceItemSlotCapacity() {
        return AUTOMATION.interfaceItemSlotCapacityStacks;
    }

    public long getInterfaceFluidSlotCapacity() {
        return (long) AUTOMATION.interfaceFluidSlotCapacityBuckets * AEFluidKey.AMOUNT_BUCKET;
    }

    public int getInterfacePageLimit() {
        return Math.max(1, AUTOMATION.meInterfacePageLimit);
    }

    public int getPatternProviderExpansionCardLimit() {
        return Math.max(0, AUTOMATION.patternProviderExpansionCardLimit);
    }

    public int getMolecularAssemblerPatternExpansionCardLimit() {
        return Math.max(0, AUTOMATION.molecularAssemblerPatternExpansionCardLimit);
    }

    public int getGrowthAcceleratorSpeed() {
        return CRAFTING.growthAccelerator;
    }

    public double getP2PTunnelEnergyTax() {
        return POWER_RATIOS.p2pTunnelEnergyTax;
    }

    public double getP2PTunnelTransportTax() {
        return POWER_RATIOS.p2pTunnelTransportTax;
    }

    public int getCraftingCalculationTimePerTick() {
        return CRAFTING_CPU.craftingCalculationTimePerTick;
    }

    @SuppressWarnings("unused")
    public boolean isTooltipShowCellUpgrades() {
        return TOOLTIP.showCellUpgrades;
    }

    public boolean isTooltipShowCellContent() {
        return TOOLTIP.showCellContent;
    }

    public int getTooltipMaxCellContentShown() {
        return TOOLTIP.maxCellContentShown;
    }

    public boolean isAnnihilationPlaneSkyDustGenerationEnabled() {
        return AUTOMATION.annihilationPlaneSkyDustGeneration;
    }

    public boolean isMatterCanonBlockDamageEnabled() {
        return AUTOMATION.matterCannonBlockDamage;
    }

    public boolean isTinyTntBlockDamageEnabled() {
        return GENERAL.tinyTntBlockDamage;
    }

    public boolean isRequireSneakForCableBlockingPanelPlacement() {
        return GENERAL.requireSneakForCableBlockingPanelPlacement;
    }

    public boolean isEnableEffects() {
        return CLIENT.enableEffects;
    }

    public boolean isPlacementPreviewEnabled() {
        return CLIENT.placementPreviewEnabled;
    }

    public boolean isShowDebugGuiOverlays() {
        return CLIENT.showDebugGuiOverlays;
    }

    public void setShowDebugGuiOverlays(boolean enabled) {
        CLIENT.showDebugGuiOverlays = enabled;
        this.save();
    }

    public boolean isUseLargeFonts() {
        return CLIENT.useLargeFonts;
    }

    public PowerUnit getSelectedEnergyUnit() {
        return CLIENT.powerUnit == null ? PowerUnit.AE : CLIENT.powerUnit;
    }

    public void setSelectedEnergyUnit(PowerUnit selectedPowerUnit) {
        CLIENT.powerUnit = selectedPowerUnit == null ? PowerUnit.AE : selectedPowerUnit;
        this.save();
    }

    public ChannelMode getChannelMode() {
        return GENERAL.channels == null ? ChannelMode.DEFAULT : GENERAL.channels;
    }

    public void setChannelMode(ChannelMode channelMode) {
        GENERAL.channels = channelMode == null ? ChannelMode.DEFAULT : channelMode;
        this.save();
    }

    public double getSpatialPowerExponent() {
        return SPATIAL.spatialPowerExponent;
    }

    public double getSpatialPowerMultiplier() {
        return SPATIAL.spatialPowerMultiplier;
    }

    public double getVibrationChamberBaseEnergyPerFuelTick() {
        return VIBRATION_CHAMBER.baseEnergyPerFuelTick;
    }

    public int getVibrationChamberMinEnergyPerGameTick() {
        return VIBRATION_CHAMBER.minEnergyPerGameTick;
    }

    public int getVibrationChamberMaxEnergyPerGameTick() {
        return VIBRATION_CHAMBER.baseMaxEnergyPerGameTick;
    }

    public double wireless_getDrainRate(double range) {
        return WIRELESS.wirelessTerminalDrainMultiplier * range;
    }

    public double wireless_getMaxRange(int boosters) {
        return WIRELESS.wirelessBaseRange
            + WIRELESS.wirelessBoosterRangeMultiplier * Math.pow(boosters, WIRELESS.wirelessBoosterExp);
    }

    public double wireless_getPowerDrain(int boosters) {
        return WIRELESS.wirelessBaseCost
            + WIRELESS.wirelessCostMultiplier
            * Math.pow(boosters, 1 + boosters / WIRELESS.wirelessHighWirelessCount);
    }

    public boolean isPinAutoCraftedItems() {
        return CLIENT.pinAutoCraftedItems;
    }

    public void setPinAutoCraftedItems(boolean enabled) {
        CLIENT.pinAutoCraftedItems = enabled;
        this.save();
    }

    public TerminalStyle getTerminalStyle() {
        return TERMINALS.terminalStyle == null ? TerminalStyle.SMALL : TERMINALS.terminalStyle;
    }

    public void setTerminalStyle(TerminalStyle terminalStyle) {
        TERMINALS.terminalStyle = terminalStyle == null ? TerminalStyle.SMALL : terminalStyle;
        this.save();
    }

    public PinDisplayMode getPinDisplayMode() {
        return TERMINALS.pinDisplayMode == null ? PinDisplayMode.SORT_TOP : TERMINALS.pinDisplayMode;
    }

    public void setPinDisplayMode(PinDisplayMode pinDisplayMode) {
        PinDisplayMode mode = pinDisplayMode == null ? PinDisplayMode.SORT_TOP : pinDisplayMode;
        if (getPinDisplayMode() != mode) {
            TERMINALS.pinDisplayMode = mode;
            this.save();
        }
    }

    public CraftingPlanSortMode getCraftingPlanSortMode() {
        return TERMINALS.craftingPlanSortMode == null ? CraftingPlanSortMode.AVAILABILITY
            : TERMINALS.craftingPlanSortMode;
    }

    public void setCraftingPlanSortMode(CraftingPlanSortMode sortMode) {
        TERMINALS.craftingPlanSortMode = sortMode == null ? CraftingPlanSortMode.AVAILABILITY : sortMode;
        this.save();
    }

    public SortDir getCraftingPlanSortDirection() {
        return TERMINALS.craftingPlanSortDirection == null ? SortDir.ASCENDING : TERMINALS.craftingPlanSortDirection;
    }

    public void setCraftingPlanSortDirection(SortDir sortDirection) {
        TERMINALS.craftingPlanSortDirection = sortDirection == null ? SortDir.ASCENDING : sortDirection;
        this.save();
    }

    public boolean isClearGridOnClose() {
        return CLIENT.clearGridOnClose;
    }

    public void setClearGridOnClose(boolean enabled) {
        CLIENT.clearGridOnClose = enabled;
        this.save();
    }

    public int getTerminalMargin() {
        return TERMINALS.terminalMargin;
    }

    public boolean isNotifyForFinishedCraftingJobs() {
        return CLIENT.notifyForFinishedCraftingJobs;
    }

    public void setNotifyForFinishedCraftingJobs(boolean enabled) {
        CLIENT.notifyForFinishedCraftingJobs = enabled;
        this.save();
    }

    public boolean isUseExternalSearch() {
        return SEARCH.useExternalSearch;
    }

    public void setUseExternalSearch(boolean enabled) {
        SEARCH.useExternalSearch = enabled;
        this.save();
    }

    public boolean isSearchModNameInTooltips() {
        return SEARCH.searchModNameInTooltips;
    }

    @SuppressWarnings("unused")
    public void setSearchModNameInTooltips(boolean enabled) {
        SEARCH.searchModNameInTooltips = enabled;
        this.save();
    }

    public boolean isClearExternalSearchOnOpen() {
        return SEARCH.clearExternalSearchOnOpen;
    }

    public void setClearExternalSearchOnOpen(boolean enabled) {
        SEARCH.clearExternalSearchOnOpen = enabled;
        this.save();
    }

    public boolean isSyncWithExternalSearch() {
        return SEARCH.syncWithExternalSearch;
    }

    public void setSyncWithExternalSearch(boolean enabled) {
        SEARCH.syncWithExternalSearch = enabled;
        this.save();
    }

    public boolean isRememberLastSearch() {
        return SEARCH.rememberLastSearch;
    }

    public void setRememberLastSearch(boolean enabled) {
        SEARCH.rememberLastSearch = enabled;
        this.save();
    }

    public boolean isAutoFocusSearch() {
        return SEARCH.autoFocusSearch;
    }

    public void setAutoFocusSearch(boolean enabled) {
        SEARCH.autoFocusSearch = enabled;
        this.save();
    }

    public String[] getPatternImportPriorityOrder() {
        return CLIENT.patternImportPriorityOrder.clone();
    }

    public void setPatternImportPriorityOrder(List<String> order) {
        setPatternImportPriorityOrder(order.toArray(String[]::new));
    }

    public void setPatternImportPriorityOrder(String[] order) {
        String[] nextOrder = order == null ? EmptyArrays.EMPTY_STRING_ARRAY : order.clone();
        if (!Arrays.equals(nextOrder, CLIENT.patternImportPriorityOrder)) {
            CLIENT.patternImportPriorityOrder = nextOrder;
            this.save();
        }
    }

    public boolean isDebugToolsEnabled() {
        return GENERAL.debugTools;
    }

    public boolean isSpawnPressesInMeteoritesEnabled() {
        return WORLD_GEN.spawnPressesInMeteoritesEnabled;
    }

    public boolean isSpawnFlawlessOnlyEnabled() {
        return WORLD_GEN.spawnFlawlessOnly;
    }

    public int[] getMeteoriteDimensionWhitelist() {
        return WORLD_GEN.meteoriteDimensionWhitelist.clone();
    }

    public int getSpatialDimensionId() {
        return WORLD_GEN.spatialDimensionId;
    }

    public int getRequests() {
        return REQUESTER.requests;
    }

    public double getIdleEnergy() {
        return REQUESTER.idleEnergy;
    }

    public boolean getRequireChannel() {
        return REQUESTER.requireChannel;
    }

    public void save() {
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        this.applyRuntimeValues();
    }

    public static final class General {
        @Config.Name("tinyTntBlockDamage")
        @Config.Comment("Enables the ability of Tiny TNT to break blocks.")
        public boolean tinyTntBlockDamage = true;
        @Config.Name("debugLogging")
        @Config.Comment("Enables extra AE2 bootstrap logging.")
        public boolean debugLogging;
        @Config.Name("debugTools")
        @Config.Comment("Enable unsupported AE2 debug tools.")
        public boolean debugTools;
        @Config.Name("blockUpdateLog")
        @Config.Comment("Enable logging for block updates.")
        public boolean blockUpdateLog;
        @Config.Name("channels")
        @Config.Comment("Changes the channel capacity that cables provide in AE2.")
        public ChannelMode channels = ChannelMode.DEFAULT;

        @Config.Name("requireSneakForCableBlockingPanelPlacement")
        @Config.Comment("If enabled, panel-type parts can only be placed on a cable side with an existing cable connection while sneaking.")
        public boolean requireSneakForCableBlockingPanelPlacement = true;
    }

    public static final class Debug {
        @Config.Name("chunkLoggerTrace")
        @Config.Comment("Enable stack trace logging for the chunk loading debug command.")
        public boolean chunkLoggerTrace;

        @Config.Name("debugEnergy")
        @Config.Comment("Treat every energy grid as if it had a creative energy cell.")
        public boolean debugEnergy;

        @Config.Name("craftingPerformanceLog")
        @Config.Comment("Enable server-side autocrafting performance timing logs.")
        public boolean craftingPerformanceLog;

        @Config.Name("tickMonitoring")
        @Config.Comment("Enable server-side AE network tick timing sampling.")
        public boolean tickMonitoring;
    }

    public static final class Battery {
        @Config.Name("colorApplicator")
        @Config.Comment("Maximum AE stored by the Color Applicator.")
        @Config.RangeInt(min = 1)
        public int colorApplicator = 20000;

        @Config.Name("chargedStaff")
        @Config.Comment("Maximum AE stored by the Charged Staff.")
        @Config.RangeInt(min = 1)
        public int chargedStaff = 8000;

        @Config.Name("matterCannon")
        @Config.Comment("Maximum AE stored by the Matter Cannon.")
        @Config.RangeInt(min = 1)
        public int matterCannon = 200000;

        @Config.Name("entropyManipulator")
        @Config.Comment("Maximum AE stored by the Entropy Manipulator.")
        @Config.RangeInt(min = 1)
        public int entropyManipulator = 100000;

        @Config.Name("wirelessTerminal")
        @Config.Comment("Maximum AE stored by wireless terminals.")
        @Config.RangeInt(min = 1)
        public int wirelessTerminal = 1600000;

        @Config.Name("portableCell")
        @Config.Comment("Maximum AE stored by portable cells.")
        @Config.RangeInt(min = 1)
        public int portableCell = 20000;
    }

    public static final class Condenser {
        @Config.Name("matterBalls")
        @Config.RangeInt(min = 1)
        public int matterBallsPower = 256;

        @Config.Name("singularity")
        @Config.RangeInt(min = 1)
        public int singularityPower = 256000;
    }

    public static final class Automation {
        @Config.Name("formationPlaneEntityLimit")
        @Config.RangeInt(min = 1)
        public int formationPlaneEntityLimit = 128;

        @Config.Name("interfaceItemSlotCapacityStacks")
        @Config.Comment("Maximum number of items a single ME Interface stocking slot can hold.")
        @Config.RangeInt(min = 1)
        public int interfaceItemSlotCapacityStacks = 1024;

        @Config.Name("interfaceFluidSlotCapacityBuckets")
        @Config.Comment("Maximum number of fluid buckets a single ME Interface stocking slot can hold.")
        @Config.RangeInt(min = 1)
        public int interfaceFluidSlotCapacityBuckets = 256;

        @Config.Name("MEInterfacePageLimit")
        @Config.Comment("Maximum number of 18-slot ME Interface pages.")
        @Config.RangeInt(min = 1)
        @Config.RequiresWorldRestart
        @Config.RequiresMcRestart
        public int meInterfacePageLimit = 1;

        @Config.Name("PatternProviderExpansionCardLimit")
        @Config.Comment("Maximum number of Pattern Expansion Cards that can be installed in one Pattern Provider.")
        @Config.RangeInt(min = 0)
        @Config.RequiresWorldRestart
        @Config.RequiresMcRestart
        public int patternProviderExpansionCardLimit = 3;

        @Config.Name("MolecularAssemblerPatternExpansionCardLimit")
        @Config.Comment("Maximum number of Pattern Expansion Cards that can be installed in one Molecular Assembler.")
        @Config.RangeInt(min = 0)
        @Config.RequiresWorldRestart
        @Config.RequiresMcRestart
        public int molecularAssemblerPatternExpansionCardLimit = 3;

        @Config.Name("annihilationPlaneSkyDustGeneration")
        @Config.Comment("If enabled, an annihilation placed face up at the maximum world height will generate sky stone passively.")
        public boolean annihilationPlaneSkyDustGeneration = true;

        @Config.Name("matterCannonBlockDamage")
        @Config.Comment("Allow the Matter Cannon to break blocks.")
        public boolean matterCannonBlockDamage = true;
    }

    public static final class Crafting {
        @Config.Name("growthAccelerator")
        @Config.Comment("Number of ticks between two crystal growth accelerator ticks.")
        @Config.RangeInt(min = 1, max = 100)
        public int growthAccelerator = 10;
    }

    public static final class CraftingCPU {
        @Config.Name("craftingCalculationTimePerTick")
        @Config.RangeInt(min = 1)
        public int craftingCalculationTimePerTick = 5;
    }

    public static final class Tooltip {
        @Config.Name("showCellUpgrades")
        @Config.Comment("Show storage cell upgrades in extended tooltips.")
        public boolean showCellUpgrades = true;

        @Config.Name("showCellContent")
        @Config.Comment("Show storage cell contents in extended tooltips.")
        public boolean showCellContent = true;

        @Config.Name("maxCellContentShown")
        @Config.Comment("Maximum number of storage cell entries shown in tooltips.")
        @Config.RangeInt(min = 1, max = 32)
        public int maxCellContentShown = 5;
    }

    public static final class Wireless {
        @Config.Name("wirelessBaseCost")
        @Config.Comment("Base AE drain for wireless access points.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessBaseCost = 8.0D;

        @Config.Name("wirelessCostMultiplier")
        @Config.Comment("Additional drain multiplier for wireless access points.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessCostMultiplier = 1.0D;

        @Config.Name("wirelessBaseRange")
        @Config.Comment("Base wireless access point range in blocks.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessBaseRange = 16.0D;

        @Config.Name("wirelessBoosterRangeMultiplier")
        @Config.Comment("Range multiplier contributed by each wireless booster.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessBoosterRangeMultiplier = 1.0D;

        @Config.Name("wirelessBoosterExp")
        @Config.Comment("Exponent applied to the installed wireless boosters when calculating range.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessBoosterExp = 1.5D;

        @Config.Name("wirelessHighWirelessCount")
        @Config.Comment("Booster scaling factor used when calculating wireless access point drain.")
        @Config.RangeDouble(min = 1.0D)
        public double wirelessHighWirelessCount = 64.0D;

        @Config.Name("wirelessTerminalDrainMultiplier")
        @Config.Comment("Energy drain per block for wireless terminals.")
        @Config.RangeDouble(min = 0.0D)
        public double wirelessTerminalDrainMultiplier = 1.0D;
    }

    public static final class PowerRatios {
        @Config.Name("p2pTunnelEnergyTax")
        @Config.Comment("The cost to transport energy through an energy P2P tunnel expressed as a factor of the transported energy.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double p2pTunnelEnergyTax = 0.025D;

        @Config.Name("p2pTunnelTransportTax")
        @Config.Comment("The cost to transport items/fluids/etc. through P2P tunnels, expressed in AE energy per equivalent I/O bus operation.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double p2pTunnelTransportTax = 0.025D;
    }

    public static final class Spatial {
        @Config.Name("spatialPowerExponent")
        @Config.RangeDouble(min = 0.0D)
        public double spatialPowerExponent = 1.35D;

        @Config.Name("spatialPowerMultiplier")
        @Config.RangeDouble(min = 0.0D)
        public double spatialPowerMultiplier = 1250.0D;
    }

    public static final class VibrationChamber {
        @Config.Name("baseEnergyPerFuelTick")
        @Config.Comment("AE energy produced per fuel burn tick.")
        @Config.RangeDouble(min = 0.1D, max = 1000.0D)
        public double baseEnergyPerFuelTick = 5.0D;

        @Config.Name("minEnergyPerGameTick")
        @Config.Comment("Minimum amount of AE/t the vibration chamber can slow down to when energy is being wasted.")
        @Config.RangeInt(min = 0, max = 1000)
        public int minEnergyPerGameTick = 4;

        @Config.Name("baseMaxEnergyPerGameTick")
        @Config.Comment("Maximum amount of AE/t the vibration chamber can speed up to when generated energy is being fully consumed.")
        @Config.RangeInt(min = 1, max = 1000)
        public int baseMaxEnergyPerGameTick = 40;
    }

    public static final class Client {
        @Config.Name("enableEffects")
        @Config.Comment("Enable AE2 particle and lightning effects.")
        public boolean enableEffects = true;

        @Config.Name("placementPreviewEnabled")
        @Config.Comment("Show part and facade placement previews.")
        public boolean placementPreviewEnabled = true;

        @Config.Name("showDebugGuiOverlays")
        @Config.Comment("Show debugging GUI overlays.")
        public boolean showDebugGuiOverlays;

        @Config.Name("useTerminalUseLargeFont")
        @Config.Comment("Use larger numbers in terminals.")
        public boolean useLargeFonts;

        @Config.Name("notifyForFinishedCraftingJobs")
        @Config.Comment("Show toast when long-running crafting jobs finish.")
        public boolean notifyForFinishedCraftingJobs = true;

        @Config.Name("pinAutoCraftedItems")
        @Config.Comment("Pin items that the player auto-crafts to the top of the terminal.")
        public boolean pinAutoCraftedItems = true;

        @Config.Name("clearGridOnClose")
        @Config.Comment("Automatically clear the crafting grid when closing the terminal.")
        public boolean clearGridOnClose;

        @Config.Name("powerUnit")
        @Config.Comment("Unit of power shown in AE UIs.")
        public PowerUnit powerUnit = PowerUnit.AE;

        @Config.Name("patternImportPriorityOrder")
        @Config.Comment("Client-side order used when selecting HEI/JEI ingredient variants for the pattern encoding terminal.")
        public String[] patternImportPriorityOrder = EmptyArrays.EMPTY_STRING_ARRAY;
    }

    public static final class Terminals {
        @Config.Name("terminalMargin")
        @Config.Comment("The vertical margin to apply when sizing terminals.")
        @Config.RangeInt(min = 0)
        public int terminalMargin = 25;
        @Config.Name("terminalStyle")
        @Config.Comment("Size of ME terminal GUIs.")
        public TerminalStyle terminalStyle = TerminalStyle.SMALL;
        @Config.Name("pinDisplayMode")
        @Config.Comment("How player pins are displayed in ME terminals.")
        public PinDisplayMode pinDisplayMode = PinDisplayMode.SORT_TOP;
        @Config.Name("craftingPlanSortMode")
        @Config.Comment("Sort mode used by the crafting plan GUI.")
        public CraftingPlanSortMode craftingPlanSortMode = CraftingPlanSortMode.AVAILABILITY;
        @Config.Name("craftingPlanSortDirection")
        @Config.Comment("Sort direction used by the crafting plan GUI.")
        public SortDir craftingPlanSortDirection = SortDir.ASCENDING;
    }

    public static final class Search {
        @Config.Name("searchModNameInTooltips")
        @Config.Comment("Should the mod name be included when searching in tooltips.")
        public boolean searchModNameInTooltips;

        @Config.Name("useExternalSearch")
        @Config.Comment("Replaces AEs own search with an external item list search.")
        public boolean useExternalSearch;

        @Config.Name("clearExternalSearchOnOpen")
        @Config.Comment("When using useExternalSearch, clears the search when the terminal opens.")
        public boolean clearExternalSearchOnOpen = true;

        @Config.Name("syncWithExternalSearch")
        @Config.Comment("Automatically sync the AE search text with an external search while the terminal is open.")
        public boolean syncWithExternalSearch = true;

        @Config.Name("rememberLastSearch")
        @Config.Comment("Remembers the last search term and restores it when the terminal opens.")
        public boolean rememberLastSearch = true;

        @Config.Name("autoFocusSearch")
        @Config.Comment("Automatically focuses the search field when the terminal opens.")
        public boolean autoFocusSearch;
    }

    public static final class WorldGen {
        @Config.Name("spawnPressesInMeteoritesEnabled")
        @Config.Comment("Spawn mysterious cubes inside meteorites.")
        public boolean spawnPressesInMeteoritesEnabled = true;
        @Config.Name("meteoriteDimensionWhitelist")
        @Config.Comment("Dimensions that may generate meteorites.")
        public int[] meteoriteDimensionWhitelist = new int[]{0};
        @Config.Name("spatialDimensionId")
        @Config.Comment("Dimension id for the spatial storage dimension.")
        @Config.RangeInt()
        public int spatialDimensionId = -256;
        @Config.Name("spawnFlawlessOnly")
        @Config.Comment("Spawn only flawless budding quartz in meteorites.")
        public boolean spawnFlawlessOnly;
    }

    public static final class TickRateConfig {
        @Config.Name("Interface")
        public TickRateRange interfaceRate = new TickRateRange(TickRates.Interface);

        @Config.Name("ImportBus")
        public TickRateRange importBus = new TickRateRange(TickRates.ImportBus);

        @Config.Name("ExportBus")
        public TickRateRange exportBus = new TickRateRange(TickRates.ExportBus);

        @Config.Name("FormationPlane")
        public TickRateRange formationPlane = new TickRateRange(TickRates.FormationPlane);

        @Config.Name("AnnihilationPlane")
        public TickRateRange annihilationPlane = new TickRateRange(TickRates.AnnihilationPlane);

        @Config.Name("METunnel")
        public TickRateRange meTunnel = new TickRateRange(TickRates.METunnel);

        @Config.Name("Inscriber")
        public TickRateRange inscriber = new TickRateRange(TickRates.Inscriber);

        @Config.Name("Charger")
        public TickRateRange charger = new TickRateRange(TickRates.Charger);

        @Config.Name("IOPort")
        public TickRateRange ioPort = new TickRateRange(TickRates.IOPort);

        @Config.Name("VibrationChamber")
        public TickRateRange vibrationChamber = new TickRateRange(TickRates.VibrationChamber);

        @Config.Name("StorageBus")
        public TickRateRange storageBus = new TickRateRange(TickRates.StorageBus);

        @Config.Name("ItemTunnel")
        public TickRateRange itemTunnel = new TickRateRange(TickRates.ItemTunnel);

        @Config.Name("LightTunnel")
        public TickRateRange lightTunnel = new TickRateRange(TickRates.LightTunnel);

        private TickRateRange get(TickRates tickRate) {
            return switch (tickRate) {
                case Interface -> this.interfaceRate;
                case ImportBus -> this.importBus;
                case ExportBus -> this.exportBus;
                case FormationPlane -> this.formationPlane;
                case AnnihilationPlane -> this.annihilationPlane;
                case METunnel -> this.meTunnel;
                case Inscriber -> this.inscriber;
                case Charger -> this.charger;
                case IOPort -> this.ioPort;
                case VibrationChamber -> this.vibrationChamber;
                case StorageBus -> this.storageBus;
                case ItemTunnel -> this.itemTunnel;
                case LightTunnel -> this.lightTunnel;
            };
        }
    }

    public static final class TickRateRange {
        @Config.Name("min")
        @Config.Comment("Minimum ticks between work cycles.")
        @Config.RangeInt(min = 1)
        public int min;

        @Config.Name("max")
        @Config.Comment("Maximum ticks between work cycles.")
        @Config.RangeInt(min = 1)
        public int max;

        private TickRateRange(TickRates tickRate) {
            this.min = tickRate.getDefaultMin();
            this.max = tickRate.getDefaultMax();
        }
    }

    public static final class Requester {
        @Config.Name("requests")
        @Config.Comment("The amount of requests a single ME Requester can hold.")
        @Config.RangeInt(min = 1, max = 64)
        public int requests = 5;

        @Config.Name("idleEnergy")
        @Config.Comment("The amount of energy (in AE) the ME Requester drains from the ME network when idle.")
        @Config.RangeDouble(min = 0)
        public double idleEnergy = 5.0;

        @Config.Name("requireChannel")
        @Config.Comment("Whether the ME Requester requires an ME network channel to function.")
        public boolean requireChannel = true;
    }
}
