package ae2.crafting.pattern;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.PatternDetailsTooltip;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AECraftingPattern implements IAssemblerPattern {
    public static final int CRAFTING_GRID_DIMENSION = 3;
    public static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;
    private static final String ENCODED_CRAFTING_PATTERN = "encoded_crafting_pattern";

    private final AEItemKey definition;
    private final boolean canSubstitute;
    private final boolean canSubstituteFluids;
    private final IRecipe recipe;
    private final List<GenericStack> sparseInputs;
    private final int[] sparseToCompressed = new int[9];
    private final Input[] inputs;
    private final ItemStack output;
    private final List<GenericStack> outputsArray;

    public AECraftingPattern(AEItemKey definition, World ignoredLevel) {
        this.definition = definition;
        var encoded = getEncodedTag(definition);
        if (encoded == null) {
            throw new IllegalArgumentException("Given item does not encode a crafting pattern: " + definition);
        }

        this.canSubstitute = encoded.getBoolean("canSubstitute");
        this.canSubstituteFluids = encoded.getBoolean("canSubstituteFluids");
        this.sparseInputs = getCraftingInputs(readItemStackList(encoded.getTagList("in", 10)));

        var recipeId = new ResourceLocation(encoded.getString("recipeId"));
        this.recipe = ForgeRegistries.RECIPES.getValue(recipeId);
        if (this.recipe == null) {
            throw new IllegalArgumentException("Pattern references unknown recipe " + recipeId);
        }

        this.output = new ItemStack(encoded.getCompoundTag("out"));
        if (this.output.isEmpty()) {
            throw new IllegalStateException("The recipe " + recipeId + " produced an empty item stack result.");
        }
        this.outputsArray = Collections.singletonList(Objects.requireNonNull(GenericStack.fromItemStack(this.output)));

        var condensedInputs = AEPatternHelper.condenseStacks(sparseInputs);
        this.inputs = new Input[condensedInputs.size()];
        Arrays.fill(this.sparseToCompressed, -1);
        for (int j = 0; j < condensedInputs.size(); ++j) {
            var condensedInput = condensedInputs.get(j);

            for (int i = 0; i < 9; ++i) {
                if (sparseInputs.get(i) != null && sparseInputs.get(i).what().equals(condensedInput.what())) {
                    if (inputs[j] == null) {
                        inputs[j] = new Input(i, condensedInput);
                    }
                    sparseToCompressed[i] = j;
                }
            }
        }
    }

    public static void encode(ItemStack result, IRecipe recipe, ItemStack[] sparseInputs, ItemStack output,
                              boolean allowSubstitutes, boolean allowFluidSubstitutes) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(sparseInputs, "sparseInputs");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(recipe.getRegistryName(), "recipe not registered");

        var encoded = new NBTTagCompound();
        encoded.setTag("in", writeItemStackList(Arrays.asList(sparseInputs)));
        var outputTag = new NBTTagCompound();
        output.writeToNBT(outputTag);
        encoded.setTag("out", outputTag);
        encoded.setString("recipeId", recipe.getRegistryName().toString());
        encoded.setBoolean("canSubstitute", allowSubstitutes);
        encoded.setBoolean("canSubstituteFluids", allowFluidSubstitutes);
        result.setTagInfo(ENCODED_CRAFTING_PATTERN, encoded);
    }

    public static PatternDetailsTooltip getInvalidPatternTooltip(ItemStack stack, World ignoredWorld,
                                                                 @Nullable Exception ignoredCause, boolean flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_CRAFTS);

        var tag = stack.getTagCompound();
        var encoded = tag != null && tag.hasKey(ENCODED_CRAFTING_PATTERN, 10) ? tag.getCompoundTag(ENCODED_CRAFTING_PATTERN) : null;
        if (encoded != null) {
            for (var input : readItemStackList(encoded.getTagList("in", 10))) {
                if (!input.isEmpty()) {
                    tooltip.addInput(AEItemKey.of(input), input.getCount());
                }
            }

            var output = new ItemStack(encoded.getCompoundTag("out"));
            if (!output.isEmpty()) {
                tooltip.addOutput(AEItemKey.of(output), output.getCount());
            }

            if (encoded.getBoolean("canSubstitute")) {
                tooltip.addProperty(GuiText.PatternTooltipSubstitutions.text());
            }

            if (encoded.getBoolean("canSubstituteFluids")) {
                tooltip.addProperty(GuiText.PatternTooltipFluidSubstitutions.text());
            }

            if (encoded.hasKey("recipeId", 8)) {
                tooltip.addProperty(GuiText.Recipe.text(),
                    new TextComponentString(encoded.getString("recipeId")));
            }
        }

        return tooltip;
    }

    private static InventoryCrafting createCraftingInventory(int width, int height) {
        return new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                return false;
            }
        }, width, height);
    }

    public static List<GenericStack> getCraftingInputs(List<ItemStack> stacks) {
        var result = new GenericStack[stacks.size()];
        for (int x = 0; x < stacks.size(); ++x) {
            if (!stacks.get(x).isEmpty()) {
                result[x] = GenericStack.fromItemStack(stacks.get(x));
            }
        }
        return Arrays.asList(result);
    }

    @Nullable
    private static NBTTagCompound getEncodedTag(AEItemKey definition) {
        var stack = Objects.requireNonNull(definition.getReadOnlyStack());
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        return tag.hasKey(ENCODED_CRAFTING_PATTERN, 10) ? tag.getCompoundTag(ENCODED_CRAFTING_PATTERN) : null;
    }

    private static NBTTagList writeItemStackList(List<ItemStack> stacks) {
        var list = new NBTTagList();
        for (var stack : stacks) {
            var tag = new NBTTagCompound();
            if (stack != null && !stack.isEmpty()) {
                stack.writeToNBT(tag);
            }
            list.appendTag(tag);
        }
        return list;
    }

    private static List<ItemStack> readItemStackList(NBTTagList list) {
        List<ItemStack> result = new ObjectArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(new ItemStack(list.getCompoundTagAt(i)));
        }
        return result;
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && ((AECraftingPattern) obj).definition.equals(definition);
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
        return outputsArray;
    }

    public boolean canSubstitute() {
        return canSubstitute;
    }

    public boolean canSubstituteFluids() {
        return canSubstituteFluids;
    }

    public List<GenericStack> getSparseInputs() {
        return sparseInputs;
    }

    public List<GenericStack> getSparseOutputs() {
        return outputsArray;
    }

    public IRecipe getRecipe() {
        return recipe;
    }

    private Ingredient getRecipeIngredient(int slot) {
        if (recipe instanceof IShapedRecipe shapedRecipe) {
            return getShapedRecipeIngredient(slot, shapedRecipe.getRecipeWidth());
        }
        return getShapelessRecipeIngredient(slot);
    }

    private Ingredient getShapedRecipeIngredient(int slot, int recipeWidth) {
        int ingredientIndex = getShapedRecipeIngredientIndex(slot, recipeWidth);

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
            return Ingredient.EMPTY;
        }
        return ingredients.get(ingredientIndex);
    }

    private int getShapedRecipeIngredientIndex(int slot, int recipeWidth) {
        int topOffset = 0;
        if (sparseInputs.get(0) == null && sparseInputs.get(1) == null && sparseInputs.get(2) == null) {
            topOffset++;
            if (sparseInputs.get(3) == null && sparseInputs.get(4) == null && sparseInputs.get(5) == null) {
                topOffset++;
            }
        }
        int leftOffset = 0;
        if (sparseInputs.get(0) == null && sparseInputs.get(3) == null && sparseInputs.get(6) == null) {
            leftOffset++;
            if (sparseInputs.get(1) == null && sparseInputs.get(4) == null && sparseInputs.get(7) == null) {
                leftOffset++;
            }
        }

        int slotX = slot % CRAFTING_GRID_DIMENSION - leftOffset;
        int slotY = slot / CRAFTING_GRID_DIMENSION - topOffset;
        return slotY * recipeWidth + slotX;
    }

    private Ingredient getShapelessRecipeIngredient(int slot) {
        int ingredientIndex = 0;
        for (int i = 0; i < slot; i++) {
            if (sparseInputs.get(i) != null) {
                ingredientIndex++;
            }
        }

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredientIndex < ingredients.size()) {
            return ingredients.get(ingredientIndex);
        }
        return Ingredient.EMPTY;
    }

    @Nullable
    public GenericStack getValidFluid(int slot) {
        int compressed = sparseToCompressed[slot];
        if (compressed != -1) {
            var itemOrFluid = inputs[compressed].possibleInputs[0];
            if (itemOrFluid.what() instanceof AEFluidKey) {
                return itemOrFluid;
            }
        }
        return null;
    }

    @Override
    public boolean isItemValid(int slot, @Nullable AEItemKey key, World level) {
        if (!canSubstitute) {
            return sparseInputs.get(slot) == null && key == null
                || sparseInputs.get(slot) != null && sparseInputs.get(slot).what().equals(key);
        }

        if (key == null) {
            return sparseInputs.get(slot) == null;
        }

        var testFrame = makeCraftingInventory();
        testFrame.setInventorySlotContents(slot, key.toStack());
        if (!recipe.matches(testFrame, level)) {
            return false;
        }

        var testOutput = recipe.getCraftingResult(testFrame);
        return !testOutput.isEmpty() && ItemStack.areItemStacksEqual(output, testOutput);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return sparseInputs.get(slot) != null;
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
        for (int sparseIndex = 0; sparseIndex < CRAFTING_GRID_SLOTS; sparseIndex++) {
            int inputId = sparseToCompressed[sparseIndex];
            if (inputId != -1) {
                var list = table[inputId];

                var validFluid = getValidFluid(sparseIndex);
                if (validFluid != null) {
                    var validFluidKey = validFluid.what();
                    long requiredAmount = validFluid.amount();
                    if (list.get(validFluidKey) >= requiredAmount) {
                        gridAccessor.set(sparseIndex, GenericStack.wrapInItemStack(validFluidKey, requiredAmount));
                        list.remove(validFluidKey, requiredAmount);
                        continue;
                    }
                }

                for (var entry : list) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey itemKey) {
                        gridAccessor.set(sparseIndex, itemKey.toStack());
                        list.remove(itemKey, 1);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public ItemStack assemble(InventoryCrafting input, World level) {
        if (input.getWidth() != CRAFTING_GRID_DIMENSION || input.getHeight() != CRAFTING_GRID_DIMENSION) {
            return ItemStack.EMPTY;
        }

        if (canSubstitute && recipe.isDynamic()) {
            var adjustedInput = copyCraftingInventory(input);
            for (int i = 0; i < sparseInputs.size(); i++) {
                ItemStack item = adjustedInput.getStackInSlot(i);
                var stack = GenericStack.unwrapItemStack(item);
                if (stack != null) {
                    var validFluid = getValidFluid(i);
                    if (validFluid != null && validFluid.equals(stack)) {
                        adjustedInput.setInventorySlotContents(i,
                            ((AEItemKey) sparseInputs.get(i).what()).toStack());
                    }
                }
            }
            return recipe.getCraftingResult(adjustedInput);
        }

        for (int i = 0; i < sparseInputs.size(); i++) {
            ItemStack item = input.getStackInSlot(i);
            var stack = GenericStack.unwrapItemStack(item);
            if (stack != null) {
                var validFluid = getValidFluid(i);
                if (validFluid != null && validFluid.equals(stack)) {
                    continue;
                }
            }

            if (!isItemValid(i, AEItemKey.of(item), level)) {
                return ItemStack.EMPTY;
            }
        }

        return output.copy();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting input) {
        if (!canSubstituteFluids) {
            return recipe.getRemainingItems(input);
        }

        var adjustedInput = copyCraftingInventory(input);
        var slotsToClear = new boolean[input.getSizeInventory()];
        for (int i = 0; i < input.getSizeInventory(); i++) {
            var validFluid = getValidFluid(i);
            if (validFluid != null) {
                var stack = GenericStack.unwrapItemStack(input.getStackInSlot(i));
                if (validFluid.equals(stack)) {
                    adjustedInput.setInventorySlotContents(i, ((AEItemKey) sparseInputs.get(i).what()).toStack());
                    slotsToClear[i] = true;
                }
            }
        }

        var result = recipe.getRemainingItems(adjustedInput);
        for (int i = 0; i < slotsToClear.length; i++) {
            if (slotsToClear[i]) {
                result.set(i, ItemStack.EMPTY);
            }
        }
        return result;
    }

    private ItemStack getRecipeRemainder(int slot, AEItemKey key) {
        var testFrame = makeCraftingInventory();
        testFrame.setInventorySlotContents(slot, key.toStack());
        var remainingItems = recipe.getRemainingItems(testFrame);
        if (slot >= 0 && slot < remainingItems.size()) {
            return remainingItems.get(slot);
        }
        return ItemStack.EMPTY;
    }

    private GenericStack getItemOrFluidInput(int slot, GenericStack item) {
        if (!(item.what() instanceof AEItemKey itemKey)) {
            return item;
        }

        var containedFluid = ContainerItemStrategies.getContainedStack(itemKey.toStack(), AEKeyType.fluids());
        var ingredientItem = itemKey.getItem();
        var isBucket = ingredientItem instanceof ItemBucket || ingredientItem == Items.MILK_BUCKET;

        if (canSubstituteFluids && containedFluid != null && isBucket) {
            var remainingItems = recipe.getRemainingItems(makeCraftingInventory());
            if (slot >= 0 && slot < remainingItems.size()) {
                var slotRemainder = remainingItems.get(slot);
                if (slotRemainder.getCount() == 1 && slotRemainder.getItem() == Items.BUCKET) {
                    return new GenericStack(containedFluid.what(), containedFluid.amount());
                }
            }
        }

        return item;
    }

    @Override
    public PatternDetailsTooltip getTooltip(World level, ITooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_CRAFTS);
        tooltip.addInputsAndOutputs(this);

        if (canSubstitute) {
            tooltip.addProperty(GuiText.PatternTooltipSubstitutions.text());
        }

        if (canSubstituteFluids) {
            tooltip.addProperty(GuiText.PatternTooltipFluidSubstitutions.text());
        }

        if (recipe.getRegistryName() != null) {
            tooltip.addProperty(GuiText.Recipe.text(),
                new TextComponentString(recipe.getRegistryName().toString()));
        }

        return tooltip;
    }

    private InventoryCrafting makeCraftingInventory() {
        var crafting = createCraftingInventory(CRAFTING_GRID_DIMENSION, CRAFTING_GRID_DIMENSION);
        for (int i = 0; i < sparseInputs.size(); i++) {
            var stack = sparseInputs.get(i);
            crafting.setInventorySlotContents(i,
                stack != null && stack.what() instanceof AEItemKey itemKey ? itemKey.toStack((int) stack.amount())
                    : ItemStack.EMPTY);
        }
        return crafting;
    }

    private InventoryCrafting copyCraftingInventory(InventoryCrafting input) {
        var crafting = createCraftingInventory(input.getWidth(), input.getHeight());
        for (int i = 0; i < input.getSizeInventory(); i++) {
            crafting.setInventorySlotContents(i, input.getStackInSlot(i).copy());
        }
        return crafting;
    }

    private class Input implements IInput {
        private final int slot;
        private final GenericStack[] possibleInputs;
        private final long multiplier;

        private Input(int slot, GenericStack condensedInput) {
            this.slot = slot;
            this.multiplier = condensedInput.amount();

            var itemOrFluidInput = getItemOrFluidInput(slot, sparseInputs.get(slot));

            if (!canSubstitute) {
                this.possibleInputs = new GenericStack[]{itemOrFluidInput};
            } else {
                ItemStack[] matchingStacks = getRecipeIngredient(slot).getMatchingStacks();
                this.possibleInputs = new GenericStack[matchingStacks.length + 1];
                this.possibleInputs[0] = itemOrFluidInput;
                for (int i = 0; i < matchingStacks.length; ++i) {
                    this.possibleInputs[i + 1] = GenericStack.fromItemStack(matchingStacks[i]);
                }
            }
        }

        @Override
        public GenericStack[] possibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, World level) {
            if (input.matches(possibleInputs[0])) {
                return true;
            }
            return canSubstitute && input instanceof AEItemKey itemKey && AECraftingPattern.this.isItemValid(slot, itemKey, level);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            if (template instanceof AEItemKey itemKey) {
                return AEItemKey.of(getRecipeRemainder(slot, itemKey));
            }
            return null;
        }
    }
}
