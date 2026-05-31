package ae2.init.client;

import ae2.api.util.AEColor;
import ae2.block.networking.CableBusColor;
import ae2.client.render.ColorableBlockEntityBlockColor;
import ae2.client.render.StaticBlockColor;
import ae2.core.definitions.AEBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;

public final class InitBlockColors {
    private InitBlockColors() {
    }

    public static void init() {
        BlockColors blockColors = Minecraft.getMinecraft().getBlockColors();
        blockColors.registerBlockColorHandler(new StaticBlockColor(AEColor.TRANSPARENT),
            AEBlocks.WIRELESS_ACCESS_POINT.block());
        blockColors.registerBlockColorHandler(new CableBusColor(), AEBlocks.CABLE_BUS.block());
        blockColors.registerBlockColorHandler(ColorableBlockEntityBlockColor.INSTANCE, AEBlocks.ME_CHEST.block());
    }
}
