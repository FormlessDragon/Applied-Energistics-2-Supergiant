package ae2.parts.automation.special;

import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;

final class ExportBusPartModels {
    static final ResourceLocation OFF = AppEng.makeId("part/export_bus_off");
    static final ResourceLocation ON = AppEng.makeId("part/export_bus_on");
    static final ResourceLocation HAS_CHANNEL = AppEng.makeId("part/export_bus_has_channel");

    private ExportBusPartModels() {
    }
}
