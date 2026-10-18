package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.tile.crafting.TileCraftingUnit;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingInventoryReturnTest {
    private static Object previousSide;
    private static AEItemKey carrot, apple;

    @BeforeAll
    static void setup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (_, method, _) -> method.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        carrot = AEItemKey.of(Items.CARROT);
        apple = AEItemKey.of(Items.APPLE);
    }

    @AfterAll
    static void restoreSide() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    private static NBTTagCompound unknownSchema() {
        var tag = new NBTTagCompound();
        tag.setInteger("schema", 37);
        tag.setString("original", "do not reinterpret");
        return tag;
    }

    @Test
    void fixedSeedPartialReceiptsPreserveEveryKeyAndCallbackAdditions() {
        var fixture = new Fixture();
        var random = new Random(53113L);
        var totals = new HashMap<AEKey, Long>();
        fixture.put(apple, 0);
        for (int i = 0; i < 48; i++) {
            var stack = new ItemStack(Items.CARROT);
            var tag = new NBTTagCompound();
            tag.setInteger("return-key", i);
            stack.setTagCompound(tag);
            AEKey key = AEItemKey.of(stack);
            long amount = random.nextLong(1, 100);
            totals.put(key, amount);
            fixture.logic.getInventory().insert(key, amount, Actionable.MODULATE);
        }
        var added = new HashSet<AEKey>();
        var expanded = new boolean[1];
        fixture.handler = (key, amount) -> {
            if (added.add(key)) {
                fixture.logic.getInventory().insert(key, 2, Actionable.MODULATE);
                totals.merge(key, 2L, Long::sum);
            }
            if (!expanded[0]) {
                expanded[0] = true;
                for (int i = 100; i < 228; i++) {
                    var stack = new ItemStack(Items.CARROT);
                    var tag = new NBTTagCompound();
                    tag.setInteger("return-key", i);
                    stack.setTagCompound(tag);
                    AEKey extra = AEItemKey.of(stack);
                    fixture.put(extra, 1);
                    totals.put(extra, 1L);
                }
            }
            return fixture.accept(key, Math.min(amount, 7));
        };
        for (int pass = 0; pass < 18; pass++) {
            fixture.logic.storeItems();
            for (var entry : totals.entrySet())
                assertEquals(entry.getValue().longValue(),
                    fixture.network.getOrDefault(entry.getKey(), 0L) + fixture.logic.getInventory().list.get(entry.getKey()));
        }
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertEquals(totals, fixture.network);
        assertFalse(fixture.logic.hasUnconfirmedInventoryReturn());
        assertFalse(fixture.save().hasKey("inventoryReturn"));
    }

    @Test
    void returnCallbackCannotRepeatTheTransferOrReplaceTheInventory() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.handler = (key, amount) -> {
            assertSame(Thread.currentThread(), fixture.owner);
            fixture.logic.storeItems();
            fixture.logic.tickCraftingLogic(null, null);
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY,
                fixture.logic.tryMergeJob(fixture.cluster.grid, fixture.plan, IActionSource.empty()).errorCode());
            assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(new NBTTagCompound()));
            assertEquals(5, fixture.logic.getInventory().list.get(carrot));
            return fixture.accept(key, amount);
        };
        fixture.logic.storeItems();
        assertEquals(1, fixture.calls);
        assertEquals(5, fixture.network.get(carrot));
        assertTrue(fixture.logic.getInventory().list.isEmpty());
    }

    @Test
    void exceptionAfterPartialAcceptanceStopsReplayAndPersistsTheOriginalIntent() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.handler = (key, _) -> {
            fixture.accept(key, 2);
            throw new IllegalStateException("accepted an unknown prefix");
        };
        fixture.logic.storeItems();
        assertTrue(fixture.cluster.isBusy());
        assertTrue(fixture.logic.isInventoryUnconfirmed(carrot));
        assertEquals(2, fixture.network.get(carrot));
        assertEquals(5, fixture.logic.getInventory().list.get(carrot), "unconfirmed alias is never a refund");
        var saved = fixture.save();
        var intent = saved.getCompoundTag("inventoryReturn");
        assertEquals(1, intent.getInteger("schema"));
        assertTrue(intent.hasUniqueId("batchId"));
        assertEquals(new GenericStack(carrot, 5), GenericStack.readTag(intent.getCompoundTag("requested")));
        fixture.logic.storeItems();
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(1, fixture.calls);
        assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(new NBTTagCompound()));
        var restored = new Fixture();
        restored.logic.readFromNBT(saved);
        restored.logic.storeItems();
        restored.logic.tickCraftingLogic(null, null);
        assertEquals(0, restored.calls);
        assertTrue(restored.cluster.isBusy());
        assertEquals(intent, restored.save().getTag("inventoryReturn"));
        saved.getCompoundTag("inventoryReturn").setString("altered", "external mutation");
        assertFalse(restored.save().getCompoundTag("inventoryReturn").hasKey("altered"));
    }

    @Test
    void invalidReceiptsKeepTheCounterAndDisableAutomaticReturn() {
        for (long invalid : new long[]{-1, 6, Long.MAX_VALUE}) {
            var fixture = new Fixture();
            fixture.put(carrot, 5);
            fixture.handler = (_, _) -> invalid;
            assertDoesNotThrow(fixture.logic::storeItems);
            assertEquals(5, fixture.logic.getInventory().list.get(carrot));
            assertTrue(fixture.logic.hasUnconfirmedInventoryReturn());
            fixture.logic.storeItems();
            assertEquals(1, fixture.calls);
        }
    }

    @Test
    void fatalExternalFailurePropagatesWithTheIntentOwnedAndTheGuardReleased() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.handler = (_, _) -> {
            throw new OutOfMemoryError("fatal return fixture");
        };
        assertThrows(OutOfMemoryError.class, fixture.logic::storeItems);
        assertTrue(fixture.logic.hasUnconfirmedInventoryReturn());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        var saved = fixture.save();
        assertDoesNotThrow(() -> fixture.logic.readFromNBT(saved), "the transient return guard must release");
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(1, fixture.calls);
    }

    @Test
    void saveInsideTheExternalCallRestoresAsUnknownEvenWhenTheOriginalLaterSucceeds() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        var inFlight = new NBTTagCompound[1];
        fixture.handler = (key, amount) -> {
            inFlight[0] = fixture.save();
            return fixture.accept(key, amount);
        };
        fixture.logic.storeItems();
        assertFalse(fixture.save().hasKey("inventoryReturn"));
        var restored = new Fixture();
        restored.logic.readFromNBT(inFlight[0]);
        restored.logic.storeItems();
        assertEquals(0, restored.calls);
        assertTrue(restored.logic.hasUnconfirmedInventoryReturn());
        assertEquals(5, restored.logic.getInventory().list.get(carrot));
    }

    @Test
    void fatalPostReceiptNotificationDoesNotReclassifyOrReplayTheConfirmedTransfer() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        Consumer<AEKey> listener = _ -> {
            throw new OutOfMemoryError("post-receipt fixture");
        };
        fixture.logic.addListener(listener);
        assertThrows(OutOfMemoryError.class, fixture.logic::storeItems);
        assertFalse(fixture.logic.hasUnconfirmedInventoryReturn());
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertEquals(5, fixture.network.get(carrot));
        assertFalse(fixture.save().hasKey("inventoryReturn"));
        fixture.logic.removeListener(listener);
        fixture.logic.storeItems();
        assertEquals(1, fixture.calls);
    }

    @Test
    void gridReplacementStopsTheOldBatchAfterAccountingForItsKnownReceipt() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.put(apple, 7);
        var replacement = fixture.newGrid();
        fixture.handler = (key, amount) -> {
            fixture.cluster.grid = replacement;
            return fixture.accept(key, amount);
        };
        fixture.logic.storeItems();
        assertEquals(1, fixture.calls);
        assertFalse(fixture.logic.hasUnconfirmedInventoryReturn());
        fixture.logic.storeItems();
        assertEquals(2, fixture.calls);
        assertEquals(Map.of(carrot, 5L, apple, 7L), fixture.network);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
    }

    @Test
    void destructionDuringTheExternalCallCannotPublishAReceiptIntoTheOldOwner() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.handler = (key, amount) -> {
            fixture.cluster.destroy();
            return fixture.accept(key, amount);
        };
        fixture.logic.storeItems();
        assertTrue(fixture.cluster.isDestroyed());
        assertTrue(fixture.logic.hasUnconfirmedInventoryReturn());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        fixture.logic.storeItems();
        assertEquals(1, fixture.calls);
    }

    @Test
    void unknownSchemaAndMalformedIntentArePreservedWithoutInventingAnEmptyBaseline() {
        for (var payload : List.of(new NBTTagString("unreadable original"), unknownSchema())) {
            var fixture = new Fixture();
            fixture.put(carrot, 5);
            var saved = fixture.save();
            saved.setTag("inventoryReturn", payload.copy());
            fixture.logic.readFromNBT(saved);
            fixture.logic.storeItems();
            assertEquals(0, fixture.calls);
            assertTrue(fixture.logic.isInventoryUnconfirmed(carrot));
            assertEquals(payload, fixture.save().getTag("inventoryReturn"));
            assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(new NBTTagCompound()));
        }
    }

    @Test
    void diagnosticRecoveryIsIndependentAndNeverRedeemsAnUnconfirmedResource() {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.put(apple, 7);
        fixture.handler = (_, _) -> {
            throw new IllegalStateException("unknown receipt");
        };
        fixture.logic.storeItems();
        var saved = fixture.save();
        var key = GenericStack.readTag(saved.getCompoundTag("inventoryReturn").getCompoundTag("requested")).what();
        assertTrue(fixture.logic.isInventoryUnconfirmed(key));
        AEKey other = key.equals(carrot) ? apple : carrot;
        assertFalse(fixture.logic.isInventoryUnconfirmed(other));
        var first = fixture.logic.getInventoryRecoveryItem();
        var second = fixture.logic.getInventoryRecoveryItem();
        assertEquals(AEItems.MISSING_CONTENT.item(), first.getItem());
        assertNull(GenericResourcePackageItem.unwrap(first));
        assertEquals(saved, first.getTagCompound().getCompoundTag("cpu_inventory_recovery"));
        first.getTagCompound().getCompoundTag("cpu_inventory_recovery").setString("altered", "snapshot");
        assertFalse(second.getTagCompound().getCompoundTag("cpu_inventory_recovery").hasKey("altered"));
        assertTrue(fixture.logic.hasUnconfirmedInventoryReturn());
    }

    @Test
    void aHostWithoutAWorldCannotDiscardAnUnconfirmedInventory() throws Exception {
        var fixture = new Fixture();
        fixture.put(carrot, 5);
        fixture.handler = (_, _) -> {
            throw new IllegalStateException("unknown receipt");
        };
        fixture.logic.storeItems();
        var tile = new TileCraftingUnit();
        var cluster = TileCraftingUnit.class.getDeclaredField("cluster");
        cluster.setAccessible(true);
        cluster.set(tile, fixture.cluster);
        tile.cancelJobAndDropContents();
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        assertFalse(fixture.cluster.isDestroyed());
    }

    @FunctionalInterface
    private interface Handler {
        long insert(AEKey key, long amount);
    }

    private static final class Fixture {
        final Thread owner = Thread.currentThread();
        final Map<AEKey, Long> network = new HashMap<>();
        final IStorageService storage;
        final TestCluster cluster;
        final CraftingCpuLogic logic;
        final ICraftingPlan plan;
        int calls;
        Handler handler = this::accept;

        Fixture() {
            var inventory = (MEStorageMonitor) Proxy.newProxyInstance(MEStorageMonitor.class.getClassLoader(),
                new Class<?>[]{MEStorageMonitor.class}, (_, method, args) -> {
                    if (method.getName().equals("insert")) {
                        calls++;
                        return handler.insert((AEKey) args[0], (long) args[1]);
                    }
                    return null;
                });
            storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[]{IStorageService.class}, (_, method, _) -> method.getName().equals("getInventory")
                    ? inventory : null);
            cluster = new TestCluster(newGrid());
            logic = cluster.craftingLogic;
            var empty = new KeyCounter();
            var patterns = new Object2LongOpenHashMap<IPatternDetails>();
            plan = (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class}, (_, method, _) -> switch (method.getName()) {
                    case "finalOutput" -> new GenericStack(carrot, 1);
                    case "bytes" -> 1L;
                    case "usedItems", "missingItems", "emittedItems" -> empty;
                    case "patternTimes" -> patterns;
                    case "intermediateFinalOutputAmount" -> 0L;
                    case "simulation", "multiplePaths" -> false;
                    default -> null;
                });
        }

        IGrid newGrid() {
            return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (_, method, _) -> method.getName().equals("getStorageService") ? storage : null);
        }

        long accept(AEKey key, long amount) {
            network.merge(key, amount, Long::sum);
            return amount;
        }

        void put(AEKey key, long amount) {
            logic.getInventory().insert(key, amount, Actionable.MODULATE);
        }

        NBTTagCompound save() {
            var data = new NBTTagCompound();
            logic.writeToNBT(data);
            return data;
        }

        ICraftingSubmitResult submit() {
            return logic.trySubmitJob(cluster.grid, plan, IActionSource.empty(), null);
        }
    }

    private static final class TestCluster extends CraftingCPUCluster {
        IGrid grid;

        TestCluster(IGrid grid) {
            super(BlockPos.ORIGIN, BlockPos.ORIGIN);
            this.grid = grid;
        }

        @Override
        public IGrid getGrid() {
            return grid;
        }

        @Override
        public IActionSource getSrc() {
            return IActionSource.empty();
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public long getAvailableStorage() {
            return 1024;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public void updateOutput(GenericStack output) {
        }
    }
}
