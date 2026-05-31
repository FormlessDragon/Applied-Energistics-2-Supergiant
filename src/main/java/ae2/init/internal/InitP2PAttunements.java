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

package ae2.init.internal;

import ae2.api.features.P2PTunnelAttunement;
import ae2.api.ids.AEBlockIds;
import ae2.api.ids.AEPartIds;
import ae2.core.localization.GuiText;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.oredict.OreDictionary;

public final class InitP2PAttunements {

    private InitP2PAttunements() {
    }

    public static void init() {
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.ME_P2P_TUNNEL);
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.FE_P2P_TUNNEL);
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.REDSTONE_P2P_TUNNEL);
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.FLUID_P2P_TUNNEL);
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.ITEM_P2P_TUNNEL);
        P2PTunnelAttunement.registerAttunementTag(AEPartIds.LIGHT_P2P_TUNNEL);
        registerTagAttunementBridges();

        P2PTunnelAttunement.registerAttunementApi(P2PTunnelAttunement.ENERGY_TUNNEL,
            CapabilityEnergy.ENERGY,
            GuiText.P2PAttunementEnergy.text());
        P2PTunnelAttunement.registerAttunementApi(P2PTunnelAttunement.FLUID_TUNNEL,
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            GuiText.P2PAttunementFluid.text());
    }

    private static void registerTagAttunementBridges() {
        String meTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.ME_P2P_TUNNEL);
        for (ResourceLocation itemId : AEPartIds.CABLE_COVERED.values()) {
            registerOre(meTunnelTag, itemId);
        }
        for (ResourceLocation itemId : AEPartIds.CABLE_DENSE_COVERED.values()) {
            registerOre(meTunnelTag, itemId);
        }
        for (ResourceLocation itemId : AEPartIds.CABLE_GLASS.values()) {
            registerOre(meTunnelTag, itemId);
        }
        for (ResourceLocation itemId : AEPartIds.CABLE_SMART.values()) {
            registerOre(meTunnelTag, itemId);
        }
        for (ResourceLocation itemId : AEPartIds.CABLE_DENSE_SMART.values()) {
            registerOre(meTunnelTag, itemId);
        }

        String energyTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.FE_P2P_TUNNEL);
        registerOre(energyTunnelTag, AEBlockIds.DENSE_ENERGY_CELL);
        registerOre(energyTunnelTag, AEBlockIds.ENERGY_ACCEPTOR);
        registerOre(energyTunnelTag, AEBlockIds.ENERGY_CELL);
        registerOre(energyTunnelTag, AEBlockIds.CREATIVE_ENERGY_CELL);

        String redstoneTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.REDSTONE_P2P_TUNNEL);
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "redstone"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "repeater"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "redstone_lamp"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "comparator"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "daylight_detector"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "redstone_torch"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "redstone_block"));
        registerOre(redstoneTunnelTag, new ResourceLocation("minecraft", "lever"));

        String fluidTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.FLUID_P2P_TUNNEL);
        registerOre(fluidTunnelTag, new ResourceLocation("minecraft", "bucket"));
        registerOre(fluidTunnelTag, new ResourceLocation("minecraft", "milk_bucket"));
        registerOre(fluidTunnelTag, new ResourceLocation("minecraft", "water_bucket"));
        registerOre(fluidTunnelTag, new ResourceLocation("minecraft", "lava_bucket"));

        String itemTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.ITEM_P2P_TUNNEL);
        registerOre(itemTunnelTag, AEPartIds.STORAGE_BUS);
        registerOre(itemTunnelTag, AEPartIds.EXPORT_BUS);
        registerOre(itemTunnelTag, AEPartIds.IMPORT_BUS);
        registerOre(itemTunnelTag, new ResourceLocation("minecraft", "hopper"));
        registerOre(itemTunnelTag, new ResourceLocation("minecraft", "chest"));
        registerOre(itemTunnelTag, new ResourceLocation("minecraft", "trapped_chest"));
        registerOre(itemTunnelTag, AEBlockIds.INTERFACE);
        registerOre(itemTunnelTag, AEPartIds.INTERFACE);

        String lightTunnelTag = P2PTunnelAttunement.getAttunementTag(AEPartIds.LIGHT_P2P_TUNNEL);
        registerOre(lightTunnelTag, new ResourceLocation("minecraft", "torch"));
        registerOre(lightTunnelTag, new ResourceLocation("minecraft", "glowstone"));
    }

    private static void registerOre(String oreName, ResourceLocation itemId) {
        Item item = Item.REGISTRY.getObject(itemId);
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("Missing P2P attunement bridge item: " + itemId);
        }
        OreDictionary.registerOre(oreName, item);
    }
}
