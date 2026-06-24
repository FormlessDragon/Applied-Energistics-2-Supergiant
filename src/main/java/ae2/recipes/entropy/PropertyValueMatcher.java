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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.JsonUtils;

import java.util.List;

public abstract class PropertyValueMatcher {
    public static PropertyValueMatcher read(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return new SingleValue(json.getAsString());
        }

        if (json.isJsonArray()) {
            List<String> values = new ObjectArrayList<>();
            int index = 0;
            for (JsonElement element : json.getAsJsonArray()) {
                values.add(JsonUtils.getString(element, "property Value " + index));
                index++;
            }
            return new MultiValue(values);
        }

        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("min") && object.has("max")) {
                return new Range(JsonUtils.getString(object, "min"), JsonUtils.getString(object, "max"));
            }
        }

        throw new JsonSyntaxException("Property values need to be strings, list of strings, or objects with min/max properties");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasValue(IProperty<? extends Comparable<?>> property, String value) {
        for (Comparable allowedValue : property.getAllowedValues()) {
            if (((IProperty) property).getName(allowedValue).equals(value)) {
                return true;
            }
        }
        return false;
    }

    public abstract void validate(IProperty<? extends Comparable<?>> property);

    public abstract <T extends Comparable<T>> boolean matches(IProperty<T> property, IBlockState state);

    private static final class SingleValue extends PropertyValueMatcher {
        private final String value;

        private SingleValue(String value) {
            this.value = value;
        }

        @Override
        public void validate(IProperty<? extends Comparable<?>> property) {
            if (!hasValue(property, this.value)) {
                throw new IllegalStateException("Property " + property.getName() + " does not have Value '" + this.value + "'");
            }
        }

        @Override
        public <T extends Comparable<T>> boolean matches(IProperty<T> property, IBlockState state) {
            return this.value.equals(property.getName(state.getValue(property)));
        }
    }

    private static final class MultiValue extends PropertyValueMatcher {
        private final List<String> values;

        private MultiValue(List<String> values) {
            this.values = values;
        }

        @Override
        public void validate(IProperty<? extends Comparable<?>> property) {
            for (String value : this.values) {
                if (!hasValue(property, value)) {
                    throw new IllegalStateException("Property " + property.getName() + " does not have Value '" + value + "'");
                }
            }
        }

        @Override
        public <T extends Comparable<T>> boolean matches(IProperty<T> property, IBlockState state) {
            String currentValue = property.getName(state.getValue(property));
            for (String value : this.values) {
                if (value.equals(currentValue)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Range extends PropertyValueMatcher {
        private final String min;
        private final String max;

        private Range(String min, String max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public void validate(IProperty<? extends Comparable<?>> property) {
            if (!hasValue(property, this.min)) {
                throw new IllegalStateException("Property " + property.getName() + " does not have Value '" + this.min + "'");
            }
            if (!hasValue(property, this.max)) {
                throw new IllegalStateException("Property " + property.getName() + " does not have Value '" + this.max + "'");
            }
        }

        @Override
        public <T extends Comparable<T>> boolean matches(IProperty<T> property, IBlockState state) {
            T minValue = PropertyUtils.getRequiredPropertyValue(property, this.min);
            T maxValue = PropertyUtils.getRequiredPropertyValue(property, this.max);
            T value = state.getValue(property);
            return value.compareTo(minValue) >= 0 && value.compareTo(maxValue) <= 0;
        }
    }
}
