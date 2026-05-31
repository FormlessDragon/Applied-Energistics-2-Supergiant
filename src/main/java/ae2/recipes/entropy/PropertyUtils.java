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

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import java.util.Map;
import java.util.Objects;

final class PropertyUtils {
    private PropertyUtils() {
    }

    static IProperty<?> getRequiredProperty(Block block, String name) {
        Objects.requireNonNull(block, "block must not be null");

        IProperty<?> property = block.getBlockState().getProperty(name);
        if (property == null) {
            throw new IllegalArgumentException("Unknown property: " + name + " on " + block.getRegistryName());
        }
        return property;
    }

    static <T extends Comparable<T>> T getRequiredPropertyValue(IProperty<T> property, String name) {
        Objects.requireNonNull(property, "property must be not null");

        for (T value : property.getAllowedValues()) {
            if (property.getName(value).equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid value '" + name + "' for property " + property.getName());
    }

    static void validatePropertyMatchers(Block block, Map<String, PropertyValueMatcher> properties) {
        for (Map.Entry<String, PropertyValueMatcher> entry : properties.entrySet()) {
            IProperty<?> property = getRequiredProperty(block, entry.getKey());
            entry.getValue().validate(property);
        }
    }

    static boolean doPropertiesMatch(Block block, IBlockState state, Map<String, PropertyValueMatcher> properties) {
        for (Map.Entry<String, PropertyValueMatcher> entry : properties.entrySet()) {
            IProperty<?> property = getRequiredProperty(block, entry.getKey());
            if (!matches(entry.getValue(), property, state)) {
                return false;
            }
        }
        return true;
    }

    static IBlockState applyProperties(IBlockState state, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            IProperty<?> property = state.getBlock().getBlockState().getProperty(entry.getKey());
            if (property != null) {
                state = applyProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    static IBlockState copyProperties(IBlockState from, IBlockState to) {
        IBlockState result = to;
        for (IProperty<?> sourceProperty : from.getPropertyKeys()) {
            IProperty<?> targetProperty = to.getBlock().getBlockState().getProperty(sourceProperty.getName());
            if (targetProperty != null) {
                String value = getPropertyValue(from, sourceProperty);
                result = applyProperty(result, targetProperty, value);
            }
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matches(PropertyValueMatcher matcher, IProperty property, IBlockState state) {
        return matcher.matches(property, state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState applyProperty(IBlockState state, IProperty property, String value) {
        for (Comparable allowedValue : (Iterable<Comparable>) property.getAllowedValues()) {
            if (property.getName(allowedValue).equals(value)) {
                return state.withProperty(property, allowedValue);
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getPropertyValue(IBlockState state, IProperty property) {
        return property.getName(state.getValue(property));
    }
}
