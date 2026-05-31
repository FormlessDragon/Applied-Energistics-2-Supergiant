package ae2.init.client;

import ae2.api.util.AEColor;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.definitions.BlockDefinition;
import ae2.core.definitions.ColoredItemDefinition;
import ae2.core.definitions.ItemDefinition;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;

@SideOnly(Side.CLIENT)
public final class InitModelRegistration {
    private InitModelRegistration() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        registerBlockItems();
        registerItems();
        registerColoredPaintBalls(AEItems.COLORED_PAINT_BALL);
        registerColoredPaintBalls(AEItems.COLORED_LUMEN_PAINT_BALL);
        registerPartItems();
        InitItemModelsProperties.init();
        registerStateMappers();
    }

    private static void registerBlockItems() {
        for (BlockDefinition<?> definition : AEBlocks.all()) {
            ItemBlock item = definition.item();
            if (item != null) {
                registerInventoryModel(item, definition.id());
            }
        }

        ItemBlock meChest = AEBlocks.ME_CHEST.item();
        if (meChest != null) {
            ModelLoader.setCustomModelResourceLocation(meChest, 0,
                new ModelResourceLocation(new ResourceLocation(AEBlocks.ME_CHEST.id().getNamespace(),
                    AEBlocks.ME_CHEST.id().getPath() + "_item"), "inventory"));
        }
    }

    private static void registerItems() {
        for (ItemDefinition<?> definition : AEItems.all()) {
            Item item = definition.item();
            if (item != null && item != AEItems.COLOR_APPLICATOR.item()) {
                registerInventoryModel(item, definition.id());
            }
        }
    }

    private static void registerPartItems() {
        for (ItemDefinition<?> definition : AEParts.all()) {
            Item item = definition.item();
            if (item != null) {
                registerInventoryModel(item, definition.id());
            }
        }

        for (ColoredItemDefinition<?> definition : AEParts.COLORED_PARTS) {
            for (AEColor color : AEColor.values()) {
                Item item = definition.item(color);
                ResourceLocation id = definition.id(color);
                if (item != null && id != null) {
                    registerInventoryModel(item, id);
                }
            }
        }
    }

    private static void registerColoredPaintBalls(ColoredItemDefinition<?> definition) {
        for (AEColor color : AEColor.values()) {
            Item item = definition.item(color);
            ResourceLocation id = definition.id(color);
            if (item != null && id != null) {
                registerInventoryModel(item, id);
            }
        }
    }

    private static void registerStateMappers() {
        ModelLoader.setCustomStateMapper(AEBlocks.SKY_STONE_CHEST.block(), normalStateMapper());
        ModelLoader.setCustomStateMapper(AEBlocks.SMOOTH_SKY_STONE_CHEST.block(), normalStateMapper());
        ModelLoader.setCustomStateMapper(AEBlocks.CRANK.block(), normalStateMapper());
        ModelLoader.setCustomStateMapper(AEBlocks.CABLE_BUS.block(), normalStateMapper());
        ModelLoader.setCustomStateMapper(AEBlocks.MOLECULAR_ASSEMBLER.block(), normalStateMapper());
    }

    private static StateMapperBase normalStateMapper() {
        return new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return new ModelResourceLocation(Objects.requireNonNull(state.getBlock().getRegistryName()), "normal");
            }
        };
    }

    private static void registerInventoryModel(Item item, ResourceLocation id) {
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(id, "inventory"));
    }
}
