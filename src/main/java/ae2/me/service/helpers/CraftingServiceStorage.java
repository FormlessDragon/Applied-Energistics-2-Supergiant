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

package ae2.me.service.helpers;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.core.localization.GuiText;
import ae2.me.service.CraftingService;
import net.minecraft.util.text.ITextComponent;

/**
 * The storage exposed by the crafting service. It does two things:
 * <ul>
 * <li>Report craftable items as craftable to the network, and thus the terminals.</li>
 * <li>Intercept crafted item injections and forward them to the CPUs.</li>
 * </ul>
 */
public class CraftingServiceStorage implements IStorageProvider {
    private final CraftingService craftingService;
    private final MEStorage inventory = new MEStorage() {
        @Override
        public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
            return true;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            // Item interception logic
            return craftingService.insertIntoCpus(what, amount, mode);
        }

        @Override
        public ITextComponent getDescription() {
            return GuiText.AutoCrafting.text();
        }
    };

    public CraftingServiceStorage(CraftingService craftingService) {
        this.craftingService = craftingService;
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(inventory, Integer.MAX_VALUE);
    }
}
