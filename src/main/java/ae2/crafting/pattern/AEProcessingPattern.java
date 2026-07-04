package ae2.crafting.pattern;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.crafting.PatternDetailsTooltip;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.integration.Integrations;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class AEProcessingPattern implements IPatternDetails {
    public static final int MAX_INPUT_SLOTS = 9 * 9;
    public static final int MAX_OUTPUT_SLOTS = 3 * 9;
    private static final String ENCODED_PROCESSING_PATTERN = "encoded_processing_pattern";
    private static final String TAG_RECIPE_TYPE_UID = "recipeTypeUid";
    private static final int INVALID_RECIPE_TYPE_UID_WARNING_LIMIT = 8;
    private static final AtomicInteger INVALID_RECIPE_TYPE_UID_WARNING_COUNT = new AtomicInteger();

    private final AEItemKey definition;
    private final List<GenericStack> sparseInputs;
    private final List<GenericStack> sparseOutputs;
    private final Input[] inputs;
    private final List<GenericStack> condensedOutputs;
    @Nullable
    private final String recipeTypeUid;

    public AEProcessingPattern(AEItemKey definition) {
        this.definition = definition;

        var encodedPattern = getEncodedTag(definition);
        if (encodedPattern == null) {
            throw new IllegalArgumentException("Given item does not encode a processing pattern: " + definition);
        }

        this.sparseInputs = readGenericStackList(encodedPattern.getTagList("inputs", Constants.NBT.TAG_COMPOUND));
        this.sparseOutputs = readGenericStackList(encodedPattern.getTagList("outputs", Constants.NBT.TAG_COMPOUND));
        validateSparseStacks(this.sparseInputs, MAX_INPUT_SLOTS, "input");
        validateSparseStacks(this.sparseOutputs, MAX_OUTPUT_SLOTS, "output");
        if (!hasAnyStack(this.sparseInputs)) {
            throw new IllegalArgumentException("At least one input must be non-null.");
        }
        if (this.sparseOutputs.isEmpty()) {
            throw new IllegalArgumentException("At least one output must be present.");
        }
        Objects.requireNonNull(this.sparseOutputs.getFirst(), "The first (primary) output must be non-null.");
        this.recipeTypeUid = readRecipeTypeUid(encodedPattern);
        var condensedInputs = AEPatternHelper.condenseStacks(sparseInputs);
        this.inputs = new Input[condensedInputs.size()];
        for (int i = 0; i < inputs.length; ++i) {
            inputs[i] = new Input(condensedInputs.get(i));
        }

        this.condensedOutputs = AEPatternHelper.condenseStacks(sparseOutputs);
    }

    public static void encode(ItemStack stack, List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs) {
        encode(stack, sparseInputs, sparseOutputs, null);
    }

    public static void encode(ItemStack stack, List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs,
                              @Nullable String recipeTypeUid) {
        if (!hasAnyStack(sparseInputs)) {
            throw new IllegalArgumentException("At least one input must be non-null.");
        }
        if (sparseOutputs.isEmpty()) {
            throw new IllegalArgumentException("At least one output must be present.");
        }
        Objects.requireNonNull(sparseOutputs.getFirst(), "The first (primary) output must be non-null.");

        String normalizedRecipeTypeUid = recipeTypeUid == null ? null : RecipeTypeUid.requireValid(recipeTypeUid);
        var encoded = new NBTTagCompound();
        encoded.setTag("inputs", writeGenericStackList(sparseInputs));
        encoded.setTag("outputs", writeGenericStackList(sparseOutputs));
        if (normalizedRecipeTypeUid != null) {
            encoded.setString(TAG_RECIPE_TYPE_UID, normalizedRecipeTypeUid);
        }
        stack.setTagInfo(ENCODED_PROCESSING_PATTERN, encoded);
    }

    public static PatternDetailsTooltip getInvalidPatternTooltip(ItemStack stack, World world,
                                                                 @Nullable Exception cause, boolean flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        var encoded = stack.getTagCompound();
        if (encoded != null && encoded.hasKey(ENCODED_PROCESSING_PATTERN, 10)) {
            var tag = encoded.getCompoundTag(ENCODED_PROCESSING_PATTERN);
            for (GenericStack input : readGenericStackList(tag.getTagList("inputs", 10))) {
                if (input != null) {
                    tooltip.addInput(input);
                }
            }
            for (GenericStack output : readGenericStackList(tag.getTagList("outputs", 10))) {
                if (output != null) {
                    tooltip.addOutput(output);
                }
            }
            addRecipeTypeProperty(tooltip, readRecipeTypeUid(tag));
        }
        return tooltip;
    }

    private static void addRecipeTypeProperty(PatternDetailsTooltip tooltip, @Nullable String recipeTypeUid) {
        if (recipeTypeUid == null || recipeTypeUid.isEmpty()) {
            return;
        }

        String title = Integrations.hei().getRecipeCategoryTitle(recipeTypeUid);
        if (title != null && !title.isEmpty()) {
            tooltip.addProperty(GuiText.RecipeType.text(), new TextComponentString(title));
        }
    }

    @Nullable
    private static String readRecipeTypeUid(NBTTagCompound encodedPattern) {
        if (!encodedPattern.hasKey(TAG_RECIPE_TYPE_UID)) {
            return null;
        }
        if (!encodedPattern.hasKey(TAG_RECIPE_TYPE_UID, Constants.NBT.TAG_STRING)) {
            warnInvalidStoredRecipeTypeUid(
                "Ignoring recipe type UID stored in a processing pattern because its NBT tag is not a string");
            return null;
        }

        String rawRecipeTypeUid = encodedPattern.getString(TAG_RECIPE_TYPE_UID);
        String normalizedRecipeTypeUid = RecipeTypeUid.normalize(rawRecipeTypeUid);
        if (normalizedRecipeTypeUid == null) {
            warnInvalidStoredRecipeTypeUid(
                "Ignoring invalid recipe type UID stored in a processing pattern (UTF-16 length: %d)",
                rawRecipeTypeUid.length());
        }
        return normalizedRecipeTypeUid;
    }

    private static void warnInvalidStoredRecipeTypeUid(String warningMessage, Object... parameters) {
        int warningIndex = INVALID_RECIPE_TYPE_UID_WARNING_COUNT.getAndUpdate(
            current -> current <= INVALID_RECIPE_TYPE_UID_WARNING_LIMIT ? current + 1 : current);
        if (warningIndex < INVALID_RECIPE_TYPE_UID_WARNING_LIMIT) {
            AELog.warn(warningMessage, parameters);
        } else if (warningIndex == INVALID_RECIPE_TYPE_UID_WARNING_LIMIT) {
            AELog.warn("Suppressing further warnings for invalid recipe type UIDs stored in processing patterns");
        }
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

    private static boolean hasAnyStack(List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            if (stack != null) {
                return true;
            }
        }
        return false;
    }

    private static void validateSparseStacks(List<GenericStack> stacks, int maxSlots, String role) {
        if (stacks.size() > maxSlots) {
            throw new IllegalArgumentException("Processing pattern has too many " + role + " slots.");
        }
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            if (stack.amount() <= 0 || stack.amount() > PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT) {
                throw new IllegalArgumentException("Processing pattern " + role + " amount is out of range.");
            }
        }
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

    @Nullable
    public String getRecipeType() {
        return Integrations.hei().getRecipeCategoryTitle(recipeTypeUid);
    }

    @Nullable
    public String getRecipeTypeUid() {
        return this.recipeTypeUid;
    }

    public List<GenericStack> getSparseInputs() {
        return sparseInputs;
    }

    public List<GenericStack> getSparseOutputs() {
        return sparseOutputs;
    }

    @Override
    public PatternDetailsTooltip getTooltip(World level, ITooltipFlag flags) {
        var tooltip = IPatternDetails.super.getTooltip(level, flags);
        addRecipeTypeProperty(tooltip, this.recipeTypeUid);
        return tooltip;
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
