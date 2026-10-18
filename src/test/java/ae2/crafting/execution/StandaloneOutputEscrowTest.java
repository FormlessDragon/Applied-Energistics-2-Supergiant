package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AEConfig;
import ae2.crafting.CraftingAttemptMetrics;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingPlan;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneOutputEscrowTest {
    private static AEItemKey carrot;
    private static Object previousSide;

    @BeforeAll
    static void setup() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        previousSide = side.get(FMLCommonHandler.instance());
        if (previousSide == null) side.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        carrot = AEItemKey.of(Items.CARROT);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(FMLCommonHandler.instance(), previousSide);
    }

    private static ExecutingCraftingJob job(CraftingCPUCluster cluster, long amount) {
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        var plan = new CraftingPlan(new GenericStack(carrot, amount), 128, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), 0, times, null, List.of(), null,
            new CraftingAttemptMetrics(), null);
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cluster);
        return new ExecutingCraftingJob(plan, ignored -> {
        }, link, null, new KeyCounter(), CraftingJobOptions.DEFAULT);
    }

    private static void invokeReturn(CraftingCpuLogic cpu) throws Exception {
        Method method = CraftingCpuLogic.class.getDeclaredMethod("returnStandaloneOutputToNetwork");
        method.setAccessible(true);
        method.invoke(cpu);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static long getLong(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getLong(target);
    }

    private static Object get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    void partialStandaloneReceiptRetainsOnlyUnacceptedOutputAndRetriesIt() throws Exception {
        var state = new StorageState();
        var cluster = new TestCluster(state);
        var cpu = cluster.craftingLogic;
        var job = job(cluster, 10);
        set(cpu, "job", job);
        cpu.getInventory().insert(carrot, 10, Actionable.MODULATE);
        set(cpu, "pendingStandaloneOutput", 10L);
        state.accepted = 3;
        invokeReturn(cpu);
        assertEquals(1, state.calls);
        assertEquals(7, getLong(cpu, "pendingStandaloneOutput"));
        assertEquals(7, cpu.getInventory().list.get(carrot));
        state.accepted = 7;
        invokeReturn(cpu);
        assertEquals(0, getLong(cpu, "pendingStandaloneOutput"));
        assertEquals(0, cpu.getInventory().list.get(carrot));
        assertEquals(2, state.calls);
    }

    @Test
    void throwingExternalReceiptKeepsEscrowAndDisablesRetryAcrossSaveLoad() throws Exception {
        var state = new StorageState();
        var cluster = new TestCluster(state);
        var cpu = cluster.craftingLogic;
        var job = job(cluster, 10);
        set(cpu, "job", job);
        cpu.getInventory().insert(carrot, 10, Actionable.MODULATE);
        set(cpu, "pendingStandaloneOutput", 10L);
        state.throwing = true;
        assertDoesNotThrow(() -> invokeReturn(cpu));
        assertEquals(1, state.calls);
        assertEquals(10, getLong(cpu, "pendingStandaloneOutput"));
        assertSame(carrot, get(cpu, "unknownStandaloneOutputKey"));
        invokeReturn(cpu);
        assertEquals(1, state.calls, "unknown external receipt must not be retried");
        var saved = new NBTTagCompound();
        cpu.writeToNBT(saved);
        assertTrue(saved.hasKey("unknownStandaloneOutput", 10));
        assertEquals(carrot, Objects.requireNonNull(GenericStack.readTag(saved.getCompoundTag("unknownStandaloneOutput"))).what());
    }

    private static final class TestCluster extends CraftingCPUCluster {
        private final StorageState state;
        private final IGrid grid;

        TestCluster(StorageState state) {
            super(BlockPos.ORIGIN, BlockPos.ORIGIN);
            this.state = state;
            IStorageService storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[]{IStorageService.class}, (p, m, a) -> m.getName().equals("getInventory") ? state.storage : null);
            this.grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (p, m, a) -> m.getName().equals("getStorageService") ? storage : null);
        }

        @Override
        public IGrid getGrid() {
            return grid;
        }

        @Override
        public IActionSource getSrc() {
            return IActionSource.empty();
        }
    }

    private static final class StorageState {
        final MEStorageMonitor storage = (MEStorageMonitor) Proxy.newProxyInstance(MEStorageMonitor.class.getClassLoader(),
            new Class<?>[]{MEStorageMonitor.class}, (p, m, a) -> {
                if (m.getName().equals("insert")) {
                    this.calls++;
                    if (this.throwing) throw new IllegalStateException("receipt unknown");
                    return Math.min(this.accepted, (long) a[1]);
                }
                if (m.getName().equals("getDescription")) return null;
                return 0L;
            });
        long accepted;
        int calls;
        boolean throwing;
    }
}
