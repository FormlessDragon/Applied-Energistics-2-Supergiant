/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
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

package ae2.api.storage;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.core.stats.AeStats;
import ae2.crafting.CraftingLink;
import com.google.common.primitives.Ints;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

public final class StorageHelper {
    private StorageHelper() {
    }

    /**
     * load a crafting link from nbt data.
     *
     * @param data to be loaded data
     * @return crafting link
     */
    public static ICraftingLink loadCraftingLink(NBTTagCompound data, ICraftingRequester req) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(req);

        return new CraftingLink(data, req);
    }

    /**
     * Extracts items from a {@link MEStorage} respecting power requirements.
     *
     * @param energy  Energy source.
     * @param inv     Inventory to extract from.
     * @param request Requested item and count.
     * @param src     Action source.
     * @return extracted items or {@code null} of nothing was extracted.
     */
    public static long poweredExtraction(IEnergySource energy, MEStorage inv,
                                         AEKey request, long amount, IActionSource src) {
        return poweredExtraction(energy, inv, request, amount, src, Actionable.MODULATE);
    }

    /**
     * Extracts items from a {@link MEStorage} respecting power requirements.
     *
     * @param energy  Energy source.
     * @param inv     Inventory to extract from.
     * @param request Requested item and count.
     * @param src     Action source.
     * @param mode    Simulate or modulate
     * @return extracted items or {@code null} of nothing was extracted.
     */
    public static long poweredExtraction(IEnergySource energy, MEStorage inv,
                                         AEKey request, long amount, IActionSource src, Actionable mode) {
        Objects.requireNonNull(energy, "energy");
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(mode, "mode");

        var retrieved = inv.extract(request, amount, Actionable.SIMULATE, src);

        var energyFactor = Math.max(1.0, request.getAmountPerOperation());
        var availablePower = energy.extractAEPower(retrieved / energyFactor, Actionable.SIMULATE,
            PowerMultiplier.CONFIG);
        var itemToExtract = Math.min((long) (availablePower * energyFactor + 0.9), retrieved);

        if (itemToExtract > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(retrieved / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                var ret = inv.extract(request, itemToExtract, Actionable.MODULATE, src);

                if (ret != 0 && request instanceof AEItemKey) {
                    src.player().ifPresent(player -> AeStats.ItemsExtracted.addToPlayer(player, Ints.saturatedCast(ret)));
                }
                return ret;
            } else {
                return itemToExtract;
            }
        }

        return 0;
    }

    /**
     * Inserts items into a {@link MEStorage} respecting power requirements.
     *
     * @param energy Energy source.
     * @param inv    Inventory to insert into.
     * @param input  Items to insert.
     * @param src    Action source.
     * @return the number of items inserted.
     */
    public static long poweredInsert(IEnergySource energy, MEStorage inv,
                                     AEKey input, long amount, IActionSource src) {
        return poweredInsert(energy, inv, input, amount, src, Actionable.MODULATE);
    }

    /**
     * Inserts items into a {@link MEStorage} respecting power requirements.
     *
     * @param energy Energy source.
     * @param inv    Inventory to insert into.
     * @param input  Items to insert.
     * @param src    Action source.
     * @param mode   Simulate or modulate
     * @return the number of items inserted.
     */
    public static long poweredInsert(IEnergySource energy, MEStorage inv, AEKey input, long amount,
                                     IActionSource src, Actionable mode) {
        Objects.requireNonNull(energy);
        Objects.requireNonNull(inv);
        Objects.requireNonNull(input);
        Objects.requireNonNull(src);
        Objects.requireNonNull(mode);

        amount = inv.insert(input, amount, Actionable.SIMULATE, src);
        if (amount <= 0) {
            return 0;
        }

        final double energyFactor = Math.max(1.0, input.getAmountPerOperation());
        final double availablePower = energy.extractAEPower(amount / energyFactor, Actionable.SIMULATE,
            PowerMultiplier.CONFIG);
        amount = Math.min((long) (availablePower * energyFactor + 0.9), amount);

        if (amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            energy.extractAEPower(amount / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
            var inserted = inv.insert(input, amount, Actionable.MODULATE, src);

            if (input instanceof AEItemKey) {
                src.player().ifPresent(player -> AeStats.ItemsInserted.addToPlayer(player, Ints.saturatedCast(inserted)));
            }

            return inserted;
        } else {
            return amount;
        }

    }
}
