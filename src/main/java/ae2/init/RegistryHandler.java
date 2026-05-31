package ae2.init;

import ae2.core.Tags;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.init.client.InitModelRegistration;
import ae2.init.internal.InitStorageCells;
import ae2.init.worldgen.InitBiomes;
import ae2.recipes.game.MeteoriteCompassBeaconRecipe;
import ae2.recipes.handlers.FurnaceRecipeRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class RegistryHandler {

    private RegistryHandler() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        AEBlocks.register(event);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        AEItems.register(event);
        InitStorageCells.init();
        OreDictionary.registerOre("crystalCertusQuartz", AEItems.CERTUS_QUARTZ_CRYSTAL.stack());
        OreDictionary.registerOre("crystalCertusQuartz", AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        OreDictionary.registerOre("gemCertusQuartz", AEItems.CERTUS_QUARTZ_CRYSTAL.stack());
        OreDictionary.registerOre("gemCertusQuartz", AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        OreDictionary.registerOre("crystalChargedCertusQuartz", AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack());
        OreDictionary.registerOre("dustCertusQuartz", AEItems.CERTUS_QUARTZ_DUST.stack());
        OreDictionary.registerOre("dustEnder", AEItems.ENDER_DUST.stack());
        OreDictionary.registerOre("dustEnderPearl", AEItems.ENDER_DUST.stack());
        OreDictionary.registerOre("itemSilicon", AEItems.SILICON.stack());
        OreDictionary.registerOre("crystalFluix", AEItems.FLUIX_CRYSTAL.stack());
        OreDictionary.registerOre("gemFluix", AEItems.FLUIX_CRYSTAL.stack());
        OreDictionary.registerOre("dustFluix", AEItems.FLUIX_DUST.stack());
        OreDictionary.registerOre("pearlFluix", AEItems.FLUIX_PEARL.stack());
        OreDictionary.registerOre("gemEntro", AEItems.ENTRO_CRYSTAL.stack());
        OreDictionary.registerOre("crystalEntro", AEItems.ENTRO_CRYSTAL.stack());
        OreDictionary.registerOre("dustEntro", AEItems.ENTRO_DUST.stack());
        OreDictionary.registerOre("ingotEntro", AEItems.ENTRO_INGOT.stack());
        OreDictionary.registerOre("ingotInfusedEntro", AEItems.ENTRO_INGOT.stack());
        OreDictionary.registerOre("blockEntro", AEBlocks.ENTRO_BLOCK.stack());
        OreDictionary.registerOre("dustSkyStone", AEItems.SKY_DUST.stack());
        OreDictionary.registerOre("itemQuartzWrench", AEItems.CERTUS_QUARTZ_WRENCH.stack());
        OreDictionary.registerOre("itemQuartzWrench", AEItems.NETHER_QUARTZ_WRENCH.stack());
        OreDictionary.registerOre("itemQuartzKnife", AEItems.CERTUS_QUARTZ_KNIFE.stack());
        OreDictionary.registerOre("itemQuartzKnife", AEItems.NETHER_QUARTZ_KNIFE.stack());
        OreDictionary.registerOre("enderPearl", new ItemStack(Items.ENDER_PEARL));
        registerVanillaDyes();
    }

    @SubscribeEvent
    public static void registerBiomes(RegistryEvent.Register<Biome> event) {
        InitBiomes.register(event);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        InitStorageCells.registerRecipes(event.getRegistry());
        event.getRegistry().register(new MeteoriteCompassBeaconRecipe()
            .setRegistryName(new ResourceLocation(Tags.MOD_ID, "tools/meteorite_compass_beacon")));
        FurnaceRecipeRegistry.register();
    }

    private static void registerVanillaDyes() {
        OreDictionary.registerOre("dyeBlack", new ItemStack(Items.DYE, 1, 0));
        OreDictionary.registerOre("dyeRed", new ItemStack(Items.DYE, 1, 1));
        OreDictionary.registerOre("dyeGreen", new ItemStack(Items.DYE, 1, 2));
        OreDictionary.registerOre("dyeBrown", new ItemStack(Items.DYE, 1, 3));
        OreDictionary.registerOre("dyeBlue", new ItemStack(Items.DYE, 1, 4));
        OreDictionary.registerOre("dyePurple", new ItemStack(Items.DYE, 1, 5));
        OreDictionary.registerOre("dyeCyan", new ItemStack(Items.DYE, 1, 6));
        OreDictionary.registerOre("dyeLightGray", new ItemStack(Items.DYE, 1, 7));
        OreDictionary.registerOre("dyeGray", new ItemStack(Items.DYE, 1, 8));
        OreDictionary.registerOre("dyePink", new ItemStack(Items.DYE, 1, 9));
        OreDictionary.registerOre("dyeLime", new ItemStack(Items.DYE, 1, 10));
        OreDictionary.registerOre("dyeYellow", new ItemStack(Items.DYE, 1, 11));
        OreDictionary.registerOre("dyeLightBlue", new ItemStack(Items.DYE, 1, 12));
        OreDictionary.registerOre("dyeMagenta", new ItemStack(Items.DYE, 1, 13));
        OreDictionary.registerOre("dyeOrange", new ItemStack(Items.DYE, 1, 14));
        OreDictionary.registerOre("dyeWhite", new ItemStack(Items.DYE, 1, 15));
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        InitModelRegistration.registerModels(event);
    }
}
