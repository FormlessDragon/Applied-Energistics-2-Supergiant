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

package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.inv.ICraftingInventory;
import ae2.crafting.inv.ListCraftingInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Helper functions used by the CPU.
 */
public class CraftingCpuHelper {
    private CraftingCpuHelper() {
    }

    /**
     * Extracts everything currently available for the plan and returns what is still missing at submission time.
     */
    public static KeyCounter extractInitialItems(ICraftingPlan plan, IGrid grid,
                                                 ListCraftingInventory cpuInventory, IActionSource src) {
        var storage = grid.getStorageService().getInventory();
        var remainingMissing = new KeyCounter();

        for (var entry : plan.usedItems()) {
            var what = entry.getKey();
            var toExtract = entry.getLongValue();
            var extracted = storage.extract(what, toExtract, Actionable.MODULATE, src);
            cpuInventory.insert(what, extracted, Actionable.MODULATE);

            if (extracted < toExtract) {
                remainingMissing.add(what, toExtract - extracted);
            }
        }

        for (var entry : plan.missingItems()) {
            var what = entry.getKey();
            var toExtract = entry.getLongValue();
            var extracted = storage.extract(what, toExtract, Actionable.MODULATE, src);
            cpuInventory.insert(what, extracted, Actionable.MODULATE);

            if (extracted < toExtract) {
                remainingMissing.add(what, toExtract - extracted);
            }
        }

        return remainingMissing;
    }

    public static NBTTagCompound generateLinkData(UUID craftId, boolean standalone, boolean req) {
        final NBTTagCompound tag = new NBTTagCompound();

        tag.setUniqueId("craftId", craftId);
        tag.setBoolean("canceled", false);
        tag.setBoolean("done", false);
        tag.setBoolean("standalone", standalone);
        tag.setBoolean("req", req);

        return tag;
    }

    public static double calculatePatternPower(KeyCounter[] craftingContainer) {
        // Calculate power.
        double sum = 0;

        for (var itemHolder : craftingContainer) {
            for (var anInput : itemHolder) {
                sum += ((double) anInput.getLongValue()) / ((double) anInput.getKey().getType().getAmountPerOperation());
            }
        }

        return sum;
    }

    @Nullable
    public static KeyCounter @org.jspecify.annotations.Nullable [] extractPatternInputs(
        IPatternDetails details,
        ICraftingInventory sourceInv,
        World level,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems) {

        // Extract inputs into the container.
        var inputs = details.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        boolean found = true;

        for (int x = 0; x < inputs.length; x++) {
            var list = inputHolder[x] = new KeyCounter();
            long remainingMultiplier = inputs[x].getMultiplier();
            for (var template : getValidItemTemplates(sourceInv, inputs[x], level)) {
                long extracted = extractTemplates(sourceInv, template, remainingMultiplier);
                if (extracted <= 0) {
                    continue;
                }

                list.add(template.key(), extracted * template.amount());

                // Container items!
                var containerItem = inputs[x].getRemainingKey(template.key());
                if (containerItem != null) {
                    expectedContainerItems.add(containerItem, extracted);
                }

                remainingMultiplier -= extracted;
                if (remainingMultiplier == 0)
                    break;
            }

            if (remainingMultiplier > 0) {
                found = false;
                break;
            }
        }

        // Failed to extract everything, put it back!
        if (!found) {
            // put stuff back.
            reinjectPatternInputs(sourceInv, inputHolder);
            return null;
        }

        // Add pattern outputs.
        for (var output : details.getOutputs()) {
            expectedOutputs.add(output.what(), output.amount());
        }

        return inputHolder;
    }

    public static void reinjectPatternInputs(ICraftingInventory sourceInv,
                                             KeyCounter[] inputHolder) {
        for (var list : inputHolder) {
            // List may be null if we failed to extract some of the pattern's inputs.
            if (list != null) {
                for (var entry : list) {
                    sourceInv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                }
            }
        }
    }

    /**
     * Get all potential input templates that count as "1" ingredient according to the given inputs for a pattern slot,
     * and which are available.
     */
    public static Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv,
                                                                IPatternDetails.IInput input, World level) {
        return () -> new Iterator<>() {
            private final GenericStack[] possibleInputs = input.possibleInputs();
            private final HashSet<FuzzyInputKey> checkedInputs = new HashSet<>();
            private int possibleInputIndex;
            private Iterator<AEKey> fuzzyIterator;
            private long fuzzyAmount;
            private InputTemplate next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }

                next = findNext();
                return next != null;
            }

            @Override
            public InputTemplate next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                var result = next;
                next = null;
                return result;
            }

            private InputTemplate findNext() {
                while (true) {
                    while (fuzzyIterator != null && fuzzyIterator.hasNext()) {
                        var fuzz = fuzzyIterator.next();
                        if (input.isValid(fuzz, level)) {
                            return new InputTemplate(fuzz, fuzzyAmount);
                        }
                    }

                    fuzzyIterator = null;
                    if (!advancePossibleInput()) {
                        return null;
                    }
                }
            }

            private boolean advancePossibleInput() {
                while (possibleInputIndex < possibleInputs.length) {
                    var stack = possibleInputs[possibleInputIndex++];
                    if (!checkedInputs.add(new FuzzyInputKey(stack.what(), stack.amount()))) {
                        continue;
                    }

                    fuzzyAmount = stack.amount();
                    fuzzyIterator = inv.findFuzzyTemplates(stack.what()).iterator();
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Extract a whole number of templates, and return how many were extracted.
     */
    public static long extractTemplates(ICraftingInventory inv, InputTemplate template, long multiplier) {
        long maxTotal = template.amount() * multiplier;
        // Extract as much as possible.
        var extracted = inv.extract(template.key(), maxTotal, Actionable.SIMULATE);
        if (extracted == 0)
            return 0;
        // Adjust to have a whole number of templates.
        multiplier = extracted / template.amount();
        maxTotal = template.amount() * multiplier;
        if (maxTotal == 0)
            return 0;
        extracted = inv.extract(template.key(), maxTotal, Actionable.MODULATE);
        if (extracted == 0 || extracted != maxTotal) {
            throw new IllegalStateException("Failed to correctly extract whole number. Invalid simulation!");
        }
        return multiplier;
    }

    private record FuzzyInputKey(Object primaryKey, long amount) {
        private FuzzyInputKey(AEKey key, long amount) {
            this(key.getPrimaryKey(), amount);
        }
    }
}
