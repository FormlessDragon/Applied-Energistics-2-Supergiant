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

package ae2.core.stats;

import ae2.core.AppEng;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatBasic;
import net.minecraft.stats.StatList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

public enum AeStats {

    ItemsInserted("items_inserted"),
    ItemsExtracted("items_extracted");

    private final ResourceLocation registryName;
    private final String statId;
    private StatBase stat;

    AeStats(String id) {
        this.registryName = AppEng.makeId(id);
        this.statId = "stat." + this.registryName.getNamespace() + "." + this.registryName.getPath();
    }

    public void addToPlayer(EntityPlayer player, int howMany) {
        player.addStat(getStat(), howMany);
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public void register() {
        getStat();
    }

    private synchronized StatBase getStat() {
        if (this.stat == null) {
            var existing = StatList.getOneShotStat(this.statId);
            if (existing == null) {
                existing = new StatBasic(this.statId, new TextComponentTranslation(this.statId))
                    .initIndependentStat()
                    .registerStat();
            }
            this.stat = existing;
        }

        return this.stat;
    }
}
