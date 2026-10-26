package ae2.crafting;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validates a compressed exact-input schedule by its net changes and maximum initial stock requirement. */
final class CraftingOrderReplay {
    static @Nullable KeyCounter requiredStock(ICraftingPlan plan, List<CraftingDispatchOrder.Batch> order,
                                              CraftingCalculation calculation) throws InterruptedException {
        var flow = new Flow();
        var counts = new Object2LongOpenHashMap<IPatternDetails>();
        for (var batch : order) {
            if (!batch.ordered()) return null;
            var pass = new Flow();
            for (var step : batch.steps()) {
                calculation.handlePausing();
                var pattern = step.pattern();
                if (pattern instanceof IAssemblerPattern || PseudoPatternDetails.isPseudo(pattern)) return null;
                var craft = new Flow();
                for (var input : pattern.getInputs()) {
                    var options = input.possibleInputs();
                    if (options.length != 1 || input.getRemainingKey(options[0].what()) != null) return null;
                    var amount = BigInteger.valueOf(options[0].amount()).multiply(BigInteger.valueOf(input.getMultiplier()));
                    craft.peak.merge(options[0].what(), amount, BigInteger::add);
                    craft.delta.merge(options[0].what(), amount.negate(), BigInteger::add);
                }
                for (var output : pattern.getOutputs()) craft.delta.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
                pass.append(craft, step.crafts());
                counts.put(pattern, Math.addExact(counts.getLong(pattern), Math.multiplyExact(step.crafts(), batch.repetitions())));
            }
            flow.append(pass, batch.repetitions());
        }
        if (!counts.equals(plan.patternTimes())) return null;
        var supplied = new HashMap<AEKey, BigInteger>();
        for (var counter : List.of(plan.usedItems(), plan.emittedItems(), plan.missingItems())) {
            counter.forEach((key, amount) -> supplied.merge(key, BigInteger.valueOf(amount), BigInteger::add));
        }
        var required = KeyCounter.checked();
        for (var entry : flow.peak.entrySet()) {
            var need = entry.getValue().max(BigInteger.ZERO);
            if (need.compareTo(supplied.getOrDefault(entry.getKey(), BigInteger.ZERO)) > 0) return null;
            if (need.signum() > 0) required.add(entry.getKey(), need.longValueExact());
        }
        var output = plan.finalOutput();
        var produced = supplied.getOrDefault(output.what(), BigInteger.ZERO).add(flow.delta.getOrDefault(output.what(), BigInteger.ZERO));
        return produced.compareTo(BigInteger.valueOf(output.amount())) >= 0 ? required : null;
    }

    private static final class Flow {
        final Map<AEKey, BigInteger> delta = new HashMap<>();
        final Map<AEKey, BigInteger> peak = new HashMap<>();

        void append(Flow next, long times) {
            var repeats = BigInteger.valueOf(times);
            // Each repetition starts at the previous one's final inventory. Net losses increase the last
            // repetition's peak; net gains leave the first repetition as the strictest requirement.
            next.peak.forEach((key, amount) -> {
                var loss = next.delta.getOrDefault(key, BigInteger.ZERO).negate().max(BigInteger.ZERO);
                var need = amount.add(loss.multiply(repeats.subtract(BigInteger.ONE)))
                    .subtract(this.delta.getOrDefault(key, BigInteger.ZERO));
                this.peak.merge(key, need, BigInteger::max);
            });
            next.delta.forEach((key, amount) -> this.delta.merge(key, amount.multiply(repeats), BigInteger::add));
        }
    }
}
