package ae2.init.client;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.AEColor;
import ae2.client.render.StaticItemColor;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.definitions.ColoredItemDefinition;
import ae2.core.definitions.ItemDefinition;
import ae2.crafting.pattern.EncodedPatternItem;
import ae2.items.misc.PaintBallItem;
import ae2.items.parts.ColoredPartItem;
import ae2.items.parts.PartItem;
import ae2.items.storage.BasicStorageCell;
import ae2.items.tools.MemoryCardItem;
import ae2.items.tools.powered.ColorApplicatorItem;
import ae2.items.tools.powered.PortableCellItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class InitItemColors {
    private InitItemColors() {
    }

    public static void init() {
        ItemColors itemColors = Minecraft.getMinecraft().getItemColors();
        itemColors.registerItemColorHandler(new StaticItemColor(AEColor.TRANSPARENT), AEBlocks.ME_CHEST.asItem());
        itemColors.registerItemColorHandler(MemoryCardItem::getTintColor, AEItems.MEMORY_CARD.asItem());
        itemColors.registerItemColorHandler(InitItemColors::getColorApplicatorColor, AEItems.COLOR_APPLICATOR.asItem());
        itemColors.registerItemColorHandler(InitItemColors::getWrappedGenericStackColor,
            AEItems.WRAPPED_GENERIC_STACK.asItem());
        itemColors.registerItemColorHandler(InitItemColors::getEncodedPatternColor,
            AEItems.CRAFTING_PATTERN.asItem(), AEItems.PROCESSING_PATTERN.asItem());

        itemColors.registerItemColorHandler(PortableCellItem::getColor,
            AEItems.PORTABLE_ITEM_CELL1K.asItem(), AEItems.PORTABLE_FLUID_CELL1K.asItem(),
            AEItems.PORTABLE_ITEM_CELL4K.asItem(), AEItems.PORTABLE_FLUID_CELL4K.asItem(),
            AEItems.PORTABLE_ITEM_CELL16K.asItem(), AEItems.PORTABLE_FLUID_CELL16K.asItem(),
            AEItems.PORTABLE_ITEM_CELL64K.asItem(), AEItems.PORTABLE_FLUID_CELL64K.asItem(),
            AEItems.PORTABLE_ITEM_CELL256K.asItem(), AEItems.PORTABLE_FLUID_CELL256K.asItem());

        itemColors.registerItemColorHandler(BasicStorageCell::getColor,
            AEItems.ITEM_CELL_1K.asItem(), AEItems.FLUID_CELL_1K.asItem(),
            AEItems.ITEM_CELL_4K.asItem(), AEItems.FLUID_CELL_4K.asItem(),
            AEItems.ITEM_CELL_16K.asItem(), AEItems.FLUID_CELL_16K.asItem(),
            AEItems.ITEM_CELL_64K.asItem(), AEItems.FLUID_CELL_64K.asItem(),
            AEItems.ITEM_CELL_256K.asItem(), AEItems.FLUID_CELL_256K.asItem());

        for (ItemDefinition<?> definition : AEItems.all()) {
            Item item = definition.asItem();
            if (item instanceof PaintBallItem) {
                registerPaintBall(itemColors, (PaintBallItem) item);
            }
        }

        registerPartColors(itemColors);
        registerPaintBalls(itemColors, AEItems.COLORED_PAINT_BALL);
        registerPaintBalls(itemColors, AEItems.COLORED_LUMEN_PAINT_BALL);
    }

    private static void registerPartColors(ItemColors itemColors) {
        for (ItemDefinition<?> definition : AEParts.all()) {
            Item item = definition.asItem();
            if (item instanceof PartItem) {
                itemColors.registerItemColorHandler(new StaticItemColor(AEColor.TRANSPARENT), item);
            }
        }

        for (ColoredItemDefinition<?> definition : AEParts.COLORED_PARTS) {
            for (AEColor color : AEColor.values()) {
                Item item = definition.item(color);
                if (item instanceof ColoredPartItem) {
                    itemColors.registerItemColorHandler(new StaticItemColor(color), item);
                }
            }
        }
    }

    private static void registerPaintBalls(ItemColors itemColors, ColoredItemDefinition<PaintBallItem> definition) {
        for (AEColor color : AEColor.VALID_COLORS) {
            PaintBallItem item = definition.item(color);
            if (item != null) {
                registerPaintBall(itemColors, item);
            }
        }
    }

    private static void registerPaintBall(ItemColors itemColors, PaintBallItem item) {
        AEColor color = item.getColor();
        final int colorValue = color.mediumVariant;
        final int r = colorValue >> 16 & 0xff;
        final int g = colorValue >> 8 & 0xff;
        final int b = colorValue & 0xff;

        int renderColor;
        if (item.isLumen()) {
            final float fail = 0.7f;
            final int full = (int) (255 * 0.3);
            renderColor = (int) (full + r * fail) << 16 | (int) (full + g * fail) << 8
                | (int) (full + b * fail) | 0xff << 24;
        } else {
            renderColor = r << 16 | g << 8 | b | 0xff << 24;
        }

        itemColors.registerItemColorHandler(new ConstantItemColor(renderColor), item);
    }

    private static int getColorApplicatorColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) {
            return -1;
        }

        AEColor color = ((ColorApplicatorItem) stack.getItem()).getActiveColor(stack);
        if (color == null) {
            return -1;
        }

        return color.getVariantByTintIndex(tintIndex);
    }

    private static int getWrappedGenericStackColor(ItemStack stack, int tintIndex) {
        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack == null) {
            return -1;
        }

        if (genericStack.what() instanceof AEItemKey itemKey) {
            ItemStack displayStack = itemKey.toStack();
            if (displayStack.getItem() == stack.getItem()) {
                return -1;
            }
            return Minecraft.getMinecraft().getItemColors().colorMultiplier(displayStack, tintIndex);
        }

        if (tintIndex != 0 || !(genericStack.what() instanceof AEFluidKey fluidKey)) {
            return -1;
        }

        return 0xFF000000 | fluidKey.getFluid().getColor(fluidKey.toStack(1));
    }

    private static int getEncodedPatternColor(ItemStack stack, int tintIndex) {
        if (!GuiScreen.isShiftKeyDown() || !(stack.getItem() instanceof EncodedPatternItem<?> encodedPattern)) {
            return -1;
        }

        var level = Minecraft.getMinecraft().world;
        if (level == null) {
            return -1;
        }

        ItemStack output = encodedPattern.getOutput(stack, level);
        if (output.isEmpty() || output.getItem() == stack.getItem()) {
            return -1;
        }

        return Minecraft.getMinecraft().getItemColors().colorMultiplier(output, tintIndex);
    }

    private record ConstantItemColor(int color) implements IItemColor {

        @Override
        public int colorMultiplier(ItemStack stack, int tintIndex) {
            return this.color;
        }
    }
}
