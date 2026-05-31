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

package ae2.core.localization;

/**
 * Texts used for supported in-game tooltip integrations.
 */
public enum InGameTooltip implements LocalizationEnum {

    HoldShift,
    NoNetwork,
    DeviceOffline,
    NetworkBooting,
    DeviceMissingChannel,
    DeviceOnline,
    EnchantedWith,
    Suppressed,
    Charged,
    Contains,
    Crafting,
    Stored,
    Showing,
    Locked,
    Unlocked,
    Channels,
    ChannelsOf,
    P2PFrequency,
    P2PMECarriedChannels,
    P2PInputManyOutputs,
    P2PInputOneOutput,
    P2POutput,
    P2PUnlinked,
    PackagedDevice,
    PackagedDeviceInvalid,
    ErrorNestedP2PTunnel,
    ErrorTooManyChannels,
    CraftingLockedByRedstoneSignal,
    CraftingLockedByLackOfRedstoneSignal,
    CraftingLockedUntilPulse,
    CraftingLockedUntilResult,
    ErrorControllerConflict,
    supported_by;

    private final String translationKey;

    InGameTooltip() {
        this.translationKey = "ae2.tooltip." + name();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
