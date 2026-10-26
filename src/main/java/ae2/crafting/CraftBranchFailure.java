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
    private final AEKey what;
    private final long howMany;
    @Nullable
    private final PlayerMessages messageKey;
    @Nullable
    private final NoNetOutputCycle cycle;

    public CraftBranchFailure(AEKey what, long howMany) {
        super("Failed: " + what + " x " + howMany);
        this.what = what;
        this.howMany = howMany;
        this.messageKey = null;
        this.cycle = null;
    }

    public CraftBranchFailure(AEKey what, long howMany, PlayerMessages messageKey) {
        this(what, howMany, messageKey, null);
    }

    public CraftBranchFailure(AEKey what, long howMany, PlayerMessages messageKey, @Nullable NoNetOutputCycle cycle) {
        super("Failed: " + what + " x " + howMany + " (" + messageKey.getTranslationKey() + ")");
        this.what = what;
        this.howMany = howMany;
        this.messageKey = messageKey;
        this.cycle = cycle;
    }

    @Nullable
    public NoNetOutputCycle getCycle() {
        return this.cycle;
    }

    public AEKey getWhat() {
        return this.what;
    }

    public PlayerMessages getLocalizedMessageKey() {
        return this.messageKey != null ? this.messageKey : PlayerMessages.CraftingMissingIngredient;
    }

    public long getMissingAmount() {
        return this.howMany;
    }

    public boolean hasExplicitMessageKey() {
        return this.messageKey != null;
    }

}
