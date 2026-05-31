/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.parts;

import ae2.api.implementations.parts.ICablePart;
import ae2.api.parts.IFacadePart;
import ae2.api.parts.IPart;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class CableBusStorage {

    @Nullable
    private ICablePart center;

    @Nullable
    private IPart[] parts;

    @Nullable
    private IFacadePart[] facades;

    private static <T> boolean isEmptyArray(@Nullable T[] array) {
        if (array == null) {
            return true;
        }

        for (var value : array) {
            if (value != null) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    protected ICablePart getCenter() {
        return this.center;
    }

    protected void setCenter(@Nullable ICablePart center) {
        this.center = center;
    }

    @Nullable
    protected IPart getPart(EnumFacing side) {
        if (this.parts == null) {
            return null;
        }

        return this.parts[side.ordinal()];
    }

    protected void setPart(EnumFacing side, @Nullable IPart part) {
        if (part == null) {
            removePart(side);
            return;
        }

        if (this.parts == null) {
            this.parts = new IPart[EnumFacing.VALUES.length];
        }

        this.parts[side.ordinal()] = part;
    }

    protected void removePart(EnumFacing side) {
        if (this.parts == null) {
            return;
        }

        this.parts[side.ordinal()] = null;
        if (isEmptyArray(this.parts)) {
            this.parts = null;
        }
    }

    @Nullable
    public IFacadePart getFacade(EnumFacing side) {
        if (this.facades == null) {
            return null;
        }

        return this.facades[side.ordinal()];
    }

    public void setFacade(EnumFacing side, @Nullable IFacadePart facade) {
        if (facade == null) {
            removeFacade(side);
            return;
        }

        if (this.facades == null) {
            this.facades = new IFacadePart[EnumFacing.VALUES.length];
        }

        this.facades[side.ordinal()] = facade;
    }

    public void removeFacade(EnumFacing side) {
        if (this.facades == null) {
            return;
        }

        this.facades[side.ordinal()] = null;
        if (isEmptyArray(this.facades)) {
            this.facades = null;
        }
    }
}
