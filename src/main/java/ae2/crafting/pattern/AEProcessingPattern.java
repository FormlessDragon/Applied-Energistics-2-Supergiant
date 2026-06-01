package ae2.crafting.pattern;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsTooltip;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class AEProcessingPattern implements IPatternDetails {
    public static final int MAX_INPUT_SLOTS = 9 * 9;
    public static final int MAX_OUTPUT_SLOTS = 3 * 9;
    private static final String ENCODED_PROCESSING_PATTERN = "encoded_processing_pattern";

    private final AEItemKey definition;
    private final List<GenericStack> sparseInputs;
    private final List<GenericStack> sparseOutputs;
    private final Input[] inputs;
    private final List<GenericStack> condensedOutputs;

    public AEProcessingPattern(AEItemKey definition) {
        this.definition = definition;

        var encodedPattern = getEncodedTag(definition);
        if (encodedPattern == null) {
            throw new IllegalArgumentException("Given item does not encode a processing pattern: " + definition);
        }

        this.sparseInputs = readGenericStackList(encodedPattern.getTagList("inputs", 10));
        this.sparseOutputs = readGenericStackList(encodedPattern.getTagList("outputs", 10));
        var condensedInputs = AEPatternHelper.condenseStacks(sparseInputs);
        this.inputs = new Input[condensedInputs.size()];
        for (int i = 0; i < inputs.length; ++i) {
            inputs[i] = new Input(condensedInputs.get(i));
        }

        this.condensedOutputs = AEPatternHelper.condenseStacks(sparseOutputs);
    }

    public static void encode(ItemStack stack, List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs) {
        if (sparseInputs.stream().noneMatch(Objects::nonNull)) {
            throw new IllegalArgumentException("At least one input must be non-null.");
        }
        Objects.requireNonNull(sparseOutputs.getFirst(), "The first (primary) output must be non-null.");

        var encoded = new NBTTagCompound();
        encoded.setTag("inputs", writeGenericStackList(sparseInputs));
        encoded.setTag("outputs", writeGenericStackList(sparseOutputs));
        stack.setTagInfo(ENCODED_PROCESSING_PATTERN, encoded);
    }

    public static PatternDetailsTooltip getInvalidPatternTooltip(ItemStack stack, World world,
                                                                 @Nullable Exception cause, boolean flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        var encoded = stack.getTagCompound();
        if (encoded != null && encoded.hasKey(ENCODED_PROCESSING_PATTERN, 10)) {
            var tag = encoded.getCompoundTag(ENCODED_PROCESSING_PATTERN);
            readGenericStackList(tag.getTagList("inputs", 10)).stream().filter(Objects::nonNull).forEach(tooltip::addInput);
            readGenericStackList(tag.getTagList("outputs", 10)).stream().filter(Objects::nonNull).forEach(tooltip::addOutput);
        }
        return tooltip;
    }

    @Nullable
    private static NBTTagCompound getEncodedTag(AEItemKey definition) {
        var stack = definition.getReadOnlyStack();
        var tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        return tag.hasKey(ENCODED_PROCESSING_PATTERN, 10) ? tag.getCompoundTag(ENCODED_PROCESSING_PATTERN) : null;
    }

    private static NBTTagList writeGenericStackList(List<GenericStack> stacks) {
        return GenericStack.writeList(stacks);
    }

    private static List<GenericStack> readGenericStackList(NBTTagList list) {
        return GenericStack.readList(list);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && ((AEProcessingPattern) obj).definition.equals(definition);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return condensedOutputs;
    }

    public List<GenericStack> getSparseInputs() {
        return sparseInputs;
    }

    public List<GenericStack> getSparseOutputs() {
        return sparseOutputs;
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        if (sparseInputs.size() == inputs.length) {
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
            return;
        }

        var allInputs = new KeyCounter();
        for (var counter : inputHolder) {
            allInputs.addAll(counter);
        }

        long multiplier = getInputPushMultiplier(inputHolder);
        for (var sparseInput : sparseInputs) {
            if (sparseInput == null) {
                continue;
            }

            var key = sparseInput.what();
            var amount = sparseInput.amount() * multiplier;
            long available = allInputs.get(key);

            if (available < amount) {
                throw new RuntimeException("Expected at least %d of %s when pushing pattern, but only %d available"
                    .formatted(amount, key, available));
            }

            inputSink.pushInput(key, amount);
            allInputs.remove(key, amount);
        }
    }

    private long getInputPushMultiplier(KeyCounter[] inputHolder) {
        long multiplier = Long.MAX_VALUE;
        for (int i = 0; i < inputs.length; i++) {
            long expectedAmount = inputs[i].getMultiplier();
            if (expectedAmount <= 0) {
                continue;
            }

            long actualAmount = 0;
            for (var input : inputHolder[i]) {
                actualAmount += input.getLongValue();
            }
            multiplier = Math.min(multiplier, actualAmount / expectedAmount);
        }
        return multiplier == Long.MAX_VALUE ? 1 : multiplier;
    }

    private static class Input implements IInput {
        private final GenericStack[] template;
        private final long multiplier;

        private Input(GenericStack stack) {
            this.template = new GenericStack[]{new GenericStack(stack.what(), 1)};
            this.multiplier = stack.amount();
        }

        @Override
        public GenericStack[] possibleInputs() {
            return template;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, World level) {
            return input.matches(template[0]);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
