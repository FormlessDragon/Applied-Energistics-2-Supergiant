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
import ae2.crafting.CraftingLink;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.service.CraftingService;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingCpuRestoreTest {
    private static AEItemKey carrot, apple;
    private static Object previousSide;

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

    private static NBTTagCompound image(long stock, boolean hasJob) {
        var data = new NBTTagCompound();
        data.setTag("inventory", GenericStack.writeList(List.of(new GenericStack(carrot, stock))));
        data.setLong("pendingStandaloneOutput", 0);
        if (hasJob) {
            var job = new NBTTagCompound();
            job.setTag("link", CraftingCpuHelper.generateLinkData(UUID.randomUUID(), false, false));
            job.setTag("finalOutput", GenericStack.writeTag(new GenericStack(carrot, 10)));
            job.setLong("remainingAmount", 10);
            job.setLong("totalFinalOutputAmount", 10);
            job.setLong("planBytes", 128);
            job.setTag("waitingFor", GenericStack.writeList(List.of(new GenericStack(apple, 2))));
            job.setTag("pseudoInventory", GenericStack.writeList(List.of(new GenericStack(carrot, 3))));
            job.setTag("tasks", new NBTTagList());
            job.setTag("temporaryPatterns", new NBTTagList());
            job.setTag("timeTracker", new NBTTagCompound());
            data.setTag("job", job);
        }
        return data;
    }

    @Test
    void movingAPreparedCounterRetainsBothCounterIdentitiesWithoutSharingMutableIndexes() {
        var target = new KeyCounter();
        target.add(carrot, 7);
        var prepared = new KeyCounter();
        prepared.add(apple, 13);
        target.moveFrom(prepared);
        assertEquals(13, target.get(apple));
        assertEquals(0, target.get(carrot));
        assertTrue(prepared.isEmpty());
        prepared.add(apple, 2);
        prepared.add(carrot, 5);
        assertEquals(13, target.get(apple));
        assertEquals(0, target.get(carrot));
        target.moveFrom(target);
        assertEquals(13, target.get(apple));
        var saturated = KeyCounter.saturating();
        saturated.add(carrot, Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> target.moveFrom(saturated));
        assertEquals(13, target.get(apple));
        assertEquals(Long.MAX_VALUE, saturated.get(carrot));
    }

    @Test
    void cpuInventoryAndJobWaitingListsBeyond1024SurviveSaveAndReload() {
        var resources = new NBTTagList();
        for (int i = 0; i < 1025; i++) {
            var stack = new ItemStack(Items.APPLE);
            var tag = new NBTTagCompound();
            tag.setInteger("variant", i);
            stack.setTagCompound(tag);
            resources.appendTag(GenericStack.writeTag(new GenericStack(AEItemKey.of(stack), i + 1L)));
        }
        var data = image(7, true);
        data.setTag("inventory", resources);
        data.getCompoundTag("job").setTag("waitingFor", resources.copy());
        var first = new Fixture();
        first.logic.readFromNBT(data);
        var saved = new NBTTagCompound();
        first.logic.writeToNBT(saved);
        var restored = new Fixture();
        restored.logic.readFromNBT(saved);
        assertEquals(1025, restored.logic.getInventory().list.size());
        for (var resource : GenericStack.readList(resources)) {
            assertEquals(resource.amount(), restored.logic.getInventory().list.get(resource.what()));
            assertEquals(resource.amount(), restored.logic.getWaitingFor(resource.what()));
        }
    }

    @Test
    void completeStateIsPublishedBeforeLinkRegistrationAndObserverCallbacks() {
        var fixture = new Fixture();
        var borrowedInventory = fixture.logic.getInventory();
        var borrowedCounter = borrowedInventory.list;
        var image = image(7, true);
        var seen = new HashSet<AEKey>();
        Runnable verify = () -> {
            assertEquals(7, fixture.logic.getInventory().list.get(carrot));
            assertEquals(2, fixture.logic.getWaitingFor(apple));
            assertEquals(3, fixture.logic.getWaitingFor(carrot));
            assertNotNull(fixture.logic.getLastLink());
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
            assertFalse(fixture.logic.canMergeJob(fixture.plan));
            fixture.logic.cancel();
            fixture.logic.storeItems();
            fixture.logic.tickCraftingLogic(null, null);
            assertEquals(0, fixture.logic.insert(apple, 1, Actionable.MODULATE));
            assertEquals(0, fixture.logic.executeCrafting(1, null, null, null));
            assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(new NBTTagCompound()));
        };
        fixture.service.onAdd = verify;
        fixture.logic.addListener(key -> {
            verify.run();
            seen.add(key);
        });
        fixture.logic.readFromNBT(image);
        assertSame(borrowedInventory, fixture.logic.getInventory());
        assertSame(borrowedCounter, fixture.logic.getInventory().list);
        assertEquals(Set.of(carrot, apple), seen);
        assertEquals(1, fixture.service.links);
        assertEquals(0, fixture.inserts);
        assertFalse(fixture.logic.hasUnconfirmedInventory());
    }

    @Test
    void malformedLateJobDataDoesNotPublishInventoryOrRegisterALink() {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
        var bad = image(7, true);
        bad.getCompoundTag("job").setString("tasks", "broken list");
        var original = bad.copy();
        assertThrows(IllegalArgumentException.class, () -> fixture.logic.readFromNBT(bad));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertFalse(fixture.logic.hasJob());
        assertEquals(0, fixture.service.links);
        assertTrue(fixture.cluster.isBusy());
        assertEquals(original, fixture.save().getCompoundTag("cpuReadFailure").getTag("source"));
        bad.setString("altered", "caller still owns the input");
        assertFalse(fixture.save().getCompoundTag("cpuReadFailure").getCompoundTag("source").hasKey("altered"));
    }

    @Test
    void failedImagesRemainOpaqueAcrossRepeatedRestartsInsteadOfGrowingOrBecomingEmpty() {
        var fixture = new Fixture();
        var bad = image(11, true);
        var tasks = new NBTTagList();
        tasks.appendTag(new NBTTagCompound());
        bad.getCompoundTag("job").setTag("tasks", tasks);
        bad.setString("addon-original", "preserve it");
        assertThrows(IllegalArgumentException.class, () -> fixture.logic.readFromNBT(bad));
        var saved = fixture.save();
        assertEquals(bad, saved.getCompoundTag("cpuReadFailure").getTag("source"));
        for (int restart = 0; restart < 3; restart++) {
            var restored = new Fixture();
            restored.logic.readFromNBT(saved);
            restored.logic.tickCraftingLogic(null, null);
            restored.logic.storeItems();
            assertTrue(restored.cluster.isBusy());
            assertEquals(0, restored.service.links);
            assertEquals(0, restored.inserts);
            assertEquals(saved, restored.save());
            var diagnostic = restored.logic.getInventoryRecoveryItem();
            assertEquals(saved, diagnostic.getTagCompound().getCompoundTag("cpu_inventory_recovery"));
            saved = restored.save();
        }
    }

    @Test
    void wrongRootAndResourceTypesRetainExistingStockAndTheOriginalInput() {
        for (int variant = 0; variant < 6; variant++) {
            var fixture = new Fixture();
            fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
            var bad = image(7, true);
            switch (variant) {
                case 0 -> bad.setString("inventory", "wrong type");
                case 1 -> bad.setString("job", "wrong type");
                case 2 -> bad.getCompoundTag("job").setString("waitingFor", "wrong type");
                case 3 -> bad.getCompoundTag("job").removeTag("finalOutput");
                case 4 -> bad.setTag("inventory", GenericStack.writeList(List.of(new GenericStack(carrot, -1))));
                case 5 -> bad.setTag("inventory", GenericStack.writeList(List.of(
                    new GenericStack(carrot, Long.MAX_VALUE), new GenericStack(carrot, 1))));
            }
            assertThrows(IllegalArgumentException.class, () -> fixture.logic.readFromNBT(bad));
            assertEquals(7, fixture.logic.getInventory().list.get(carrot));
            assertEquals(0, fixture.service.links);
            assertEquals(bad, fixture.save().getCompoundTag("cpuReadFailure").getTag("source"));
        }
    }

    @Test
    void aLiveJobCanOnlyReloadTheSameOwnedImageWithoutReplacingItsLink() {
        var fixture = new Fixture();
        fixture.logic.readFromNBT(image(7, true));
        var link = fixture.logic.getLastLink();
        var saved = fixture.save();
        fixture.logic.readFromNBT(saved);
        assertSame(link, fixture.logic.getLastLink());
        assertEquals(1, fixture.service.links);
        var replacement = saved.copy();
        replacement.removeTag("job");
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(replacement));
        assertSame(link, fixture.logic.getLastLink());
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertTrue(fixture.logic.hasUnconfirmedInventory());
    }

    @Test
    void anAlreadyTerminalJobIsRemovedByAnImageWithoutAJobAndReusedOutputTagsAreCleared() {
        var fixture = new Fixture();
        fixture.logic.readFromNBT(image(7, true));
        ((CraftingLink) fixture.logic.getLastLink()).markDone();
        var saved = fixture.save();
        var noJob = saved.copy();
        noJob.removeTag("job");
        fixture.logic.readFromNBT(noJob);
        assertFalse(fixture.logic.hasJob());
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        fixture.logic.writeToNBT(saved);
        assertFalse(saved.hasKey("job"));
        assertFalse(saved.hasKey("cpuReadFailure"));
        assertNull(fixture.cluster.output);
    }

    @Test
    void anImageWithACompletedLinkCannotReceiveOrMergeAndFinishesBeforeAnyFurtherProduction() {
        var fixture = new Fixture();
        var saved = image(7, true);
        saved.getCompoundTag("job").getCompoundTag("link").setBoolean("done", true);
        fixture.logic.readFromNBT(saved);
        var link = fixture.logic.getLastLink();
        assertTrue(link.isDone());
        assertFalse(fixture.logic.canMergeJob(fixture.plan));
        assertEquals(0, fixture.logic.insert(apple, 1, Actionable.MODULATE));
        fixture.logic.tickCraftingLogic(null, null);
        assertFalse(fixture.logic.hasJob());
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertEquals(1, fixture.inserts);
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(1, fixture.inserts);
    }

    @Test
    void partialLinkPublicationFailureRetainsThePublishedResourcesAndStopsAutomaticWork() {
        var fixture = new Fixture();
        var image = image(7, true);
        fixture.service.onAdd = () -> {
            throw new IllegalStateException("partial registration");
        };
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(image));
        assertEquals(1, fixture.service.links);
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertNotNull(fixture.logic.getLastLink());
        assertEquals("link-publication", fixture.save().getCompoundTag("cpuReadFailure").getString("phase"));
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(0, fixture.inserts);
        assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
        var restored = new Fixture();
        var saved = fixture.save();
        restored.logic.readFromNBT(saved);
        assertEquals(saved, restored.save());
        assertEquals(0, restored.service.links);
    }

    @Test
    void fatalLinkPublicationFailurePropagatesWithARestartSafePendingMarker() {
        var fixture = new Fixture();
        fixture.service.onAdd = () -> {
            throw new OutOfMemoryError("fatal link fixture");
        };
        assertThrows(OutOfMemoryError.class, () -> fixture.logic.readFromNBT(image(7, true)));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertTrue(fixture.logic.hasUnconfirmedInventory());
        var saved = fixture.save();
        assertEquals("link-publication", saved.getCompoundTag("cpuReadFailure").getString("phase"));
        var restored = new Fixture();
        restored.logic.readFromNBT(saved);
        assertEquals(saved, restored.save());
        assertEquals(0, restored.service.links);
    }

    @Test
    void fatalDecodeFailureLeavesTheLiveInventoryUntouchedAndReleasesTheReadGuard() {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
        var bad = image(7, true);
        var tasks = new NBTTagList();
        tasks.appendTag(carrot.toTag());
        bad.getCompoundTag("job").setTag("tasks", tasks);
        fixture.cluster.onLevel = () -> {
            throw new OutOfMemoryError("fatal decode fixture");
        };
        assertThrows(OutOfMemoryError.class, () -> fixture.logic.readFromNBT(bad));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertEquals(0, fixture.service.links);
        fixture.cluster.onLevel = null;
        assertDoesNotThrow(() -> fixture.logic.readFromNBT(image(7, false)));
        assertFalse(fixture.logic.hasUnconfirmedInventory());
    }

    @Test
    void resourcesAddedByADecodeCallbackAreNotOverwrittenByThePreparedCounter() {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
        fixture.onServiceLookup = () -> fixture.logic.getInventory().insert(apple, 2, Actionable.MODULATE);
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(image(7, true)));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertEquals(2, fixture.logic.getInventory().list.get(apple));
        assertEquals(0, fixture.service.links);
    }

    @Test
    void aChangedGridCannotReceiveThePreparedJobLink() {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
        fixture.onServiceLookup = () -> fixture.cluster.grid = fixture.newGrid();
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(image(7, true)));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertFalse(fixture.logic.hasJob());
        assertEquals(0, fixture.service.links);
    }

    @Test
    void anUnknownIntentCannotAuthorizeAReplacementInventoryWithTheSameBatchId() {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(carrot, 7, Actionable.MODULATE);
        fixture.failReturn = true;
        fixture.logic.storeItems();
        var saved = fixture.save();
        var changed = saved.copy();
        changed.setTag("inventory", GenericStack.writeList(List.of(new GenericStack(carrot, 1))));
        assertThrows(IllegalStateException.class, () -> fixture.logic.readFromNBT(changed));
        assertEquals(7, fixture.logic.getInventory().list.get(carrot));
        assertEquals(saved.getTag("inventoryReturn"), fixture.save().getTag("inventoryReturn"));
        assertTrue(fixture.cluster.isBusy());
    }

    private static final class Fixture {
        final IStorageService storage;
        final Service service;
        final Cluster cluster;
        final CraftingCpuLogic logic;
        final ICraftingPlan plan;
        Runnable onServiceLookup;
        boolean failReturn;
        int inserts;

        Fixture() {
            var inventory = (MEStorageMonitor) Proxy.newProxyInstance(MEStorageMonitor.class.getClassLoader(),
                new Class<?>[]{MEStorageMonitor.class}, (_, method, args) -> {
                    if (method.getName().equals("insert")) {
                        inserts++;
                        if (failReturn) throw new IllegalStateException("unknown return fixture");
                        return (long) args[1];
                    }
                    return null;
                });
            storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[]{IStorageService.class}, (_, method, _) -> method.getName().equals("getInventory")
                    ? inventory : null);
            var grid = newGrid();
            service = new Service(grid, storage);
            cluster = new Cluster(grid);
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
                (_, method, _) -> switch (method.getName()) {
                    case "getStorageService" -> storage;
                    case "getCraftingService" -> {
                        if (onServiceLookup != null) {
                            var callback = onServiceLookup;
                            onServiceLookup = null;
                            callback.run();
                        }
                        yield this.service;
                    }
                    default -> null;
                });
        }

        NBTTagCompound save() {
            var tag = new NBTTagCompound();
            logic.writeToNBT(tag);
            return tag;
        }

        ICraftingSubmitResult submit() {
            return logic.trySubmitJob(cluster.grid, plan, IActionSource.empty(), null);
        }
    }

    private static final class Service extends CraftingService {
        Runnable onAdd;
        int links;

        Service(IGrid grid, IStorageService storage) {
            super(grid, storage, null);
        }

        @Override
        public void addLink(CraftingLink link) {
            super.addLink(link);
            links++;
            if (onAdd != null) onAdd.run();
        }
    }

    private static final class Cluster extends CraftingCPUCluster {
        IGrid grid;
        Runnable onLevel;
        GenericStack output;

        Cluster(IGrid grid) {
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
        public long getAvailableStorage() {
            return 1024;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public void updateOutput(GenericStack output) {
            this.output = output;
        }

        @Override
        public World getLevel() {
            if (onLevel != null) onLevel.run();
            return null;
        }
    }
}
