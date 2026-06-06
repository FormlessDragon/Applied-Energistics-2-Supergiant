package ae2.integration.modules.igtooltip;

import ae2.api.integrations.igtooltip.BaseClassRegistration;
import ae2.api.integrations.igtooltip.ClientRegistration;
import ae2.api.integrations.igtooltip.CommonRegistration;
import ae2.api.integrations.igtooltip.PartTooltips;
import ae2.api.integrations.igtooltip.TooltipProvider;
import ae2.api.parts.IPart;
import ae2.block.AEBaseTileBlock;
import ae2.block.crafting.CraftingMonitorBlock;
import ae2.block.crafting.PatternProviderBlock;
import ae2.block.misc.ChargerBlock;
import ae2.block.networking.CableBusBlock;
import ae2.block.networking.CrystalResonanceGeneratorBlock;
import ae2.core.AppEng;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.integration.modules.igtooltip.blocks.ChargerDataProvider;
import ae2.integration.modules.igtooltip.blocks.CraftingMonitorDataProvider;
import ae2.integration.modules.igtooltip.blocks.CrystalResonanceGeneratorProvider;
import ae2.integration.modules.igtooltip.blocks.GridNodeStateDataProvider;
import ae2.integration.modules.igtooltip.blocks.PatternProviderDataProvider;
import ae2.integration.modules.igtooltip.blocks.PowerStorageDataProvider;
import ae2.integration.modules.igtooltip.parts.AnnihilationPlaneDataProvider;
import ae2.integration.modules.igtooltip.parts.ChannelDataProvider;
import ae2.integration.modules.igtooltip.parts.GridNodeStateProvider;
import ae2.integration.modules.igtooltip.parts.P2PStateDataProvider;
import ae2.integration.modules.igtooltip.parts.PartHostTooltips;
import ae2.integration.modules.igtooltip.parts.StorageMonitorDataProvider;
import ae2.parts.AEBasePart;
import ae2.parts.automation.AnnihilationPlanePart;
import ae2.parts.networking.IUsedChannelProvider;
import ae2.parts.p2p.P2PTunnelPart;
import ae2.parts.reporting.AbstractMonitorPart;
import ae2.tile.AEBaseTile;
import ae2.tile.crafting.TileCraftingMonitor;
import ae2.tile.crafting.TilePatternProvider;
import ae2.tile.misc.TileCharger;
import ae2.tile.networking.TileCableBus;
import ae2.tile.networking.TileCrystalResonanceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.ServiceLoader;

public final class TooltipProviders implements TooltipProvider {

    public static final ServiceLoader<TooltipProvider> LOADER = ServiceLoader.load(TooltipProvider.class,
        TooltipProviders.class.getClassLoader());
    private static final Logger LOG = LoggerFactory.getLogger(TooltipProviders.class);

    static {
        PartTooltips.addBody(IUsedChannelProvider.class, new ChannelDataProvider());
        PartTooltips.addServerData(IUsedChannelProvider.class, new ChannelDataProvider());
        PartTooltips.addBody(AnnihilationPlanePart.class, new AnnihilationPlaneDataProvider());
        PartTooltips.addServerData(AnnihilationPlanePart.class, new AnnihilationPlaneDataProvider());
        PartTooltips.addBody(IPart.class, new GridNodeStateProvider());
        PartTooltips.addServerData(IPart.class, new GridNodeStateProvider());
        PartTooltips.addBody(AbstractMonitorPart.class, new StorageMonitorDataProvider());
        PartTooltips.addBody(P2PTunnelPart.class, new P2PStateDataProvider());
        PartTooltips.addServerData(P2PTunnelPart.class, new P2PStateDataProvider());
        PartTooltips.addBody(PatternProviderLogicHost.class, new PatternProviderDataProvider());
        PartTooltips.addServerData(PatternProviderLogicHost.class, new PatternProviderDataProvider());
        PartTooltips.addBody(AEBasePart.class, DebugProvider::providePartBody, DEBUG_PRIORITY);
        PartTooltips.addServerData(AEBasePart.class, DebugProvider::providePartData, DEBUG_PRIORITY);
    }

    public static void loadCommon(CommonRegistration registration) {
        var baseClasses = new BaseClassRegistrationImpl();

        for (var provider : TooltipProviders.LOADER) {
            provider.registerCommon(registration);
            provider.registerBlockEntityBaseClasses(baseClasses);
        }

        for (var clazz : baseClasses.getBaseClasses()) {
            LOG.debug("Registering default-data for BE {} and sub-classes", clazz);
            registration.addBlockEntityData(AppEng.makeId("grid_node"), clazz.blockEntity(),
                new GridNodeStateDataProvider());
            registration.addBlockEntityData(AppEng.makeId("power_storage"), clazz.blockEntity(),
                new PowerStorageDataProvider());
            registration.addBlockEntityData(AppEng.makeId("debug"), clazz.blockEntity(),
                DebugProvider::provideBlockEntityData);
        }

        for (var clazz : baseClasses.getPartHostClasses()) {
            LOG.debug("Registering part host provider for {} and sub-classes", clazz);
            registration.addBlockEntityData(
                AppEng.makeId("base_" + clazz.blockEntity().getSimpleName().toLowerCase(Locale.ROOT)),
                clazz.blockEntity(),
                PartHostTooltips::provideServerData);
        }
    }

    public static void loadClient(ClientRegistration registration) {
        var baseClasses = new BaseClassRegistrationImpl();

        for (var provider : TooltipProviders.LOADER) {
            provider.registerClient(registration);
            provider.registerBlockEntityBaseClasses(baseClasses);
        }

        for (var clazz : baseClasses.getBaseClasses()) {
            LOG.debug("Registering default client providers for BE {} and sub-classes", clazz);
            registration.addBlockEntityBody(
                clazz.blockEntity(),
                clazz.block(),
                TooltipIds.POWER_STORAGE,
                new PowerStorageDataProvider());
            registration.addBlockEntityBody(
                clazz.blockEntity(),
                clazz.block(),
                TooltipIds.GRID_NODE_STATE,
                new GridNodeStateDataProvider());
            registration.addBlockEntityBody(
                clazz.blockEntity(),
                clazz.block(),
                TooltipIds.DEBUG,
                DebugProvider::provideBlockEntityBody,
                TooltipProvider.DEBUG_PRIORITY);
        }

        for (var clazz : baseClasses.getPartHostClasses()) {
            LOG.debug("Registering part host body provider for {} and sub-classes", clazz);
            registration.addBlockEntityBody(
                clazz.blockEntity(),
                clazz.block(),
                TooltipIds.PART_TOOLTIP,
                PartHostTooltips::buildTooltip);
        }
    }

    @Override
    public void registerCommon(CommonRegistration registration) {
        registration.addBlockEntityData(
            AppEng.makeId("pattern_provider"),
            TilePatternProvider.class,
            new PatternProviderDataProvider());
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        registration.addBlockEntityBody(
            TileCrystalResonanceGenerator.class,
            CrystalResonanceGeneratorBlock.class,
            TooltipIds.CRYSTAL_RESONANCE_GENERATOR,
            new CrystalResonanceGeneratorProvider());
        registration.addBlockEntityBody(
            TileCharger.class,
            ChargerBlock.class,
            TooltipIds.CHARGER,
            new ChargerDataProvider());
        registration.addBlockEntityBody(
            TileCraftingMonitor.class,
            CraftingMonitorBlock.class,
            TooltipIds.CRAFTING_MONITOR,
            new CraftingMonitorDataProvider());
        registration.addBlockEntityBody(
            TilePatternProvider.class,
            PatternProviderBlock.class,
            TooltipIds.PATTERN_PROVIDER,
            new PatternProviderDataProvider());
    }

    @Override
    public void registerBlockEntityBaseClasses(BaseClassRegistration registration) {
        registration.addBaseBlockEntity(AEBaseTile.class, AEBaseTileBlock.class);
        registration.addPartHost(TileCableBus.class, CableBusBlock.class);
    }
}


