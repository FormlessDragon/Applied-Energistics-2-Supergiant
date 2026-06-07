package ae2.integration.modules.waila;

import ae2.block.AEBaseTileBlock;
import ae2.tile.AEBaseTile;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

@SuppressWarnings("unused")
public final class WailaModule {
    private WailaModule() {
    }

    public static void register(IWailaRegistrar registrar) {
        IWailaDataProvider bodyProvider = new WailaBlockEntityInfoProvider(false, true);
        registrar.registerBodyProvider(bodyProvider, AEBaseTile.class);

        IWailaDataProvider serverDataProvider = new WailaBlockEntityInfoProvider(true, false);
        registrar.registerNBTProvider(serverDataProvider, AEBaseTileBlock.class);
    }
}
