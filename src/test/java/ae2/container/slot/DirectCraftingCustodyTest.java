package ae2.container.slot;

import ae2.api.config.Actionable;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.storage.MEStorage;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.core.AEConfig;
import ae2.me.helpers.BaseActionSource;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectCraftingCustodyTest {
    private static Object previousSide;

    @BeforeAll
    static void setup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        previousSide = f.get(FMLCommonHandler.instance());
        if (previousSide == null) f.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
    }

    @AfterAll
    static void cleanup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        f.set(FMLCommonHandler.instance(), previousSide);
    }

    private static Object plan(Fixture fixture, int count) throws Exception {
        return method(CraftingTermSlot.class, "planBatchCraft", EntityPlayer.class, int.class)
            .invoke(fixture.slot, fixture.player, count);
    }

    private static Object singlePlan(Fixture fixture) throws Exception {
        return method(CraftingTermSlot.class, "planSingleCraft", EntityPlayer.class)
            .invoke(fixture.slot, fixture.player);
    }

    private static void execute(Fixture fixture, Object plan, ItemStack output) throws Exception {
        assertNotNull(plan);
        method(CraftingTermSlot.class, "executePlan", EntityPlayer.class, InternalInventory.class,
            plan.getClass(), ItemStack.class).invoke(fixture.slot, fixture.player, fixture.target, plan, output);
    }

    private static Fixture fixture(AppEngInternalInventory grid) throws Exception {
        var player = player();
        var storage = new ControlledStorage();
        var context = (Context) unsafe().allocateInstance(Context.class);
        context.recipe = new Recipe();
        var slot = new CraftingTermSlot(player, new BaseActionSource(), (amount, mode, multiplier) -> amount,
            storage, grid, grid, context, 0, 0);
        return new Fixture(player, grid, storage, slot, new AppEngInternalInventory(1));
    }

    private static ProbePlayer player() throws Exception {
        var player = (ProbePlayer) unsafe().allocateInstance(ProbePlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.uuid = UUID.randomUUID();
        return player;
    }

    private static Method method(Class<?> type, String name, Class<?>... types) throws Exception {
        var method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Unsafe unsafe() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    @Test
    void partialRequiredExtractionIsRefundedAndPublishesNoCraft() throws Exception {
        var grid = new AppEngInternalInventory(9);
        grid.setItemDirect(0, new ItemStack(Items.APPLE));
        var fixture = fixture(grid);
        fixture.storage.available = 10;
        fixture.storage.actualLimit = 1;
        Object plan = plan(fixture, 3);
        execute(fixture, plan, new ItemStack(Items.DIAMOND, 3));
        assertEquals(1, fixture.storage.extracted);
        assertEquals(1, fixture.storage.inserted, "the partial required receipt is returned to its owner");
        assertEquals(10, fixture.storage.available);
        assertEquals(1, grid.getStackInSlot(0).getCount());
        assertTrue(fixture.target.getStackInSlot(0).isEmpty());
    }

    @Test
    void refusedGridReceiptPreventsCraftedOutput() throws Exception {
        var grid = new AppEngInternalInventory(9) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }
        };
        grid.setItemDirect(0, new ItemStack(Items.APPLE));
        var fixture = fixture(grid);
        execute(fixture, plan(fixture, 1), new ItemStack(Items.DIAMOND));
        assertEquals(1, grid.getStackInSlot(0).getCount());
        assertTrue(fixture.target.getStackInSlot(0).isEmpty());
    }

    @Test
    void networkCallbackChangingGridKeyAbortsAndRefundsWithoutOverwritingNewStack() throws Exception {
        var grid = new AppEngInternalInventory(9);
        grid.setItemDirect(0, new ItemStack(Items.APPLE));
        grid.setItemDirect(1, new ItemStack(Items.CARROT));
        var fixture = fixture(grid);
        fixture.storage.available = 1;
        fixture.storage.callback = () -> grid.setItemDirect(0, new ItemStack(Items.DIAMOND));
        execute(fixture, singlePlan(fixture), new ItemStack(Items.DIAMOND));
        assertEquals(1, fixture.storage.extracted);
        assertEquals(1, fixture.storage.inserted);
        assertSame(Items.DIAMOND, grid.getStackInSlot(0).getItem());
        assertTrue(fixture.target.getStackInSlot(0).isEmpty());
    }

    private record Fixture(ProbePlayer player, AppEngInternalInventory grid, ControlledStorage storage,
                           CraftingTermSlot slot, AppEngInternalInventory target) {
    }

    private static final class ProbePlayer extends EntityPlayerMP {
        UUID uuid;

        ProbePlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return uuid;
        }
    }

    private static final class Context extends ContainerCraftingTerm {
        IRecipe recipe;

        private Context() {
            super(null, null);
        }

        @Override
        public IRecipe getCurrentRecipe() {
            return recipe;
        }

        @Override
        public List<ItemStack> getViewCells() {
            return List.of();
        }
    }

    private static final class Recipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            return inv.getStackInSlot(0).getItem() == Items.APPLE
                && (inv.getStackInSlot(1).isEmpty() || inv.getStackInSlot(1).getItem() == Items.CARROT);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return new ItemStack(Items.DIAMOND);
        }

        @Override
        public ItemStack getRecipeOutput() {
            return new ItemStack(Items.DIAMOND);
        }

        @Override
        public boolean canFit(int width, int height) {
            return true;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            return NonNullList.withSize(9, ItemStack.EMPTY);
        }
    }

    private static final class ControlledStorage implements MEStorage {
        long available, extracted, inserted, actualLimit = Long.MAX_VALUE;
        Runnable callback = () -> {
        };

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long value = Math.min(available, amount);
            if (mode == Actionable.SIMULATE) return value;
            value = Math.min(value, actualLimit);
            available -= value;
            extracted += value;
            callback.run();
            return value;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                available += amount;
                inserted += amount;
            }
            return amount;
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString("controlled");
        }
    }
}
