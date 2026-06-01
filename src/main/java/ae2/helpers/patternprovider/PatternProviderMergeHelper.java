package ae2.helpers.patternprovider;

import java.util.List;
import java.util.function.Predicate;

import ae2.api.config.Actionable;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

final class PatternProviderMergeHelper {
    private PatternProviderMergeHelper() {
    }

    @Nullable
    static TargetMatch findSinglePushTarget(List<ExternalTarget> targets, KeyCounter[] inputHolder,
                                            PatternProviderInsertionMode insertionMode,
                                            Predicate<ExternalTarget> isUnavailable) {
        for (int i = 0; i < targets.size(); i++) {
            ExternalTarget target = targets.get(i);
            if (!isUnavailable.test(target) && acceptsAnyForEveryInput(target.target(), inputHolder, insertionMode)) {
                return new TargetMatch(target, i, 1);
            }
        }
        return null;
    }

    @Nullable
    static TargetMatch findMergedSinglePushTarget(List<ExternalTarget> targets, KeyCounter[] inputHolder,
                                                  int maxMultiplier,
                                                  PatternProviderInsertionMode insertionMode,
                                                  Predicate<ExternalTarget> isUnavailable) {
        for (int i = 0; i < targets.size(); i++) {
            ExternalTarget target = targets.get(i);
            if (isUnavailable.test(target)) {
                continue;
            }

            int multiplier = findMaxExternalMultiplier(target.target(), inputHolder, maxMultiplier, insertionMode);
            if (multiplier > 0) {
                return new TargetMatch(target, i, multiplier);
            }
        }
        return null;
    }

    static int findMaxExternalMultiplier(PatternProviderTarget target, KeyCounter[] inputHolder, int maxMultiplier,
                                         PatternProviderInsertionMode insertionMode) {
        if (maxMultiplier <= 0) {
            return 0;
        }
        if (!acceptsAllFully(target, inputHolder, 1, insertionMode)) {
            return 0;
        }

        int low = 1;
        int high = 1;
        while (high < maxMultiplier) {
            int next = Math.min(maxMultiplier, high * 2);
            if (!acceptsAllFully(target, inputHolder, next, insertionMode)) {
                break;
            }
            low = next;
            high = next;
        }
        if (high == maxMultiplier) {
            return high;
        }

        int upper = Math.min(maxMultiplier, high * 2);
        while (low + 1 < upper) {
            int mid = low + (upper - low) / 2;
            if (acceptsAllFully(target, inputHolder, mid, insertionMode)) {
                low = mid;
            } else {
                upper = mid;
            }
        }
        return low;
    }

    static boolean acceptsAllFully(PatternProviderTarget target, KeyCounter[] inputHolder, int multiplier,
                                   PatternProviderInsertionMode insertionMode) {
        for (KeyCounter inputList : inputHolder) {
            for (Object2LongMap.Entry<AEKey> input : inputList) {
                long amount = input.getLongValue() * multiplier;
                long inserted = target.insert(input.getKey(), amount, Actionable.SIMULATE, insertionMode);
                if (inserted < amount) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean acceptsAnyForEveryInput(PatternProviderTarget target, KeyCounter[] inputHolder,
                                           PatternProviderInsertionMode insertionMode) {
        for (KeyCounter inputList : inputHolder) {
            for (Object2LongMap.Entry<AEKey> input : inputList) {
                if (target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE, insertionMode) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    record ExternalTarget(EnumFacing direction, PatternProviderTarget target) {
    }

    record TargetMatch(ExternalTarget target, int matchedTargetIndex, int multiplier) {
    }
}
