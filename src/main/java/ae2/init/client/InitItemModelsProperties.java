package ae2.init.client;

import ae2.core.definitions.AEItems;
import ae2.items.tools.powered.ColorApplicatorItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;

import java.util.Objects;

public final class InitItemModelsProperties {
    private InitItemModelsProperties() {
    }

    public static void init() {
        Item colorApplicator = Objects.requireNonNull(AEItems.COLOR_APPLICATOR.asItem());
        ModelResourceLocation plainModel = new ModelResourceLocation("ae2:color_applicator", "inventory");
        ModelResourceLocation coloredModel = new ModelResourceLocation("ae2:color_applicator_colored", "inventory");

        ModelLoader.registerItemVariants(colorApplicator, plainModel, coloredModel);
        ModelLoader.setCustomMeshDefinition(colorApplicator, stack -> {
            ColorApplicatorItem colorApplicatorItem = AEItems.COLOR_APPLICATOR.get();
            return colorApplicatorItem.getActiveColor(stack) != null
                ? coloredModel
                : plainModel;
        });
    }
}
