package ae2.integration.modules.hei;

import ae2.api.upgrades.IUpgradeableItem;
import ae2.core.AppEng;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;

@ParametersAreNonnullByDefault
class AddItemUpgradeRecipeWrapper implements ICraftingRecipeWrapper {
    private final IUpgradeableItem baseItem;
    private final Item upgrade;

    AddItemUpgradeRecipeWrapper(IUpgradeableItem baseItem, Item upgrade) {
        this.baseItem = baseItem;
        this.upgrade = upgrade;
    }

    private static ItemStack makeUpgraded(IUpgradeableItem baseItem, Item upgrade) {
        ItemStack upgraded = new ItemStack(asItem(baseItem));
        baseItem.getUpgrades(upgraded).addItems(new ItemStack(upgrade));
        return upgraded;
    }

    private static Item asItem(IUpgradeableItem baseItem) {
        return (Item) baseItem;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, Arrays.asList(new ItemStack(asItem(this.baseItem)),
            new ItemStack(this.upgrade)));
        ingredients.setOutput(VanillaTypes.ITEM, makeUpgraded(this.baseItem, this.upgrade));
    }

    @Override
    public ResourceLocation getRegistryName() {
        ResourceLocation baseItemId = Item.REGISTRY.getNameForObject(asItem(this.baseItem));
        ResourceLocation upgradeId = Item.REGISTRY.getNameForObject(this.upgrade);
        String basePath = baseItemId == null ? "unknown" : baseItemId.getPath();
        String upgradePath = upgradeId == null ? "unknown" : upgradeId.getPath();
        return AppEng.makeId("add_item_upgrade/" + basePath + "/" + upgradePath);
    }
}
