package ae2.container.implementations;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.security.IActionHost;
import ae2.api.parts.IPart;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.ISubGuiHost;
import ae2.container.AEBaseContainer;
import ae2.container.ContainerConstants;
import ae2.container.PendingGridFills;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.core.AEConfig;
import ae2.core.PlayerState;
import ae2.crafting.execution.CraftingSubmitResult;
import ae2.hooks.ticking.TickHandler;
import ae2.util.ILevelRunnable;
import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftConfirmCalculationCancellationTest {
    private static final List<ProbePlayer> fillPlayers = new ArrayList<>();
    private static Object previousSide;

    private static void drain(ProbePlayer player) throws Exception {
        var handler = TickHandler.instance();
        var queuesField = TickHandler.class.getDeclaredField("callQueue");
        queuesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var queues = (Map<World, Queue<ILevelRunnable>>)
            queuesField.get(handler);
        var queue = queues.get(player.world);
        if (queue == null) return;
        var method = TickHandler.class.getDeclaredMethod("processQueue", Queue.class,
            World.class);
        method.setAccessible(true);
        var watch = TickHandler.class.getDeclaredField("stopWatch");
        watch.setAccessible(true);
        for (int attempt = 0; attempt < 10 && !queue.isEmpty(); attempt++) {
            ((Stopwatch) watch.get(handler)).reset();
            method.invoke(handler, queue, player.world);
        }
        assertTrue(queue.isEmpty());
        queues.remove(player.world);
        var sizes = TickHandler.class.getDeclaredField("queueSizes");
        sizes.setAccessible(true);
        ((Map<?, ?>) sizes.get(handler)).remove(queue);
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        previousSide = side.get(FMLCommonHandler.instance());
        if (previousSide == null) {
            side.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                IFMLSidedHandler.class.getClassLoader(),
                new Class<?>[]{IFMLSidedHandler.class},
                (_, method, _) -> method.getName().equals("getSide")
                    ? Side.SERVER : null));
        }
        Bootstrap.register();
        AEConfig.init();
        PendingGridFills.startSession();
    }

    @AfterAll
    static void restoreSide() throws Exception {
        PendingGridFills.shutdown();
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(FMLCommonHandler.instance(), previousSide);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void attachGrid(ContainerCraftConfirm container, IGrid grid) throws Exception {
        container.setValidContainer(true);
        set(container, "cachedMergeableCpus", new Reference2BooleanOpenHashMap<>());
        var node = (IGridNode) Proxy.newProxyInstance(
            IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
            (proxy, method, args) -> method.getName().equals("grid") ? grid : null);
        var part = Proxy.newProxyInstance(IPart.class.getClassLoader(),
            new Class<?>[]{IPart.class, IActionHost.class},
            (proxy, method, args) -> method.getName().equals("getActionableNode") ? node : null);
        var host = AEBaseContainer.class.getDeclaredField("partHost");
        host.setAccessible(true);
        host.set(container, part);
    }

    private static IGrid grid() {
        return (IGrid) Proxy.newProxyInstance(
            IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (proxy, method, args) -> null);
    }

    private static ICraftingPlan plan() {
        return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
            new Class<?>[]{ICraftingPlan.class}, (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            });
    }

    private static Object read(ContainerCraftConfirm container, List<Future<ICraftingPlan>> futures) throws Exception {
        var method = ContainerCraftConfirm.class.getDeclaredMethod("readCompletedCalculations", Object.class, List.class);
        method.setAccessible(true);
        return method.invoke(container, get(container, "calculationGeneration"), futures);
    }

    private static boolean start(ContainerCraftConfirm container, int count,
                                 Function<GenericStack, Future<ICraftingPlan>> starter) throws Exception {
        // Only ownership is exercised; target contents are not consumed by the injected starter.
        var targets = Collections.<GenericStack>nCopies(count, null);
        var method = ContainerCraftConfirm.class.getDeclaredMethod("startBatchCalculations", List.class, Function.class);
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(container, targets, starter);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private static void cancel(ContainerCraftConfirm container) throws Exception {
        var method = ContainerCraftConfirm.class.getDeclaredMethod("cancelPendingCalculations");
        method.setAccessible(true);
        method.invoke(container);
    }

    private static ContainerCraftConfirm container() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (ContainerCraftConfirm) ((Unsafe) field.get(null)).allocateInstance(ContainerCraftConfirm.class);
    }

    private static Object get(Object target, String name) {
        try {
            var field = ContainerCraftConfirm.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            var field = ContainerCraftConfirm.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @AfterEach
    void clearPendingOrders() throws Exception {
        for (var player : fillPlayers) {
            drain(player);
            PendingGridFills.playerLeft(player);
        }
        fillPlayers.clear();
    }

    @Test
    void closingInsideSuccessfulSubmissionKeepsOnlyTheBoundOrder() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        fixture.onSubmit = () -> {
            fixture.close();
            fixture.player.openContainer = fixture.terminal;
        };
        fixture.container.startJob();
        drain(fixture.player);
        assertSame(fills, PendingGridFills.get(fixture.player));
        assertEquals(0, fixture.returns);
        fixture.player.openContainer = ContainerConstants.EMPTY_CONTAINER;
        PendingGridFills.scheduleInterrupt(fixture.player);
        drain(fixture.player);
        assertNull(PendingGridFills.get(fixture.player));
    }

    @Test
    void closingInsideRejectedSubmissionDropsTheUnsubmittedOrder() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.useFills();
        fixture.error = CraftingSubmitErrorCode.CPU_BUSY;
        fixture.onSubmit = fixture::close;
        fixture.container.startJob();
        drain(fixture.player);
        assertNull(PendingGridFills.get(fixture.player));
    }

    @Test
    void closingBeforeAnUnknownOrFatalReceiptRetainsTheOriginalOrder() throws Exception {
        for (boolean fatal : new boolean[]{false, true}) {
            var fixture = new SubmissionFixture();
            var fills = fixture.useFills();
            fixture.onSubmit = () -> {
                fixture.close();
                fixture.player.openContainer = fixture.terminal;
                if (fatal) throw new OutOfMemoryError("unknown fatal outcome");
                throw new IllegalStateException("unknown outcome");
            };
            if (fatal) assertThrows(OutOfMemoryError.class, fixture.container::startJob);
            else fixture.container.startJob();
            drain(fixture.player);
            assertSame(fills, PendingGridFills.get(fixture.player));
        }
    }

    @Test
    void staleCleanupAndReceiptCannotAffectAReplacementEvenWithTheSameList() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var original = fixture.container.getPendingFillsOrder();
        fixture.onSubmit = () -> {
            fixture.close();
            PendingGridFills.scheduleInterrupt(fixture.player);
            PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        };
        fixture.container.startJob();
        drain(fixture.player);
        assertSame(fills, PendingGridFills.get(fixture.player));
        assertNotSame(original, PendingGridFills.getOrder(fixture.player));
        // The old accepted receipt must not mark the replacement as submitted.
        PendingGridFills.scheduleAbandonCheck(fixture.player);
        drain(fixture.player);
        assertNull(PendingGridFills.get(fixture.player));
    }

    @Test
    void aLaterRejectionCannotEraseAPreviousAcceptedOrUnknownReceipt() throws Exception {
        for (boolean accepted : new boolean[]{true, false}) {
            var fixture = new SubmissionFixture();
            var fills = fixture.useFills();
            var order = fixture.container.getPendingFillsOrder();
            try (var first = PendingGridFills.beginSubmission(order)) {
                if (accepted) first.complete(true);
            }
            try (var next = PendingGridFills.beginSubmission(order)) {
                next.complete(false);
                next.complete(false);
            }
            PendingGridFills.scheduleAbandonCheck(fixture.player, order);
            drain(fixture.player);
            assertSame(fills, PendingGridFills.get(fixture.player));
        }
    }

    @Test
    void staleMissingMaterialResultCannotRemoveNewOrderDestinations() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var original = fixture.container.getPendingFillsOrder();
        PendingGridFills.set(fixture.player, fills);
        var missing = new KeyCounter();
        missing.add(fills.getFirst().what(), 1);
        PendingGridFills.dropUnavailable(fixture.player, original, missing);
        assertEquals(1, fills.size());
        PendingGridFills.dropUnavailable(fixture.player, missing);
        assertTrue(fills.isEmpty());
    }

    @Test
    void closingWithoutStartingStillAbandonsTheUnsubmittedOrder() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.useFills();
        fixture.close();
        drain(fixture.player);
        assertNull(PendingGridFills.get(fixture.player));
    }

    @Test
    void submissionReentryAndRepeatedClickCannotSubmitTheSameGenerationTwice() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSubmit = fixture.container::startJob;
        fixture.container.startJob();
        fixture.container.startJob();
        assertEquals(1, fixture.submissions);
        assertEquals(1, fixture.returns);
        assertSame(get(fixture.container, "calculationGeneration"), get(fixture.container, "submittedGeneration"));
    }

    @Test
    void submissionCallbackCannotOverwriteOrNavigateAReplacementPlan() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSubmit = fixture::replace;
        fixture.container.startJob();
        fixture.assertReplacement();
        assertEquals(0, fixture.returns);
        fixture.onSubmit = null;
        fixture.container.startJob();
        assertEquals(2, fixture.submissions);
        assertEquals(1, fixture.returns);
    }

    @Test
    void successGetterCancellationDoesNotChangeTheNewView() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSuccess = fixture::replace;
        fixture.container.startJob();
        fixture.assertReplacement();
        assertEquals(0, fixture.returns);
        assertEquals(1, fixture.submissions);
    }

    @Test
    void failedReceiptCallbacksDoNotReadFurtherOrPublishIntoANewPlan() throws Exception {
        for (boolean atCode : new boolean[]{true, false}) {
            var fixture = new SubmissionFixture();
            fixture.error = CraftingSubmitErrorCode.CPU_BUSY;
            if (atCode) fixture.onError = fixture::replace;
            else fixture.onDetail = fixture::replace;
            fixture.container.startJob();
            fixture.assertReplacement();
            assertEquals(atCode ? 0 : 1, fixture.detailReads);
            assertEquals(0, fixture.returns);
        }
    }

    @Test
    void confirmedRejectionPublishesAFixedReceiptAndAllowsRetry() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.error = CraftingSubmitErrorCode.CPU_BUSY;
        fixture.container.startJob();
        var receipt = fixture.container.submitError.result();
        int reads = fixture.errorReads;
        fixture.error = CraftingSubmitErrorCode.CPU_OFFLINE;
        assertEquals(CraftingSubmitErrorCode.CPU_BUSY, receipt.errorCode());
        assertEquals(reads, fixture.errorReads);
        assertTrue(fixture.container.isValidContainer());
        fixture.error = null;
        fixture.container.startJob();
        assertEquals(2, fixture.submissions);
        assertEquals(1, fixture.returns);
    }

    @Test
    void submitOrSuccessGetterFailureBlocksReplayAndClosesOnlyTheCurrentView() throws Exception {
        for (boolean atSubmit : new boolean[]{true, false}) {
            var fixture = new SubmissionFixture();
            Runnable fail = () -> {
                throw new IllegalStateException("outcome unavailable");
            };
            if (atSubmit) fixture.onSubmit = fail;
            else fixture.onSuccess = fail;
            fixture.container.startJob();
            assertFalse(fixture.container.isValidContainer());
            for (int i = 0; i < 50; i++) fixture.container.startJob();
            assertEquals(1, fixture.submissions);
            assertEquals(0, fixture.returns);
        }
    }

    @Test
    void unknownReceiptAfterReplacementDoesNotInvalidateTheReplacement() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSubmit = () -> {
            fixture.replace();
            throw new IllegalStateException("old outcome unavailable");
        };
        fixture.container.startJob();
        fixture.assertReplacement();
        assertEquals(0, fixture.returns);
    }

    @Test
    void navigationAndFailureMetadataCallbacksCannotChangeNewStateAfterTheyReturn() throws Exception {
        for (boolean success : new boolean[]{true, false}) {
            var fixture = new SubmissionFixture();
            if (success) fixture.container.onPlayerRead = fixture::replace;
            else {
                fixture.error = CraftingSubmitErrorCode.CPU_BUSY;
                fixture.onOutput = fixture::replace;
            }
            fixture.container.startJob();
            fixture.assertReplacement();
            assertEquals(0, fixture.returns);
        }
    }

    @Test
    void sourceSwitchAfterSubmitCannotNavigateOrDisableTheOldView() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSubmit = () -> fixture.current[0] = grid();
        fixture.container.startJob();
        assertEquals(0, fixture.returns);
        assertTrue(fixture.container.isAutoStart());
    }

    @Test
    void fatalSubmitErrorPropagatesAndStillBlocksReplayOfTheAttemptedGeneration() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.onSubmit = () -> {
            throw new OutOfMemoryError("fatal submit fixture");
        };
        assertThrows(OutOfMemoryError.class, fixture.container::startJob);
        assertEquals(false, get(fixture.container, "submitting"));
        fixture.container.startJob();
        assertEquals(1, fixture.submissions);
    }

    @Test
    void oldConnectionCannotReadClearOrOverwriteANewerConnectionWithTheSameUuid() throws Exception {
        var old = new SubmissionFixture();
        var oldFills = old.useFills();
        PendingGridFills.scheduleInterrupt(old.player);
        var next = new SubmissionFixture();
        next.player.id = old.player.id;
        var newFills = next.useFills();
        assertNull(PendingGridFills.get(old.player));
        var field = PlayerState.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        ((PlayerState) field.get(null)).onPlayerLoggedOut(
            new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent(old.player));
        PendingGridFills.clear(old.player);
        PendingGridFills.set(old.player, oldFills);
        drain(old.player);
        assertSame(newFills, PendingGridFills.get(next.player));
    }

    @Test
    void anotherDestinationOrGridCannotBorrowAnOrderFromTheSamePlayer() throws Exception {
        for (boolean changeGrid : new boolean[]{false, true}) {
            var fixture = new SubmissionFixture();
            fixture.useFills();
            if (changeGrid) fixture.current[0] = grid();
            else fixture.terminal.target = new Object();
            assertNull(PendingGridFills.getOrderForHost(fixture.player,
                fixture.terminal.target, fixture.current[0]));
            assertNull(PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
            assertNull(PendingGridFills.get(fixture.player));
        }
    }

    @Test
    void terminalIdentityCallbackCannotRemoveTheReplacementOrder() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var previous = fixture.container.getPendingFillsOrder();
        fixture.terminal.onTargetRead = () -> {
            fixture.terminal.target = new Object();
            PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        };
        assertNull(PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
        assertNotSame(previous, PendingGridFills.getOrder(fixture.player));
        assertSame(fills, PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
    }

    @Test
    void outerRegistrationCannotOverwriteAnOrderInstalledByItsIdentityGetter() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var replacement = new ArrayList<>(fills);
        fixture.terminal.onTargetRead = () -> PendingGridFills.set(
            fixture.player, replacement, fixture.terminal, fixture.current[0]);
        PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        assertSame(replacement, PendingGridFills.get(fixture.player));
    }

    @Test
    void legacyRegistrationRequiresATerminalAndBindsItsActualDestination() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        PendingGridFills.clear(fixture.player);
        fixture.player.openContainer = ContainerConstants.EMPTY_CONTAINER;
        PendingGridFills.set(fixture.player, fills);
        assertNull(PendingGridFills.get(fixture.player));
        fixture.player.openContainer = fixture.terminal;
        PendingGridFills.set(fixture.player, fills);
        assertSame(fills, PendingGridFills.get(fixture.player));
        fixture.terminal.target = new Object();
        assertNull(PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
    }

    @Test
    void differentTerminalAtDeferredCleanupEndsTheOldSessionEvenAfterSuccess() throws Exception {
        var fixture = new SubmissionFixture();
        fixture.useFills();
        fixture.container.startJob();
        var other = allocate(HeadlessTerminal.class);
        other.target = new Object();
        other.node = fixture.terminal.node;
        fixture.player.openContainer = other;
        PendingGridFills.scheduleAbandonCheck(fixture.player, fixture.container.getPendingFillsOrder());
        drain(fixture.player);
        assertNull(PendingGridFills.get(fixture.player));
    }

    @Test
    void dimensionRespawnAndCloneBoundariesInvalidateThePreviousOrders() throws Exception {
        var field = PlayerState.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        var state = (PlayerState) field.get(null);
        var dimension = new SubmissionFixture();
        dimension.useFills();
        var oldWorld = dimension.player.world;
        dimension.player.world = allocate(WorldServer.class);
        state.onPlayerChangedDimension(new net.minecraftforge.fml.common.gameevent.PlayerEvent
            .PlayerChangedDimensionEvent(dimension.player, 0, 1));
        assertNull(PendingGridFills.get(dimension.player));
        // Release the test queue belonging to the original world as well.
        dimension.player.world = oldWorld;
        drain(dimension.player);
        var respawn = new SubmissionFixture();
        respawn.useFills();
        state.onPlayerRespawn(new net.minecraftforge.fml.common.gameevent.PlayerEvent
            .PlayerRespawnEvent(respawn.player, false));
        assertNull(PendingGridFills.get(respawn.player));
        var original = new SubmissionFixture();
        original.useFills();
        var clone = new SubmissionFixture();
        clone.player.id = original.player.id;
        clone.player.world = original.player.world;
        state.onPlayerClone(new net.minecraftforge.event.entity.player.PlayerEvent
            .Clone(clone.player, original.player, true));
        assertNull(PendingGridFills.get(original.player));
        assertNull(PendingGridFills.get(clone.player));
        PendingGridFills.playerLeft(clone.player);
    }

    @Test
    void worldUnloadAndServerShutdownCloseAdmissionBeforeOldActorsCanPublish() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        PendingGridFills.clearWorld(fixture.player.world);
        assertNull(PendingGridFills.get(fixture.player));
        PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        assertNull(PendingGridFills.get(fixture.player));
        PendingGridFills.playerJoined(fixture.player);
        PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        assertSame(fills, PendingGridFills.get(fixture.player));
        try {
            PendingGridFills.shutdown();
            PendingGridFills.playerJoined(fixture.player);
            PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
            assertNull(PendingGridFills.get(fixture.player));
            PendingGridFills.startSession();
            PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
            assertNull(PendingGridFills.get(fixture.player));
            PendingGridFills.playerJoined(fixture.player);
            PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
            assertSame(fills, PendingGridFills.get(fixture.player));
        } finally {
            PendingGridFills.startSession();
        }
    }

    @Test
    void recreatedWirelessHostKeepsTheStackDestinationButAReplacementStackDoesNot() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var host = allocate(StackHost.class);
        host.stack = new ItemStack(Items.APPLE);
        fixture.terminal.target = host;
        PendingGridFills.set(fixture.player, fills, fixture.terminal, fixture.current[0]);
        var reopened = allocate(StackHost.class);
        reopened.stack = host.stack;
        fixture.terminal.target = reopened;
        assertSame(fills, PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
        reopened.stack = reopened.stack.copy();
        assertNull(PendingGridFills.get(fixture.player, fixture.terminal, fixture.current[0]));
    }

    @Test
    void cancellationDetachesAllWorkAndContinuesAfterOrdinaryCallbackFailure() throws Exception {
        var container = container();
        var replacement = new CompletableFuture<ICraftingPlan>();
        var calls = new AtomicInteger();
        var first = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean cancel(boolean interrupt) {
                assertTrue(interrupt);
                assertNull(CraftConfirmCalculationCancellationTest.get(container, "job"));
                assertNull(CraftConfirmCalculationCancellationTest.get(container, "batchJobs"));
                calls.incrementAndGet();
                set(container, "job", replacement);
                throw new IllegalStateException("cancel fixture");
            }
        };
        var child = new CompletableFuture<ICraftingPlan>();
        var batch = new ArrayList<Future<ICraftingPlan>>(List.of(child));
        set(container, "job", first);
        set(container, "batchJobs", batch);
        cancel(container);
        assertEquals(1, calls.get());
        assertTrue(child.isCancelled());
        assertTrue(batch.isEmpty());
        assertSame(replacement, get(container, "job"));
        assertFalse(replacement.isCancelled());
    }

    @Test
    void failureStartingThirdChildCancelsAllPreviouslyStartedCalculations() throws Exception {
        var container = container();
        var first = new CompletableFuture<ICraftingPlan>();
        var second = new CompletableFuture<ICraftingPlan>();
        var calls = new AtomicInteger();
        var expected = new IllegalStateException("third startup failed");
        var actual = assertThrows(IllegalStateException.class, () -> start(container, 3, target -> switch (calls.getAndIncrement()) {
            case 0 -> first;
            case 1 -> second;
            default -> throw expected;
        }));
        assertSame(expected, actual);
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        assertNull(get(container, "batchJobs"));
        assertNull(get(container, "batchTargets"));
    }

    @Test
    void cancellationDuringStartupCancelsReturnedFutureAndKeepsNewWork() throws Exception {
        var container = container();
        var first = new CompletableFuture<ICraftingPlan>();
        var returnedAfterCancel = new CompletableFuture<ICraftingPlan>();
        var replacement = new CompletableFuture<ICraftingPlan>();
        var calls = new AtomicInteger();
        assertFalse(start(container, 3, target -> {
            if (calls.getAndIncrement() == 0) return first;
            try {
                CraftConfirmCalculationCancellationTest.cancel(container);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            set(container, "job", replacement);
            return returnedAfterCancel;
        }));
        assertEquals(2, calls.get());
        assertTrue(first.isCancelled());
        assertTrue(returnedAfterCancel.isCancelled());
        assertFalse(replacement.isCancelled());
        assertSame(replacement, get(container, "job"));
    }

    @Test
    void successfulStartupOwnsEveryChildUntilCancellation() throws Exception {
        var container = container();
        var children = new ArrayList<CompletableFuture<ICraftingPlan>>();
        assertTrue(start(container, 3, target -> {
            var child = new CompletableFuture<ICraftingPlan>();
            children.add(child);
            return child;
        }));
        assertEquals(children, get(container, "batchJobs"));
        assertTrue(children.stream().noneMatch(Future::isCancelled));
        cancel(container);
        assertTrue(children.stream().allMatch(Future::isCancelled));
        assertNull(get(container, "batchJobs"));
    }

    @Test
    void cancellationInsideGetDiscardsOldResultAndPreservesReplacement() throws Exception {
        var container = container();
        var replacement = new CompletableFuture<ICraftingPlan>();
        var returned = plan();
        var future = new CompletableFuture<ICraftingPlan>() {
            @Override
            public ICraftingPlan get() {
                try {
                    CraftConfirmCalculationCancellationTest.cancel(container);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                set(container, "job", replacement);
                return returned;
            }
        };
        future.complete(returned);
        set(container, "job", future);
        assertNull(read(container, List.of(future)));
        assertSame(replacement, get(container, "job"));
        assertNull(get(container, "result"));
    }

    @Test
    void cancellationInsideDoneCheckStopsBeforeGet() throws Exception {
        var container = container();
        var calls = new AtomicInteger();
        var future = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean isDone() {
                try {
                    CraftConfirmCalculationCancellationTest.cancel(container);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                return true;
            }

            @Override
            public ICraftingPlan get() {
                calls.incrementAndGet();
                return plan();
            }
        };
        set(container, "job", future);
        assertNull(read(container, List.of(future)));
        assertEquals(0, calls.get());
    }

    @Test
    void singleStartupAfterCancellationDiscardsReturnedFuture() throws Exception {
        var container = container();
        var returned = new CompletableFuture<ICraftingPlan>();
        var replacement = new CompletableFuture<ICraftingPlan>();
        var method = ContainerCraftConfirm.class.getDeclaredMethod("startCalculation", Supplier.class);
        method.setAccessible(true);
        Supplier<Future<ICraftingPlan>> starter = () -> {
            try {
                CraftConfirmCalculationCancellationTest.cancel(container);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            set(container, "job", replacement);
            return returned;
        };
        assertEquals(false, method.invoke(container, starter));
        assertTrue(returned.isCancelled());
        assertSame(replacement, get(container, "job"));
        assertFalse(replacement.isCancelled());
    }

    @Test
    void currentCompletedBatchReadsAllResultsWithoutWaitingOnIncompleteFuture() throws Exception {
        var container = container();
        var one = plan();
        var two = plan();
        var pending = new CompletableFuture<ICraftingPlan>();
        assertNull(read(container, List.of(CompletableFuture.completedFuture(one), pending)));
        assertEquals(List.of(one, two), read(container, List.of(
            CompletableFuture.completedFuture(one), CompletableFuture.completedFuture(two))));
    }

    @Test
    void reentrantCancellationPreventsOuterBeginFromClearingNewPlan() throws Exception {
        var container = container();
        var replacement = new CompletableFuture<ICraftingPlan>();
        var latest = plan();
        var first = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean cancel(boolean interrupt) {
                try {
                    CraftConfirmCalculationCancellationTest.cancel(container);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                set(container, "job", replacement);
                set(container, "result", latest);
                return super.cancel(interrupt);
            }
        };
        set(container, "job", first);
        var method = ContainerCraftConfirm.class.getDeclaredMethod("beginPlan");
        method.setAccessible(true);
        assertNull(method.invoke(container));
        assertSame(replacement, get(container, "job"));
        assertSame(latest, get(container, "result"));
    }

    @Test
    void calculationSourceMustMatchBothBoundAndCurrentGrid() throws Exception {
        var container = container();
        container.setValidContainer(true);
        var original = grid();
        var other = grid();
        var current = new IGrid[]{original};
        var node = (IGridNode) Proxy.newProxyInstance(
            IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
            (proxy, method, args) -> method.getName().equals("grid") ? current[0] : null);
        var part = Proxy.newProxyInstance(IPart.class.getClassLoader(),
            new Class<?>[]{IPart.class, IActionHost.class},
            (proxy, method, args) -> method.getName().equals("getActionableNode") ? node : null);
        var host = AEBaseContainer.class.getDeclaredField("partHost");
        host.setAccessible(true);
        host.set(container, part);
        var generation = new Object();
        set(container, "calculationGeneration", generation);
        set(container, "calculationGrid", original);
        var method = ContainerCraftConfirm.class.getDeclaredMethod("isCalculationCurrent", Object.class,
            IGrid.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(container, generation, original));
        current[0] = other;
        assertEquals(false, method.invoke(container, generation, original));
        assertEquals(false, method.invoke(container, generation, other));
        current[0] = original;
        container.setValidContainer(false);
        assertEquals(false, method.invoke(container, generation, original));
    }

    @Test
    void sourceInvalidationClearsPublishedStateAndReleasesOwnedCalculation() throws Exception {
        var container = container();
        var future = new CompletableFuture<ICraftingPlan>();
        set(container, "job", future);
        set(container, "result", plan());
        set(container, "calculationGrid", grid());
        container.setValidContainer(true);
        var method = ContainerCraftConfirm.class.getDeclaredMethod("invalidateCalculationSource");
        method.setAccessible(true);
        method.invoke(container);
        assertTrue(future.isCancelled());
        assertNull(get(container, "job"));
        assertNull(get(container, "result"));
        assertNull(get(container, "calculationGrid"));
        assertFalse(container.isValidContainer());
    }

    @Test
    void treeRoundTripKeepsOriginalGridAndAdoptsMatchingFuture() throws Exception {
        var fixture = new SubmissionFixture();
        var fills = fixture.useFills();
        var order = fixture.container.getPendingFillsOrder();
        var confirm = container();
        var source = fixture.original;
        attachGrid(confirm, source);
        var completed = plan();
        set(confirm, "calculationGrid", source);
        set(confirm, "result", completed);
        set(confirm, "pendingFillsOrder", order);
        assertSame(source, confirm.getResultGrid());
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var tree = (ContainerCraftingTree) ((Unsafe) field.get(null)).allocateInstance(ContainerCraftingTree.class);
        var future = CompletableFuture.completedFuture(completed);
        tree.setJob(future, confirm.getResultGrid(), confirm.getPendingFillsOrder());
        PendingGridFills.set(fixture.player, fills);
        var receiving = container();
        attachGrid(receiving, source);
        set(receiving, "pendingFillsOrder", PendingGridFills.getOrder(fixture.player));
        receiving.setJob(tree.getJob(), tree.getCalculationGrid(), tree.getPendingFillsOrder());
        assertSame(future, get(receiving, "job"));
        assertSame(source, get(receiving, "calculationGrid"));
        assertSame(order, receiving.getPendingFillsOrder());
        assertFalse(future.isCancelled());
    }

    @Test
    void treeFutureCannotBeReboundToDifferentReceivingGrid() throws Exception {
        var receiving = container();
        attachGrid(receiving, grid());
        var oldFuture = new CompletableFuture<ICraftingPlan>();
        receiving.setJob(oldFuture, grid());
        assertTrue(oldFuture.isCancelled());
        assertNull(get(receiving, "job"));
        assertNull(get(receiving, "calculationGrid"));
        assertFalse(receiving.isValidContainer());
    }

    private static final class ProbePlayer extends EntityPlayerMP {
        private UUID id;

        private ProbePlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return this.id;
        }
    }

    private static final class HeadlessConfirm extends ContainerCraftConfirm {
        private InventoryPlayer inventory;
        private Runnable onPlayerRead;

        private HeadlessConfirm() {
            super(null, null);
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public InventoryPlayer getPlayerInventory() {
            return this.inventory;
        }

        @Override
        public EntityPlayer getPlayer() {
            if (this.onPlayerRead != null) this.onPlayerRead.run();
            return null;
        }
    }

    private static final class SubmissionFixture {
        private final HeadlessConfirm container;
        private final ProbePlayer player;
        private final IGrid[] current = new IGrid[1];
        private final IGrid original;
        private final ContainerCraftConfirm.SyncableSubmitResult newError =
            new ContainerCraftConfirm.SyncableSubmitResult(CraftingSubmitResult.CPU_OFFLINE);
        private HeadlessTerminal terminal;
        private Runnable onSubmit, onSuccess, onError, onDetail, onOutput;
        private CraftingSubmitErrorCode error;
        private int submissions, returns, errorReads, detailReads;
        private ICraftingPlan replacement;

        SubmissionFixture() throws Exception {
            container = allocate(HeadlessConfirm.class);
            player = allocate(ProbePlayer.class);
            player.id = UUID.randomUUID();
            container.inventory = new InventoryPlayer(player);
            player.inventory = container.inventory;
            var service = (ICraftingService) Proxy.newProxyInstance(
                ICraftingService.class.getClassLoader(),
                new Class<?>[]{ICraftingService.class}, (_, method, _) -> {
                    if (!method.getName().equals("submitJob")) throw new AssertionError(method.getName());
                    submissions++;
                    if (onSubmit != null) onSubmit.run();
                    return new ICraftingSubmitResult() {
                        public boolean successful() {
                            if (onSuccess != null) onSuccess.run();
                            return error == null;
                        }

                        public CraftingSubmitErrorCode errorCode() {
                            errorReads++;
                            if (onError != null) onError.run();
                            return error;
                        }

                        public Object errorDetail() {
                            detailReads++;
                            if (onDetail != null) onDetail.run();
                            return null;
                        }

                        public ICraftingLink link() {
                            return null;
                        }
                    };
                });
            original = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (_, method, _) -> method.getName().equals("getCraftingService") ? service : null);
            current[0] = original;
            var node = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (_, method, _) -> method.getName().equals("grid") ? current[0] : null);
            var part = Proxy.newProxyInstance(IPart.class.getClassLoader(),
                new Class<?>[]{IPart.class, IActionHost.class},
                (_, method, _) -> method.getName().equals("getActionableNode") ? node : null);
            var host = AEBaseContainer.class.getDeclaredField("partHost");
            host.setAccessible(true);
            host.set(container, part);
            set(container, "host", Proxy.newProxyInstance(
                ISubGuiHost.class.getClassLoader(), new Class<?>[]{ISubGuiHost.class},
                (_, method, _) -> {
                    if (method.getName().equals("returnToMainContainer")) returns++;
                    return null;
                }));
            set(container, "calculationGeneration", new Object());
            set(container, "calculationGrid", original);
            set(container, "result", newPlan());
            container.setValidContainer(true);
            container.setAutoStart(true);
        }

        private List<ICraftingGridContainer.GridFill> useFills() throws Exception {
            player.world = allocate(WorldServer.class);
            fillPlayers.add(player);
            PendingGridFills.playerJoined(player);
            terminal = allocate(HeadlessTerminal.class);
            terminal.target = container.getTarget();
            terminal.node = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (_, method, _) -> method.getName().equals("grid") ? current[0] : null);
            player.openContainer = terminal;
            var fills = new ArrayList<ICraftingGridContainer.GridFill>();
            fills.add(new ICraftingGridContainer.GridFill(0,
                AEItemKey.of(Items.APPLE), 1));
            PendingGridFills.set(player, fills, terminal, current[0]);
            set(container, "pendingFillsOrder", PendingGridFills.getOrder(player));
            return fills;
        }

        private void close() {
            container.onContainerClosed(player);
            player.openContainer = ContainerConstants.EMPTY_CONTAINER;
        }

        private ICraftingPlan newPlan() {
            var output = new GenericStack(AEItemKey.of(Items.APPLE), 1);
            var missing = new KeyCounter();
            return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "simulation" -> false;
                    case "missingItems" -> missing;
                    case "finalOutput" -> {
                        if (onOutput != null) onOutput.run();
                        yield output;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
        }

        private void replace() {
            try {
                cancel(container);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            replacement = newPlan();
            set(container, "result", replacement);
            set(container, "calculationGrid", original);
            container.setAutoStart(true);
            container.setValidContainer(true);
            container.submitError = newError;
        }

        private void assertReplacement() {
            assertSame(replacement, container.getResult());
            assertTrue(container.isAutoStart());
            assertTrue(container.isValidContainer());
            assertSame(newError, container.submitError);
        }
    }

    private static final class HeadlessTerminal extends ContainerCraftingTerm {
        private Object target;
        private IGridNode node;
        private Runnable onTargetRead;

        private HeadlessTerminal() {
            super(null, null);
        }

        @Override
        public Object getTarget() {
            var callback = this.onTargetRead;
            this.onTargetRead = null;
            if (callback != null) callback.run();
            return this.target;
        }

        @Override
        public IGridNode getGridNode() {
            return this.node;
        }
    }

    private static final class StackHost extends ItemGuiHost<Item> {
        private ItemStack stack;

        private StackHost() {
            super(null, null, null);
        }

        @Override
        public ItemStack getItemStack() {
            return this.stack;
        }
    }
}
