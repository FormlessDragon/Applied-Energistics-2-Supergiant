package ae2.client.patternimport;

import ae2.api.client.PatternRecipeTransferSlot;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public final class PatternTransferPlan {

    private PatternTransferPlan() {
    }

    public static List<PatternRecipeTransferSlot> packedSlots(List<PatternRecipeTransferSlot> slots) {
        List<PatternRecipeTransferSlot> packed = new ObjectArrayList<>(slots.size());
        for (PatternRecipeTransferSlot slot : slots) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }

            GenericStack stack = slot.getStack();
            if (stack != null) {
                slot.addCandidate(stack.what());
            }
            packed.add(slot);
        }
        return packed;
    }

    public static List<List<String>> serializeCandidateKeyTags(List<PatternRecipeTransferSlot> packedSlots) {
        List<List<String>> tags = new ObjectArrayList<>(packedSlots.size());
        for (PatternRecipeTransferSlot slot : packedSlots) {
            List<String> slotTags = new ObjectArrayList<>(slot.getCandidates().size());
            for (AEKey candidate : slot.getCandidates()) {
                if (candidate != null) {
                    slotTags.add(candidate.toTagGeneric().toString());
                }
            }
            tags.add(slotTags);
        }
        return tags;
    }
}
