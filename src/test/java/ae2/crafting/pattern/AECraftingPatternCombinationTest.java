package ae2.crafting.pattern;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.container.ContainerConstants;
import ae2.core.AEConfig;
import ae2.crafting.CraftingEventSimulation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AECraftingPatternCombinationTest {
    private static CoupledRecipe recipe;
    private static HookItem hookItem;
    private static DynamicRecipe dynamicRecipe;
    private static Object previousSide;

    @BeforeAll
    static void bootstrap() throws Exception {
        var sidedDelegate = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        sidedDelegate.setAccessible(true);
        previousSide = sidedDelegate.get(FMLCommonHandler.instance());
        if (previousSide == null) {
            sidedDelegate.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
                (_, method, _) -> method.getName().equals("getSide") ? Side.SERVER : null));
        }
        Bootstrap.register();
        AEConfig.init();
        recipe = new CoupledRecipe();
        recipe.setRegistryName("ae2_audit", "coupled_substitutions");
        ForgeRegistries.RECIPES.register(recipe);
        hookItem = new HookItem();
        hookItem.setRegistryName("ae2_audit", "crafting_hook_result");
        ForgeRegistries.ITEMS.register(hookItem);
        dynamicRecipe = new DynamicRecipe();
        dynamicRecipe.setRegistryName("ae2_audit", "dynamic_crafting_hooks");
        ForgeRegistries.RECIPES.register(dynamicRecipe);
    }

    @AfterAll
    static void restoreSide() throws Exception {
        var sidedDelegate = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        sidedDelegate.setAccessible(true);
        sidedDelegate.set(FMLCommonHandler.instance(), previousSide);
    }

    private static AECraftingPattern pattern() {
        return pattern(true);
    }

    private static AECraftingPattern pattern(boolean allowSubstitutes) {
        var encoded = new ItemStack(Items.PAPER);
        var slots = new ItemStack[9];
        slots[0] = new ItemStack(Items.APPLE);
        slots[1] = new ItemStack(Items.POTATO);
        for (int i = 2; i < slots.length; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), allowSubstitutes, false);
        return new AECraftingPattern(AEItemKey.of(encoded), null);
    }

    private static InventoryCrafting inventory() {
        return new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
    }

    @Test
    void assembleRejectsInvalidCombinationAfterOneFullValidation() {
        var pattern = pattern();
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.BAKED_POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertTrue(pattern.assemble(actual, null).isEmpty());
        assertEquals(1, recipe.matchCalls);
        assertEquals(0, recipe.resultCalls);
    }

    @Test
    void assembleKeepsValidCombinationOutput() {
        var pattern = pattern();
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertEquals(Items.CARROT, pattern.assemble(actual, null).getItem());
        assertEquals(1, recipe.matchCalls);
        assertEquals(1, recipe.resultCalls);
    }

    @Test
    void assembleRespectsDisabledSubstitution() {
        var pattern = pattern(false);
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertTrue(pattern.assemble(actual, null).isEmpty());
        assertEquals(0, recipe.matchCalls);
        assertEquals(0, recipe.resultCalls);
    }

    private static AECraftingPattern pattern(IRecipe recipe, InventoryCrafting template, boolean fluids) {
        var encoded = new ItemStack(Items.PAPER);
        var inputs = new ItemStack[9];
        for (int i = 0; i < inputs.length; i++) inputs[i] = template.getStackInSlot(i);
        AECraftingPattern.encode(encoded, recipe, inputs, recipe.getCraftingResult(template), true, fluids);
        return new AECraftingPattern(AEItemKey.of(encoded), null);
    }

    @Test
    void shapedMirrorsAndShapelessReorderingFollowTheRecipeIncludingEmptySlots() {
        var ingredients = NonNullList.from(Ingredient.EMPTY,
            Ingredient.fromItems(Items.APPLE), Ingredient.fromItems(Items.POTATO));
        var shaped = new ShapedRecipes("", 2, 1, ingredients, new ItemStack(Items.CARROT));
        shaped.setRegistryName("ae2_audit", "shaped_layout");
        ForgeRegistries.RECIPES.register(shaped);
        var shapeless = new ShapelessRecipes("", new ItemStack(Items.CARROT), ingredients);
        shapeless.setRegistryName("ae2_audit", "shapeless_layout");
        ForgeRegistries.RECIPES.register(shapeless);
        var template = inventory();
        template.setInventorySlotContents(0, new ItemStack(Items.APPLE));
        template.setInventorySlotContents(1, new ItemStack(Items.POTATO));
        var shapedPattern = pattern(shaped, template, false);
        var shapelessPattern = pattern(shapeless, template, false);

        var mirror = inventory();
        mirror.setInventorySlotContents(4, new ItemStack(Items.POTATO));
        mirror.setInventorySlotContents(5, new ItemStack(Items.APPLE));
        assertTrue(shaped.matches(mirror, null));
        assertEquals(Items.CARROT, shapedPattern.assemble(mirror, null).getItem());
        mirror.setInventorySlotContents(0, new ItemStack(Items.STICK));
        assertFalse(shaped.matches(mirror, null));
        assertTrue(shapedPattern.assemble(mirror, null).isEmpty());

        var scattered = inventory();
        scattered.setInventorySlotContents(8, new ItemStack(Items.APPLE));
        scattered.setInventorySlotContents(0, new ItemStack(Items.POTATO));
        assertTrue(shapeless.matches(scattered, null));
        assertEquals(Items.CARROT, shapelessPattern.assemble(scattered, null).getItem());
        scattered.setInventorySlotContents(4, new ItemStack(Items.STICK));
        assertFalse(shapeless.matches(scattered, null));
        assertTrue(shapelessPattern.assemble(scattered, null).isEmpty());
    }

    @Test
    void fluidSubstitutionMatchesTheBucketRecipeWithoutCreatingAnExtraBucket() throws Exception {
        var previousCapability = CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
        var constructor = Capability.class.getDeclaredConstructor(String.class, Capability.IStorage.class, Callable.class);
        constructor.setAccessible(true);
        var storage = new Capability.IStorage<IFluidHandlerItem>() {
            public NBTBase writeNBT(Capability<IFluidHandlerItem> capability, IFluidHandlerItem instance, EnumFacing side) {
                return new NBTTagCompound();
            }

            public void readNBT(Capability<IFluidHandlerItem> capability, IFluidHandlerItem instance, EnumFacing side, NBTBase nbt) {
            }
        };
        @SuppressWarnings("unchecked")
        var capability = (Capability<IFluidHandlerItem>) constructor.newInstance(IFluidHandlerItem.class.getName(),
            storage, (Callable<IFluidHandlerItem>) () -> new FluidHandlerItemStack(new ItemStack(Items.BUCKET), 1000));
        CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY = capability;
        try {
            var bucketRecipe = new ShapedRecipes("", 1, 1,
                NonNullList.from(Ingredient.EMPTY, Ingredient.fromItems(Items.WATER_BUCKET)),
                new ItemStack(Items.CARROT));
            bucketRecipe.setRegistryName("ae2_audit", "fluid_substitution");
            ForgeRegistries.RECIPES.register(bucketRecipe);
            var buckets = inventory();
            buckets.setInventorySlotContents(0, new ItemStack(Items.WATER_BUCKET));
            var pattern = pattern(bucketRecipe, buckets, true);
            var fluid = pattern.getValidFluid(0);
            assertNotNull(fluid);
            assertEquals(new GenericStack(AEFluidKey.of(FluidRegistry.WATER), 1000), fluid);
            var actual = inventory();
            actual.setInventorySlotContents(0, GenericStack.wrapInItemStack(fluid));
            assertEquals(pattern.assemble(buckets, null).getItem(), pattern.assemble(actual, null).getItem());
            assertEquals(Items.BUCKET, pattern.getRemainingItems(buckets).get(0).getItem());
            assertTrue(pattern.getRemainingItems(actual).get(0).isEmpty());
            assertEquals(fluid, GenericStack.unwrapItemStack(actual.getStackInSlot(0)));
            actual.setInventorySlotContents(0, GenericStack.wrapInItemStack(fluid.what(), fluid.amount() - 1));
            assertTrue(pattern.assemble(actual, null).isEmpty());
        } finally {
            CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY = previousCapability;
        }
    }

    @Test
    void dynamicResultAppliesItemAndCraftingEventHooksOnceWithoutChangingInput() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var cachedWorld = CraftingEventSimulation.class.getDeclaredField("simulatedWorld");
        var cachedPlayer = CraftingEventSimulation.class.getDeclaredField("simulatedPlayer");
        cachedWorld.setAccessible(true);
        cachedPlayer.setAccessible(true);
        Object previousWorld = cachedWorld.get(null);
        Object previousPlayer = cachedPlayer.get(null);
        var simulated = (World) unsafe.allocateInstance(Class.forName("ae2.crafting.SimulatedCraftingWorld"));
        var provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(simulated, new WorldProviderSurface());
        var world = (WorldServer) unsafe.allocateInstance(WorldServer.class);
        var player = (FakePlayer) unsafe.allocateInstance(FakePlayer.class);
        cachedWorld.set(null, simulated);
        cachedPlayer.set(null, player);
        var listener = new CraftingListener();
        var busIdField = EventBus.class.getDeclaredField("busID");
        busIdField.setAccessible(true);
        @SuppressWarnings("deprecation")
        int busId = busIdField.getInt(FMLCommonHandler.instance().bus());
        // Plain JUnit does not run Forge's event transformer, which supplies the no-arg constructor used by register.
        var listeners = new PlayerEvent.ItemCraftedEvent(player, ItemStack.EMPTY, inventory()).getListenerList();
        IEventListener callback = event -> {
            if (event instanceof PlayerEvent.ItemCraftedEvent crafted) {
                listener.onCraft(crafted);
            }
        };
        listeners.register(busId, EventPriority.NORMAL, callback);
        try {
            var inputs = new ItemStack[9];
            Arrays.fill(inputs, ItemStack.EMPTY);
            inputs[0] = new ItemStack(Items.APPLE);
            var encoded = new ItemStack(Items.PAPER);
            AECraftingPattern.encode(encoded, dynamicRecipe, inputs, new ItemStack(hookItem), true, false);
            var pattern = new AECraftingPattern(AEItemKey.of(encoded), null);
            var actual = inventory();
            actual.setInventorySlotContents(0, new ItemStack(Items.APPLE, 3));
            hookItem.calls = dynamicRecipe.resultCalls = 0;
            var output = pattern.assemble(actual, world);
            assertFalse(output.isEmpty());
            assertEquals(1, dynamicRecipe.resultCalls);
            assertEquals(1, hookItem.calls);
            assertEquals(1, listener.calls);
            assertEquals(3, output.getTagCompound().getInteger("inputCount"));
            assertEquals("created", output.getDisplayName());
            assertTrue(output.getTagCompound().getBoolean("event"));
            assertEquals(3, actual.getStackInSlot(0).getCount());
            assertFalse(actual.getStackInSlot(0).hasTagCompound());
        } finally {
            listeners.unregister(busId, callback);
            cachedWorld.set(null, previousWorld);
            cachedPlayer.set(null, previousPlayer);
        }
    }

    public static final class CraftingListener {
        private int calls;

        public void onCraft(PlayerEvent.ItemCraftedEvent event) {
            if (event.crafting.getItem() == hookItem) {
                calls++;
                event.crafting.getTagCompound().setBoolean("event", true);
            }
        }
    }

    private static final class HookItem extends Item {
        private int calls;

        @Override
        public void onCreated(ItemStack stack, World world, EntityPlayer player) {
            calls++;
            stack.setStackDisplayName("created");
        }
    }

    private static final class DynamicRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private int resultCalls;

        public boolean matches(InventoryCrafting input, World world) {
            return input.getStackInSlot(0).getItem() == Items.APPLE;
        }

        public ItemStack getCraftingResult(InventoryCrafting input) {
            resultCalls++;
            var output = new ItemStack(hookItem);
            var tag = new NBTTagCompound();
            tag.setInteger("inputCount", input.getStackInSlot(0).getCount());
            output.setTagCompound(tag);
            return output;
        }

        public boolean canFit(int width, int height) {
            return true;
        }

        public boolean isDynamic() {
            return true;
        }

        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }
    }

    private static final class CoupledRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IShapedRecipe {
        private int matchCalls;
        private int resultCalls;

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            matchCalls++;
            var first = inv.getStackInSlot(0).getItem();
            var second = inv.getStackInSlot(1).getItem();
            return (first == Items.APPLE || first == Items.GOLDEN_APPLE)
                && (second == Items.POTATO || second == Items.BAKED_POTATO)
                && !(first == Items.GOLDEN_APPLE && second == Items.BAKED_POTATO);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            resultCalls++;
            return new ItemStack(Items.CARROT);
        }

        @Override
        public int getRecipeWidth() {
            return 2;
        }

        @Override
        public int getRecipeHeight() {
            return 1;
        }

        @Override
        public boolean canFit(int width, int height) {
            return width >= 2 && height >= 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return new ItemStack(Items.CARROT);
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.from(Ingredient.EMPTY,
                Ingredient.fromItems(Items.APPLE, Items.GOLDEN_APPLE),
                Ingredient.fromItems(Items.POTATO, Items.BAKED_POTATO));
        }
    }
}
