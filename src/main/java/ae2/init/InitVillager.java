package ae2.init;

import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.BlockDefinition;
import ae2.core.definitions.ItemDefinition;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;

public final class InitVillager {

    private static final VillagerProfession PROFESSION = new VillagerProfession(
        AppEng.makeId("fluix_researcher").toString(),
        "minecraft:textures/entity/villager/librarian.png",
        "minecraft:textures/entity/zombie_villager/zombie_librarian.png");

    private static boolean initialized;

    private InitVillager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        ForgeRegistries.VILLAGER_PROFESSIONS.register(PROFESSION);
        VillagerCareer career = new VillagerCareer(PROFESSION, "fluix_researcher");

        buyItems(career, 1, AEItems.CERTUS_QUARTZ_CRYSTAL, 3, 4);
        sellItems(career, 1, AEItems.METEORITE_COMPASS, 2, 1);

        sellItems(career, 2, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED, 3, 10);
        sellItems(career, 2, AEItems.SILICON, 5, 8);
        buyItems(career);

        sellItems(career);
        buyItems(career, 3, AEItems.FLUIX_CRYSTAL, 5, 4);

        sellItems(career, 4, AEItems.MATTER_BALL, 5, 8);
        buyItems(career, 4, AEItems.CALCULATION_PROCESSOR_PRESS, 10, 1);
        buyItems(career, 4, AEItems.ENGINEERING_PROCESSOR_PRESS, 10, 1);
        buyItems(career, 4, AEItems.LOGIC_PROCESSOR_PRESS, 10, 1);
        buyItems(career, 4, AEItems.SILICON_PRESS, 10, 1);

        career.addTrade(5, new EntityVillager.ListItemForEmeralds(new ItemStack(Items.SLIME_BALL, 5),
            new EntityVillager.PriceInfo(8, 8)));
        initialized = true;
    }

    private static void sellItems(VillagerCareer career, int level, ItemDefinition<?> soldItem, int numberOfItems,
                                  int maxUses) {
        career.addTrade(level, new EntityVillager.EmeraldForItems(soldItem.asItem(),
            new EntityVillager.PriceInfo(numberOfItems, numberOfItems)));
    }

    private static void sellItems(VillagerCareer career) {
        career.addTrade(3, new EntityVillager.EmeraldForItems(itemOf(AEBlocks.QUARTZ_GLASS),
            new EntityVillager.PriceInfo(2, 2)));
    }

    private static void buyItems(VillagerCareer career, int level, ItemDefinition<?> boughtItem, int emeraldCost,
                                 int numberOfItems) {
        career.addTrade(level, new EntityVillager.ListItemForEmeralds(
            new ItemStack(boughtItem.asItem(), numberOfItems), new EntityVillager.PriceInfo(emeraldCost, emeraldCost)));
    }

    private static void buyItems(VillagerCareer career) {
        career.addTrade(2, new EntityVillager.ListItemForEmeralds(
            new ItemStack(itemOf(AEBlocks.SKY_STONE_BLOCK), 8), new EntityVillager.PriceInfo(5, 5)));
    }

    private static Item itemOf(BlockDefinition<?> definition) {
        ItemBlock itemBlock = definition.item();
        return itemBlock == null ? Items.AIR : itemBlock;
    }
}
