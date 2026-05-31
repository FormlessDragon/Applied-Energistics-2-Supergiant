/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.features;

import ae2.api.ids.AEPartIds;
import ae2.api.parts.IPartItem;
import ae2.parts.p2p.P2PTunnelPart;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Registry for how p2p Tunnels are attuned
 */
public final class P2PTunnelAttunement {
    /**
     * The default tunnel part for ME tunnels. Use this to register additional attunement options.
     */
    @SuppressWarnings("unused")
    public static final ResourceLocation ME_TUNNEL = AEPartIds.ME_P2P_TUNNEL;
    /**
     * The default tunnel part for energy (i.e. Forge Energy) tunnels. Use this to register additional attunement
     * options.
     */
    public static final ResourceLocation ENERGY_TUNNEL = AEPartIds.FE_P2P_TUNNEL;
    /**
     * The default tunnel part for redstone tunnels. Use this to register additional attunement options.
     */
    @SuppressWarnings("unused")
    public static final ResourceLocation REDSTONE_TUNNEL = AEPartIds.REDSTONE_P2P_TUNNEL;
    /**
     * The default tunnel part for fluid tunnels. Use this to register additional attunement options.
     */
    public static final ResourceLocation FLUID_TUNNEL = AEPartIds.FLUID_P2P_TUNNEL;
    /**
     * The default tunnel part for item tunnels. Use this to register additional attunement options.
     */
    @SuppressWarnings("unused")
    public static final ResourceLocation ITEM_TUNNEL = AEPartIds.ITEM_P2P_TUNNEL;
    /**
     * The default tunnel part for light tunnels. Use this to register additional attunement options.
     */
    @SuppressWarnings("unused")
    public static final ResourceLocation LIGHT_TUNNEL = AEPartIds.LIGHT_P2P_TUNNEL;
    private static final int INITIAL_CAPACITY = 40;
    static final Map<String, Item> tagTunnels = new Object2ObjectOpenHashMap<>(INITIAL_CAPACITY);
    static final List<ApiAttunement> apiAttunements = new ObjectArrayList<>(INITIAL_CAPACITY);

    private P2PTunnelAttunement() {
    }

    public static String getAttunementTag(ResourceLocation tunnel) {
        validateTunnelPartItem(tunnel);
        return tunnel.getNamespace() + ":p2p_attunements/" + tunnel.getPath();
    }

    /**
     * Attunement based on the standard item tag: {@code <tunnel item namespace>:p2p_attunements/<tunnel item path>}
     */
    public synchronized static void registerAttunementTag(ResourceLocation tunnel) {
        tagTunnels.put(getAttunementTag(tunnel), validateTunnelPartItem(tunnel));
    }

    /**
     * Attunement based on the ability of getting a capability from the item.
     *
     * @param tunnelPart  The P2P-tunnel part item.
     * @param description Description for display in item-list and recipe-view integrations.
     */
    public synchronized static void registerAttunementApi(ResourceLocation tunnelPart, Capability<?> cap,
                                                          ITextComponent description) {
        Objects.requireNonNull(cap, "cap");
        apiAttunements.add(new ApiAttunement(cap, validateTunnelPartItem(tunnelPart), description));
    }

    /**
     * @param trigger attunement trigger
     * @return The part item for a P2P-Tunnel that should handle the given attunement, or an empty item stack.
     */
    public synchronized static ItemStack getTunnelPartByTriggerItem(ItemStack trigger) {
        if (trigger.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Tags first
        for (var oreId : OreDictionary.getOreIDs(trigger)) {
            var tagTunnelItem = tagTunnels.get(OreDictionary.getOreName(oreId));
            if (tagTunnelItem != null) {
                return new ItemStack(tagTunnelItem);
            }
        }

        // Check provided APIs
        for (var apiAttunement : apiAttunements) {
            if (apiAttunement.hasApi(trigger)) {
                return new ItemStack(apiAttunement.tunnelType());
            }
        }

        return ItemStack.EMPTY;
    }

    static Item validateTunnelPartItem(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        var item = Item.REGISTRY.getObject(itemId);
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("Tunnel item must be registered first: " + itemId);
        }
        if (!(item instanceof IPartItem<?> partItem)) {
            throw new IllegalArgumentException("Given tunnel part item is not a part");
        }

        if (!P2PTunnelPart.class.isAssignableFrom(partItem.getPartClass())) {
            throw new IllegalArgumentException("Given tunnel part item results in a part that is not a P2P tunnel: "
                + partItem);
        }

        return item;
    }

    record ApiAttunement(Capability<?> capability, Item tunnelType, ITextComponent description) {

        public boolean hasApi(ItemStack stack) {
            return stack.hasCapability(capability, null) && stack.getCapability(capability, null) != null;
        }
    }
}
