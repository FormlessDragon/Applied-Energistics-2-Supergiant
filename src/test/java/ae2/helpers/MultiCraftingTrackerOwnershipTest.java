package ae2.helpers;

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.AEConfig;
import com.google.common.collect.ImmutableSet;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiCraftingTrackerOwnershipTest {
    private static World world;
    private static AEKey apple;
    private static Object previousSide;

    @BeforeAll
    static void setup() throws Exception {
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
        apple = AEItemKey.of(Items.APPLE);
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        world = (WorldServer) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
    }

    @AfterAll
    static void restoreSide() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(FMLCommonHandler.instance(), previousSide);
    }

    private static ICraftingPlan plan() {
        return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
            new Class<?>[]{ICraftingPlan.class}, (p, m, a) -> {
                throw new AssertionError(m.getName());
            });
    }

    private static NBTTagCompound linkData(int slot, UUID id) {
        var result = new NBTTagCompound();
        var link = new NBTTagCompound();
        link.setUniqueId("craftId", id);
        link.setBoolean("req", true);
        result.setTag("links-" + slot, link);
        return result;
    }

    @Test
    void amountChangeCancelsOldFutureAndStartsNewRequest() {
        var fixture = new Fixture();
        var old = fixture.next;
        fixture.handle(1);
        fixture.next = new CompletableFuture<>();
        fixture.handle(2);
        assertTrue(old.isCancelled());
        assertEquals(2, fixture.starts);
        assertEquals(0, fixture.submissions);
    }

    @Test
    void serviceChangeRejectsCompletedOldPlan() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.service = fixture.service();
        fixture.next = new CompletableFuture<>();
        fixture.handle(1);
        assertEquals(0, fixture.submissions);
        assertEquals(2, fixture.starts);
    }

    @Test
    void cancellationDuringStartCancelsReturnedFutureAndBlocksReentry() {
        var fixture = new Fixture();
        var future = fixture.next;
        fixture.onStart = () -> {
            fixture.tracker.cancel();
            fixture.handle(1);
        };
        fixture.handle(1);
        assertTrue(future.isCancelled());
        assertFalse(fixture.tracker.isBusy(0));
        assertEquals(1, fixture.starts);
    }

    @Test
    void cancellationDuringGetPreventsSubmit() {
        var fixture = new Fixture();
        fixture.next = new CompletableFuture<>() {
            @Override
            public ICraftingPlan get() {
                fixture.tracker.cancel();
                return plan();
            }
        };
        fixture.next.complete(null);
        fixture.handle(1);
        fixture.handle(1);
        assertEquals(0, fixture.submissions);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void sameRequestSubmitsAndCancelRevokesRoutingBeforeCallbacks() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.submissions);
        fixture.onCancel = () -> {
            var link = fixture.tracker.getRequestedJobs().iterator().next();
            assertEquals(-1, fixture.tracker.getSlot(link));
            fixture.handle(1);
        };
        fixture.tracker.cancel();
        assertEquals(1, fixture.starts);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void confirmedLinkCancelFailureRetainsOwnershipAndBlocksRoutingAcrossSave() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.onCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        fixture.tracker.cancel();
        assertEquals(1, fixture.tracker.getRequestedJobs().size());
        assertTrue(fixture.tracker.isBusy(0));
        assertEquals(-1, fixture.tracker.getSlot(link));
        for (int i = 0; i < 50; i++) fixture.handle(1);
        assertEquals(1, fixture.submissions);
        assertEquals(1, fixture.linkCancellations);
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        assertTrue(saved.getCompoundTag("links-0").getBoolean("trackerCancelRequested"));
        var restored = new Fixture();
        restored.tracker.readFromNBT(saved);
        var restoredLink = restored.tracker.getRequestedJobs().iterator().next();
        assertEquals(fixture.linkId, restoredLink.getCraftingID());
        assertEquals(-1, restored.tracker.getSlot(restoredLink));
        assertTrue(restored.tracker.isBusy(0));
        restored.tracker.cancel();
        assertTrue(restored.tracker.getRequestedJobs().isEmpty());
        assertFalse(restored.tracker.isBusy(0));
    }

    @Test
    void cancellationWithoutTerminalReceiptRemainsOwnedUntilNotification() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.linkCancelAccepted = false;
        fixture.tracker.cancel();
        assertTrue(fixture.tracker.isBusy(0));
        assertEquals(-1, fixture.tracker.getSlot(link));
        fixture.handle(1);
        assertEquals(1, fixture.linkCancellations);
        fixture.tracker.jobStateChange(link);
        assertFalse(fixture.tracker.isBusy(0));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void confirmedCancelFailureDoesNotSkipSiblingLinkOrFuture() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        fixture.next = new CompletableFuture<>();
        fixture.tracker.handleCrafting(1, apple, 1, world, fixture.service, fixture.source);
        var pending = fixture.next;
        fixture.onCancel = () -> {
            throw new IllegalStateException("first cancel failed");
        };
        fixture.tracker.cancel();
        assertTrue(pending.isCancelled());
        assertTrue(fixture.tracker.isBusy(0));
        assertFalse(fixture.tracker.isBusy(1));

        var second = new Fixture();
        second.handle(1);
        second.next.complete(plan());
        second.handle(1);
        second.next = new CompletableFuture<>();
        second.tracker.handleCrafting(1, apple, 1, world, second.service, second.source);
        second.next.complete(plan());
        second.tracker.handleCrafting(1, apple, 1, world, second.service, second.source);
        var calls = new AtomicInteger();
        second.onCancel = () -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("first only");
        };
        second.tracker.cancel();
        assertEquals(2, calls.get());
        assertTrue(second.tracker.isBusy(0));
        assertFalse(second.tracker.isBusy(1));
    }

    @Test
    void nestedCancelDoesNotRepeatTheSameExternalCancellation() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        fixture.onCancel = fixture.tracker::cancel;
        fixture.tracker.cancel();
        assertEquals(1, fixture.linkCancellations);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void cancellationCannotBeErasedOrReplacedBySameSlotNbt() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.onCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        fixture.tracker.cancel();
        fixture.tracker.readFromNBT(new NBTTagCompound());
        assertSame(link, fixture.tracker.getRequestedJobs().iterator().next());
        assertEquals(-1, fixture.tracker.getSlot(link));
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        fixture.tracker.readFromNBT(saved);
        assertSame(link, fixture.tracker.getRequestedJobs().iterator().next());
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(
            linkData(0, UUID.randomUUID())));
        assertSame(link, fixture.tracker.getRequestedJobs().iterator().next());
        assertEquals(-1, fixture.tracker.getSlot(link));
    }

    @Test
    void cancellationSettledDuringRestoreCannotBeResurrectedByTheOuterRead() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        var sibling = UUID.randomUUID();
        fixture.onCancel = () -> {
            fixture.tracker.readFromNBT(linkData(1, sibling));
            throw new IllegalStateException("cancel unavailable");
        };
        fixture.tracker.cancel();
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        fixture.onLinkIdRead = () -> fixture.tracker.jobStateChange(link);
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(saved));
        assertEquals(1, fixture.tracker.getRequestedJobs().size());
        assertEquals(sibling, fixture.tracker.getRequestedJobs().iterator().next().getCraftingID());
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void fatalCancellationPropagatesAndReleasesTheReentryGuardWithoutDroppingLink() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.onCancel = () -> {
            throw new OutOfMemoryError("fatal cancel fixture");
        };
        assertThrows(OutOfMemoryError.class, fixture.tracker::cancel);
        assertSame(link, fixture.tracker.getRequestedJobs().iterator().next());
        assertEquals(-1, fixture.tracker.getSlot(link));
        fixture.onCancel = null;
        fixture.tracker.cancel();
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void invalidCancellationFlagCannotPublishTheRestOfTheLoadedLinks() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var original = fixture.tracker.getRequestedJobs();
        var saved = linkData(1, UUID.randomUUID());
        saved.getCompoundTag("links-1").setString("trackerCancelRequested", "true");
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(saved));
        assertEquals(original, fixture.tracker.getRequestedJobs());
    }

    @Test
    void cancelStateGetterFailureRetainsLinkAndRevokedRouting() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.onLinkStatus = () -> {
            throw new IllegalStateException("state unavailable");
        };
        fixture.tracker.cancel();
        assertSame(link, fixture.tracker.getRequestedJobs().iterator().next());
        assertEquals(-1, fixture.tracker.getSlot(link));
        fixture.tracker.jobStateChange(link);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void ordinaryCancelFailureDoesNotSkipRemainingJobs() {
        var fixture = new Fixture();
        var calls = new AtomicInteger();
        fixture.next = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                throw new IllegalStateException("cancel fixture");
            }
        };
        fixture.handle(1);
        var second = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean cancel(boolean interrupt) {
                calls.incrementAndGet();
                return super.cancel(interrupt);
            }
        };
        fixture.next = second;
        fixture.tracker.handleCrafting(1, apple, 1, world, fixture.service, fixture.source);
        fixture.tracker.cancel();
        assertTrue(second.isCancelled());
        assertEquals(1, calls.get());
        assertFalse(fixture.tracker.isBusy(0));
        assertFalse(fixture.tracker.isBusy(1));
    }

    @Test
    void fullCancellationDuringSubmitCancelsLateLinkWithoutRestoringItsSlot() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onSubmit = () -> {
            fixture.tracker.cancel();
            fixture.handle(1);
        };
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.submissions);
        assertEquals(1, fixture.linkCancellations);
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void planningInvalidationOrSuspensionDuringSubmitStillOwnsCommittedProduction() {
        for (boolean suspend : new boolean[]{false, true}) {
            var fixture = new Fixture();
            fixture.handle(1);
            fixture.next.complete(plan());
            fixture.onSubmit = suspend ? fixture.tracker::suspendCalculations
                : fixture.tracker::cancelCalculations;
            assertTrue(fixture.handle(1));
            assertEquals(1, fixture.tracker.getRequestedJobs().size());
            assertTrue(fixture.tracker.isBusy(0));
            assertEquals(0, fixture.linkCancellations);
        }
    }

    @Test
    void restoredProductionDuringSubmitIsNotOverwrittenByTheLateReceipt() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        var restored = UUID.randomUUID();
        fixture.onSubmit = () -> fixture.tracker.readFromNBT(linkData(0, restored));
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.linkCancellations);
        assertEquals(restored, fixture.tracker.getRequestedJobs().iterator().next().getCraftingID());
    }

    @Test
    void receiptGetterCancellationAlsoCancelsReturnedLink() {
        for (boolean atLink : new boolean[]{true, false}) {
            var fixture = new Fixture();
            fixture.handle(1);
            fixture.next.complete(plan());
            if (atLink) fixture.onReceiptLink = fixture.tracker::cancel;
            else fixture.onReceiptSuccess = fixture.tracker::cancel;
            assertTrue(fixture.handle(1));
            assertEquals(1, fixture.linkCancellations);
            assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
            assertFalse(fixture.tracker.isBusy(0));
        }
    }

    @Test
    void submitExceptionStopsDuplicateRetryAndPersistsUnconfirmedRequest() {
        var fixture = new Fixture();
        fixture.handle(3);
        fixture.next.complete(plan());
        fixture.onSubmit = () -> {
            throw new IllegalStateException("commit outcome unavailable");
        };
        assertFalse(fixture.handle(3));
        for (int i = 0; i < 50; i++) fixture.handle(3);
        fixture.tracker.cancelCalculations();
        fixture.tracker.cancel();
        fixture.handle(3);
        assertEquals(1, fixture.starts);
        assertEquals(1, fixture.submissions);
        assertTrue(fixture.tracker.isBusy(0));
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        var pending = saved.getCompoundTag("unconfirmed-submission-0");
        assertEquals(1, pending.getInteger("schema"));
        assertTrue(pending.hasUniqueId("submissionId"));
        assertEquals(new GenericStack(apple, 3), GenericStack.readTag(pending.getCompoundTag("request")));
        var restored = new Fixture();
        restored.tracker.readFromNBT(saved);
        restored.handle(3);
        assertEquals(0, restored.starts);
        assertTrue(restored.tracker.isBusy(0));
        var again = new NBTTagCompound();
        restored.tracker.writeToNBT(again);
        assertEquals(pending, again.getCompoundTag("unconfirmed-submission-0"));
    }

    @Test
    void receiptFailureRetainsAnyReturnedLinkAndNeverResubmits() {
        for (boolean atLink : new boolean[]{true, false}) {
            var fixture = new Fixture();
            fixture.handle(1);
            fixture.next.complete(plan());
            Runnable failure = () -> {
                throw new IllegalStateException("receipt unavailable");
            };
            if (atLink) fixture.onReceiptLink = failure;
            else fixture.onReceiptSuccess = failure;
            assertFalse(fixture.handle(1));
            fixture.handle(1);
            assertEquals(1, fixture.submissions);
            assertTrue(fixture.tracker.isBusy(0));
            assertEquals(atLink ? 0 : 1, fixture.tracker.getRequestedJobs().size());
        }
    }

    @Test
    void successfulReceiptWithoutRequesterLinkIsUnconfirmed() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.missingReceiptLink = true;
        assertFalse(fixture.handle(1));
        fixture.handle(1);
        assertEquals(1, fixture.submissions);
        assertTrue(fixture.tracker.isBusy(0));
    }

    @Test
    void confirmedRejectionReleasesSubmissionAndPreservesRetryCooldown() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.missingReceiptLink = true;
        fixture.rejected = true;
        assertFalse(fixture.handle(1));
        assertFalse(fixture.tracker.isBusy(0));
        fixture.handle(1);
        assertEquals(1, fixture.starts);
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        assertFalse(saved.hasKey("unconfirmed-submission-0"));
    }

    @Test
    void cancellationFailureRetainsKnownReceiptAndOriginalIdentityAcrossSave() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onSubmit = fixture.tracker::cancel;
        fixture.onCancel = () -> {
            throw new IllegalStateException("cancel outcome unavailable");
        };
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.tracker.getRequestedJobs().size());
        assertTrue(fixture.tracker.isBusy(0));
        fixture.handle(1);
        assertEquals(1, fixture.submissions);
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        var restored = new Fixture();
        restored.tracker.readFromNBT(saved);
        assertEquals(fixture.linkId, restored.tracker.getRequestedJobs().iterator().next().getCraftingID());
        assertTrue(restored.tracker.isBusy(0));
        restored.tracker.cancel();
        assertFalse(restored.tracker.isBusy(0));
    }

    @Test
    void unknownOrMalformedPendingDataIsPreservedAndCannotBeSilentlyCleared() {
        var fixture = new Fixture();
        var saved = new NBTTagCompound();
        saved.setString("unconfirmed-submission-0", "unknown payload");
        fixture.tracker.readFromNBT(saved);
        fixture.tracker.readFromNBT(new NBTTagCompound());
        fixture.handle(1);
        assertEquals(0, fixture.starts);
        assertTrue(fixture.tracker.isBusy(0));
        var again = new NBTTagCompound();
        fixture.tracker.writeToNBT(again);
        assertEquals(saved.getTag("unconfirmed-submission-0"), again.getTag("unconfirmed-submission-0"));
        var conflict = linkData(1, UUID.randomUUID());
        conflict.setString("unconfirmed-submission-0", "different unresolved request");
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(conflict));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void snapshotDuringSubmitKeepsAnUnconfirmedRecordWithoutReplayingOnLoad() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        var saved = new NBTTagCompound();
        fixture.onSubmit = () -> fixture.tracker.writeToNBT(saved);
        assertTrue(fixture.handle(1));
        assertTrue(saved.hasKey("unconfirmed-submission-0"));
        var restored = new Fixture();
        restored.tracker.readFromNBT(saved);
        restored.handle(1);
        assertEquals(0, restored.starts);
        fixture.tracker.writeToNBT(saved);
        assertFalse(saved.hasKey("unconfirmed-submission-0"));
        assertTrue(saved.hasKey("links-0"));
    }

    @Test
    void terminalCallbackReleasesKnownUnconfirmedReceiptWithoutTouchingOtherProduction() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onReceiptSuccess = () -> {
            throw new IllegalStateException("receipt unavailable");
        };
        assertFalse(fixture.handle(1));
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        fixture.tracker.jobStateChange(link);
        assertFalse(fixture.tracker.isBusy(0));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void terminalCallbackWhileReadingReceiptCannotRepublishDeadLink() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onReceiptSuccess = () -> fixture.tracker.jobStateChange(
            fixture.tracker.getRequestedJobs().iterator().next());
        assertTrue(fixture.handle(1));
        assertFalse(fixture.tracker.isBusy(0));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void sameSubmissionIdCannotReplaceUnconfirmedResourceIdentity() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onSubmit = () -> {
            throw new IllegalStateException("unknown commit");
        };
        fixture.handle(1);
        var saved = new NBTTagCompound();
        fixture.tracker.writeToNBT(saved);
        var pending = saved.getCompoundTag("unconfirmed-submission-0");
        pending.getCompoundTag("request").setLong(GenericStack.AMOUNT_FIELD, 2);
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(saved));
        var original = new NBTTagCompound();
        fixture.tracker.writeToNBT(original);
        assertEquals(1, original.getCompoundTag("unconfirmed-submission-0")
                                .getCompoundTag("request").getLong(GenericStack.AMOUNT_FIELD));
    }

    @Test
    void returnedButUnconfirmedLinkCannotRouteOutputIntoAConfiguredSlot() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onReceiptSuccess = () -> {
            throw new IllegalStateException("receipt unavailable");
        };
        assertFalse(fixture.handle(1));
        var link = fixture.tracker.getRequestedJobs().iterator().next();
        assertEquals(-1, fixture.tracker.getSlot(link));
    }

    @Test
    void terminalCancellationCallbackReleasesReceiptWithoutRequeryingFailingStatus() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onSubmit = fixture.tracker::cancel;
        fixture.onCancel = () -> fixture.tracker.jobStateChange(
            fixture.tracker.getRequestedJobs().iterator().next());
        fixture.onLinkStatus = () -> {
            throw new IllegalStateException("state unavailable");
        };
        assertTrue(fixture.handle(1));
        assertFalse(fixture.tracker.isBusy(0));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void contradictoryFailureReceiptCancelsItsLinkBeforeApplyingCooldown() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.rejected = true;
        assertFalse(fixture.handle(1));
        assertEquals(1, fixture.linkCancellations);
        assertFalse(fixture.tracker.isBusy(0));
        fixture.handle(1);
        assertEquals(1, fixture.starts);
    }

    @Test
    void fatalSubmitErrorPropagatesWhileLeavingARecordThatBlocksReplay() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onSubmit = () -> {
            throw new OutOfMemoryError("fatal fixture");
        };
        assertThrows(OutOfMemoryError.class, () -> fixture.handle(1));
        fixture.handle(1);
        assertEquals(1, fixture.submissions);
        assertTrue(fixture.tracker.isBusy(0));
    }

    @Test
    void suspensionReleasesCalculationAndBlocksLaterStartsUntilMatchingResume() {
        var fixture = new Fixture();
        var old = fixture.next;
        fixture.handle(1);
        long generation = fixture.tracker.suspendCalculations();
        assertTrue(old.isCancelled());
        fixture.handle(1);
        assertEquals(1, fixture.starts);
        fixture.tracker.resumeCalculations(generation - 1);
        fixture.handle(1);
        assertEquals(1, fixture.starts);
        fixture.tracker.resumeCalculations(generation);
        fixture.next = new CompletableFuture<>();
        fixture.handle(1);
        assertEquals(2, fixture.starts);
    }

    @Test
    void newerCloseDuringInitializationRejectsOldResumeAndPreservesProduction() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        assertTrue(fixture.handle(1));
        var production = fixture.tracker.getRequestedJobs();
        long old = fixture.tracker.suspendCalculations();
        long current = fixture.tracker.suspendCalculations();
        fixture.tracker.resumeCalculations(old);
        fixture.tracker.handleCrafting(1, apple, 1, world, fixture.service, fixture.source);
        assertEquals(1, fixture.starts);
        assertEquals(production, fixture.tracker.getRequestedJobs());
        fixture.tracker.resumeCalculations(current);
        fixture.next = new CompletableFuture<>();
        fixture.tracker.handleCrafting(1, apple, 1, world, fixture.service, fixture.source);
        assertEquals(2, fixture.starts);
    }

    @Test
    void suspensionDuringBeginCancelsFutureAfterReturn() {
        var fixture = new Fixture();
        var future = fixture.next;
        fixture.onStart = fixture.tracker::suspendCalculations;
        fixture.handle(1);
        assertTrue(future.isCancelled());
        assertFalse(fixture.tracker.isBusy(0));
        fixture.handle(1);
        assertEquals(1, fixture.starts);
    }

    @Test
    void configurationInvalidationRetainsProductionAndCancelsPendingOtherSlot() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        assertTrue(fixture.handle(1));
        var production = fixture.tracker.getRequestedJobs();
        var pending = new CompletableFuture<ICraftingPlan>();
        fixture.next = pending;
        fixture.tracker.handleCrafting(1, apple, 1, world, fixture.service, fixture.source);
        fixture.tracker.cancelCalculations();
        assertTrue(pending.isCancelled());
        assertEquals(production, fixture.tracker.getRequestedJobs());
        assertTrue(fixture.tracker.isBusy(0));
        assertFalse(fixture.tracker.isBusy(1));
    }

    @Test
    void configurationInvalidationDuringBeginReleasesLateFuture() {
        var fixture = new Fixture();
        var future = fixture.next;
        fixture.onStart = fixture.tracker::cancelCalculations;
        fixture.handle(1);
        assertTrue(future.isCancelled());
        assertFalse(fixture.tracker.isBusy(0));
        fixture.onStart = null;
        fixture.next = new CompletableFuture<>();
        fixture.handle(1);
        assertTrue(fixture.tracker.isBusy(0));
        assertEquals(2, fixture.starts);
    }

    @Test
    void configurationReadCancelsPendingAndBlocksCallbackStartsUntilExit() {
        var fixture = new Fixture();
        var future = fixture.next;
        fixture.handle(1);
        fixture.tracker.readConfiguration(() -> {
            assertTrue(future.isCancelled());
            fixture.handle(1);
            assertEquals(1, fixture.starts);
        });
        fixture.next = new CompletableFuture<>();
        fixture.handle(1);
        assertEquals(2, fixture.starts);
    }

    @Test
    void linkNbtReplacesMissingSlotsAndRoundTripsOriginalCraftId() {
        var fixture = new Fixture();
        var id = UUID.randomUUID();
        var data = new NBTTagCompound();
        var link = new NBTTagCompound();
        link.setUniqueId("craftId", id);
        link.setBoolean("req", true);
        data.setTag("links-1", link);
        fixture.tracker.readFromNBT(data);
        assertEquals(id, fixture.tracker.getRequestedJobs().iterator().next().getCraftingID());
        var written = new NBTTagCompound();
        fixture.tracker.writeToNBT(written);
        assertEquals(id, written.getCompoundTag("links-1").getUniqueId("craftId"));
        fixture.tracker.readFromNBT(new NBTTagCompound());
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
    }

    @Test
    void corruptLinkReadRetainsOldLinksAndDoesNotReopenSuspendedTracker() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.handle(1);
        var old = fixture.tracker.getRequestedJobs();
        var data = new NBTTagCompound();
        var bad = new NBTTagCompound();
        bad.setBoolean("req", false);
        data.setTag("links-1", bad);
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readFromNBT(data));
        assertEquals(old, fixture.tracker.getRequestedJobs());
        fixture.tracker.suspendCalculations();
        fixture.tracker.readFromNBT(new NBTTagCompound());
        fixture.handle(1);
        assertEquals(1, fixture.starts);
    }

    @Test
    void failedConfigurationReadReleasesTemporaryGuard() {
        var fixture = new Fixture();
        assertThrows(IllegalStateException.class, () -> fixture.tracker.readConfiguration(() -> {
            throw new IllegalStateException("read fixture");
        }));
        fixture.handle(1);
        assertEquals(1, fixture.starts);
    }

    @Test
    void linkStateGetterCancellationCannotCrashOrRestoreDetachedArray() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onLinkStatus = fixture.tracker::cancel;
        assertTrue(fixture.handle(1));
        assertTrue(fixture.tracker.getRequestedJobs().isEmpty());
        assertFalse(fixture.tracker.isBusy(0));
        assertEquals(1, fixture.submissions);
    }

    @Test
    void ordinaryLinkStateFailureRetainsCommittedLink() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.onLinkStatus = () -> {
            throw new IllegalStateException("link state fixture");
        };
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.tracker.getRequestedJobs().size());
        assertTrue(fixture.tracker.isBusy(0));
        fixture.handle(1);
        assertEquals(1, fixture.submissions);
    }

    @Test
    void linkStateGetterNbtReplacementRemainsPublished() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        var id = UUID.randomUUID();
        fixture.onLinkStatus = () -> {
            var data = new NBTTagCompound();
            var link = new NBTTagCompound();
            link.setUniqueId("craftId", id);
            link.setBoolean("req", true);
            data.setTag("links-1", link);
            fixture.tracker.readFromNBT(data);
        };
        assertTrue(fixture.handle(1));
        assertEquals(1, fixture.tracker.getRequestedJobs().size());
        assertEquals(id, fixture.tracker.getRequestedJobs().iterator().next().getCraftingID());
        assertFalse(fixture.tracker.isBusy(0));
        assertTrue(fixture.tracker.isBusy(1));
    }

    @Test
    void ownerGetterCancellationBeforeStartDoesNotRegisterNewCalculation() {
        var fixture = new Fixture();
        fixture.onOwnerRead = fixture.tracker::cancelCalculations;
        fixture.handle(1);
        assertEquals(0, fixture.starts);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void ownerGetterCancellationAtFinalSubmitCheckPreventsSubmission() {
        var fixture = new Fixture();
        fixture.handle(1);
        fixture.next.complete(plan());
        fixture.ownerReads = 0;
        fixture.onOwnerRead = () -> {
            if (fixture.ownerReads == 4) fixture.tracker.cancelCalculations();
        };
        fixture.handle(1);
        assertEquals(0, fixture.submissions);
        assertFalse(fixture.tracker.isBusy(0));
    }

    @Test
    void ownerGetterCancellationDuringRequestMatchDoesNotRestartOldCall() {
        var fixture = new Fixture();
        fixture.handle(1);
        var old = fixture.next;
        fixture.onOwnerRead = fixture.tracker::cancelCalculations;
        fixture.handle(1);
        assertTrue(old.isCancelled());
        assertEquals(1, fixture.starts);
        assertFalse(fixture.tracker.isBusy(0));
    }

    private static final class Fixture {
        private final ICraftingRequester owner = (ICraftingRequester) Proxy.newProxyInstance(
            ICraftingRequester.class.getClassLoader(), new Class<?>[]{ICraftingRequester.class},
            (p, m, a) -> {
                if (m.getName().equals("getActionableNode")) {
                    this.ownerReads++;
                    if (this.onOwnerRead != null) this.onOwnerRead.run();
                }
                return m.getName().equals("getRequestedJobs") ? ImmutableSet.of() : null;
            });
        private final MultiCraftingTracker tracker = new MultiCraftingTracker(owner, 2);
        private final IActionSource source = IActionSource.ofMachine(owner);
        private final UUID linkId = UUID.randomUUID();
        private Runnable onOwnerRead;
        private int ownerReads;
        private ICraftingService service = service();
        private CompletableFuture<ICraftingPlan> next = new CompletableFuture<>();
        private int starts, submissions;
        private Runnable onStart, onCancel, onLinkStatus, onSubmit, onReceiptLink, onReceiptSuccess;
        private Runnable onLinkIdRead;
        private boolean missingReceiptLink, rejected;
        private boolean linkCancelAccepted = true;
        private int linkCancellations;

        private boolean handle(long amount) {
            return tracker.handleCrafting(0, apple, amount, world, service, source);
        }

        private ICraftingService service() {
            return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
                new Class<?>[]{ICraftingService.class}, (p, m, a) -> {
                    if (m.getName().equals("beginCraftingCalculation")) {
                        starts++;
                        if (onStart != null) onStart.run();
                        return next;
                    }
                    if (m.getName().equals("submitJob")) {
                        submissions++;
                        if (onSubmit != null) onSubmit.run();
                        boolean[] canceled = {false};
                        var link = (ICraftingLink) Proxy.newProxyInstance(ICraftingLink.class.getClassLoader(),
                            new Class<?>[]{ICraftingLink.class}, (lp, lm, la) -> switch (lm.getName()) {
                                case "isCanceled", "isDone" -> {
                                    var callback = onLinkStatus;
                                    onLinkStatus = null;
                                    if (callback != null) callback.run();
                                    yield lm.getName().equals("isCanceled") && canceled[0];
                                }
                                case "isStandalone" -> false;
                                case "cancel" -> {
                                    linkCancellations++;
                                    if (onCancel != null) onCancel.run();
                                    canceled[0] = linkCancelAccepted;
                                    yield null;
                                }
                                case "getCraftingID" -> {
                                    var callback = onLinkIdRead;
                                    onLinkIdRead = null;
                                    if (callback != null) callback.run();
                                    yield linkId;
                                }
                                case "writeToNBT" -> {
                                    var tag = (NBTTagCompound) la[0];
                                    tag.setUniqueId("craftId", linkId);
                                    tag.setBoolean("req", true);
                                    tag.setBoolean("canceled", canceled[0]);
                                    yield null;
                                }
                                case "hashCode" -> System.identityHashCode(lp);
                                case "equals" -> lp == la[0];
                                default -> null;
                            });
                        return new ICraftingSubmitResult() {
                            public boolean successful() {
                                if (onReceiptSuccess != null) onReceiptSuccess.run();
                                return !rejected;
                            }

                            public CraftingSubmitErrorCode errorCode() {
                                return rejected ? CraftingSubmitErrorCode.CPU_BUSY : null;
                            }

                            public Object errorDetail() {
                                return null;
                            }

                            public ICraftingLink link() {
                                if (onReceiptLink != null) onReceiptLink.run();
                                return missingReceiptLink ? null : link;
                            }
                        };
                    }
                    throw new AssertionError(m.getName());
                });
        }
    }
}
