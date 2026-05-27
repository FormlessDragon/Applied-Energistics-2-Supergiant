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

package appeng.init.client;

import appeng.block.crafting.CraftingUnitType;
import appeng.block.paint.PaintModel;
import appeng.block.qnb.QnbFormedModel;
import appeng.client.render.FacadeItemModel;
import appeng.client.render.cablebus.CableBusModel;
import appeng.client.render.cablebus.P2PTunnelFrequencyModel;
import appeng.client.render.crafting.CraftingCubeModel;
import appeng.client.render.model.BuiltInModelLoader;
import appeng.client.render.model.ColorApplicatorModel;
import appeng.client.render.model.DriveModel;
import appeng.client.render.model.EncodedPatternModel;
import appeng.client.render.model.GlassModel;
import appeng.client.render.model.MemoryCardModel;
import appeng.client.render.model.MeteoriteCompassModel;
import appeng.client.render.model.WrappedGenericStackModel;
import appeng.client.render.tesr.spatial.SpatialPylonModel;
import appeng.core.AppEng;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;
import java.util.function.Supplier;

@SideOnly(Side.CLIENT)
public final class InitBuiltInModels {
    private static final Map<String, IModel> BUILT_IN_MODELS = new Object2ObjectLinkedOpenHashMap<>();
    private static boolean initialized;

    private InitBuiltInModels() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        addBuiltInModel(CableBusModel::new, "block/cable_bus", "builtin/cable_bus",
            "block/builtin/cable_bus", "models/block/builtin/cable_bus");
        addBuiltInModel("block/crafting/unit_formed", () -> new CraftingCubeModel(CraftingUnitType.UNIT));
        addBuiltInModel("block/crafting/accelerator_formed", () -> new CraftingCubeModel(CraftingUnitType.ACCELERATOR));
        addBuiltInModel("block/crafting/storage_1k_formed", () -> new CraftingCubeModel(CraftingUnitType.STORAGE_1K));
        addBuiltInModel("block/crafting/storage_4k_formed", () -> new CraftingCubeModel(CraftingUnitType.STORAGE_4K));
        addBuiltInModel("block/crafting/storage_16k_formed", () -> new CraftingCubeModel(CraftingUnitType.STORAGE_16K));
        addBuiltInModel("block/crafting/storage_64k_formed", () -> new CraftingCubeModel(CraftingUnitType.STORAGE_64K));
        addBuiltInModel("block/crafting/storage_256k_formed", () -> new CraftingCubeModel(CraftingUnitType.STORAGE_256K));
        addBuiltInModel("block/crafting/monitor_formed", () -> new CraftingCubeModel(CraftingUnitType.MONITOR));
        addBuiltInModel(SpatialPylonModel::new, "block/spatial_pylon", "blocks/spatial_pylon/builtin",
            "models/blocks/spatial_pylon/builtin");
        addBuiltInModel("block/qnb/qnb_formed", QnbFormedModel::new);
        addBuiltInModel(DriveModel::new, "block/drive", "builtin/drive", "block/builtin/drive",
            "models/block/builtin/drive");
        addBuiltInModel(GlassModel::new, "block/quartz_glass", "builtin/quartz_glass",
            "block/builtin/quartz_glass", "models/block/builtin/quartz_glass");
        addBuiltInModel("block/paint", PaintModel::new);
        addBuiltInModel("item/color_applicator", ColorApplicatorModel::new);
        addBuiltInModel("item/crafting_pattern", () -> new EncodedPatternModel("item/crafting_pattern_base"));
        addBuiltInModel("item/processing_pattern", () -> new EncodedPatternModel("item/processing_pattern_base"));
        addBuiltInModel("item/meteorite_compass", MeteoriteCompassModel::new);
        addBuiltInModel("item/facade", FacadeItemModel::new);
        addBuiltInModel("item/memory_card", MemoryCardModel::new);
        addBuiltInModel("item/wrapped_generic_stack", WrappedGenericStackModel::new);
        addBuiltInModel("part/p2p/p2p_tunnel_frequency", P2PTunnelFrequencyModel::new);
        addPlaneModel("part/annihilation_plane", "part/annihilation_plane");
        addPlaneModel("part/annihilation_plane_on", "part/annihilation_plane_on");
        addPlaneModel("part/identity_annihilation_plane", "part/identity_annihilation_plane");
        addPlaneModel("part/identity_annihilation_plane_on", "part/identity_annihilation_plane_on");
        addPlaneModel("part/formation_plane", "part/formation_plane");
        addPlaneModel("part/formation_plane_on", "part/formation_plane_on");
        ModelLoaderRegistry.registerLoader(new BuiltInModelLoader(BUILT_IN_MODELS));
    }

    private static <T extends IModel> void addBuiltInModel(String id, Supplier<T> modelFactory) {
        addBuiltInModel(modelFactory, id);
    }

    private static <T extends IModel> void addBuiltInModel(Supplier<T> modelFactory, String... ids) {
        T model = modelFactory.get();
        for (String id : ids) {
            addBuiltInModelAlias(id, model);
        }
    }

    private static void addBuiltInModelAlias(String id, IModel model) {
        if (BUILT_IN_MODELS.put(id, model) != null) {
            throw new IllegalStateException("Duplicate built-in model ID: " + AppEng.makeId(id));
        }
    }

    private static void addPlaneModel(String planeName, String frontTexture) {
        var frontTextureId = AppEng.makeId(frontTexture);
        var sidesTextureId = AppEng.makeId("part/plane_sides");
        var backTextureId = AppEng.makeId("part/transition_plane_back");
        addBuiltInModel(planeName, () -> new appeng.parts.automation.PlaneModel(frontTextureId, sidesTextureId,
            backTextureId));
    }
}
