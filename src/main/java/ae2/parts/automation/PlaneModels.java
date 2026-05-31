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

package ae2.parts.automation;

import ae2.api.parts.IPartModel;
import ae2.core.AppEng;
import ae2.parts.PartModel;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * Contains a mapping from a Plane's connections to the models to use for that state.
 */
public class PlaneModels {

    public static final ResourceLocation MODEL_CHASSIS_OFF = AppEng.makeId(
        "part/transition_plane_off");
    public static final ResourceLocation MODEL_CHASSIS_ON = AppEng.makeId(
        "part/transition_plane_on");
    public static final ResourceLocation MODEL_CHASSIS_HAS_CHANNEL = AppEng.makeId(
        "part/transition_plane_has_channel");

    private final IPartModel modelOff;
    private final IPartModel modelOn;
    private final IPartModel modelHasChannel;

    public PlaneModels(String prefixOff, String prefixOn) {
        ResourceLocation planeOff = AppEng.makeId(prefixOff);
        ResourceLocation planeOn = AppEng.makeId(prefixOn);

        this.modelOff = new PartModel(MODEL_CHASSIS_OFF, planeOff);
        this.modelOn = new PartModel(MODEL_CHASSIS_ON, planeOff);
        this.modelHasChannel = new PartModel(MODEL_CHASSIS_HAS_CHANNEL, planeOn);
    }

    public IPartModel getModel(boolean hasPower, boolean hasChannel) {
        if (hasPower && hasChannel) {
            return this.modelHasChannel;
        } else if (hasPower) {
            return this.modelOn;
        } else {
            return this.modelOff;
        }
    }

    public List<IPartModel> getModels() {
        return ImmutableList.of(this.modelOff, this.modelOn, this.modelHasChannel);
    }

}
