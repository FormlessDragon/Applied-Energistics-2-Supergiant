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

package ae2.crafting;

import ae2.api.stacks.AEKey;
import ae2.core.localization.PlayerMessages;

import org.jetbrains.annotations.Nullable;

public class CraftBranchFailure extends Exception {
    @Nullable
    private final PlayerMessages messageKey;

    public CraftBranchFailure(AEKey what, long howMany) {
        super("Failed: " + what + " x " + howMany);
        this.messageKey = null;
    }

    public CraftBranchFailure(AEKey what, long howMany, PlayerMessages messageKey) {
        super("Failed: " + what + " x " + howMany + " (" + messageKey.getTranslationKey() + ")");
        this.messageKey = messageKey;
    }

    public PlayerMessages getLocalizedMessageKey() {
        return this.messageKey != null ? this.messageKey : PlayerMessages.CraftingNoPlan;
    }

    public boolean hasExplicitMessageKey() {
        return this.messageKey != null;
    }

}
