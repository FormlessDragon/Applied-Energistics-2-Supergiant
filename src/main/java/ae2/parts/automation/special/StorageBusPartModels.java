package ae2.parts.automation.special;

import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;

final class StorageBusPartModels {
    static final ResourceLocation OFF = AppEng.makeId("part/storage_bus_off");
    static final ResourceLocation ON = AppEng.makeId("part/storage_bus_on");
    static final ResourceLocation HAS_CHANNEL = AppEng.makeId("part/storage_bus_has_channel");

    private StorageBusPartModels() {
    }
}
