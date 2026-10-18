package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.crafting.CraftingAttemptMetrics;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingPlan;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.service.CraftingService;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CraftingDispatchBudgetTest {
    private static AEItemKey apple, carrot;
    private static Object previousSide;

    @BeforeAll
    static void bootstrap() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        previousSide = side.get(FMLCommonHandler.instance());
        if (previousSide == null) side.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        apple = AEItemKey.of(Items.APPLE);
        carrot = AEItemKey.of(Items.CARROT);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(FMLCommonHandler.instance(), previousSide);
    }

    private static Cpu cpu(int size, int count) {
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        return cpu(cluster.craftingLogic, size, count);
    }

    private static Cpu cpu(CraftingCpuLogic logic, int size, int count) {
        var cluster = logic.cluster;
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cluster);
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        for (int i = 0; i < size; i++) times.put(new Pattern(), count);
        var plan = new CraftingPlan(new GenericStack(carrot, (long) size * count), 128, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), 0, times, null, List.of(), null,
            new CraftingAttemptMetrics(), null);
        var job = new ExecutingCraftingJob(plan, key -> {
        }, link, null, new KeyCounter(), CraftingJobOptions.DEFAULT);
        setJob(logic, job);
        logic.getInventory().insert(apple, (long) size * count, Actionable.MODULATE);
        return new Cpu(logic, job);
    }

    private static void setJob(CraftingCpuLogic logic, ExecutingCraftingJob job) {
        try {
            var field = CraftingCpuLogic.class.getDeclaredField("job");
            field.setAccessible(true);
            field.set(logic, job);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void nonPositiveBudgetDoesNotCallGettersOrMutate() {
        var cpu = cpu(3, 1);
        var energy = new Energy();
        for (int budget : new int[]{Integer.MIN_VALUE, -1, 0}) {
            assertEquals(0, cpu.logic.executeCraftingWithProviderLookup(budget, energy.service, null,
                pattern -> {
                    fail("Zero budget must not look up providers");
                    return List.of();
                }));
        }
        assertEquals(3, cpu.logic.getStored(apple));
        assertEquals(0, energy.spent);
        assertEquals(0, cpu.waiting());
    }

    @Test
    void completedTasksRespectBudgetAndKeepSupplierTrace() {
        var cpu = cpu(5, 1);
        var energy = new Energy();
        var provider = new Provider(false);
        for (int i = 1; i <= 5; i++) {
            assertEquals(1, cpu.run(1, energy, provider));
            assertEquals(i, provider.accepted);
            assertEquals(5 - i, cpu.logic.getStored(apple));
            assertEquals(i, cpu.waiting());
        }
        assertEquals(0, cpu.run(1, energy, provider));
        assertEquals(5, cpu.job.tasks.size());
        assertTrue(cpu.job.tasks.values().stream().allMatch(p -> p.value == 0));
        assertEquals(5, energy.spent);
    }

    @Test
    void fixedSeedMergeAndSinglePushesConserveInputsOutputsAndEnergy() {
        var random = new Random(141006L);
        for (int scenario = 0; scenario < 64; scenario++) {
            int size = random.nextInt(1, 10);
            int count = random.nextInt(1, 8);
            int budget = random.nextInt(1, 14);
            var cpu = cpu(size, count);
            var energy = new Energy();
            var provider = new Provider(random.nextBoolean());
            int pushed = cpu.run(budget, energy, provider);
            int expected = Math.min(budget, size * (provider.merge ? count : 1));
            assertEquals(expected, pushed);
            assertEquals(pushed, provider.accepted);
            assertEquals(pushed, cpu.waiting());
            assertEquals((long) size * count - pushed, cpu.logic.getStored(apple));
            assertEquals(pushed, energy.spent);
        }
    }

    @Test
    void cursorGivesEveryPendingTaskATurn() {
        var cpu = cpu(4, 20);
        var energy = new Energy();
        var provider = new Provider(false);
        for (int i = 0; i < 4; i++) assertEquals(1, cpu.run(1, energy, provider));
        assertEquals(4, provider.patterns.stream().distinct().count());
        assertTrue(cpu.job.tasks.values().stream().allMatch(p -> p.value == 19));
    }

    @Test
    void busyRejectedAndEnergyLimitedProvidersDoNotLoseInputs() {
        var cpu = cpu(2, 4);
        var energy = new Energy();
        var busy = new Provider(true);
        busy.busy = true;
        assertEquals(0, cpu.run(3, energy, busy));
        var rejected = new Provider(true);
        rejected.reject = true;
        assertEquals(0, cpu.run(3, energy, rejected));
        assertEquals(8, cpu.logic.getStored(apple));
        assertEquals(0, energy.spent);
        energy.available = 2;
        var partial = new Provider(true);
        assertEquals(2, cpu.run(3, energy, partial));
        assertEquals(6, cpu.logic.getStored(apple));
        assertEquals(2, energy.spent);
        assertEquals(2, cpu.waiting());
    }

    @Test
    void providerCannotExpandTheRequestedMergeBudget() {
        var cpu = cpu(2, 50);
        var provider = new Provider(true);
        provider.limit = Integer.MAX_VALUE;
        assertEquals(3, cpu.run(3, new Energy(), provider));
        assertEquals(3, provider.accepted);
    }

    @Test
    void emptyTaskTableDoesNotEnterSchedulingBudget() {
        var cpu = cpu(1, 1);
        cpu.job.tasks.clear();
        var energy = new Energy();
        assertEquals(0, cpu.logic.executeCraftingWithProviderLookup(1, energy.service, null, pattern -> {
            fail("An empty task table must not look up providers");
            return List.of();
        }));
        assertEquals(1, cpu.logic.getStored(apple));
        assertEquals(0, energy.spent);
    }

    @Test
    void recursiveDispatchIsDeferredAndGuardClearsAfterGetterFailure() {
        var cpu = cpu(2, 3);
        var energy = new Energy();
        var provider = new Provider(false);
        provider.onPush = () -> {
            assertEquals(0, cpu.run(10, energy, provider));
            cpu.logic.tickCraftingLogic(null, null);
        };
        assertEquals(1, cpu.run(1, energy, provider));
        assertEquals(1, provider.accepted);
        assertThrows(IllegalStateException.class,
            () -> cpu.logic.executeCraftingWithProviderLookup(1, energy.service, null,
                p -> {
                    throw new IllegalStateException("PA-141 controlled lookup failure");
                }));
        provider.onPush = () -> {
        };
        assertEquals(1, cpu.run(1, energy, provider));
    }

    @Test
    void cancellationDuringGetterDoesNotSubmitAndNewJobResetsCursor() {
        var cpu = cpu(3, 2);
        var energy = new Energy();
        var provider = new Provider(false);
        provider.onBusy = () -> setJob(cpu.logic, null);
        assertEquals(0, cpu.run(1, energy, provider));
        assertEquals(6, cpu.logic.getStored(apple));
        assertEquals(0, energy.spent);
        setJob(cpu.logic, cpu.job);
        provider.onBusy = () -> {
        };
        assertEquals(1, cpu.run(1, energy, provider));
    }

    @Test
    void acceptedWorkIsReturnedEvenWhenProviderReplacesJob() {
        var cpu = cpu(3, 2);
        var provider = new Provider(false);
        var energy = new Energy();
        provider.onPush = () -> setJob(cpu.logic, null);
        assertEquals(1, cpu.run(3, energy, provider));
        assertEquals(1, provider.accepted);
        assertEquals(1, cpu.waiting());
        assertEquals(5, cpu.logic.getStored(apple));
        assertEquals(1, energy.spent);
    }

    @Test
    void simulationReentryCannotPublishWorkFromTheReplacedJob() {
        for (int replacementOnSimulation = 1; replacementOnSimulation <= 2; replacementOnSimulation++) {
            var cpu = cpu(1, 3);
            var provider = new Provider(false);
            var energy = new Energy();
            int replaceOn = replacementOnSimulation;
            energy.onSimulate = () -> {
                if (++energy.simulations == replaceOn) setJob(cpu.logic, null);
            };
            assertEquals(0, cpu.run(1, energy, provider));
            assertEquals(0, provider.accepted);
            assertEquals(3, cpu.logic.getStored(apple), "still-held inputs must stay with the CPU");
            assertEquals(0, energy.spent);
            assertEquals(0, cpu.waiting());
        }
    }

    @Test
    void tickKeepsConfirmedBudgetAfterFailureAndDoesNotRunAReplacementJob() throws Exception {
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN) {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getCoProcessors() {
                return 2;
            }
        };
        var energy = new Energy();
        var provider = new Provider(false);
        var logic = new CraftingCpuLogic(cluster) {
            @Override
            public int executeCrafting(int budget, CraftingService service, IEnergyService suppliedEnergy,
                                       World world) {
                return executeCraftingWithProviderLookup(budget, suppliedEnergy, world, p -> List.of(provider));
            }
        };
        var cpu = cpu(logic, 1, 10);
        var usedOps = CraftingCpuLogic.class.getDeclaredField("usedOps");
        usedOps.setAccessible(true);
        energy.onModulate = () -> {
            logic.tickCraftingLogic(null, null);
            throw new IllegalStateException("controlled callback failure after provider confirmation");
        };
        assertThrows(IllegalStateException.class, () -> logic.tickCraftingLogic(energy.service, null));
        assertEquals(1, provider.accepted);
        assertArrayEquals(new int[]{1, 0, 0}, (int[]) usedOps.get(logic));

        energy.onModulate = () -> {
        };
        var replacement = cpu(new CraftingCpuLogic(cluster), 1, 10).job;
        provider.onPush = () -> setJob(logic, replacement);
        logic.tickCraftingLogic(energy.service, null);
        assertEquals(2, provider.accepted, "the next outer dispatch must not run the replacement job");
        assertArrayEquals(new int[]{1, 1, 0}, (int[]) usedOps.get(logic));
        assertEquals(10, replacement.tasks.values().iterator().next().value);
    }

    @Test
    void remainingBytesUsesExactProductBeforeDivision() {
        var cpu = cpu(1, 1);
        var random = new Random(550014L);
        for (int i = 0; i < 128; i++) {
            long bytes = i == 0 ? 1_000_000_000_000L : random.nextLong(1, Long.MAX_VALUE);
            long total = i == 0 ? 1_000_000_000_000L : random.nextLong(1, Long.MAX_VALUE);
            long remaining = i == 0 ? total : random.nextLong(total);
            cpu.job.planBytes = bytes;
            cpu.job.totalFinalOutputAmount = total;
            cpu.job.remainingAmount = remaining;
            long expected = BigInteger.valueOf(bytes).multiply(BigInteger.valueOf(remaining))
                                      .divide(BigInteger.valueOf(total)).max(BigInteger.ONE).longValueExact();
            assertEquals(expected, cpu.job.estimateRemainingPlanBytes());
            cpu.job.remainingAmount = total;
            assertEquals(bytes, cpu.job.estimateRemainingPlanBytes());
        }
    }

    @Test
    void providerCallbackCompletionStopsBeforeInputsAreTakenOrPatternsPushed() {
        for (boolean merge : new boolean[]{false, true}) {
            var cpu = cpu(3, 2);
            var energy = new Energy();
            var provider = new Provider(merge);
            provider.onBusy = cpu.job.link::markDone;
            assertEquals(0, cpu.run(2, energy, provider));
            assertEquals(6, cpu.logic.getStored(apple));
            assertEquals(0, provider.accepted);
            assertEquals(0, energy.spent);
            assertEquals(0, cpu.waiting());
        }
    }

    @Test
    void pendingOutputsUseOneTaskTraversalUntilTaskProgressChanges() {
        var cpu = cpu(2, 2);
        Pattern.outputCalls = 0;
        assertEquals(4, cpu.logic.getPendingOutputs(carrot));
        int callsAfterFirstSnapshot = Pattern.outputCalls;
        assertEquals(4, cpu.logic.getPendingOutputs(carrot));
        assertEquals(callsAfterFirstSnapshot, Pattern.outputCalls);
        assertEquals(1, cpu.run(1, new Energy(), new Provider(false)));
        assertEquals(3, cpu.logic.getPendingOutputs(carrot));
        assertTrue(Pattern.outputCalls > callsAfterFirstSnapshot);
    }

    @Test
    void blockedProviderAttemptsAreBoundedAndFairCursorContinuesAcrossTicks() {
        var cpu = cpu(100, 1);
        var provider = new Provider(false);
        provider.busy = true;
        assertEquals(0, cpu.run(1, new Energy(), provider));
        assertTrue(provider.busyCalls <= 8, "one success budget must bound provider attempts");
        int firstCursor = cpu.job.schedulingCursor;
        assertEquals(0, cpu.run(1, new Energy(), provider));
        assertTrue(provider.busyCalls <= 16);
        assertNotEquals(firstCursor, cpu.job.schedulingCursor, "blocked work still advances the fair cursor");
    }

    private record Cpu(CraftingCpuLogic logic, ExecutingCraftingJob job) {
        int run(int budget, Energy energy, Provider provider) {
            return logic.executeCraftingWithProviderLookup(budget, energy.service, null, p -> List.of(provider));
        }

        long waiting() {
            return job.waitingFor.extract(carrot, Long.MAX_VALUE, Actionable.SIMULATE);
        }
    }

    private static final class Energy {
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
            new Class<?>[]{IEnergyService.class}, (p, m, a) -> {
                if (m.getName().equals("extractAEPower")) {
                    if (a[1] == Actionable.SIMULATE) this.onSimulate.run();
                    double result = Math.min((double) a[0], this.available);
                    if (a[1] == Actionable.MODULATE) {
                        this.spent += result;
                        this.available -= result;
                        this.onModulate.run();
                    }
                    return result;
                }
                throw new UnsupportedOperationException(m.getName());
            });
        double spent;
        double available = Double.MAX_VALUE;
        int simulations;
        Runnable onSimulate = () -> {
        };
        Runnable onModulate = () -> {
        };
    }

    private static final class Provider implements ICraftingProvider {
        final boolean merge;
        final Thread owner = Thread.currentThread();
        final List<IPatternDetails> patterns = new ArrayList<>();
        boolean busy, reject;
        int limit = -1;
        int accepted;
        int busyCalls;
        Runnable onPush = () -> {
        };
        Runnable onBusy = () -> {
        };

        Provider(boolean merge) {
            this.merge = merge;
        }

        public List<IPatternDetails> getAvailablePatterns() {
            assertSame(owner, Thread.currentThread());
            return List.of();
        }

        public boolean isBusy() {
            assertSame(owner, Thread.currentThread());
            busyCalls++;
            onBusy.run();
            return busy;
        }

        public boolean canMergePatternPush(IPatternDetails p) {
            assertSame(owner, Thread.currentThread());
            return merge;
        }

        public int getMaxPatternPushMultiplier(IPatternDetails p, int max) {
            return limit < 0 ? max : limit;
        }

        public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs, int multiplier) {
            assertSame(owner, Thread.currentThread());
            if (reject) return false;
            accepted += multiplier;
            patterns.add(p);
            onPush.run();
            return true;
        }
    }

    private static final class Pattern implements IPatternDetails {
        static int outputCalls;

        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.PAPER);
        }

        public List<GenericStack> getOutputs() {
            outputCalls++;
            return List.of(new GenericStack(carrot, 1));
        }

        public IInput[] getInputs() {
            return new IInput[]{new IInput() {
                public GenericStack[] possibleInputs() {
                    return new GenericStack[]{new GenericStack(apple, 1)};
                }

                public long getMultiplier() {
                    return 1;
                }

                public boolean isValid(AEKey key, World world) {
                    return apple.equals(key);
                }

                public AEKey getRemainingKey(AEKey key) {
                    return null;
                }
            }};
        }
    }
}
