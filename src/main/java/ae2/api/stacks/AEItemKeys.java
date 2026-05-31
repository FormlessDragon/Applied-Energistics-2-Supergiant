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

package ae2.api.stacks;

import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

final class AEItemKeys extends AEKeyType {
    static final AEItemKeys INSTANCE = new AEItemKeys();

    private AEItemKeys() {
        super(AppEng.makeId("i"), AEItemKey.class, GuiText.Items.text());
    }

    @Override
    public AEItemKey readFromPacket(PacketBuffer input) {
        Objects.requireNonNull(input);

        return AEItemKey.fromPacket(input);
    }

    @Override
    public AEItemKey loadKeyFromTag(NBTTagCompound tag) {
        return AEItemKey.fromTag(tag);
    }

    @Override
    public boolean supportsFuzzyRangeSearch() {
        return true;
    }

    @Override
    public Stream<String> getTagNames() {
        return Arrays.stream(OreDictionary.getOreNames());
    }
}
