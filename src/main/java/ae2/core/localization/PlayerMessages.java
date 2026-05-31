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

package ae2.core.localization;

public enum PlayerMessages implements LocalizationEnum {

    NetworkToolNotFound,
    InvalidMachine,
    InvalidMachinePartiallyRestored,
    LoadedSettings,
    MaxUpgradesInstalled,
    MaxUpgradesOfTypeInstalled,
    UnsupportedUpgrade,
    MissingBlankPatterns,
    MissingUpgrades,
    ResetSettings,
    SavedSettings,
    SettingCleared,
    isNowLocked,
    isNowUnlocked,
    FacadePropertySelected,
    FacadePropertyWrapped,
    DeviceNotLinked,
    LinkedNetworkNotFound,
    OnlyEmptyCellsCanBeDisassembled,
    AmmoDepleted,
    ChestCannotReadStorageCell,
    CraftingCpuBusy,
    CompassTestSection,
    NoSpatialIOLevel,
    NoSpatialIOPlots,
    OutOfRange,
    Unknown,
    PlayerConnected,
    PlayerDisconnected,
    UnknownAE2Player,
    ChannelModeCurrent,
    ChannelModeSet,
    DebugEnergyCurrent,
    DebugEnergySet,
    GuiOverlaysEnabled,
    GuiOverlaysDisabled,
    ChunkLoggerEnabled,
    ChunkLoggerDisabled,
    TickMonitoringEnabled,
    TickMonitoringDisabled,
    GridsExporting,
    GridsExported,
    GridsExportedZip,
    GridExportIncomplete,
    GridExportWriteFailed,
    GridExportFinishFailed,
    SpatialPlotSummary,
    SpatialPlotId,
    SpatialPlotOwner,
    SpatialPlotSize,
    SpatialPlotOrigin,
    SpatialPlotRegion,
    SpatialPlotLastSource,
    SpatialPlotLastMin,
    SpatialPlotLastMax,
    SpatialPlotLastTime,
    SpatialStorageLevelUnavailable,
    CraftingJobError,
    CraftingNoPlan,
    CraftingNoNetOutput,
    CraftingTreeScreenshotFailed,
    CraftingTreeScreenshotNoData,
    CraftingTreeScreenshotSaved,
    GridDataSaved,
    WirelessRestockEnabled,
    WirelessRestockDisabled,
    WirelessMagnetOff,
    WirelessMagnetInventory,
    WirelessMagnetME,
    WirelessMagnetMENoMagnet,
    DebugConnectedSides,
    DebugCardGrids,
    DebugCardTotalNodes,
    DebugCardMeanNodes,
    DebugCardMaxNodes,
    DebugCardTicking,
    DebugCardCurrentTick,
    DebugCardMainNodeOfGridConnectedTile,
    DebugCardGridDetails,
    DebugCardGridPowered,
    DebugCardGridBooted,
    DebugCardNodesInGrid,
    DebugCardGridPivotNode,
    DebugCardNodeDetails,
    DebugCardThisNode,
    DebugCardThisNodeActive,
    DebugCardNodeExposedOnSide,
    DebugCardCableDistance,
    DebugCardFrequency,
    DebugCardNoNodeAvailable,
    DebugCardNotNetworkedBlock,
    DebugCardCableBusDetails,
    DebugCardInWorld,
    DebugCardHasRedstone,
    DebugCardNodeChannels,
    DebugCardChannels,
    DebugCardEnergyStorageDetails,
    DebugCardEnergy,
    DebugCardGridEnergy,
    DebugCardDelayedInitDetails,
    DebugCardQueuedForReady,
    DebugCardReadyInvoked,
    MeteoriteUnsuitableLocation,
    MeteoriteSpawned,
    CubeGeneratorSpawningIn,
    CubeGeneratorSize,
    CommandRequiresServerWorld,
    ConfigModifierSuccess;

    private final String translationKey;

    PlayerMessages() {
        this.translationKey = "chat.ae2." + name();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
