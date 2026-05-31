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

package ae2.spatial;

import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;

public final class SpatialStorageDimensionIds {

    public static final String DIMENSION_NAME = "ae2_spatial_storage";
    public static final String DIMENSION_SUFFIX = "_ae2_spatial_storage";
    public static final ResourceLocation WORLD_ID = AppEng.makeId("spatial_storage");

    static DimensionType dimensionType;

    private SpatialStorageDimensionIds() {
    }

    public static int getDimensionId() {
        return AEConfig.instance().getSpatialDimensionId();
    }

    public static synchronized void init() {
        int dimensionId = getDimensionId();

        if (DimensionManager.isDimensionRegistered(dimensionId)) {
            DimensionType existing = DimensionManager.getProviderType(dimensionId);
            if (!DIMENSION_NAME.equals(existing.getName())
                || !WorldProviderSpatial.class.equals(existing.createDimension().getClass())) {
                throw new IllegalStateException(String.format(
                    "Configured spatial dimension id %d is already registered for %s. Change ae2.cfg spatialDimensionId.",
                    dimensionId, existing.getName()));
            }
            dimensionType = existing;
            return;
        }

        if (dimensionType == null) {
            dimensionType = DimensionType.register(DIMENSION_NAME, DIMENSION_SUFFIX,
                dimensionId, WorldProviderSpatial.class, false);
        }

        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            DimensionManager.registerDimension(dimensionId, dimensionType);
            AELog.info("Registered spatial storage dimension %s", dimensionId);
        }
    }

    public static DimensionType getDimensionType() {
        init();
        return dimensionType;
    }
}
