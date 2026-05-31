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

import java.util.Objects;

final class AEFluidKeys extends AEKeyType {
    static final AEFluidKeys INSTANCE = new AEFluidKeys();

    private AEFluidKeys() {
        super(AppEng.makeId("f"), AEFluidKey.class, GuiText.Fluids.text());
    }

    @Override
    public int getAmountPerOperation() {
        // On Forge this was 125mb (so 125/1000th of a bucket)
        return AEFluidKey.AMOUNT_BUCKET * 125 / 1000;
    }

    @Override
    public int getAmountPerByte() {
        return 8 * AEFluidKey.AMOUNT_BUCKET;
    }

    @Override
    public AEFluidKey readFromPacket(PacketBuffer input) {
        Objects.requireNonNull(input);

        return AEFluidKey.fromPacket(input);
    }

    @Override
    public AEFluidKey loadKeyFromTag(NBTTagCompound tag) {
        return AEFluidKey.fromTag(tag);
    }

    @Override
    public int getAmountPerUnit() {
        return AEFluidKey.AMOUNT_BUCKET;
    }

    @Override
    public String getUnitSymbol() {
        return "B";
    }
}
