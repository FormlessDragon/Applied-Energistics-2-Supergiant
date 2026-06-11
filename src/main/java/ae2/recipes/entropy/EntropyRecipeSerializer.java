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

package ae2.recipes.entropy;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EntropyRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        EntropyMode mode = readMode(json);
        EntropyRecipe.Input input = readInput(JsonUtils.getJsonObject(json, "input"));
        EntropyRecipe.Output output = readOutput(JsonUtils.getJsonObject(json, "output"), ctx);
        AERecipeTypes.ENTROPY.register(new EntropyRecipe(mode, input, output));
    }

    private EntropyMode readMode(JsonObject json) {
        String mode = JsonUtils.getString(json, "mode", "heat");
        try {
            return EntropyMode.fromString(mode);
        } catch (IllegalArgumentException e) {
            throw new JsonSyntaxException(e.getMessage(), e);
        }
    }

    private EntropyRecipe.Input readInput(JsonObject json) {
        Optional<EntropyRecipe.BlockInput> block = json.has("block")
            ? Optional.of(readBlockInput(JsonUtils.getJsonObject(json, "block")))
            : Optional.empty();
        Optional<EntropyRecipe.FluidInput> fluid = json.has("fluid")
            ? Optional.of(readFluidInput(JsonUtils.getJsonObject(json, "fluid")))
            : Optional.empty();
        return new EntropyRecipe.Input(block, fluid);
    }

    private EntropyRecipe.Output readOutput(JsonObject json, JsonContext ctx) {
        Optional<EntropyRecipe.BlockOutput> block = json.has("block")
            ? Optional.of(readBlockOutput(JsonUtils.getJsonObject(json, "block")))
            : Optional.empty();
        Optional<EntropyRecipe.FluidOutput> fluid = json.has("fluid")
            ? Optional.of(readFluidOutput(JsonUtils.getJsonObject(json, "fluid")))
            : Optional.empty();
        List<ItemStack> drops = json.has("drops") ? JsonRecipeUtils.readItemStacks(json, "drops", ctx)
            : Collections.emptyList();
        return new EntropyRecipe.Output(block, fluid, drops);
    }

    private EntropyRecipe.BlockInput readBlockInput(JsonObject json) {
        Block block = JsonRecipeUtils.readBlock(json, "id");
        Map<String, PropertyValueMatcher> properties = readMatchers(readProperties(json));
        return new EntropyRecipe.BlockInput(block, properties);
    }

    private EntropyRecipe.FluidInput readFluidInput(JsonObject json) {
        Fluid fluid = readFluid(json);
        Map<String, PropertyValueMatcher> properties = readMatchers(readProperties(json));
        return new EntropyRecipe.FluidInput(fluid, properties);
    }

    private EntropyRecipe.BlockOutput readBlockOutput(JsonObject json) {
        Block block = JsonRecipeUtils.readBlock(json, "id");
        int metadata = JsonUtils.getInt(json, "metadata", -1);
        if (metadata < -1 || metadata > 15) {
            throw new JsonSyntaxException("metadata must be between 0 and 15, or -1 for the default block state");
        }
        boolean keepProperties = JsonUtils.getBoolean(json, "keepProperties", false);
        Map<String, String> properties = readStrings(readProperties(json));
        return new EntropyRecipe.BlockOutput(block, metadata, keepProperties, properties);
    }

    private EntropyRecipe.FluidOutput readFluidOutput(JsonObject json) {
        Fluid fluid = readFluid(json);
        boolean keepProperties = JsonUtils.getBoolean(json, "keepProperties", false);
        Map<String, String> properties = readStrings(readProperties(json));
        return new EntropyRecipe.FluidOutput(fluid, keepProperties, properties);
    }

    private JsonObject readProperties(JsonObject json) {
        return json.has("properties") ? JsonUtils.getJsonObject(json, "properties") : null;
    }

    private Map<String, PropertyValueMatcher> readMatchers(JsonObject json) {
        Map<String, PropertyValueMatcher> result = new Object2ObjectLinkedOpenHashMap<>();
        if (json == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            result.put(entry.getKey(), PropertyValueMatcher.read(entry.getValue()));
        }
        return result;
    }

    private Map<String, String> readStrings(JsonObject json) {
        Map<String, String> result = new Object2ObjectLinkedOpenHashMap<>();
        if (json == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            result.put(entry.getKey(), JsonUtils.getString(json, entry.getKey()));
        }
        return result;
    }

    private Fluid readFluid(JsonObject json) {
        String id = JsonUtils.getString(json, "id");
        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) {
            ResourceLocation resourceLocation = readResourceLocation(id);
            fluid = FluidRegistry.getFluid(resourceLocation.getPath());
        }
        if (fluid == null) {
            throw new JsonSyntaxException("Unknown fluid: " + id);
        }
        return fluid;
    }

    private ResourceLocation readResourceLocation(String id) {
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            throw new JsonSyntaxException("Invalid fluid id: " + id, e);
        }
    }
}
