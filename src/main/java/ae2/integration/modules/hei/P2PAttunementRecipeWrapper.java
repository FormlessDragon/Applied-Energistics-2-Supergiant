package ae2.integration.modules.hei;

import ae2.api.features.P2PTunnelAttunementInternal;
import ae2.core.localization.ItemModText;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
class P2PAttunementRecipeWrapper implements IRecipeWrapper {
    private final List<ItemStack> inputs;
    private final ItemStack output;
    private final List<String> description;

    P2PAttunementRecipeWrapper(List<ItemStack> inputs, Item output, List<String> description) {
        this.inputs = inputs;
        this.output = new ItemStack(output);
        this.description = description;
    }

    static P2PAttunementRecipeWrapper forApi(P2PTunnelAttunementInternal.Resultant entry, List<ItemStack> allStacks) {
        List<ItemStack> inputs = allStacks.stream()
                                          .filter(entry.stackPredicate())
                                          .map(ItemStack::copy)
                                          .toList();
        ITextComponent entryDescription = entry.description();
        return new P2PAttunementRecipeWrapper(inputs, entry.tunnelType(), List.of(
            ItemModText.P2P_API_ATTUNEMENT.getLocal(),
            entryDescription.getFormattedText()));
    }

    static P2PAttunementRecipeWrapper forTag(String oreName, Item output) {
        List<ItemStack> inputs = net.minecraftforge.oredict.OreDictionary.getOres(oreName).stream()
                                                                         .map(ItemStack::copy)
                                                                         .toList();
        return new P2PAttunementRecipeWrapper(inputs, output, List.of(
            ItemModText.P2P_TAG_ATTUNEMENT.getLocal()));
    }

    boolean hasInputs() {
        return !this.inputs.isEmpty();
    }

    List<String> getDescription() {
        return this.description;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, Collections.singletonList(this.inputs));
        ingredients.setOutput(VanillaTypes.ITEM, this.output);
    }
}
