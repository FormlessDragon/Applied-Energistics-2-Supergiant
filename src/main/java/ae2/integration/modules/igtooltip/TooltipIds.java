package ae2.integration.modules.igtooltip;

import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;

public final class TooltipIds {
    public static final ResourceLocation DEBUG = makeId("debug");
    public static final ResourceLocation GRID_NODE_STATE = makeId("grid_node_state");
    public static final ResourceLocation POWER_STORAGE = makeId("power_storage");
    public static final ResourceLocation CRAFTING_MONITOR = makeId("crafting_monitor");
    public static final ResourceLocation PATTERN_PROVIDER = makeId("pattern_provider");
    public static final ResourceLocation CHARGER = makeId("charger");
    public static final ResourceLocation CRYSTAL_RESONANCE_GENERATOR = makeId("crystal_resonance_generator");
    public static final ResourceLocation PART_NAME = makeId("part_name");
    public static final ResourceLocation PART_ICON = makeId("part_icon");
    public static final ResourceLocation PART_MOD_NAME = makeId("part_mod_name");
    public static final ResourceLocation PART_TOOLTIP = makeId("part_tooltip");

    private TooltipIds() {
    }

    private static ResourceLocation makeId(String path) {
        return new ResourceLocation(AppEng.MOD_ID, path);
    }
}
