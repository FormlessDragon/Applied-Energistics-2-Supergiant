package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.config.CondenserOutput;
import ae2.api.config.Settings;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorageChangeListener;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.items.storage.VoidCellItem;
import ae2.me.cells.VoidCellInventory;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CondenserCommitTest {
    private static Object previousSide;
    private static int REQUIRED;

    @BeforeAll
    static void setup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        REQUIRED = CondenserOutput.MATTER_BALLS.requiredPower;
        assertTrue(REQUIRED > 0);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    private static TestTile tile() {
        var tile = new TestTile();
        ((AppEngInternalInventory) tile.getOutputSlot()).setEnableClientEvents(true);
        tile.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS);
        return tile;
    }

    @Test
    void tilePublishesAfterDebitAndRecoversFromNotificationFailure() {
        var tile = tile();
        tile.onChange = () -> {
            assertEquals(0, tile.getStoredPower());
            tile.addPower(0); // Original callback reentry must see the debit, never the old budget.
        };
        tile.addPower(REQUIRED);
        assertEquals(1, tile.getOutputSlot().getStackInSlot(0).getCount());
        tile.onChange = () -> {
            throw new IllegalStateException("notification after mutation");
        };
        assertDoesNotThrow(() -> tile.addPower(REQUIRED));
        assertEquals(2, tile.getOutputSlot().getStackInSlot(0).getCount());
        assertEquals(0, tile.getStoredPower());
        tile.onChange = () -> {
        };
        tile.addPower(REQUIRED);
        assertEquals(3, tile.getOutputSlot().getStackInSlot(0).getCount());

        var key = AEItemKey.of(AEItems.MATTER_BALL.item());
        tile.getOutputSlot().setItemDirect(0, key.toStack(63));
        tile.setStoredCondenserPower(3.0 * REQUIRED);
        tile.onChange = () -> fail("native commit must not call a host callback");
        var receipt = tile.commitCondenserOutput(key, 3, REQUIRED);
        assertEquals(1, receipt.confirmed());
        assertEquals(2, receipt.stillHeld());
        assertEquals(0, receipt.unconfirmed());
        assertSame(tile, receipt.sourceGeneration());
        assertSame(tile.getOutputSlot(), receipt.targetGeneration());
        assertEquals(2.0 * REQUIRED, tile.getStoredPower());
        var rejected = tile.commitCondenserOutput(key, 1, REQUIRED);
        assertEquals(0, rejected.confirmed());
        assertEquals(1, rejected.stillHeld());
        assertTrue(rejected.batchId() > receipt.batchId());
        assertEquals(64, tile.getOutputSlot().getStackInSlot(0).getCount());
        tile.onChange = () -> {
        };
        tile.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY);
        var stale = tile.commitCondenserOutput(key, 1, REQUIRED);
        assertEquals(0, stale.confirmed(), "an old mode/cost cannot commit after the config changes");
        assertEquals(2.0 * REQUIRED, tile.getStoredPower());
    }

    @Test
    void fixedSeedPowerAndOutputConservationMatchesAnIndependentModel() {
        var tile = tile();
        var random = new Random(104005L);
        long totalPower = 0, removed = 0;
        for (int i = 0; i < 256; i++) {
            int raw = random.nextInt(4096);
            double budget = Math.min(tile.getStorage(), tile.getStoredPower() + raw);
            int before = tile.getOutputSlot().getStackInSlot(0).getCount();
            int produced = Math.min(64 - before, (int) (budget / REQUIRED));
            tile.addPower(raw);
            totalPower += raw;
            assertEquals(before + produced, tile.getOutputSlot().getStackInSlot(0).getCount());
            assertEquals(budget - (double) produced * REQUIRED, tile.getStoredPower());
            if (i % 3 == 0) removed += tile.getOutputSlot().extractItem(0, random.nextInt(1, 65), false).getCount();
            assertEquals(totalPower, tile.getStoredPower()
                + (removed + tile.getOutputSlot().getStackInSlot(0).getCount()) * (double) REQUIRED);
        }
        double held = tile.getStoredPower();
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            tile.addPower(bad);
            assertEquals(held, tile.getStoredPower());
        }
        // Positive overflow saturates at the declared capacity, rather than erasing a valid stored budget.
        tile.addPower(Double.MAX_VALUE);
        assertTrue(tile.getStoredPower() > 0);
        assertTrue(tile.getStoredPower() <= tile.getStorage());
        tile.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH);
        assertEquals(0, tile.getStoredPower());
    }

    @Test
    void voidCellCallbacksObserveCommittedPowerAndFailuresDoNotEraseOtherReceipts() {
        ItemStack stack = AEItems.VOID_CELL.stack();
        ((VoidCellItem) stack.getItem()).setMode(stack, CondenserOutput.MATTER_BALLS);
        var saves = new AtomicInteger();
        var cell = new VoidCellInventory(stack, saves::incrementAndGet);
        var owner = Thread.currentThread();
        var successfulNotices = new AtomicInteger();
        cell.addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                assertSame(owner, Thread.currentThread());
                return true;
            }

            public void onStackChange(AEKey key, long delta) {
                assertSame(owner, Thread.currentThread());
                assertEquals(0, cell.getStoredCondenserPower());
                throw new IllegalStateException("failing storage observer");
            }

            public void onListUpdate() {
                fail("ordinary delta must not become a full update");
            }
        }, new Object());
        cell.addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey key, long delta) {
                successfulNotices.addAndGet((int) delta);
            }

            public void onListUpdate() {
                fail("ordinary delta must not become a full update");
            }
        }, new Object());
        var source = AEItemKey.of(Items.APPLE);
        assertEquals(REQUIRED, cell.insert(source, REQUIRED, Actionable.MODULATE, null));
        assertEquals(1, successfulNotices.get());
        assertEquals(0, cell.getStoredCondenserPower());
        assertEquals(REQUIRED, cell.insert(source, REQUIRED, Actionable.SIMULATE, null));
        assertEquals(1, successfulNotices.get());
        assertEquals(REQUIRED, cell.insert(source, REQUIRED, Actionable.MODULATE, null));
        var contents = new KeyCounter();
        cell.getAvailableStacks(contents);
        assertEquals(2, contents.get(AEItemKey.of(AEItems.MATTER_BALL.item())));
        assertEquals(2, successfulNotices.get());
        assertTrue(saves.get() >= 2);
        cell.persist();
        NBTTagCompound saved = stack.getTagCompound();
        assertEquals(0, saved.getDouble(VoidCellItem.VOID_CELL_ENERGY));
        assertEquals(2, saved.getTagList("void_cell_stored_stacks", 10).getCompoundTagAt(0).getLong("#"));
    }

    private static final class TestTile extends TileCondenser {
        final Thread owner = Thread.currentThread();
        Runnable onChange = () -> {
        };

        @Override
        public double getStorage() {
            return 1_000_000;
        }

        @Override
        public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
            assertSame(owner, Thread.currentThread());
            onChange.run();
            super.onChangeInventory(inventory, slot);
        }
    }
}
