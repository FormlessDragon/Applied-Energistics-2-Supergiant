package ae2.integration.modules.hei;

import ae2.container.me.items.ContainerCraftingTerm;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

record CraftingRecipeTransferAnalysis(
    Outcome outcome,
    IntSet missingGuiSlots,
    IntSet craftableGuiSlots,
    int ingredientCount) {

    CraftingRecipeTransferAnalysis {
        missingGuiSlots = IntSets.unmodifiable(new IntRBTreeSet(missingGuiSlots));
        craftableGuiSlots = IntSets.unmodifiable(new IntRBTreeSet(craftableGuiSlots));
    }

    static CraftingRecipeTransferAnalysis analyze(ContainerCraftingTerm.MissingIngredientSlots missingSlots,
                                                  int ingredientCount) {
        return of(missingSlots.missingSlots(), missingSlots.craftableSlots(), ingredientCount, 1);
    }

    static CraftingRecipeTransferAnalysis of(IntSet missingGuiSlots, IntSet craftableGuiSlots,
                                             int ingredientCount) {
        return of(missingGuiSlots, craftableGuiSlots, ingredientCount, 0);
    }

    private static CraftingRecipeTransferAnalysis of(IntSet missingSlots, IntSet craftableSlots,
                                                     int ingredientCount, int guiSlotOffset) {
        int unavailableCount = missingSlots.size() + craftableSlots.size();

        Outcome outcome;
        if (unavailableCount == 0) {
            outcome = Outcome.READY;
        } else if (unavailableCount >= ingredientCount) {
            outcome = Outcome.BLOCK_ALL_MISSING;
        } else if (!missingSlots.isEmpty() && !craftableSlots.isEmpty()) {
            outcome = Outcome.PARTIAL_MIXED;
        } else if (!craftableSlots.isEmpty()) {
            outcome = Outcome.PARTIAL_CRAFTABLE;
        } else {
            outcome = Outcome.PARTIAL_UNCRAFTABLE;
        }

        return new CraftingRecipeTransferAnalysis(outcome, toGuiSlots(missingSlots, guiSlotOffset),
            toGuiSlots(craftableSlots, guiSlotOffset), ingredientCount);
    }

    private static IntSet toGuiSlots(IntSet slots, int offset) {
        IntRBTreeSet result = new IntRBTreeSet();
        for (int slot : slots) {
            result.add(slot + offset);
        }
        return result;
    }

    boolean hasImmediatelyAvailableIngredients() {
        return getUnavailableCount() < ingredientCount;
    }

    boolean hasCraftableMissingIngredients() {
        return !craftableGuiSlots.isEmpty();
    }

    boolean hasUncraftableMissingIngredients() {
        return !missingGuiSlots.isEmpty();
    }

    IntCollection getMissingGuiSlots() {
        IntList result = new IntArrayList(missingGuiSlots.size());
        result.addAll(missingGuiSlots);
        return result;
    }

    IntCollection getCraftableGuiSlots() {
        IntList result = new IntArrayList(craftableGuiSlots.size());
        result.addAll(craftableGuiSlots);
        return result;
    }

    private int getUnavailableCount() {
        return missingGuiSlots.size() + craftableGuiSlots.size();
    }

    enum Outcome {
        READY,
        BLOCK_ALL_MISSING,
        PARTIAL_UNCRAFTABLE,
        PARTIAL_CRAFTABLE,
        PARTIAL_MIXED
    }
}
