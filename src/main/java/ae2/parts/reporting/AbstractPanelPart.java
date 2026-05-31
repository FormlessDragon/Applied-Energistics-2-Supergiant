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

package ae2.parts.reporting;

import ae2.api.parts.IPartItem;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import net.minecraft.util.ResourceLocation;

public abstract class AbstractPanelPart extends AbstractReportingPart {

    @PartModels
    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/monitor_base");

    public AbstractPanelPart(IPartItem<?> partItem) {
        super(partItem, false);
    }

    @Override
    public boolean isLightSource() {
        return true;
    }

    protected abstract int getBrightnessColor();
}
