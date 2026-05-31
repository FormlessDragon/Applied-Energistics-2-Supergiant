package ae2.init;

import ae2.api.AECapabilities;
import ae2.api.parts.RegisterPartCapabilitiesEvent;
import ae2.api.parts.RegisterPartCapabilitiesEventInternal;
import ae2.helpers.externalstorage.GenericStackFluidStorage;
import ae2.helpers.externalstorage.GenericStackItemStorage;
import ae2.parts.crafting.PatternProviderPart;
import ae2.parts.encoding.PatternEncodingTerminalPart;
import ae2.parts.misc.InterfacePart;
import ae2.parts.networking.EnergyAcceptorPart;
import ae2.parts.p2p.FEP2PTunnelPart;
import ae2.parts.p2p.FluidP2PTunnelPart;
import ae2.parts.p2p.ItemP2PTunnelPart;
import ae2.tile.networking.TileCableBus;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.Objects;

public final class InitCapabilityProviders {

    private static boolean initialized;

    private InitCapabilityProviders() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        RegisterPartCapabilitiesEvent partEvent = new RegisterPartCapabilitiesEvent();
        partEvent.addHostType(TileCableBus.class);
        partEvent.register(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, part.getLogic().getBlankPatternInv().toItemHandler()),
            PatternEncodingTerminalPart.class);
        partEvent.register(AECapabilities.GENERIC_INTERNAL_INV,
            (part, side) -> ignoreSide(side, part.getLogic().getReturnInv()),
            PatternProviderPart.class);
        partEvent.register(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, new GenericStackItemStorage(part.getLogic().getReturnInv())),
            PatternProviderPart.class);
        partEvent.register(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, new GenericStackFluidStorage(part.getLogic().getReturnInv())),
            PatternProviderPart.class);
        partEvent.register(AECapabilities.GENERIC_INTERNAL_INV,
            (part, side) -> ignoreSide(side, part.getInterfaceLogic().getStorage()),
            InterfacePart.class);
        partEvent.register(AECapabilities.ME_STORAGE,
            (part, side) -> ignoreSide(side, part.getInterfaceLogic().getInventory()),
            InterfacePart.class);
        partEvent.register(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, new GenericStackItemStorage(part.getInterfaceLogic().getStorage())),
            InterfacePart.class);
        partEvent.register(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, new GenericStackFluidStorage(part.getInterfaceLogic().getStorage())),
            InterfacePart.class);
        partEvent.register(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, part.getExposedApi()),
            ItemP2PTunnelPart.class);
        partEvent.register(CapabilityEnergy.ENERGY,
            (part, side) -> ignoreSide(side, part.getExposedApi()),
            FEP2PTunnelPart.class);
        partEvent.register(CapabilityEnergy.ENERGY,
            (part, side) -> ignoreSide(side, part.getEnergyStorage()),
            EnergyAcceptorPart.class);
        partEvent.register(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            (part, side) -> ignoreSide(side, part.getExposedApi()),
            FluidP2PTunnelPart.class);
        MinecraftForge.EVENT_BUS.post(partEvent);
        RegisterPartCapabilitiesEventInternal.register(partEvent);
        initialized = true;
    }

    private static <T> T ignoreSide(EnumFacing side, T value) {
        Objects.hashCode(side);
        return value;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
