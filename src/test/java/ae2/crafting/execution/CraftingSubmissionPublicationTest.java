package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
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
import ae2.crafting.CraftingLinkNexus;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingSubmissionPublicationTest {
    private static Object previousSide;
    private static AEItemKey carrot;

    @BeforeAll
    static void bootstrap() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (_, method, _) -> method.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        carrot = AEItemKey.of(Items.CARROT);
    }

    @AfterAll
    static void restoreSide() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    @Test
    void committedLinksAndInputsSurviveOrdinaryDisplayAndSaveFailureWithASuccessfulReceipt() {
        var fixture = new Fixture();
        fixture.cluster.onDisplay = () -> {
            assertEquals(2, fixture.crafting.links.size());
            assertEquals(5, fixture.logic.getInventory().list.get(carrot));
            throw new IllegalStateException("display unavailable");
        };
        fixture.cluster.failSave = true;
        var result = fixture.submit();
        assertTrue(result.successful());
        assertSame(fixture.crafting.links.getLast(), result.link());
        assertEquals(Objects.requireNonNull(fixture.logic.getLastLink()).getCraftingID(), Objects.requireNonNull(result.link()).getCraftingID());
        assertTrue(fixture.logic.hasJob());
        assertEquals(1, fixture.extracts);
        assertEquals(1, fixture.outputReads);
        assertEquals(1, fixture.cluster.saves);
        Objects.requireNonNull(result.link()).cancel();
        assertTrue(fixture.logic.getLastLink().isCanceled());
    }

    @Test
    void failedSaveIsRetriedByTheOwnerTickWithoutRepeatingExtractionOrSubmission() {
        var fixture = new Fixture();
        fixture.cluster.failSave = true;
        var result = fixture.submit();
        fixture.cluster.active = false;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(2, fixture.cluster.saves);
        fixture.cluster.failSave = false;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(3, fixture.cluster.saves);
        assertEquals(1, fixture.extracts);
        assertEquals(2, fixture.crafting.links.size());
        assertSame(fixture.crafting.links.getLast(), result.link());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
    }

    @Test
    void notificationReentryCannotStartOrTickAnotherJobInsideTheSubmission() {
        var fixture = new Fixture();
        fixture.cluster.onDisplay = () -> {
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.logic.tryMergeJob(
                fixture.grid, fixture.plan, IActionSource.empty()).errorCode());
            fixture.logic.tickCraftingLogic(null, fixture.crafting);
            assertEquals(0, fixture.cluster.saves);
        };
        fixture.cluster.onSave = () -> assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
        assertTrue(fixture.submit().successful());
        assertEquals(1, fixture.extracts);
        assertEquals(2, fixture.crafting.links.size());
        assertEquals(1, fixture.cluster.saves);
    }

    @Test
    void playerMetadataFailureHappensBeforeAnyInputExtraction() {
        var fixture = new Fixture();
        var source = (IActionSource) Proxy.newProxyInstance(IActionSource.class.getClassLoader(),
            new Class<?>[]{IActionSource.class}, (_, method, _) -> {
                if (method.getName().equals("player")) throw new IllegalStateException("owner unavailable");
                return Optional.empty();
            });
        assertThrows(IllegalStateException.class, () -> fixture.logic.trySubmitJob(
            fixture.grid, fixture.plan, source, fixture.requester));
        assertEquals(0, fixture.extracts);
        assertFalse(fixture.logic.hasJob());
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertTrue(fixture.submit().successful());
    }

    @Test
    void displayUsesTheCommittedOutputInsteadOfCallingThePlanGetterAgain() {
        var fixture = new Fixture();
        fixture.failRepeatedOutput = true;
        assertTrue(fixture.submit().successful());
        assertEquals(1, fixture.outputReads);
        assertEquals(new GenericStack(carrot, 1), fixture.cluster.output);
    }

    @Test
    void structuralLinkRegistrationFailureStillPropagatesAndDoesNotLoseTheCpuInventory() {
        var fixture = new Fixture();
        fixture.crafting.failRegistration = true;
        assertThrows(IllegalStateException.class, fixture::submit);
        assertTrue(fixture.logic.hasJob());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        assertEquals(1, fixture.extracts);
        assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
        fixture.cluster.active = false;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(1, fixture.cluster.saves);
    }

    @Test
    void fatalDisplayFailurePropagatesAfterLinkOwnershipAndReleasesTheSubmissionGuard() {
        var fixture = new Fixture();
        fixture.cluster.onDisplay = () -> {
            throw new OutOfMemoryError("fatal display fixture");
        };
        assertThrows(OutOfMemoryError.class, fixture::submit);
        assertTrue(fixture.logic.hasJob());
        assertEquals(2, fixture.crafting.links.size());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        fixture.cluster.active = false;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(1, fixture.cluster.saves);
        assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
    }

    @Test
    void displayCallbackCancellationReturnsTheCanceledReceiptWithoutResurrectingProduction() {
        var fixture = new Fixture();
        fixture.cluster.onDisplay = fixture.logic::cancel;
        var result = fixture.submit();
        assertTrue(result.successful());
        assertTrue(Objects.requireNonNull(result.link()).isCanceled());
        assertFalse(fixture.logic.hasJob());
        assertEquals(5, fixture.returned);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertEquals(1, fixture.extracts);
    }

    @Test
    void planPreparationFailuresLeaveNetworkInputsUntouchedAndReleaseTheGuard() {
        for (var getter : List.of("finalOutput", "intermediateFinalOutputAmount", "emittedItems", "patternTimes",
            "usedItems", "missingItems")) {
            var fixture = new Fixture();
            fixture.failedGetter = getter;
            assertThrows(IllegalStateException.class, fixture::submit, getter);
            assertEquals(0, fixture.extracts, getter);
            assertTrue(fixture.logic.getInventory().list.isEmpty(), getter);
            assertFalse(fixture.logic.hasJob(), getter);
            assertTrue(fixture.crafting.links.isEmpty(), getter);
            fixture.failedGetter = null;
            assertTrue(fixture.submit().successful(), getter);
            assertEquals(1, fixture.extracts, getter);
        }
    }

    @Test
    void patternMetadataFailureHappensBeforeAnyInputExtraction() {
        var fixture = new Fixture();
        var pattern = (IPatternDetails) Proxy.newProxyInstance(
            IPatternDetails.class.getClassLoader(),
            new Class<?>[]{IPatternDetails.class}, (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "getOutputs" -> throw new IllegalStateException("pattern unavailable");
                default -> null;
            });
        fixture.patterns.put(pattern, 1);
        assertThrows(IllegalStateException.class, fixture::submit);
        assertEquals(0, fixture.extracts);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertFalse(fixture.logic.hasJob());
        assertTrue(fixture.crafting.links.isEmpty());
    }

    @Test
    void ordinaryListenersCannotInterruptExtractionMissingAccountingOrCancellation() {
        var fixture = new Fixture();
        fixture.emitted.add(carrot, 2);
        fixture.missing.add(carrot, 7);
        fixture.extractLimit = 3;
        var calls = new int[2];
        fixture.logic.addListener(_ -> {
            calls[0]++;
            throw new IllegalStateException("listener unavailable");
        });
        fixture.logic.addListener(_ -> calls[1]++);
        var receipt = fixture.submit();
        assertTrue(receipt.successful());
        assertSame(fixture.crafting.links.getLast(), receipt.link());
        assertEquals(2, fixture.extracts);
        assertEquals(6, fixture.logic.getInventory().list.get(carrot));
        assertEquals(8, fixture.logic.getWaitingFor(carrot));
        assertArrayEquals(new int[]{4, 4}, calls);
        fixture.logic.cancel();
        assertFalse(fixture.logic.hasJob());
        assertTrue(Objects.requireNonNull(receipt.link()).isCanceled());
        assertEquals(6, fixture.returned);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        assertTrue(calls[0] > 4);
        assertEquals(calls[0], calls[1]);
    }

    @Test
    void listenersUseIdentityAndDuplicateRegistrationDoesNotDuplicateDelivery() {
        var fixture = new Fixture();
        class EqualListener implements Consumer<AEKey> {
            int calls;

            @Override
            public void accept(AEKey key) {
                calls++;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof EqualListener;
            }

            @Override
            public int hashCode() {
                return 0;
            }
        }
        var first = new EqualListener();
        var second = new EqualListener();
        fixture.logic.addListener(first);
        fixture.logic.addListener(first);
        fixture.logic.addListener(second);
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertEquals(1, first.calls);
        assertEquals(1, second.calls);
        fixture.logic.removeListener(first);
        fixture.logic.removeListener(first);
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertEquals(1, first.calls);
        assertEquals(2, second.calls);
    }

    @Test
    void removeAndReaddCannotReviveTheOldRegistrationDuringNotification() {
        var fixture = new Fixture();
        var calls = new int[2];
        var edited = new boolean[1];
        var listeners = new ArrayList<Consumer<AEKey>>();
        for (int index = 0; index < 2; index++) {
            final int current = index;
            listeners.add(_ -> {
                calls[current]++;
                if (!edited[0]) {
                    edited[0] = true;
                    var other = listeners.get(1 - current);
                    fixture.logic.removeListener(other);
                    fixture.logic.addListener(other);
                }
            });
        }
        listeners.forEach(fixture.logic::addListener);
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertEquals(1, calls[0] + calls[1], "a revoked registration cannot start after another callback re-adds it");
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertEquals(3, calls[0] + calls[1]);
        listeners.forEach(fixture.logic::removeListener);
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertEquals(3, calls[0] + calls[1]);
    }

    @Test
    void aListenerAddedDuringNotificationStartsWithTheNextNestedChange() {
        var fixture = new Fixture();
        var calls = new int[2];
        var nested = new boolean[1];
        Consumer<AEKey> added = _ -> calls[1]++;
        fixture.logic.addListener(_ -> {
            calls[0]++;
            if (!nested[0]) {
                nested[0] = true;
                fixture.logic.addListener(added);
                fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
            }
        });
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertArrayEquals(new int[]{2, 1}, calls);
        assertEquals(2, fixture.logic.getInventory().list.get(carrot));
        fixture.logic.getInventory().insert(carrot, 1, Actionable.MODULATE);
        assertArrayEquals(new int[]{3, 2}, calls);
    }

    @Test
    void fatalNotificationFailureStillPropagatesAndRemovalReleasesItsRegistration() {
        var fixture = new Fixture();
        Consumer<AEKey> fatal = _ -> {
            throw new OutOfMemoryError("fatal listener fixture");
        };
        fixture.logic.addListener(fatal);
        assertThrows(OutOfMemoryError.class,
            () -> fixture.logic.getInventory().insert(carrot, 2, Actionable.MODULATE));
        assertEquals(2, fixture.logic.getInventory().list.get(carrot));
        fixture.logic.removeListener(fatal);
        var calls = new int[1];
        fixture.logic.addListener(_ -> calls[0]++);
        fixture.logic.getInventory().insert(carrot, 3, Actionable.MODULATE);
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        assertEquals(1, calls[0]);
    }

    @Test
    void partialUsedAndMissingExtractionRetainsExactWaitingCountsAndProgress() {
        var fixture = new Fixture();
        fixture.missing.add(carrot, 7);
        fixture.emitted.add(carrot, 2);
        fixture.extractLimit = 3;
        assertTrue(fixture.submit().successful());
        assertEquals(2, fixture.extracts);
        assertEquals(6, fixture.logic.getInventory().list.get(carrot));
        var tag = new NBTTagCompound();
        fixture.logic.writeToNBT(tag);
        var job = tag.getCompoundTag("job");
        var waiting = GenericStack.readList(job.getTagList("waitingFor", 10));
        assertEquals(List.of(new GenericStack(carrot, 8)), waiting);
        assertEquals(8, fixture.logic.getElapsedTimeTracker().getRemainingWorkUnits());
        fixture.logic.cancel();
        assertEquals(6, fixture.returned);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
    }

    @Test
    void cancellationCommitsBothLinksAndDetachesTheJobBeforeTheRequesterCallback() {
        var fixture = new Fixture();
        var receipt = fixture.submit();
        var cpuLink = fixture.logic.getLastLink();
        fixture.onTerminal = () -> {
            assertFalse(fixture.logic.hasJob());
            assertTrue(Objects.requireNonNull(cpuLink).isCanceled());
            assertTrue(Objects.requireNonNull(receipt.link()).isCanceled());
            fixture.logic.cancel();
            Objects.requireNonNull(receipt.link()).cancel();
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.submit().errorCode());
            assertEquals(CraftingSubmitErrorCode.CPU_BUSY, fixture.logic.tryMergeJob(
                fixture.grid, fixture.plan, IActionSource.empty()).errorCode());
            fixture.logic.tickCraftingLogic(null, fixture.crafting);
            assertEquals(0, fixture.returned);
            var saved = new NBTTagCompound();
            fixture.logic.writeToNBT(saved);
            assertFalse(saved.hasKey("job"));
            throw new IllegalStateException("terminal notification unavailable");
        };
        assertDoesNotThrow(fixture.logic::cancel);
        assertEquals(1, fixture.terminalCalls);
        assertEquals(5, fixture.returned);
        assertFalse(fixture.logic.hasJob());
        fixture.onTerminal = null;
        assertTrue(fixture.submit().successful(), "the finish guard must release after notification failure");
    }

    @Test
    void completionReceiptSurvivesTerminalNotificationFailureAndCannotBeCanceledAgain() {
        var fixture = new Fixture();
        fixture.emitted.add(carrot, 1);
        var receipt = fixture.submit();
        var cpuLink = fixture.logic.getLastLink();
        fixture.onTerminal = () -> {
            assertFalse(fixture.logic.hasJob());
            assertTrue(Objects.requireNonNull(cpuLink).isDone());
            assertTrue(Objects.requireNonNull(receipt.link()).isDone());
            fixture.logic.cancel();
            Objects.requireNonNull(receipt.link()).cancel();
            throw new IllegalStateException("completion notification unavailable");
        };
        assertEquals(1, fixture.logic.insert(carrot, 1, Actionable.MODULATE));
        assertEquals(1, fixture.delivered);
        assertEquals(5, fixture.returned);
        assertEquals(1, fixture.terminalCalls);
        assertTrue(Objects.requireNonNull(receipt.link()).isDone());
        assertFalse(Objects.requireNonNull(receipt.link()).isCanceled());
        assertTrue(Objects.requireNonNull(cpuLink).isDone());
        assertNull(fixture.cluster.output);
        assertFalse(fixture.logic.hasJob());
        ((CraftingLink) cpuLink).markDone();
        Objects.requireNonNull(receipt.link()).cancel();
        assertEquals(1, fixture.terminalCalls);
    }

    @Test
    void displayAndSaveFailuresCannotPreventTerminalInventoryReturn() {
        var fixture = new Fixture();
        var receipt = fixture.submit();
        fixture.cluster.onDisplay = () -> {
            throw new IllegalStateException("display unavailable");
        };
        fixture.cluster.failSave = true;
        assertDoesNotThrow(fixture.logic::cancel);
        assertFalse(fixture.logic.hasJob());
        assertTrue(Objects.requireNonNull(receipt.link()).isCanceled());
        assertEquals(5, fixture.returned);
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        int attempts = fixture.cluster.saves;
        fixture.cluster.failSave = false;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(attempts + 1, fixture.cluster.saves);
        assertEquals(5, fixture.returned, "save retry must not return the same inputs again");
    }

    @Test
    void aReplacementPublishedByATerminalCallbackIsNotClearedOrDumpedByTheOldFinish() throws Exception {
        var fixture = new Fixture();
        fixture.submit();
        var replacementLink = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
            fixture.cluster);
        var replacement = new ExecutingCraftingJob(fixture.plan, _ -> {
        }, replacementLink, null,
            CraftingJobOptions.DEFAULT);
        var jobField = CraftingCpuLogic.class.getDeclaredField("job");
        jobField.setAccessible(true);
        fixture.onTerminal = () -> {
            try {
                jobField.set(fixture.logic, replacement);
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
            fixture.cluster.output = new GenericStack(carrot, 9);
        };
        fixture.logic.cancel();
        assertSame(replacementLink, fixture.logic.getLastLink());
        assertEquals(new GenericStack(carrot, 9), fixture.cluster.output);
        assertEquals(0, fixture.returned);
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
    }

    @Test
    void terminalPatternAndOwnerNotificationFailuresDoNotSkipRemainingCleanup() throws Exception {
        var fixture = new Fixture();
        var failOutputs = new boolean[1];
        var pattern = (IPatternDetails) Proxy.newProxyInstance(
            IPatternDetails.class.getClassLoader(),
            new Class<?>[]{IPatternDetails.class}, (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "getOutputs" -> {
                    if (failOutputs[0]) throw new IllegalStateException("terminal pattern unavailable");
                    yield List.of(new GenericStack(carrot, 1));
                }
                default -> null;
            });
        fixture.patterns.put(pattern, 1);
        var receipt = fixture.submit();
        var owned = new ExecutingCraftingJob(fixture.plan, _ -> {
        }, (CraftingLink) fixture.logic.getLastLink(), 123,
            CraftingJobOptions.DEFAULT);
        var jobField = CraftingCpuLogic.class.getDeclaredField("job");
        jobField.setAccessible(true);
        jobField.set(fixture.logic, owned);
        failOutputs[0] = true;
        fixture.cluster.failLevel = true;
        assertDoesNotThrow(fixture.logic::cancel);
        assertFalse(fixture.logic.hasJob());
        assertTrue(Objects.requireNonNull(receipt.link()).isCanceled());
        assertEquals(5, fixture.returned);
        assertNull(fixture.cluster.output);
    }

    @Test
    void fatalTerminalNotificationLeavesBothLinksTerminalAndInputsOwnedByTheCpu() {
        var fixture = new Fixture();
        var receipt = fixture.submit();
        var cpuLink = fixture.logic.getLastLink();
        fixture.onTerminal = () -> {
            throw new OutOfMemoryError("fatal terminal fixture");
        };
        assertThrows(OutOfMemoryError.class, fixture.logic::cancel);
        assertFalse(fixture.logic.hasJob());
        assertTrue(Objects.requireNonNull(cpuLink).isCanceled());
        assertTrue(Objects.requireNonNull(receipt.link()).isCanceled());
        assertEquals(5, fixture.logic.getInventory().list.get(carrot));
        assertEquals(0, fixture.returned);
        fixture.onTerminal = null;
        fixture.logic.tickCraftingLogic(null, fixture.crafting);
        assertEquals(5, fixture.returned);
        assertEquals(1, fixture.terminalCalls);
        assertTrue(fixture.submit().successful());
    }

    @Test
    void detachedAndLateLinksKeepTheFirstTerminalStateWithoutRepeatingNotifications() {
        for (boolean cancel : List.of(false, true)) {
            var fixture = new Fixture();
            var id = UUID.randomUUID();
            var cpuLink = new CraftingLink(CraftingCpuHelper.generateLinkData(id, false, false), fixture.cluster);
            var nexus = new CraftingLinkNexus(id);
            cpuLink.setNexus(nexus);
            if (cancel) cpuLink.cancel();
            else cpuLink.markDone();
            var requesterLink = new CraftingLink(CraftingCpuHelper.generateLinkData(id, false, true), fixture.requester);
            requesterLink.setNexus(nexus);
            assertEquals(1, fixture.terminalCalls);
            assertEquals(cancel, requesterLink.isCanceled());
            assertEquals(!cancel, requesterLink.isDone());
            assertEquals(0, cpuLink.insert(carrot, 1, Actionable.MODULATE));
            assertEquals(0, fixture.delivered);
            requesterLink.setNexus(nexus);
            requesterLink.setNexus(null);
            cpuLink.setNexus(null);
            cpuLink.cancel();
            cpuLink.markDone();
            assertEquals(cancel, cpuLink.isCanceled());
            assertEquals(!cancel, cpuLink.isDone());
            assertEquals(cancel, requesterLink.isCanceled());
            assertEquals(!cancel, requesterLink.isDone());
            assertEquals(1, fixture.terminalCalls);
            var saved = new NBTTagCompound();
            requesterLink.writeToNBT(saved);
            var restored = new CraftingLink(saved, fixture.requester);
            assertEquals(cancel, restored.isCanceled());
            assertEquals(!cancel, restored.isDone());
        }
    }

    @Test
    void standaloneCompletionIsRetainedLocallyWithoutANexus() {
        var fixture = new Fixture();
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), fixture.cluster);
        link.markDone();
        link.cancel();
        assertTrue(link.isDone());
        assertFalse(link.isCanceled());
        var saved = new NBTTagCompound();
        link.writeToNBT(saved);
        assertTrue(new CraftingLink(saved, fixture.cluster).isDone());
    }

    @Test
    void cleanupAfterAnAlreadyCompletedLinkKeepsCompletionAsTheFirstTerminalOutcome() {
        var fixture = new Fixture();
        var receipt = fixture.submit();
        var cpuLink = (CraftingLink) fixture.logic.getLastLink();
        Objects.requireNonNull(cpuLink).markDone();
        assertEquals(1, fixture.terminalCalls);
        fixture.logic.cancel();
        assertFalse(fixture.logic.hasJob());
        assertTrue(cpuLink.isDone());
        assertTrue(Objects.requireNonNull(receipt.link()).isDone());
        assertFalse(cpuLink.isCanceled());
        assertFalse(Objects.requireNonNull(receipt.link()).isCanceled());
        assertEquals(1, fixture.terminalCalls);
        assertEquals(5, fixture.returned);
    }

    private static final class Fixture {
        private final KeyCounter missing = new KeyCounter();
        private final KeyCounter emitted = new KeyCounter();
        private final Object2LongOpenHashMap<IPatternDetails> patterns = new Object2LongOpenHashMap<>();
        private final IGrid grid;
        private final TestCluster cluster;
        private final CraftingCpuLogic logic;
        private final ICraftingPlan plan;
        private final ICraftingRequester requester = (ICraftingRequester) Proxy.newProxyInstance(
            ICraftingRequester.class.getClassLoader(), new Class<?>[]{ICraftingRequester.class},
            (_, method, args) -> switch (method.getName()) {
                case "getRequestedJobs" -> ImmutableSet.of();
                case "jobStateChange" -> {
                    this.terminalCalls++;
                    if (this.onTerminal != null) this.onTerminal.run();
                    yield null;
                }
                case "insertCraftedItems" -> {
                    this.delivered += (long) args[2];
                    yield (long) args[2];
                }
                default -> null;
            });
        private int extracts, outputReads;
        private long returned;
        private long delivered;
        private int terminalCalls;
        private Runnable onTerminal;
        private boolean failRepeatedOutput;
        private String failedGetter;
        private long extractLimit = Long.MAX_VALUE;
        private TestCraftingService crafting;

        private Fixture() {
            var inventory = (MEStorageMonitor) Proxy.newProxyInstance(MEStorageMonitor.class.getClassLoader(),
                new Class<?>[]{MEStorageMonitor.class}, (_, method, args) -> switch (method.getName()) {
                    case "extract" -> {
                        extracts++;
                        yield Math.min((long) args[1], extractLimit);
                    }
                    case "insert" -> {
                        returned += (long) args[1];
                        yield (long) args[1];
                    }
                    default -> null;
                });
            var storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[]{IStorageService.class}, (_, method, _) -> method.getName().equals("getInventory")
                    ? inventory : null);
            grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (_, method, _) -> switch (method.getName()) {
                    case "getStorageService" -> storage;
                    case "getCraftingService" -> this.crafting;
                    default -> null;
                });
            crafting = new TestCraftingService(grid, storage);
            cluster = new TestCluster(grid);
            logic = cluster.craftingLogic;
            var used = new KeyCounter();
            used.add(carrot, 5);
            plan = (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class}, (_, method, _) -> {
                    if (method.getName().equals(failedGetter)) throw new IllegalStateException("plan unavailable");
                    return switch (method.getName()) {
                        case "finalOutput" -> {
                            outputReads++;
                            if (failRepeatedOutput && outputReads > 1)
                                throw new IllegalStateException("repeated getter");
                            yield new GenericStack(carrot, 1);
                        }
                        case "bytes" -> 128L;
                        case "usedItems" -> used;
                        case "emittedItems" -> emitted;
                        case "missingItems" -> missing;
                        case "patternTimes" -> patterns;
                        case "intermediateFinalOutputAmount" -> 0L;
                        case "simulation", "multiplePaths" -> false;
                        default -> throw new AssertionError(method.getName());
                    };
                });
        }

        private ICraftingSubmitResult submit() {
            return logic.trySubmitJob(grid, plan, IActionSource.empty(), requester);
        }
    }

    private static final class TestCraftingService extends CraftingService {
        private final List<CraftingLink> links = new ArrayList<>();
        private boolean failRegistration;

        private TestCraftingService(IGrid grid, IStorageService storage) {
            super(grid, storage, null);
        }

        @Override
        public void addLink(CraftingLink link) {
            if (this.failRegistration) throw new IllegalStateException("registration unavailable");
            super.addLink(link);
            links.add(link);
        }
    }

    private static final class TestCluster extends CraftingCPUCluster {
        private final IGrid grid;
        private boolean active = true, failSave, failLevel;
        private int saves;
        private Runnable onDisplay, onSave;
        private GenericStack output;

        private TestCluster(IGrid grid) {
            super(BlockPos.ORIGIN, BlockPos.ORIGIN);
            this.grid = grid;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public long getAvailableStorage() {
            return 1024;
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
        public World getLevel() {
            if (failLevel) throw new IllegalStateException("owner level unavailable");
            return null;
        }

        @Override
        public void updateOutput(GenericStack output) {
            this.output = output;
            var callback = this.onDisplay;
            this.onDisplay = null;
            if (callback != null) callback.run();
        }

        @Override
        public void markDirty() {
            saves++;
            if (onSave != null) onSave.run();
            if (failSave) throw new IllegalStateException("save unavailable");
        }
    }
}
