package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.crafting.CraftingAttemptMetrics;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingLinkNexus;
import ae2.crafting.CraftingPlan;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import com.google.common.collect.ImmutableSet;
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
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterReceiptTest {
    private static AEItemKey carrot;
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
        carrot = AEItemKey.of(Items.CARROT);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        f.set(FMLCommonHandler.instance(), previousSide);
    }

    private static ExecutingCraftingJob job(CraftingCPUCluster cluster, ReceiptState state, long amount) {
        var requester = (ICraftingRequester) Proxy.newProxyInstance(ICraftingRequester.class.getClassLoader(),
            new Class<?>[]{ICraftingRequester.class}, (p, m, a) -> {
                if (m.getName().equals("insertCraftedItems")) {
                    state.calls++;
                    if (state.throwing) throw new IllegalStateException("requester receipt unknown");
                    return Math.min(state.accepted, (long) a[2]);
                }
                if (m.getName().equals("getRequestedJobs")) return ImmutableSet.of();
                return null;
            });
        var plan = new CraftingPlan(new GenericStack(carrot, amount), 128, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), 0,
            new Object2LongOpenHashMap<>(), null, List.of(), null, new CraftingAttemptMetrics(), null);
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), false, true), requester);
        link.setNexus(new CraftingLinkNexus(link.getCraftingID()));
        return new ExecutingCraftingJob(plan, ignored -> {
        }, link, null, new KeyCounter(), CraftingJobOptions.DEFAULT);
    }

    private static void invokeRetry(CraftingCpuLogic cpu, ExecutingCraftingJob job) throws Exception {
        var method = CraftingCpuLogic.class.getDeclaredMethod("retryPendingRequesterOutput", ExecutingCraftingJob.class);
        method.setAccessible(true);
        method.invoke(cpu, job);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void partialRequesterReceiptStaysInCpuAndRetriesWithoutDuplication() throws Exception {
        var state = new ReceiptState();
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        var cpu = cluster.craftingLogic;
        var job = job(cluster, state, 10);
        job.waitingFor.insert(carrot, 10, Actionable.MODULATE);
        set(cpu, "job", job);
        state.accepted = 3;
        assertEquals(10, cpu.insert(carrot, 10, Actionable.MODULATE));
        assertEquals(1, state.calls);
        assertEquals(7, job.pendingRequesterOutput);
        assertEquals(7, cpu.getInventory().list.get(carrot));
        assertEquals(7, job.remainingAmount);
        state.accepted = 2;
        invokeRetry(cpu, job);
        assertEquals(2, state.calls);
        assertEquals(5, job.pendingRequesterOutput);
        assertEquals(5, cpu.getInventory().list.get(carrot));
        assertEquals(5, job.remainingAmount);
        state.accepted = 5;
        invokeRetry(cpu, job);
        assertEquals(3, state.calls);
        assertEquals(0, job.pendingRequesterOutput);
        assertEquals(0, cpu.getInventory().list.get(carrot));
        assertFalse(cpu.hasJob());
    }

    @Test
    void requesterExceptionCreatesUnknownEscrowAndStopsRetry() throws Exception {
        var state = new ReceiptState();
        state.throwing = true;
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        var cpu = cluster.craftingLogic;
        var job = job(cluster, state, 10);
        job.waitingFor.insert(carrot, 10, Actionable.MODULATE);
        set(cpu, "job", job);
        assertEquals(10, cpu.insert(carrot, 10, Actionable.MODULATE));
        assertTrue(job.requesterOutputUnknown);
        assertEquals(10, cpu.getInventory().list.get(carrot));
        invokeRetry(cpu, job);
        assertEquals(1, state.calls);
        var saved = new NBTTagCompound();
        cpu.writeToNBT(saved);
        assertTrue(saved.getCompoundTag("job").getBoolean("requesterOutputUnknown"));
        assertEquals(0, saved.getCompoundTag("job").getLong("pendingRequesterOutput"));
    }

    private static final class ReceiptState {
        long accepted;
        int calls;
        boolean throwing;
    }
}
