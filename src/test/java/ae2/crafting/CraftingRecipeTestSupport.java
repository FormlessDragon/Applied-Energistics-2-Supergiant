package ae2.crafting;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import sun.misc.Unsafe;

import java.util.concurrent.Callable;

/**
 * Inert context for real recipe callbacks, shared by the assembler planning tests.
 */
abstract class CraftingRecipeTestSupport extends CraftingPlanningTestSupport {
    private Object previousSimulatedWorld;
    private Object previousSimulatedPlayer;
    private Capability<IFluidHandlerItem> previousFluidCapability;

    @BeforeEach
    void prepareRecipeValidationContext() throws Exception {
        var worldField = CraftingEventSimulation.class.getDeclaredField("simulatedWorld");
        var playerField = CraftingEventSimulation.class.getDeclaredField("simulatedPlayer");
        worldField.setAccessible(true);
        playerField.setAccessible(true);
        previousSimulatedWorld = worldField.get(null);
        previousSimulatedPlayer = playerField.get(null);
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var world = (SimulatedCraftingWorld) unsafe.allocateInstance(SimulatedCraftingWorld.class);
        var provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        worldField.set(null, world);
        playerField.set(null, unsafe.allocateInstance(FakePlayer.class));
        previousFluidCapability = CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
        if (previousFluidCapability == null) {
            var constructor = Capability.class.getDeclaredConstructor(String.class, Capability.IStorage.class, Callable.class);
            constructor.setAccessible(true);
            var storage = new Capability.IStorage<IFluidHandlerItem>() {
                public NBTBase writeNBT(Capability<IFluidHandlerItem> capability, IFluidHandlerItem instance, EnumFacing side) {
                    return new NBTTagCompound();
                }

                public void readNBT(Capability<IFluidHandlerItem> capability, IFluidHandlerItem instance, EnumFacing side,
                                    NBTBase data) {
                }
            };
            @SuppressWarnings("unchecked")
            var capability = (Capability<IFluidHandlerItem>) constructor.newInstance(IFluidHandlerItem.class.getName(),
                storage, (Callable<IFluidHandlerItem>) () -> new FluidHandlerItemStack(new ItemStack(Items.BUCKET), 1000));
            CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY = capability;
        }
    }

    @AfterEach
    void restoreRecipeValidationContext() throws Exception {
        var worldField = CraftingEventSimulation.class.getDeclaredField("simulatedWorld");
        var playerField = CraftingEventSimulation.class.getDeclaredField("simulatedPlayer");
        worldField.setAccessible(true);
        playerField.setAccessible(true);
        worldField.set(null, previousSimulatedWorld);
        playerField.set(null, previousSimulatedPlayer);
        CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY = previousFluidCapability;
    }
}
