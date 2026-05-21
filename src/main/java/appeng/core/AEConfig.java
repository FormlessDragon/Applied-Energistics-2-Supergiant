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

package appeng.core;

import appeng.api.config.CondenserOutput;
import appeng.api.config.PowerUnit;
import appeng.api.config.TerminalStyle;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.stacks.AEFluidKey;
import appeng.core.settings.TickRates;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class AEConfig {

    private static AEConfig INSTANCE;

    private final Configuration configuration;

    private boolean debugLogging;
    private boolean debugToolsEnabled;
    private boolean blockUpdateLog;
    private boolean chunkLoggerTrace;
    private boolean debugEnergy;
    private boolean showDebugGuiOverlays;
    private boolean useLargeFonts;
    private boolean annihilationPlaneSkyDustGeneration = true;
    private boolean notifyForFinishedCraftingJobs = true;
    private TerminalStyle terminalStyle = TerminalStyle.SMALL;
    private boolean pinAutoCraftedItems = true;
    private boolean clearGridOnClose;
    private int terminalMargin = 25;
    private boolean searchModNameInTooltips;
    private boolean useExternalSearch;
    private boolean clearExternalSearchOnOpen = true;
    private boolean syncWithExternalSearch = true;
    private boolean rememberLastSearch = true;
    private boolean autoFocusSearch;
    private boolean spawnPressesInMeteoritesEnabled = true;
    private boolean spawnFlawlessOnly;
    private int[] meteoriteDimensionWhitelist = new int[]{0};
    private int spatialDimensionId = -256;
    private int craftingCalculationTimePerTick = 5;
    private int formationPlaneEntityLimit = 128;
    private int interfaceItemSlotCapacityStacks = 512;
    private int interfaceFluidSlotCapacityBuckets = 512;
    private int growthAcceleratorSpeed = 10;
    private boolean tooltipShowCellUpgrades = true;
    private boolean tooltipShowCellContent = true;
    private int tooltipMaxCellContentShown = 5;
    private int wirelessTerminalBattery = 1600000;
    private int portableCellBattery = 20000;
    private int colorApplicatorBattery = 20000;
    private int chargedStaffBattery = 8000;
    private int matterCannonBattery = 200000;
    private int entropyManipulatorBattery = 100000;
    private int condenserMatterBallsPower = 256;
    private int condenserSingularityPower = 256000;
    private double wirelessBaseCost = 8.0;
    private double wirelessCostMultiplier = 1.0;
    private double wirelessBaseRange = 16.0;
    private double wirelessBoosterRangeMultiplier = 1.0;
    private double wirelessBoosterExp = 1.5;
    private double wirelessHighWirelessCount = 64.0;
    private double wirelessTerminalDrainMultiplier = 1.0;
    private double p2pTunnelEnergyTax = 0.025;
    private double p2pTunnelTransportTax = 0.025;
    private double spatialPowerExponent = 1.35;
    private double spatialPowerMultiplier = 1250.0;
    private double vibrationChamberBaseEnergyPerFuelTick = 5.0;
    private int vibrationChamberMinEnergyPerGameTick = 4;
    private int vibrationChamberMaxEnergyPerGameTick = 40;
    private boolean matterCannonBlockDamage = true;
    private boolean tinyTntBlockDamage = true;
    private boolean enableEffects = true;
    private boolean placementPreviewEnabled = true;
    private PowerUnit selectedPowerUnit = PowerUnit.AE;
    private ChannelMode channelMode = ChannelMode.DEFAULT;

    private AEConfig(File file) {
        this.configuration = new Configuration(file);
        this.load();
    }

    public static synchronized void init(File file) {
        INSTANCE = new AEConfig(file);
    }

    public static AEConfig instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("AEConfig has not been initialized yet");
        }
        return INSTANCE;
    }

    private void load() {
        this.configuration.load();
        this.debugLogging = this.configuration.getBoolean("debugLogging", "general", false,
            "Enables extra AE2 bootstrap logging.");
        this.debugToolsEnabled = this.configuration.getBoolean("debugTools", "general", false,
            "Enable unsupported AE2 debug tools.");
        this.blockUpdateLog = this.configuration.getBoolean("blockUpdateLog", "general", false,
            "Enable logging for block updates.");
        this.chunkLoggerTrace = this.configuration.getBoolean("chunkLoggerTrace", "debug", false,
            "Enable stack trace logging for the chunk loading debug command.");
        this.debugEnergy = this.configuration.getBoolean("debugEnergy", "debug", false,
            "Treat every energy grid as if it had a creative energy cell.");
        String channelModeName = this.configuration.getString("channels", "general", ChannelMode.DEFAULT.name(),
            "Changes the channel capacity that cables provide in AE2.");
        try {
            this.channelMode = ChannelMode.valueOf(channelModeName);
        } catch (IllegalArgumentException ignored) {
            this.channelMode = ChannelMode.DEFAULT;
            this.configuration.get("general", "channels", ChannelMode.DEFAULT.name()).set(ChannelMode.DEFAULT.name());
        }
        this.colorApplicatorBattery = this.configuration.getInt("colorApplicator", "battery",
            this.colorApplicatorBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by the Color Applicator.");
        this.chargedStaffBattery = this.configuration.getInt("chargedStaff", "battery",
            this.chargedStaffBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by the Charged Staff.");
        this.matterCannonBattery = this.configuration.getInt("matterCannon", "battery",
            this.matterCannonBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by the Matter Cannon.");
        this.entropyManipulatorBattery = this.configuration.getInt("entropyManipulator", "battery",
            this.entropyManipulatorBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by the Entropy Manipulator.");
        this.condenserMatterBallsPower = this.configuration.getInt("matterBalls", "condenser",
            this.condenserMatterBallsPower, 1, Integer.MAX_VALUE, "");
        this.condenserSingularityPower = this.configuration.getInt("singularity", "condenser",
            this.condenserSingularityPower, 1, Integer.MAX_VALUE, "");
        this.wirelessTerminalBattery = this.configuration.getInt("wirelessTerminal", "battery",
            this.wirelessTerminalBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by wireless terminals.");
        this.portableCellBattery = this.configuration.getInt("portableCell", "battery",
            this.portableCellBattery, 1, Integer.MAX_VALUE,
            "Maximum AE stored by portable cells.");
        this.formationPlaneEntityLimit = this.configuration.getInt("formationPlaneEntityLimit", "automation",
            this.formationPlaneEntityLimit, 1, Integer.MAX_VALUE, "");
        this.interfaceItemSlotCapacityStacks = this.configuration.getInt("interfaceItemSlotCapacityStacks", "automation",
            this.interfaceItemSlotCapacityStacks, 1, Integer.MAX_VALUE,
            "Maximum number of items a single ME Interface stocking slot can hold.");
        this.interfaceFluidSlotCapacityBuckets = this.configuration.getInt("interfaceFluidSlotCapacityBuckets",
            "automation", this.interfaceFluidSlotCapacityBuckets, 1, Integer.MAX_VALUE,
            "Maximum number of fluid buckets a single ME Interface stocking slot can hold.");
        this.growthAcceleratorSpeed = this.configuration.getInt("growthAccelerator", "crafting",
            this.growthAcceleratorSpeed, 1, 100,
            "Number of ticks between two crystal growth accelerator ticks");
        this.craftingCalculationTimePerTick = this.configuration.getInt("craftingCalculationTimePerTick",
            "craftingCPU", this.craftingCalculationTimePerTick, 1, Integer.MAX_VALUE, "");
        this.tooltipShowCellUpgrades = this.configuration.getBoolean("showCellUpgrades", "tooltip",
            this.tooltipShowCellUpgrades, "Show storage cell upgrades in extended tooltips.");
        this.tooltipShowCellContent = this.configuration.getBoolean("showCellContent", "tooltip",
            this.tooltipShowCellContent, "Show storage cell contents in extended tooltips.");
        this.tooltipMaxCellContentShown = this.configuration.getInt("maxCellContentShown", "tooltip",
            this.tooltipMaxCellContentShown, 1, 32, "Maximum number of storage cell entries shown in tooltips.");
        this.wirelessBaseCost = this.configuration.getFloat("wirelessBaseCost", "wireless",
            (float) this.wirelessBaseCost, 0.0f, Float.MAX_VALUE,
            "Base AE drain for wireless access points.");
        this.wirelessCostMultiplier = this.configuration.getFloat("wirelessCostMultiplier", "wireless",
            (float) this.wirelessCostMultiplier, 0.0f, Float.MAX_VALUE,
            "Additional drain multiplier for wireless access points.");
        this.wirelessBaseRange = this.configuration.getFloat("wirelessBaseRange", "wireless",
            (float) this.wirelessBaseRange, 0.0f, Float.MAX_VALUE,
            "Base wireless access point range in blocks.");
        this.wirelessBoosterRangeMultiplier = this.configuration.getFloat("wirelessBoosterRangeMultiplier", "wireless",
            (float) this.wirelessBoosterRangeMultiplier, 0.0f, Float.MAX_VALUE,
            "Range multiplier contributed by each wireless booster.");
        this.wirelessBoosterExp = this.configuration.getFloat("wirelessBoosterExp", "wireless",
            (float) this.wirelessBoosterExp, 0.0f, Float.MAX_VALUE,
            "Exponent applied to the installed wireless boosters when calculating range.");
        this.wirelessHighWirelessCount = this.configuration.getFloat("wirelessHighWirelessCount", "wireless",
            (float) this.wirelessHighWirelessCount, 1.0f, Float.MAX_VALUE,
            "Booster scaling factor used when calculating wireless access point drain.");
        this.wirelessTerminalDrainMultiplier = this.configuration.getFloat("wirelessTerminalDrainMultiplier",
            "wireless", (float) this.wirelessTerminalDrainMultiplier, 0.0f, Float.MAX_VALUE,
            "Energy drain per block for wireless terminals.");
        this.p2pTunnelEnergyTax = this.configuration.getFloat("p2pTunnelEnergyTax", "powerRatios",
            (float) this.p2pTunnelEnergyTax, 0.0f, 1.0f,
            "The cost to transport energy through an energy P2P tunnel expressed as a factor of the transported energy.");
        this.p2pTunnelTransportTax = this.configuration.getFloat("p2pTunnelTransportTax", "powerRatios",
            (float) this.p2pTunnelTransportTax, 0.0f, 1.0f,
            "The cost to transport items/fluids/etc. through P2P tunnels, expressed in AE energy per equivalent I/O bus operation.");
        this.spatialPowerMultiplier = this.configuration.getFloat("spatialPowerMultiplier", "spatial",
            (float) this.spatialPowerMultiplier, 0.0f, Float.MAX_VALUE, "");
        this.spatialPowerExponent = this.configuration.getFloat("spatialPowerExponent", "spatial",
            (float) this.spatialPowerExponent, 0.0f, Float.MAX_VALUE, "");
        this.vibrationChamberBaseEnergyPerFuelTick = this.configuration.getFloat("baseEnergyPerFuelTick", "vibrationChamber",
            (float) this.vibrationChamberBaseEnergyPerFuelTick, 0.1f, 1000.0f,
            "AE energy produced per fuel burn tick (reminder: coal = 1600, block of coal = 16000, lava bucket = 20000 burn ticks)");
        this.vibrationChamberMinEnergyPerGameTick = this.configuration.getInt("minEnergyPerGameTick", "vibrationChamber",
            this.vibrationChamberMinEnergyPerGameTick, 0, 1000,
            "Minimum amount of AE/t the vibration chamber can slow down to when energy is being wasted.");
        this.vibrationChamberMaxEnergyPerGameTick = this.configuration.getInt("baseMaxEnergyPerGameTick", "vibrationChamber",
            this.vibrationChamberMaxEnergyPerGameTick, 1, 1000,
            "Maximum amount of AE/t the vibration chamber can speed up to when generated energy is being fully consumed.");
        this.annihilationPlaneSkyDustGeneration = this.configuration.getBoolean("annihilationPlaneSkyDustGeneration",
            "automation", this.annihilationPlaneSkyDustGeneration,
            "If enabled, an annihilation placed face up at the maximum world height will generate sky stone passively.");
        this.matterCannonBlockDamage = this.configuration.getBoolean("matterCannonBlockDamage", "automation",
            this.matterCannonBlockDamage, "Allow the Matter Cannon to break blocks.");
        this.tinyTntBlockDamage = this.configuration.getBoolean("tinyTntBlockDamage", "general",
            this.tinyTntBlockDamage, "Enables the ability of Tiny TNT to break blocks.");
        this.enableEffects = this.configuration.getBoolean("enableEffects", "client",
            this.enableEffects, "Enable AE2 particle and lightning effects.");
        this.placementPreviewEnabled = this.configuration.getBoolean("placementPreviewEnabled", "client",
            this.placementPreviewEnabled, "Show part and facade placement previews.");
        this.showDebugGuiOverlays = this.configuration.getBoolean("showDebugGuiOverlays", "client",
            this.showDebugGuiOverlays, "Show debugging GUI overlays.");
        this.useLargeFonts = this.configuration.getBoolean("useTerminalUseLargeFont", "client",
            this.useLargeFonts, "Use larger numbers in terminals.");
        CondenserOutput.MATTER_BALLS.requiredPower = this.condenserMatterBallsPower;
        CondenserOutput.SINGULARITY.requiredPower = this.condenserSingularityPower;
        this.notifyForFinishedCraftingJobs = this.configuration.getBoolean("notifyForFinishedCraftingJobs", "client",
            this.notifyForFinishedCraftingJobs, "Show toast when long-running crafting jobs finish.");
        String terminalStyleName = this.configuration.getString("terminalStyle", "terminals", TerminalStyle.SMALL.name(),
            "Size of ME terminal GUIs");
        try {
            this.terminalStyle = TerminalStyle.valueOf(terminalStyleName);
        } catch (IllegalArgumentException ignored) {
            this.terminalStyle = TerminalStyle.SMALL;
            this.configuration.get("terminals", "terminalStyle", TerminalStyle.SMALL.name())
                              .set(TerminalStyle.SMALL.name());
        }
        this.pinAutoCraftedItems = this.configuration.getBoolean("pinAutoCraftedItems", "client",
            this.pinAutoCraftedItems, "Pin items that the player auto-crafts to the top of the terminal");
        this.clearGridOnClose = this.configuration.getBoolean("clearGridOnClose", "client",
            this.clearGridOnClose, "Automatically clear the crafting grid when closing the terminal");
        this.terminalMargin = this.configuration.getInt("terminalMargin", "terminals", this.terminalMargin, 0,
            Integer.MAX_VALUE, "The vertical margin to apply when sizing terminals.");
        this.searchModNameInTooltips = this.configuration.getBoolean("searchModNameInTooltips", "search",
            this.searchModNameInTooltips, "Should the mod name be included when searching in tooltips.");
        this.useExternalSearch = this.configuration.getBoolean("useExternalSearch", "search", this.useExternalSearch,
            "Replaces AEs own search with an external item list search");
        this.clearExternalSearchOnOpen = this.configuration.getBoolean("clearExternalSearchOnOpen", "search",
            this.clearExternalSearchOnOpen,
            "When using useExternalSearch, clears the search when the terminal opens");
        this.syncWithExternalSearch = this.configuration.getBoolean("syncWithExternalSearch", "search",
            this.syncWithExternalSearch,
            "Automatically sync the AE search text with an external search while the terminal is open");
        this.rememberLastSearch = this.configuration.getBoolean("rememberLastSearch", "search",
            this.rememberLastSearch,
            "Remembers the last search term and restores it when the terminal opens");
        this.autoFocusSearch = this.configuration.getBoolean("autoFocusSearch", "search", this.autoFocusSearch,
            "Automatically focuses the search field when the terminal opens");
        this.spawnPressesInMeteoritesEnabled = this.configuration.getBoolean("spawnPressesInMeteoritesEnabled",
            "worldGen", this.spawnPressesInMeteoritesEnabled,
            "Spawn mysterious cubes inside meteorites.");
        this.spawnFlawlessOnly = this.configuration.getBoolean("spawnFlawlessOnly", "worldGen",
            this.spawnFlawlessOnly, "Spawn only flawless budding quartz in meteorites.");
        this.meteoriteDimensionWhitelist = this.configuration
            .get("worldGen", "meteoriteDimensionWhitelist", this.meteoriteDimensionWhitelist,
                "Dimensions that may generate meteorites.")
            .getIntList();
        this.spatialDimensionId = this.configuration.getInt("spatialDimensionId", "worldGen",
            this.spatialDimensionId, Integer.MIN_VALUE, Integer.MAX_VALUE,
            "Dimension id for the spatial storage dimension.");
        String selectedPowerUnitName = this.configuration.getString("powerUnit", "client", PowerUnit.AE.name(),
            "Unit of power shown in AE UIs");
        try {
            this.selectedPowerUnit = PowerUnit.valueOf(selectedPowerUnitName);
        } catch (IllegalArgumentException ignored) {
            this.selectedPowerUnit = PowerUnit.AE;
            this.configuration.get("client", "powerUnit", PowerUnit.AE.name()).set(PowerUnit.AE.name());
        }

        for (TickRates tickRate : TickRates.values()) {
            String category = "tick_rates." + tickRate.name();
            tickRate.setMin(this.configuration.getInt("min", category, tickRate.getDefaultMin(), 1, Integer.MAX_VALUE,
                "Minimum ticks between work cycles."));
            tickRate.setMax(this.configuration.getInt("max", category, tickRate.getDefaultMax(), tickRate.getMin(),
                Integer.MAX_VALUE, "Maximum ticks between work cycles."));
        }

        if (this.configuration.hasChanged()) {
            this.configuration.save();
        }
    }

    public boolean isDebugLoggingEnabled() {
        return this.debugLogging;
    }

    public boolean isBlockUpdateLogEnabled() {
        return this.blockUpdateLog;
    }

    public boolean isChunkLoggerTraceEnabled() {
        return this.chunkLoggerTrace;
    }

    public boolean isDebugEnergyEnabled() {
        return this.debugEnergy;
    }

    public double getGridEnergyStoragePerNode() {
        return 25.0D;
    }

    public double getCrystalResonanceGeneratorRate() {
        return 20.0D;
    }

    public int getColorApplicatorBattery() {
        return this.colorApplicatorBattery;
    }

    public int getChargedStaffBattery() {
        return this.chargedStaffBattery;
    }

    public int getMatterCannonBattery() {
        return this.matterCannonBattery;
    }

    public int getEntropyManipulatorBattery() {
        return this.entropyManipulatorBattery;
    }

    public int getWirelessTerminalBattery() {
        return this.wirelessTerminalBattery;
    }

    public int getPortableCellBattery() {
        return this.portableCellBattery;
    }

    public int getFormationPlaneEntityLimit() {
        return this.formationPlaneEntityLimit;
    }

    public long getInterfaceItemSlotCapacity() {
        return this.interfaceItemSlotCapacityStacks;
    }

    public long getInterfaceFluidSlotCapacity() {
        return (long) this.interfaceFluidSlotCapacityBuckets * AEFluidKey.AMOUNT_BUCKET;
    }

    public int getGrowthAcceleratorSpeed() {
        return this.growthAcceleratorSpeed;
    }

    public double getP2PTunnelEnergyTax() {
        return this.p2pTunnelEnergyTax;
    }

    public double getP2PTunnelTransportTax() {
        return this.p2pTunnelTransportTax;
    }

    public int getCraftingCalculationTimePerTick() {
        return this.craftingCalculationTimePerTick;
    }

    public boolean isTooltipShowCellUpgrades() {
        return this.tooltipShowCellUpgrades;
    }

    public boolean isTooltipShowCellContent() {
        return this.tooltipShowCellContent;
    }

    public int getTooltipMaxCellContentShown() {
        return this.tooltipMaxCellContentShown;
    }

    public boolean isAnnihilationPlaneSkyDustGenerationEnabled() {
        return this.annihilationPlaneSkyDustGeneration;
    }

    public boolean isMatterCanonBlockDamageEnabled() {
        return this.matterCannonBlockDamage;
    }

    public boolean isTinyTntBlockDamageEnabled() {
        return this.tinyTntBlockDamage;
    }

    public boolean isEnableEffects() {
        return this.enableEffects;
    }

    public boolean isPlacementPreviewEnabled() {
        return this.placementPreviewEnabled;
    }

    public boolean isShowDebugGuiOverlays() {
        return this.showDebugGuiOverlays;
    }

    public void setShowDebugGuiOverlays(boolean enabled) {
        if (enabled != this.showDebugGuiOverlays) {
            this.showDebugGuiOverlays = enabled;
            this.configuration.get("client", "showDebugGuiOverlays", false).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isUseLargeFonts() {
        return this.useLargeFonts;
    }

    public PowerUnit getSelectedEnergyUnit() {
        return this.selectedPowerUnit;
    }

    public void setSelectedEnergyUnit(PowerUnit selectedPowerUnit) {
        this.selectedPowerUnit = selectedPowerUnit == null ? PowerUnit.AE : selectedPowerUnit;
        this.configuration.get("client", "powerUnit", PowerUnit.AE.name()).set(this.selectedPowerUnit.name());
        this.configuration.save();
    }

    public ChannelMode getChannelMode() {
        return this.channelMode;
    }

    public void setChannelMode(ChannelMode channelMode) {
        this.channelMode = channelMode == null ? ChannelMode.DEFAULT : channelMode;
        this.configuration.get("general", "channels", ChannelMode.DEFAULT.name()).set(this.channelMode.name());
        this.configuration.save();
    }

    public void setDebugEnergyEnabled(boolean enabled) {
        if (enabled != this.debugEnergy) {
            this.debugEnergy = enabled;
            this.configuration.get("debug", "debugEnergy", false).set(enabled);
            this.configuration.save();
        }
    }

    public double getSpatialPowerExponent() {
        return this.spatialPowerExponent;
    }

    public double getSpatialPowerMultiplier() {
        return this.spatialPowerMultiplier;
    }

    public double getVibrationChamberBaseEnergyPerFuelTick() {
        return this.vibrationChamberBaseEnergyPerFuelTick;
    }

    public int getVibrationChamberMinEnergyPerGameTick() {
        return this.vibrationChamberMinEnergyPerGameTick;
    }

    public int getVibrationChamberMaxEnergyPerGameTick() {
        return this.vibrationChamberMaxEnergyPerGameTick;
    }

    public double wireless_getDrainRate(double range) {
        return this.wirelessTerminalDrainMultiplier * range;
    }

    public double wireless_getMaxRange(int boosters) {
        return this.wirelessBaseRange
            + this.wirelessBoosterRangeMultiplier * Math.pow(boosters, this.wirelessBoosterExp);
    }

    public double wireless_getPowerDrain(int boosters) {
        return this.wirelessBaseCost
            + this.wirelessCostMultiplier
            * Math.pow(boosters, 1 + boosters / this.wirelessHighWirelessCount);
    }

    public boolean isPinAutoCraftedItems() {
        return this.pinAutoCraftedItems;
    }

    public void setPinAutoCraftedItems(boolean enabled) {
        if (enabled != this.pinAutoCraftedItems) {
            this.pinAutoCraftedItems = enabled;
            this.configuration.get("client", "pinAutoCraftedItems", true).set(enabled);
            this.configuration.save();
        }
    }

    public TerminalStyle getTerminalStyle() {
        return this.terminalStyle;
    }

    public void setTerminalStyle(TerminalStyle terminalStyle) {
        TerminalStyle nextStyle = terminalStyle == null ? TerminalStyle.SMALL : terminalStyle;
        if (nextStyle != this.terminalStyle) {
            this.terminalStyle = nextStyle;
            this.configuration.get("terminals", "terminalStyle", TerminalStyle.SMALL.name()).set(nextStyle.name());
            this.configuration.save();
        }
    }

    public boolean isClearGridOnClose() {
        return this.clearGridOnClose;
    }

    public void setClearGridOnClose(boolean enabled) {
        if (enabled != this.clearGridOnClose) {
            this.clearGridOnClose = enabled;
            this.configuration.get("client", "clearGridOnClose", false).set(enabled);
            this.configuration.save();
        }
    }

    public int getTerminalMargin() {
        return this.terminalMargin;
    }

    public boolean isNotifyForFinishedCraftingJobs() {
        return this.notifyForFinishedCraftingJobs;
    }

    public void setNotifyForFinishedCraftingJobs(boolean enabled) {
        if (enabled != this.notifyForFinishedCraftingJobs) {
            this.notifyForFinishedCraftingJobs = enabled;
            this.configuration.get("client", "notifyForFinishedCraftingJobs", true).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isUseExternalSearch() {
        return this.useExternalSearch;
    }

    public void setUseExternalSearch(boolean enabled) {
        if (enabled != this.useExternalSearch) {
            this.useExternalSearch = enabled;
            this.configuration.get("search", "useExternalSearch", false).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isSearchModNameInTooltips() {
        return this.searchModNameInTooltips;
    }

    public void setSearchModNameInTooltips(boolean enabled) {
        if (enabled != this.searchModNameInTooltips) {
            this.searchModNameInTooltips = enabled;
            this.configuration.get("search", "searchModNameInTooltips", false).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isClearExternalSearchOnOpen() {
        return this.clearExternalSearchOnOpen;
    }

    public void setClearExternalSearchOnOpen(boolean enabled) {
        if (enabled != this.clearExternalSearchOnOpen) {
            this.clearExternalSearchOnOpen = enabled;
            this.configuration.get("search", "clearExternalSearchOnOpen", true).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isSyncWithExternalSearch() {
        return this.syncWithExternalSearch;
    }

    public void setSyncWithExternalSearch(boolean enabled) {
        if (enabled != this.syncWithExternalSearch) {
            this.syncWithExternalSearch = enabled;
            this.configuration.get("search", "syncWithExternalSearch", true).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isRememberLastSearch() {
        return this.rememberLastSearch;
    }

    public void setRememberLastSearch(boolean enabled) {
        if (enabled != this.rememberLastSearch) {
            this.rememberLastSearch = enabled;
            this.configuration.get("search", "rememberLastSearch", true).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isAutoFocusSearch() {
        return this.autoFocusSearch;
    }

    public void setAutoFocusSearch(boolean enabled) {
        if (enabled != this.autoFocusSearch) {
            this.autoFocusSearch = enabled;
            this.configuration.get("search", "autoFocusSearch", false).set(enabled);
            this.configuration.save();
        }
    }

    public boolean isDebugToolsEnabled() {
        return this.debugToolsEnabled;
    }

    public boolean isSpawnPressesInMeteoritesEnabled() {
        return this.spawnPressesInMeteoritesEnabled;
    }

    public boolean isSpawnFlawlessOnlyEnabled() {
        return this.spawnFlawlessOnly;
    }

    public int[] getMeteoriteDimensionWhitelist() {
        return this.meteoriteDimensionWhitelist.clone();
    }

    public int getSpatialDimensionId() {
        return this.spatialDimensionId;
    }

    public void save() {
        if (this.configuration.hasChanged()) {
            this.configuration.save();
        }
    }
}
