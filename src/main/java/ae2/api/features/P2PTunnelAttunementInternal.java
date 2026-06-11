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

package ae2.api.features;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Internal methods that complement {@link P2PTunnelAttunement} and which are not part of the public API.
 */
public final class P2PTunnelAttunementInternal {

    private P2PTunnelAttunementInternal() {
    }

    /**
     * Gets a report which sources of attunement exist for a given tunnel type.
     */
    @SuppressWarnings("unused")
    public static AttunementInfo getAttunementInfo(ResourceLocation tunnelType) {
        var tunnelItem = P2PTunnelAttunement.validateTunnelPartItem(tunnelType);

        Set<Capability<?>> caps = new ReferenceOpenHashSet<>();

        for (var entry : P2PTunnelAttunement.apiAttunements) {
            if (entry.tunnelType() == tunnelItem) {
                caps.add(entry.capability());
            }
        }

        return new AttunementInfo(caps);
    }

    public static List<Resultant> getApiTunnels() {
        var result = new ObjectArrayList<Resultant>(P2PTunnelAttunement.apiAttunements.size());
        for (var info : P2PTunnelAttunement.apiAttunements) {
            result.add(new Resultant(info.description(), info.tunnelType(), info::hasApi));
        }
        return result;
    }

    public static Map<String, Item> getTagTunnels() {
        return Map.copyOf(P2PTunnelAttunement.tagTunnels);
    }

    public static List<Item> getManageableTunnels() {
        return List.copyOf(P2PTunnelAttunement.manageableTunnels);
    }

    public static boolean supportsMultipleInputs(Item tunnelItem) {
        return P2PTunnelAttunement.multipleInputTunnels.getOrDefault(tunnelItem, false);
    }

    public static boolean supportsMultipleInputs(ResourceLocation tunnelType) {
        Item tunnelItem = Item.REGISTRY.getObject(tunnelType);
        if (tunnelItem == null || tunnelItem == Items.AIR) {
            return false;
        }
        return supportsMultipleInputs(tunnelItem);
    }

    public record AttunementInfo(Set<Capability<?>> apis) {
    }

    public record Resultant(ITextComponent description, Item tunnelType, Predicate<ItemStack> stackPredicate) {
    }
}
