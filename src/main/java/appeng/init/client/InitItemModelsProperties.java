package appeng.init.client;

import appeng.core.definitions.AEItems;
import appeng.items.storage.ViewCellItem;
import appeng.items.tools.powered.ColorApplicatorItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;

public final class InitItemModelsProperties {
    private InitItemModelsProperties() {
    }

    public static void init() {
        Item colorApplicator = AEItems.COLOR_APPLICATOR.asItem();
        ModelResourceLocation plainModel = new ModelResourceLocation("ae2:color_applicator", "inventory");
        ModelResourceLocation coloredModel = new ModelResourceLocation("ae2:color_applicator_colored", "inventory");

        ModelLoader.registerItemVariants(colorApplicator, plainModel, coloredModel);
        ModelLoader.setCustomMeshDefinition(colorApplicator, stack -> {
            ColorApplicatorItem colorApplicatorItem = AEItems.COLOR_APPLICATOR.get();
            return colorApplicatorItem.getActiveColor(stack) != null
                ? coloredModel
                : plainModel;
        });

        Item viewCell = AEItems.VIEW_CELL.asItem();
        ModelResourceLocation enabledViewCellModel = new ModelResourceLocation("ae2:view_cell", "inventory");
        ModelResourceLocation disabledViewCellModel = new ModelResourceLocation("ae2:view_cell_disabled", "inventory");

        ModelLoader.registerItemVariants(viewCell, enabledViewCellModel, disabledViewCellModel);
        ModelLoader.setCustomMeshDefinition(viewCell, stack -> {
            ViewCellItem viewCellItem = AEItems.VIEW_CELL.get();
            return viewCellItem.isEnabled(stack) ? enabledViewCellModel : disabledViewCellModel;
        });
    }
}
