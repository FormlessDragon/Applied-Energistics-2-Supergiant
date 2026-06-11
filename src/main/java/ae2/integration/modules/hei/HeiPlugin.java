package ae2.integration.modules.hei;

import ae2.api.config.Actionable;
import ae2.api.config.CondenserOutput;
import ae2.api.features.P2PTunnelAttunementInternal;
import ae2.api.integrations.hei.IngredientConverters;
import ae2.api.upgrades.IUpgradeableItem;
import ae2.api.upgrades.Upgrades;
import ae2.client.gui.Icon;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.container.me.items.ContainerWirelessCraftingTerm;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.localization.GuiText;
import ae2.core.localization.ItemModText;
import ae2.integration.abstraction.ItemListMod;
import ae2.items.materials.StorageComponentItem;
import ae2.items.parts.FacadeItem;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.game.StorageCellUpgradeRecipe;
import ae2.recipes.quartzcutting.QuartzCuttingRecipe;
import ae2.tile.misc.TileCondenser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@JEIPlugin
@ParametersAreNonnullByDefault
public class HeiPlugin implements IModPlugin {
    public static final AEGuiHandler GUI_HANDLER = new AEGuiHandler();
    private static volatile IJeiRuntime runtime;

    static IJeiRuntime getRuntime() {
        return runtime;
    }

    private static void registerInscriberRecipes(IModRegistry registry) {
        List<InscriberRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.INSCRIBER.getRecipes()) {
            recipes.add(new InscriberRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, InscriberRecipeCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.INSCRIBER.stack(), InscriberRecipeCategory.UID);
    }

    private static void registerChargerRecipes(IModRegistry registry) {
        List<ChargerRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.CHARGER.getRecipes()) {
            recipes.add(new ChargerRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, ChargerRecipeCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.CHARGER.stack(), ChargerRecipeCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.CRANK.stack(), ChargerRecipeCategory.UID);
    }

    private static void registerCrystalAssemblerRecipes(IModRegistry registry) {
        List<CrystalAssemblerRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.CRYSTAL_ASSEMBLER.getRecipes()) {
            recipes.add(new CrystalAssemblerRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, CrystalAssemblerRecipeCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.CRYSTAL_ASSEMBLER.stack(), CrystalAssemblerRecipeCategory.UID);
    }

    private static void registerCrystalFixerRecipes(IModRegistry registry) {
        List<CrystalFixerRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.CRYSTAL_FIXER.getRecipes()) {
            recipes.add(new CrystalFixerRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, CrystalFixerRecipeCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.CRYSTAL_FIXER.stack(), CrystalFixerRecipeCategory.UID);
    }

    private static void registerCondenserRecipes(IModRegistry registry) {
        List<ItemStack> viableComponents = new ObjectArrayList<>();
        addViableComponent(viableComponents, AEItems.CELL_COMPONENT_1K.stack());
        addViableComponent(viableComponents, AEItems.CELL_COMPONENT_4K.stack());
        addViableComponent(viableComponents, AEItems.CELL_COMPONENT_16K.stack());
        addViableComponent(viableComponents, AEItems.CELL_COMPONENT_64K.stack());
        addViableComponent(viableComponents, AEItems.CELL_COMPONENT_256K.stack());

        registry.addRecipes(List.of(
            new CondenserOutputWrapper(CondenserOutput.MATTER_BALLS, AEItems.MATTER_BALL.stack(), viableComponents,
                new IconDrawable(Icon.CONDENSER_OUTPUT_MATTER_BALL)),
            new CondenserOutputWrapper(CondenserOutput.SINGULARITY, AEItems.SINGULARITY.stack(), viableComponents,
                new IconDrawable(Icon.CONDENSER_OUTPUT_SINGULARITY))), CondenserCategory.UID);
        registry.addRecipeCatalyst(AEBlocks.CONDENSER.stack(), CondenserCategory.UID);
    }

    private static void registerTransformRecipes(IModRegistry registry) {
        List<TransformRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
            recipes.add(new TransformRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, TransformCategory.UID);
        registry.addRecipeCatalyst(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack(), TransformCategory.UID);
    }

    private static void registerFacadeRecipes(IModRegistry registry) {
        Item facadeItem = AEItems.FACADE.item();
        if (!(facadeItem instanceof FacadeItem facade)) {
            return;
        }

        ItemStack cableAnchor = AEParts.CABLE_ANCHOR.stack();
        if (cableAnchor.isEmpty()) {
            return;
        }

        registry.addRecipeRegistryPlugin(new FacadeRegistryPlugin(facade, cableAnchor));
    }

    private static void registerP2PAttunementRecipes(IModRegistry registry) {
        List<P2PAttunementRecipeWrapper> recipes = new ObjectArrayList<>();
        List<ItemStack> allStacks = new ObjectArrayList<>(registry.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM));

        for (var entry : P2PTunnelAttunementInternal.getApiTunnels()) {
            P2PAttunementRecipeWrapper recipe = P2PAttunementRecipeWrapper.forApi(entry, allStacks);
            if (recipe.hasInputs()) {
                recipes.add(recipe);
            }
        }

        for (var entry : P2PTunnelAttunementInternal.getTagTunnels().entrySet()) {
            P2PAttunementRecipeWrapper recipe = P2PAttunementRecipeWrapper.forTag(entry.getKey(), entry.getValue());
            if (recipe.hasInputs()) {
                recipes.add(recipe);
            }
        }

        registry.addRecipes(recipes, P2PAttunementCategory.UID);
        registry.addRecipeCatalyst(AEParts.ME_P2P_TUNNEL.stack(), P2PAttunementCategory.UID);
    }

    private static void registerCraftingRecipeWrappers(IModRegistry registry) {
        registry.handleRecipes(StorageCellUpgradeRecipe.class, StorageCellUpgradeRecipeWrapper::new,
            VanillaRecipeCategoryUid.CRAFTING);
        registry.handleRecipes(QuartzCuttingRecipe.class, QuartzCuttingRecipeWrapper::new,
            VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(AEParts.CRAFTING_TERMINAL.stack(), VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(AEItems.WIRELESS_CRAFTING_TERMINAL.stack(), VanillaRecipeCategoryUid.CRAFTING);

        List<AddItemUpgradeRecipeWrapper> upgradeRecipes = new ObjectArrayList<>();
        for (var entry : Upgrades.getUpgradableItems().entrySet()) {
            IUpgradeableItem baseItem = entry.getKey();
            for (var upgrade : entry.getValue()) {
                upgradeRecipes.add(new AddItemUpgradeRecipeWrapper(baseItem, upgrade));
            }
        }
        registry.addRecipes(upgradeRecipes, VanillaRecipeCategoryUid.CRAFTING);
    }

    private static void registerDescriptions(IModRegistry registry) {
        registry.addIngredientInfo(AEItems.CERTUS_QUARTZ_CRYSTAL.stack(), VanillaTypes.ITEM,
            GuiText.CertusQuartzObtain.getLocal());

        if (AEConfig.instance().isSpawnPressesInMeteoritesEnabled()) {
            String inWorldCraftingPresses = GuiText.inWorldCraftingPresses.getLocal();
            registry.addIngredientInfo(AEItems.LOGIC_PROCESSOR_PRESS.stack(), VanillaTypes.ITEM,
                inWorldCraftingPresses);
            registry.addIngredientInfo(AEItems.CALCULATION_PROCESSOR_PRESS.stack(), VanillaTypes.ITEM,
                inWorldCraftingPresses);
            registry.addIngredientInfo(AEItems.ENGINEERING_PROCESSOR_PRESS.stack(), VanillaTypes.ITEM,
                inWorldCraftingPresses);
            registry.addIngredientInfo(AEItems.SILICON_PRESS.stack(), VanillaTypes.ITEM, inWorldCraftingPresses);
        }

        registry.addIngredientInfo(AEBlocks.CRANK.stack(), VanillaTypes.ITEM,
            ItemModText.CRANK_DESCRIPTION.getLocal());
    }

    private static void blacklistTechnicalItems(IModRegistry registry) {
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();
        addItemToBlacklist(blacklist, AEItems.MISSING_CONTENT.stack());
        addItemToBlacklist(blacklist, AEItems.WRAPPED_GENERIC_STACK.stack());
        addItemToBlacklist(blacklist, AEItems.CRAFTING_PATTERN.stack());
        addItemToBlacklist(blacklist, AEItems.PROCESSING_PATTERN.stack());
        addItemToBlacklist(blacklist, AEItems.FACADE.stack());
        addItemToBlacklist(blacklist, AEItems.PACKAGE.stack());
        addItemToBlacklist(blacklist, AEBlocks.CABLE_BUS.stack());
        addItemToBlacklist(blacklist, AEBlocks.MATRIX_FRAME.stack());
        addItemToBlacklist(blacklist, AEBlocks.PAINT.stack());
        addItemToBlacklist(blacklist, AEItems.DEBUG_CARD.stack());
        addItemToBlacklist(blacklist, AEItems.DEBUG_ERASER.stack());
        addItemToBlacklist(blacklist, AEItems.DEBUG_METEORITE_PLACER.stack());
        addItemToBlacklist(blacklist, AEItems.DEBUG_REPLICATOR_CARD.stack());
        addItemToBlacklist(blacklist, AEBlocks.DEBUG_ENERGY_GEN.stack());
        addItemToBlacklist(blacklist, AEBlocks.DEBUG_ITEM_GEN.stack());
        addItemToBlacklist(blacklist, AEBlocks.DEBUG_CUBE_GEN.stack());
        addItemToBlacklist(blacklist, AEBlocks.DEBUG_PHANTOM_NODE.stack());
    }

    private static void addItemToBlacklist(IIngredientBlacklist blacklist, ItemStack stack) {
        if (!stack.isEmpty()) {
            stack = stack.copy();
            stack.setItemDamage(OreDictionary.WILDCARD_VALUE);
            blacklist.addIngredientToBlacklist(stack);
        }
    }

    private static void registerEntropyRecipes(IModRegistry registry) {
        List<EntropyRecipeWrapper> recipes = new ObjectArrayList<>();
        for (var recipe : AERecipeTypes.ENTROPY.getRecipes()) {
            recipes.add(new EntropyRecipeWrapper(recipe));
        }
        registry.addRecipes(recipes, EntropyRecipeCategory.UID);

        ItemStack entropyManipulator = AEItems.ENTROPY_MANIPULATOR.stack();
        if (entropyManipulator.getItem() instanceof AEBasePoweredItem poweredItem) {
            poweredItem.injectAEPower(entropyManipulator, poweredItem.getAEMaxPower(entropyManipulator),
                Actionable.MODULATE);
        }
        registry.addRecipeCatalyst(entropyManipulator, EntropyRecipeCategory.UID);
    }

    private static void addViableComponent(List<ItemStack> viableComponents, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof StorageComponentItem storageComponent)) {
            return;
        }
        if (storageComponent.getBytes(stack) * TileCondenser.BYTE_MULTIPLIER
            >= CondenserOutput.MATTER_BALLS.requiredPower) {
            viableComponents.add(stack);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void registerItemSubtypes(ISubtypeRegistry subtypeRegistry) {
        Item facade = AEItems.FACADE.item();
        if (facade != null) {
            subtypeRegistry.useNbtForSubtypes(facade);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(
            new ChargerRecipeCategory(registry.getJeiHelpers().getGuiHelper()),
            new InscriberRecipeCategory(registry.getJeiHelpers().getGuiHelper()),
            new CrystalAssemblerRecipeCategory(registry.getJeiHelpers().getGuiHelper()),
            new CrystalFixerRecipeCategory(registry.getJeiHelpers().getGuiHelper()),
            new CondenserCategory(registry.getJeiHelpers().getGuiHelper()),
            new TransformCategory(registry.getJeiHelpers().getGuiHelper()),
            new EntropyRecipeCategory(registry.getJeiHelpers().getGuiHelper()),
            new P2PAttunementCategory(registry.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry registry) {
        IngredientConverters.register(new ItemIngredientConverter());
        IngredientConverters.register(new FluidIngredientConverter());

        registerInscriberRecipes(registry);
        registerChargerRecipes(registry);
        registerCrystalAssemblerRecipes(registry);
        registerCrystalFixerRecipes(registry);
        registerCondenserRecipes(registry);
        registerTransformRecipes(registry);
        registerEntropyRecipes(registry);
        registerFacadeRecipes(registry);
        registerP2PAttunementRecipes(registry);
        registerCraftingRecipeWrappers(registry);
        registerDescriptions(registry);
        blacklistTechnicalItems(registry);

        var transferHelper = registry.getJeiHelpers().recipeTransferHandlerHelper();
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new CraftingRecipeTransferHandler<>(ContainerCraftingTerm.class, transferHelper),
            VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(
            new CraftingRecipeTransferHandler<>(ContainerCraftingTerm.class, transferHelper));
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new CraftingRecipeTransferHandler<>(ContainerWirelessCraftingTerm.class, transferHelper),
            VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(
            new CraftingRecipeTransferHandler<>(ContainerWirelessCraftingTerm.class, transferHelper));
        PatternEncodingRecipeTransferHandler patternTransferHandler = new PatternEncodingRecipeTransferHandler();
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(patternTransferHandler);
        registry.addAdvancedGuiHandlers(GUI_HANDLER);
        registry.addGhostIngredientHandler(GUI_HANDLER.getGuiContainerClass(), GUI_HANDLER);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        HeiPlugin.runtime = runtime;
        ItemListMod.setAdapter(new HeiItemListModAdapter(runtime.getIngredientFilter(),
            runtime.getIngredientListOverlay()));
    }
}
