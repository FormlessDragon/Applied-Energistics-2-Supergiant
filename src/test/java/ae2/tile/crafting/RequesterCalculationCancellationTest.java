package ae2.tile.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.crafting.execution.CraftingSubmitResult;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.tile.crafting.requester.BlockedState;
import ae2.tile.crafting.requester.LinkState;
import ae2.tile.crafting.requester.NoPatternState;
import ae2.tile.crafting.requester.PlanState;
import ae2.tile.crafting.requester.RequestStatus;
import ae2.tile.crafting.requester.StatusState;
import net.minecraft.block.state.IBlockState;
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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterCalculationCancellationTest {
    private static Object previousSide;

    @BeforeAll
    static void setup() throws Exception {
        Bootstrap.register();
        AEConfig.init();
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (proxy, method, args) -> method.getName().equals("getSide") ? Side.SERVER : null));
    }

    @AfterAll
    static void cleanup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    private static ICraftingLink startLink(TestTile tile) {
        configure(tile);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertInstanceOf(LinkState.class, states(tile)[0]);
        return link;
    }

    private static TestTile unknownSubmission() {
        var tile = new TestTile();
        configure(tile);
        tile.onSubmit = () -> {
            throw new IllegalStateException("unknown outcome");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        return tile;
    }

    private static ICraftingSubmitResult receipt(
        ICraftingLink link) {
        return CraftingSubmitResult.successful(link);
    }

    private static ICraftingLink link(TestTile tile) {
        boolean[] canceled = {false};
        return (ICraftingLink) Proxy.newProxyInstance(
            RequesterCalculationCancellationTest.class.getClassLoader(),
            new Class<?>[]{ICraftingLink.class}, (proxy, method, args) -> switch (method.getName()) {
                case "cancel" -> {
                    tile.linkCancels++;
                    if (tile.onLinkCancel != null) tile.onLinkCancel.run();
                    canceled[0] = tile.cancelAccepted;
                    yield null;
                }
                case "isCanceled", "isDone" -> {
                    if (tile.onLinkStatus != null) tile.onLinkStatus.run();
                    yield method.getName().equals("isCanceled") && canceled[0];
                }
                case "isStandalone" -> false;
                case "getCraftingID" -> tile.linkId;
                case "writeToNBT" -> {
                    if (tile.onLinkWrite != null) tile.onLinkWrite.run();
                    var tag = (NBTTagCompound) args[0];
                    tag.setUniqueId("craftId", tile.linkId);
                    tag.setBoolean("req", true);
                    tag.setBoolean("canceled", canceled[0]);
                    yield null;
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            });
    }

    private static IGrid grid() {
        return (IGrid) Proxy.newProxyInstance(
            IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (proxy, method, args) -> {
                throw new AssertionError(method.getName());
            });
    }

    private static void configure(TestTile tile) {
        tile.getRequests().get(0).updateConfiguredStack(new GenericStack(
            AEItemKey.of(Items.IRON_INGOT), 10));
    }

    private static ICraftingPlan plan(TestTile tile, Runnable outputCallback) {
        var output = new GenericStack(tile.getRequests().get(0).getKey(), 1);
        return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
            new Class<?>[]{ICraftingPlan.class}, (proxy, method, args) -> switch (method.getName()) {
                case "missingItems", "emittedItems", "usedItems" -> new KeyCounter();
                case "finalOutput" -> {
                    if (outputCallback != null) outputCallback.run();
                    yield output;
                }
                default -> throw new AssertionError(method.getName());
            });
    }

    private static StatusState callbackState(Function<TileRequester, StatusState> callback) {
        return new StatusState() {
            @Override
            public StatusState handle(TileRequester host, int slot) {
                return callback.apply(host);
            }

            @Override
            public RequestStatus type() {
                return RequestStatus.REQUESTING;
            }

            @Override
            public TickRateModulation getTickRateModulation() {
                return TickRateModulation.SAME;
            }
        };
    }

    private static StatusState[] states(TileRequester tile) {
        try {
            var field = TileRequester.class.getDeclaredField("requestStatus");
            field.setAccessible(true);
            return (StatusState[]) field.get(tile);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Field nestedField() throws Exception {
        var field = BlockedState.class.getDeclaredField("simulatedPlanState");
        field.setAccessible(true);
        return field;
    }

    @Test
    void successfulSubmissionOwnsLinkAndRejectsNestedSubmission() {
        var tile = new TestTile();
        configure(tile);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        tile.onSubmit = tile::handleRequests;
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        assertEquals(Set.of(link), tile.getRequestedJobs());
        assertInstanceOf(LinkState.class, states(tile)[0]);
        assertEquals(1, tile.insertCraftedItems(link, tile.getRequests().get(0).getKey(), 1,
            Actionable.MODULATE));
    }

    @Test
    void submissionCancellationAndReceiptCancellationCannotPublishIntoTheNewConfiguration() {
        for (int phase = 0; phase < 3; phase++) {
            var tile = new TestTile();
            configure(tile);
            var link = link(tile);
            tile.receipt = () -> receipt(link);
            Runnable cancel = () -> tile.getRequests().get(0).updateConfiguredStack(null);
            if (phase == 0) tile.onSubmit = cancel;
            else if (phase == 1) tile.onReceiptLink = cancel;
            else tile.onReceiptSuccess = cancel;
            states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
            tile.handleRequests();
            assertEquals(1, tile.submissions);
            assertEquals(1, tile.linkCancels);
            assertTrue(tile.getRequestedJobs().isEmpty());
            assertNull(tile.getRequests().get(0).getKey());
            assertEquals(0, tile.insertCraftedItems(link, AEItemKey.of(Items.IRON_INGOT),
                1, Actionable.MODULATE));
            assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
        }
    }

    @Test
    void submitOrReceiptExceptionsBlockReplayAndPersistAnExplicitUnknownStatus() {
        for (int phase = 0; phase < 3; phase++) {
            var tile = new TestTile();
            configure(tile);
            var link = link(tile);
            tile.receipt = () -> receipt(link);
            Runnable fail = () -> {
                throw new IllegalStateException("outcome unavailable");
            };
            if (phase == 0) tile.onSubmit = fail;
            else if (phase == 1) tile.onReceiptLink = fail;
            else tile.onReceiptSuccess = fail;
            states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
            tile.handleRequests();
            for (int tick = 0; tick < 50; tick++) tile.handleRequests();
            assertEquals(1, tile.submissions);
            assertSame(StatusState.IDLE, states(tile)[0]);
            assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
            var saved = new NBTTagCompound();
            tile.saveAdditional(saved);
            assertTrue(saved.getCompoundTag("request_submissions").hasKey("0"));
            var restored = new TestTile();
            restored.loadTag(saved);
            restored.handleRequests();
            assertEquals(0, restored.submissions);
            assertEquals(RequestStatus.UNCONFIRMED, restored.getRequests().get(0).getClientStatus());
            assertEquals(phase == 2 ? 1 : 0, restored.getRequestedJobs().size());
        }
    }

    @Test
    void cancellationFailureKeepsTheLateLinkButDisablesItsOutputRoute() {
        var tile = new TestTile();
        configure(tile);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        tile.onSubmit = () -> tile.onRequestChanged(0);
        tile.onLinkCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(Set.of(link), tile.getRequestedJobs());
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        assertEquals(0, tile.insertCraftedItems(link, tile.getRequests().get(0).getKey(), 1,
            Actionable.SIMULATE));
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        var restored = new TestTile();
        restored.loadTag(saved);
        assertEquals(tile.linkId, restored.getRequestedJobs().iterator().next().getCraftingID());
        tile.jobStateChange(link);
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void unknownRecordCannotBeErasedByConfigurationOrMissingFieldsOrReplacedByAnotherId() {
        var tile = new TestTile();
        configure(tile);
        tile.onSubmit = () -> {
            throw new IllegalStateException("unknown");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        tile.onRequestChanged(0);
        tile.loadTag(new NBTTagCompound());
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        saved.getCompoundTag("request_submissions").getCompoundTag("0")
             .setUniqueId("submissionId", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> tile.loadTag(saved));
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void definiteRejectionKeepsTheExistingRetryBehaviorWithoutAUnknownRecord() {
        var tile = new TestTile();
        configure(tile);
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        assertEquals(RequestStatus.CPU, tile.getRequests().get(0).getClientStatus());
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        assertTrue(saved.getCompoundTag("request_submissions").isEmpty());
    }

    @Test
    void fatalSubmitErrorPropagatesAndLeavesAnUnknownRecordThatPreventsReplay() {
        var tile = new TestTile();
        configure(tile);
        tile.onSubmit = () -> {
            throw new OutOfMemoryError("fatal fixture");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        assertThrows(OutOfMemoryError.class, tile::handleRequests);
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void publishedLinkNotificationCancellationRevokesTheReceiptBeforeOuterPublication() {
        var tile = new TestTile();
        configure(tile);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        tile.notification = () -> {
            if (states(tile)[0] instanceof LinkState) {
                tile.notification = null;
                tile.getRequests().get(0).updateConfiguredStack(null);
            }
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(1, tile.linkCancels);
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertSame(StatusState.IDLE, states(tile)[0]);
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void terminalReceiptCallbackNeverPublishesTheDeadLink() {
        var tile = new TestTile();
        configure(tile);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        tile.onReceiptSuccess = () -> {
            tile.jobStateChange(link);
            tile.getRequests().get(0).setEnabled(false);
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertSame(StatusState.IDLE, states(tile)[0]);
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void submissionSnapshotAndKnownUnknownLinkRoundTripRetainTheirOriginalIdentity() {
        var tile = new TestTile();
        configure(tile);
        var snapshot = new NBTTagCompound();
        tile.onSubmit = () -> tile.saveAdditional(snapshot);
        var link = link(tile);
        tile.receipt = () -> receipt(link);
        tile.onReceiptSuccess = () -> {
            throw new IllegalStateException("receipt unavailable");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        var inFlight = new TestTile();
        inFlight.loadTag(snapshot);
        inFlight.handleRequests();
        assertEquals(0, inFlight.submissions);
        assertEquals(RequestStatus.UNCONFIRMED, inFlight.getRequests().get(0).getClientStatus());
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        tile.loadTag(saved);
        assertSame(link, tile.getRequestedJobs().iterator().next());
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        assertEquals(0, tile.insertCraftedItems(link, tile.getRequests().get(0).getKey(), 1,
            Actionable.SIMULATE));
    }

    @Test
    void unknownSchemaPayloadIsPreservedAndInvalidRootDoesNotReenablePlanning() {
        var tile = new TestTile();
        configure(tile);
        var saved = new NBTTagCompound();
        var records = new NBTTagCompound();
        records.setString("0", "unrecognized original payload");
        saved.setTag("request_submissions", records);
        tile.loadTag(saved);
        tile.handleRequests();
        assertEquals(0, tile.submissions);
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        var copy = new NBTTagCompound();
        tile.saveAdditional(copy);
        assertEquals(records, copy.getCompoundTag("request_submissions"));
        saved.setString("request_submissions", "broken root");
        assertThrows(IllegalStateException.class, () -> tile.loadTag(saved));
        tile.handleRequests();
        assertEquals(0, tile.submissions);
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void unknownReceiptSaveFailureRetriesOnlyTheSaveAndKeepsTheSlotBlocked() {
        var tile = new TestTile();
        configure(tile);
        tile.onSubmit = () -> {
            throw new IllegalStateException("outcome unavailable");
        };
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.failSave = true;
        assertDoesNotThrow(tile::handleRequests);
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        tile.failSave = false;
        tile.handleRequests();
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void successfulReceiptWithoutLinkRemainsUnconfirmedInsteadOfRepeatingThePlan() {
        var tile = new TestTile();
        configure(tile);
        tile.receipt = () -> receipt(null);
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        tile.handleRequests();
        assertEquals(1, tile.submissions);
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void confirmedLinkCancellationFailureRetainsOwnershipAndBlocksReplayAcrossReload() {
        var tile = new TestTile();
        var link = startLink(tile);
        var key = tile.getRequests().get(0).getKey();
        tile.onLinkCancel = () -> {
            assertTrue(tile.getRequestedJobs().contains(link));
            assertEquals(0, tile.insertCraftedItems(link, key, 1, Actionable.SIMULATE));
            throw new IllegalStateException("cancel unavailable");
        };
        tile.onRequestChanged(0);
        assertEquals(1, tile.linkCancels);
        assertTrue(tile.getRequestedJobs().contains(link));
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        for (int tick = 0; tick < 50; tick++) tile.handleRequests();
        assertEquals(1, tile.submissions);
        assertEquals(1, tile.linkCancels);
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        assertEquals("cancel", saved.getCompoundTag("request_submissions").getCompoundTag("0")
                                    .getString("operation"));
        var restored = new TestTile();
        restored.loadTag(saved);
        var restoredLink = restored.getRequestedJobs().iterator().next();
        assertEquals(tile.linkId, restoredLink.getCraftingID());
        assertEquals(RequestStatus.UNCONFIRMED, restored.getRequests().get(0).getClientStatus());
        assertEquals(0, restored.insertCraftedItems(restoredLink, key, 1, Actionable.MODULATE));
        restored.onRequestChanged(0);
        assertTrue(restored.getRequestedJobs().isEmpty());
        assertEquals(RequestStatus.IDLE, restored.getRequests().get(0).getClientStatus());
    }

    @Test
    void confirmedLinkNonterminalCancellationWaitsForAuthoritativeNotification() {
        var tile = new TestTile();
        var link = startLink(tile);
        tile.cancelAccepted = false;
        tile.onRequestChanged(0);
        tile.handleRequests();
        assertTrue(tile.getRequestedJobs().contains(link));
        assertEquals(1, tile.linkCancels);
        tile.jobStateChange(link);
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void nestedConfirmedCancellationCannotRepeatTheExternalCallOrAdmitNewWork() {
        var tile = new TestTile();
        startLink(tile);
        tile.onLinkCancel = () -> {
            tile.onRequestChanged(0);
            tile.handleRequests();
        };
        tile.onRequestChanged(0);
        assertEquals(1, tile.linkCancels);
        assertEquals(1, tile.submissions);
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void confirmedCancellationStatusFailureKeepsTheLinkUntilItCanBeConfirmed() {
        var tile = new TestTile();
        var link = startLink(tile);
        tile.onLinkStatus = () -> {
            throw new IllegalStateException("state unavailable");
        };
        tile.onRequestChanged(0);
        assertTrue(tile.getRequestedJobs().contains(link));
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        tile.onLinkStatus = null;
        tile.onRequestChanged(0);
        assertTrue(tile.getRequestedJobs().isEmpty());
    }

    @Test
    void confirmedCancellationTerminalCallbackThenExceptionStillReleasesOwnership() {
        var tile = new TestTile();
        var link = startLink(tile);
        tile.onLinkCancel = () -> {
            tile.jobStateChange(link);
            throw new IllegalStateException("notification failed after terminal");
        };
        tile.onRequestChanged(0);
        assertTrue(tile.getRequestedJobs().isEmpty());
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void fatalConfirmedCancellationPropagatesWithItsRecordAndFinallyReleasesReentryGuard() {
        var tile = new TestTile();
        var link = startLink(tile);
        tile.onLinkCancel = () -> {
            throw new OutOfMemoryError("fatal cancel fixture");
        };
        assertThrows(OutOfMemoryError.class, () -> tile.onRequestChanged(0));
        assertTrue(tile.getRequestedJobs().contains(link));
        assertTrue(tile.canProcessRequests());
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
        tile.onLinkCancel = null;
        tile.onRequestChanged(0);
        assertTrue(tile.getRequestedJobs().isEmpty());
    }

    @Test
    void pendingCancellationRejectsAConflictingLinkReadWithoutDroppingOtherLiveRows() {
        var tile = new TestTile();
        var link = startLink(tile);
        tile.onLinkCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        tile.onRequestChanged(0);
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        tile.loadTag(saved);
        assertSame(link, tile.getRequestedJobs().iterator().next());
        var conflicting = new NBTTagCompound();
        var rows = new NBTTagCompound();
        var otherLink = new NBTTagCompound();
        otherLink.setUniqueId("craftId", UUID.randomUUID());
        otherLink.setBoolean("req", true);
        rows.setTag("0", otherLink);
        conflicting.setTag("request_status", rows);
        assertThrows(IllegalStateException.class, () -> tile.loadTag(conflicting));
        assertSame(link, tile.getRequestedJobs().iterator().next());
        assertEquals(RequestStatus.UNCONFIRMED, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void oneConfirmedCancellationFailureDoesNotSkipSiblingCalculationCancellation() throws Exception {
        var tile = new TestTile();
        var link = startLink(tile);
        var pending = new CompletableFuture<ICraftingPlan>();
        states(tile)[1] = new PlanState(pending);
        tile.onLinkCancel = () -> {
            throw new IllegalStateException("first cancel failed");
        };
        var cancel = TileRequester.class.getDeclaredMethod("cancelAllRequests");
        cancel.setAccessible(true);
        cancel.invoke(tile);
        assertTrue(pending.isCancelled());
        assertTrue(tile.getRequestedJobs().contains(link));
        assertSame(StatusState.IDLE, states(tile)[1]);
    }

    @Test
    void unresolvedOriginalPayloadIsHandedOffAsADiagnosticAndClearDoesNotRepeatIt() {
        var tile = new TestTile();
        var data = new NBTTagCompound();
        var records = new NBTTagCompound();
        records.setString("0", "original addon payload");
        data.setTag("request_submissions", records);
        tile.loadTag(data);
        var drops = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(drops);
        assertEquals(1, drops.size());
        var recovery = drops.getFirst();
        assertEquals(AEItems.MISSING_CONTENT.item(), recovery.getItem());
        assertEquals(records.getTag("0"), recovery.getTagCompound().getTag("requester_submission"));
        assertEquals(0, recovery.getTagCompound().getInteger("requester_slot"));
        assertNull(GenericResourcePackageItem.unwrap(recovery));
        tile.clearContent();
        var again = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(again);
        assertTrue(again.isEmpty());
    }

    @Test
    void knownUnconfirmedCancellationRecoveryKeepsCraftIdWithoutMintingTheRequestedResource() {
        var tile = new TestTile();
        startLink(tile);
        tile.onLinkCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        var drops = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(drops);
        assertEquals(1, drops.size());
        var record = drops.getFirst().getTagCompound().getCompoundTag("requester_submission");
        assertEquals("cancel", record.getString("operation"));
        assertEquals(tile.linkId, record.getUniqueId("knownCraftId"));
        assertEquals(tile.linkId, record.getCompoundTag("link").getUniqueId("craftId"));
        assertTrue(record.hasUniqueId("submissionId"));
        assertNull(GenericResourcePackageItem.unwrap(drops.getFirst()));
        tile.clearContent();
        assertTrue(tile.getRequestedJobs().isEmpty());
    }

    @Test
    void recoverySurvivesLinkSerializationFailureAndKeepsConfirmedBufferedDropsSeparate() {
        var tile = new TestTile();
        var link = startLink(tile);
        var key = tile.getRequests().get(0).getKey();
        assertEquals(1, tile.insertCraftedItems(link, key, 1, Actionable.MODULATE));
        tile.onLinkCancel = () -> {
            throw new IllegalStateException("cancel unavailable");
        };
        tile.onLinkWrite = () -> {
            throw new IllegalStateException("serialize unavailable");
        };
        var drops = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(drops);
        assertEquals(2, drops.size());
        var recovery = drops.stream().filter(stack -> stack.getItem() == AEItems.MISSING_CONTENT.item())
                            .findFirst().orElseThrow();
        var record = recovery.getTagCompound().getCompoundTag("requester_submission");
        assertEquals(tile.linkId, record.getUniqueId("knownCraftId"));
        assertTrue(record.getBoolean("linkSerializationFailed"));
        assertTrue(record.hasUniqueId("submissionId"));
        var resources = drops.stream().map(GenericResourcePackageItem::unwrap)
                             .filter(Objects::nonNull).toList();
        assertEquals(List.of(new GenericStack(key, 1)), resources);
        tile.clearContent();
        assertEquals(0, tile.getStorageTracker().getBufferedAmount(0, key));
    }

    @Test
    void clearWithoutACollectedRecoveryKeepsTheOriginalForALaterDropAttempt() {
        var tile = unknownSubmission();
        var before = new NBTTagCompound();
        tile.saveAdditional(before);
        tile.clearContent();
        var retained = new NBTTagCompound();
        tile.saveAdditional(retained);
        assertEquals(before.getTag("request_submissions"), retained.getTag("request_submissions"));
        var drops = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(drops);
        assertEquals(1, drops.size());
        tile.clearContent();
        retained = new NBTTagCompound();
        tile.saveAdditional(retained);
        assertTrue(retained.getCompoundTag("request_submissions").isEmpty());
    }

    @Test
    void rejectedOrThrowingDropCollectionCannotAuthorizeClearingTheUnconfirmedRecord() {
        for (boolean throwing : new boolean[]{true, false}) {
            var tile = unknownSubmission();
            var drops = new ArrayList<ItemStack>() {
                @Override
                public boolean add(ItemStack stack) {
                    if (throwing) throw new IllegalStateException("collection unavailable");
                    return false;
                }
            };
            if (throwing) assertThrows(IllegalStateException.class, () -> tile.addAdditionalDrops(drops));
            else tile.addAdditionalDrops(drops);
            tile.clearContent();
            var saved = new NBTTagCompound();
            tile.saveAdditional(saved);
            assertTrue(saved.getCompoundTag("request_submissions").hasKey("0"));
            var accepted = new ArrayList<ItemStack>();
            tile.addAdditionalDrops(accepted);
            assertEquals(1, accepted.size());
        }
    }

    @Test
    void removalInsideSubmitKeepsTheInFlightOriginalAndDoesNotConvertTheUnknownAmountToDrops() {
        var tile = new TestTile();
        configure(tile);
        var drops = new ArrayList<ItemStack>();
        tile.onSubmit = () -> {
            tile.addAdditionalDrops(drops);
            tile.clearContent();
        };
        tile.onReceiptSuccess = () -> {
            throw new IllegalStateException("late outcome unavailable");
        };
        tile.receipt = () -> receipt(link(tile));
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(1, drops.size());
        var record = drops.getFirst().getTagCompound().getCompoundTag("requester_submission");
        assertTrue(record.hasUniqueId("submissionId"));
        assertTrue(record.hasKey("request", 10));
        assertFalse(record.hasKey("link"));
        assertNull(GenericResourcePackageItem.unwrap(drops.getFirst()));
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        assertEquals(record.getUniqueId("submissionId"), saved.getCompoundTag("request_submissions")
                                                              .getCompoundTag("0").getUniqueId("submissionId"));
    }

    @Test
    void recoverySnapshotsRemainIndependentAcrossCollectionAndSameVersionSave() {
        var tile = unknownSubmission();
        var first = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(first);
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        var original = saved.getCompoundTag("request_submissions").getCompoundTag("0").copy();
        first.getFirst().getTagCompound().getCompoundTag("requester_submission").setString("extra", "edited copy");
        var second = new ArrayList<ItemStack>();
        tile.addAdditionalDrops(second);
        assertEquals(original, second.getFirst().getTagCompound().getCompoundTag("requester_submission"));
        var restored = new TestTile();
        restored.loadTag(saved);
        var afterReload = new ArrayList<ItemStack>();
        restored.addAdditionalDrops(afterReload);
        assertEquals(original, afterReload.getFirst().getTagCompound().getCompoundTag("requester_submission"));
    }

    @Test
    void nestedCalculationIsDetachedBeforeReentrantCancellationCallback() throws Exception {
        var tile = new TestTile();
        var calls = new AtomicInteger();
        var blocked = BlockedState.missing();
        var future = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean cancel(boolean interrupt) {
                calls.incrementAndGet();
                assertTrue(interrupt);
                assertSame(StatusState.IDLE, states(tile)[0]);
                assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
                try {
                    assertNull(nestedField().get(blocked));
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                tile.onRequestChanged(0);
                return super.cancel(interrupt);
            }
        };
        nestedField().set(blocked, new PlanState(future));
        states(tile)[0] = blocked;
        tile.getRequests().get(0).setClientStatus(RequestStatus.MISSING);
        tile.onRequestChanged(0);
        assertTrue(future.isCancelled());
        assertEquals(1, calls.get());
        assertEquals(1, tile.notifications);
        assertSame(StatusState.IDLE, states(tile)[0]);
        tile.onRequestChanged(0);
        assertEquals(1, calls.get());
    }

    @Test
    void cancelAllContinuesAfterOneExtensionThrowsAndCancelsNestedFuture() throws Exception {
        var tile = new TestTile();
        var nested = new CompletableFuture<ICraftingPlan>();
        var direct = new CompletableFuture<ICraftingPlan>();
        var blocked = BlockedState.cpu();
        nestedField().set(blocked, new PlanState(nested));
        states(tile)[0] = new StatusState() {
            @Override
            public void cancel() {
                assertSame(StatusState.IDLE, states(tile)[0]);
                throw new IllegalStateException("cancel fixture");
            }

            @Override
            public StatusState handle(TileRequester host, int slot) {
                return this;
            }

            @Override
            public RequestStatus type() {
                return RequestStatus.CRAFTING;
            }

            @Override
            public TickRateModulation getTickRateModulation() {
                return TickRateModulation.SAME;
            }
        };
        states(tile)[1] = blocked;
        states(tile)[2] = new PlanState(direct);
        var method = TileRequester.class.getDeclaredMethod("cancelAllRequests");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(tile));
        assertTrue(nested.isCancelled());
        assertTrue(direct.isCancelled());
        for (var state : states(tile)) assertSame(StatusState.IDLE, state);
        assertNull(nestedField().get(blocked));
    }

    @Test
    void cancellationBetweenDoneCheckAndGetReturnsIdle() {
        var tile = new TestTile();
        var future = (Future<ICraftingPlan>) Proxy.newProxyInstance(Future.class.getClassLoader(),
            new Class<?>[]{Future.class}, (proxy, method, args) -> switch (method.getName()) {
                case "isDone" -> true;
                case "isCancelled" -> false;
                case "get" -> throw new CancellationException("completed check raced cancellation");
                default -> false;
            });
        assertSame(StatusState.IDLE, new PlanState(future).handle(tile, 0));
    }

    @Test
    void fatalCancellationErrorPropagatesAfterStateDetach() {
        var tile = new TestTile();
        var fatal = new OutOfMemoryError("cancel fixture");
        states(tile)[0] = new PlanState(new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                throw fatal;
            }
        });
        assertSame(fatal, assertThrows(OutOfMemoryError.class, () -> tile.onRequestChanged(0)));
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void canceledCallbackCannotPublishItsNewFuture() {
        var tile = new TestTile();
        var future = new CompletableFuture<ICraftingPlan>();
        states(tile)[0] = callbackState(host -> {
            host.onRequestChanged(0);
            return new PlanState(future);
        });
        tile.handleRequests();
        assertSame(StatusState.IDLE, states(tile)[0]);
        assertTrue(future.isCancelled());
    }

    @Test
    void canceledBlockedCallbackReleasesNewlyAcquiredNestedFuture() throws Exception {
        var tile = new TestTile();
        var blocked = BlockedState.missing();
        var future = new CompletableFuture<ICraftingPlan>();
        tile.getRequests().get(0).updateConfiguredStack(new GenericStack(
            AEItemKey.of(Items.IRON_INGOT), 10));
        states(tile)[0] = blocked;
        tile.planning = () -> {
            tile.onRequestChanged(0);
            return future;
        };
        tile.handleRequests();
        assertSame(StatusState.IDLE, states(tile)[0]);
        assertTrue(future.isCancelled());
        assertNull(nestedField().get(blocked));
    }

    @Test
    void notificationCancellationCannotBeOverwrittenByOuterLoop() {
        var tile = new TestTile();
        var future = new CompletableFuture<ICraftingPlan>();
        states(tile)[0] = callbackState(host -> new PlanState(future));
        tile.notification = () -> {
            tile.notification = null;
            tile.onRequestChanged(0);
        };
        tile.handleRequests();
        assertSame(StatusState.IDLE, states(tile)[0]);
        assertEquals(RequestStatus.IDLE, tile.getRequests().get(0).getClientStatus());
        assertTrue(future.isCancelled());
    }

    @Test
    void nestedHandlingDoesNotReenterCurrentSlotAndGuardClearsAfterFailure() {
        var tile = new TestTile();
        var calls = new AtomicInteger();
        states(tile)[0] = callbackState(host -> {
            calls.incrementAndGet();
            tile.handleRequests();
            throw new IllegalStateException("state fixture");
        });
        assertThrows(IllegalStateException.class, tile::handleRequests);
        assertEquals(1, calls.get());
        states(tile)[0] = callbackState(host -> {
            calls.incrementAndGet();
            return StatusState.IDLE;
        });
        tile.handleRequests();
        assertEquals(2, calls.get());
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void noPatternTransitionSurvivesItsOwnCancellation() {
        var tile = new TestTile();
        states(tile)[0] = callbackState(host -> host.disableRequestForNoPattern(0));
        tile.handleRequests();
        assertInstanceOf(NoPatternState.class, states(tile)[0]);
        assertFalse(tile.getRequests().get(0).isEnabled());
        assertEquals(RequestStatus.NO_PATTERN, tile.getRequests().get(0).getClientStatus());
    }

    @Test
    void configuredAmountAndBatchUpdatesCancelOnlyUnsubmittedWork() throws Exception {
        var tile = new TestTile();
        configure(tile);
        var direct = new CompletableFuture<ICraftingPlan>();
        states(tile)[0] = new PlanState(direct);
        tile.getRequests().get(0).updateAmount(20);
        assertTrue(direct.isCancelled());
        var nested = new CompletableFuture<ICraftingPlan>();
        var blocked = BlockedState.cpu();
        nestedField().set(blocked, new PlanState(nested));
        states(tile)[0] = blocked;
        tile.getRequests().get(0).updateBatchSize(2);
        assertTrue(nested.isCancelled());
        var canceledLinks = new AtomicInteger();
        var link = (ICraftingLink) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{ICraftingLink.class},
            (proxy, method, args) -> {
                if (method.getName().equals("cancel")) canceledLinks.incrementAndGet();
                return null;
            });
        var state = new LinkState(link);
        states(tile)[0] = state;
        tile.getRequests().get(0).updateState(true, true);
        assertSame(state, states(tile)[0]);
        assertEquals(0, canceledLinks.get());
    }

    @Test
    void futureGetConfigurationChangePreventsSubmission() {
        var tile = new TestTile();
        configure(tile);
        var future = new CompletableFuture<ICraftingPlan>() {
            @Override
            public ICraftingPlan get() {
                tile.getRequests().get(0).updateAmount(20);
                return plan(tile, null);
            }
        };
        future.complete(null);
        states(tile)[0] = new PlanState(future);
        tile.handleRequests();
        assertEquals(0, tile.submissions);
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void metadataConfigurationChangePreventsSubmission() {
        var tile = new TestTile();
        configure(tile);
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(
            plan(tile, () -> tile.getRequests().get(0).updateState(true, true))));
        tile.handleRequests();
        assertEquals(0, tile.submissions);
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void unchangedCompletedPlanStillSubmits() {
        var tile = new TestTile();
        configure(tile);
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)));
        tile.handleRequests();
        assertEquals(1, tile.submissions);
    }

    @Test
    void storageReadAfterConfigurationChangeDoesNotRestoreOldKnownAmount() {
        var tile = new TestTile();
        configure(tile);
        var key = tile.getRequests().get(0).getKey();
        tile.storageRead = () -> {
            tile.getRequests().get(0).updateConfiguredStack(null);
            return 99L;
        };
        tile.handleRequests();
        assertEquals(0, tile.getStorageTracker().getKnownAmount(0, key));
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void originalGridRejectsCompletedPlanAfterChangeBetweenTicks() {
        var tile = new TestTile();
        configure(tile);
        tile.grid = grid();
        var source = tile.captureCalculationSource(0);
        var future = CompletableFuture.completedFuture(plan(tile, null));
        states(tile)[0] = new PlanState(future, source);
        tile.grid = grid();
        tile.handleRequests();
        assertEquals(0, tile.submissions);
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void unchangedOriginalSourceStillSubmits() {
        var tile = new TestTile();
        configure(tile);
        tile.grid = grid();
        states(tile)[0] = new PlanState(CompletableFuture.completedFuture(plan(tile, null)),
            tile.captureCalculationSource(0));
        tile.handleRequests();
        assertEquals(1, tile.submissions);
    }

    @Test
    void gridListenerCancelsNestedWorkEvenAfterGridIdentityReturns() throws Exception {
        var tile = new TestTile();
        configure(tile);
        tile.grid = grid();
        var source = tile.captureCalculationSource(0);
        var future = new CompletableFuture<ICraftingPlan>();
        var blocked = BlockedState.cpu();
        nestedField().set(blocked, new PlanState(future, source));
        states(tile)[0] = blocked;
        var original = tile.grid;
        tile.grid = grid();
        tile.listener.onGridChanged(tile, null);
        tile.grid = original;
        tile.listener.onGridChanged(tile, null);
        assertFalse(tile.isCalculationSourceCurrent(source));
        assertTrue(future.isCancelled());
        assertNull(nestedField().get(blocked));
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void gridChangeInsideBeginCancelsNewlyReturnedFuture() {
        var tile = new TestTile();
        configure(tile);
        tile.grid = grid();
        var future = new CompletableFuture<ICraftingPlan>();
        tile.planning = () -> {
            tile.grid = grid();
            tile.listener.onGridChanged(tile, null);
            return future;
        };
        states(tile)[0] = StatusState.REQUEST;
        tile.handleRequests();
        assertTrue(future.isCancelled());
        assertEquals(0, tile.submissions);
        assertSame(StatusState.IDLE, states(tile)[0]);
    }

    @Test
    void unloadClosesAdmissionBeforeCancelCallbacksAndReadyReopens() {
        var tile = new TestTile();
        configure(tile);
        var starts = new AtomicInteger();
        tile.planning = () -> {
            starts.incrementAndGet();
            return new CompletableFuture<>();
        };
        states(tile)[0] = new PlanState(new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                assertFalse(tile.canProcessRequests());
                states(tile)[1] = StatusState.REQUEST;
                tile.handleRequests();
                StatusState.REQUEST.handle(tile, 1);
                assertTrue(tile.callBaseBegin().isDone());
                return super.cancel(interrupt);
            }
        });
        tile.onChunkUnloaded();
        tile.handleRequests();
        assertEquals(0, starts.get());
        assertFalse(tile.canProcessRequests());
        tile.onReady();
        assertTrue(tile.canProcessRequests());
        tile.handleRequests();
        assertEquals(1, starts.get());
    }

    @Test
    void ordinaryCancellationBlocksOtherSlotReentryThenRestoresAdmission() {
        var tile = new TestTile();
        configure(tile);
        var starts = new AtomicInteger();
        tile.planning = () -> {
            starts.incrementAndGet();
            return new CompletableFuture<>();
        };
        states(tile)[1] = StatusState.REQUEST;
        states(tile)[0] = new PlanState(new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                assertFalse(tile.canProcessRequests());
                tile.handleRequests();
                assertTrue(tile.callBaseBegin().isDone());
                throw new IllegalStateException("cancel admission fixture");
            }
        });
        tile.onRequestUpdated(0);
        assertEquals(0, starts.get());
        assertTrue(tile.canProcessRequests());
        tile.handleRequests();
        assertEquals(1, starts.get());
    }

    @Test
    void removalDuringReadyCannotReopenClosedRequester() {
        var tile = new TestTile();
        tile.nodeCreate = tile::setRemoved;
        tile.onReady();
        assertFalse(tile.canProcessRequests());
        tile.nodeCreate = null;
        tile.onReady();
        assertTrue(tile.canProcessRequests());
    }

    @Test
    void fatalCancellationReleasesTemporaryAdmissionGuard() {
        var tile = new TestTile();
        states(tile)[0] = new PlanState(new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                throw new OutOfMemoryError("fixture");
            }
        });
        assertThrows(OutOfMemoryError.class, () -> tile.onRequestUpdated(0));
        assertTrue(tile.canProcessRequests());
    }

    @Test
    void loadCancelsNestedPlanBeforeReadingAndPreservesSavedBuffer() throws Exception {
        var tile = new TestTile();
        configure(tile);
        var key = tile.getRequests().get(0).getKey();
        tile.getStorageTracker().addPending(0, key, 7);
        var data = new NBTTagCompound();
        tile.saveAdditional(data);
        var blocked = BlockedState.missing();
        var future = new CompletableFuture<ICraftingPlan>() {
            @Override
            public boolean cancel(boolean interrupt) {
                assertFalse(tile.canProcessRequests());
                assertSame(StatusState.IDLE, states(tile)[0]);
                assertTrue(tile.callBaseBegin().isDone());
                tile.handleRequests();
                return super.cancel(interrupt);
            }
        };
        nestedField().set(blocked, new PlanState(future, tile.captureCalculationSource(0)));
        states(tile)[0] = blocked;
        var source = tile.captureCalculationSource(0);
        tile.loadTag(data);
        assertTrue(future.isCancelled());
        assertNull(nestedField().get(blocked));
        assertFalse(tile.isCalculationSourceCurrent(source));
        assertEquals(7, tile.getStorageTracker().getBufferedAmount(0, key));
        assertEquals(key, tile.getRequests().get(0).getKey());
        assertTrue(tile.canProcessRequests());
    }

    @Test
    void loadDoesNotCancelSubmittedLinkOrReopenUnloadedTile() {
        var tile = new TestTile();
        var canceled = new AtomicInteger();
        var link = (ICraftingLink) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{ICraftingLink.class},
            (proxy, method, args) -> {
                if (method.getName().equals("cancel")) canceled.incrementAndGet();
                return null;
            });
        var state = new LinkState(link);
        states(tile)[0] = state;
        tile.loadTag(new NBTTagCompound());
        assertSame(state, states(tile)[0]);
        assertEquals(0, canceled.get());
        tile.onChunkUnloaded();
        tile.loadTag(new NBTTagCompound());
        assertFalse(tile.canProcessRequests());
    }

    @Test
    void failedLoadReleasesTemporaryAdmissionGuard() {
        var tile = new TestTile();
        var data = new NBTTagCompound() {
            @Override
            public boolean hasKey(String key, int type) {
                throw new IllegalStateException("load fixture");
            }
        };
        assertThrows(IllegalStateException.class, () -> tile.loadTag(data));
        assertTrue(tile.canProcessRequests());
    }

    private static final class TestTile extends TileRequester {
        private final UUID linkId = UUID.randomUUID();
        private IGrid grid;
        private IGridNodeListener<TileRequester> listener;
        private Runnable nodeCreate;
        private int notifications;
        private Runnable notification;
        private Supplier<Future<ICraftingPlan>> planning;
        private Supplier<Long> storageRead;
        private int submissions;
        private int linkCancels;
        private Runnable onSubmit, onReceiptLink, onReceiptSuccess, onLinkCancel;
        private Runnable onLinkStatus;
        private Runnable onLinkWrite;
        private boolean cancelAccepted = true;
        private Supplier<ICraftingSubmitResult> receipt;
        private boolean failSave;

        @Override
        protected IManagedGridNode createMainNode() {
            // Use the production listener, with a controllable managed node for Grid identity changes.
            var realNode = super.createMainNode();
            try {
                var init = realNode.getClass().getDeclaredField("initData");
                init.setAccessible(true);
                var data = init.get(realNode);
                var field = data.getClass().getDeclaredField("listener");
                field.setAccessible(true);
                listener = (IGridNodeListener<TileRequester>) field.get(data);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            return (IManagedGridNode) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IManagedGridNode.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGrid")) return grid;
                    if (method.getName().equals("create") && nodeCreate != null) nodeCreate.run();
                    if (method.getReturnType() == IManagedGridNode.class) return proxy;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        }

        @Override
        public void saveChanges() {
            if (this.failSave) throw new IllegalStateException("save unavailable");
            super.saveChanges();
        }

        private Future<ICraftingPlan> callBaseBegin() {
            return super.beginPlan(AEItemKey.of(Items.IRON_INGOT), 1);
        }

        @Override
        public IBlockState getBlockState() {
            return null;
        }

        @Override
        public boolean hasIdleCpu() {
            return true;
        }

        @Override
        public long getStoredAmount(AEKey key) {
            return storageRead == null ? 0 : storageRead.get();
        }

        @Override
        public boolean planMaterialSubset(AEKey key, long amount) {
            return false;
        }

        @Override
        public ICraftingSubmitResult submitPlan(ICraftingPlan plan, int slot) {
            submissions++;
            if (onSubmit != null) onSubmit.run();
            var result = receipt == null ? CraftingSubmitResult.NO_CPU_FOUND : receipt.get();
            return new ICraftingSubmitResult() {
                @Override
                public boolean successful() {
                    if (onReceiptSuccess != null) onReceiptSuccess.run();
                    return result.successful();
                }

                @Override
                public CraftingSubmitErrorCode errorCode() {
                    return result.errorCode();
                }

                @Override
                public Object errorDetail() {
                    return result.errorDetail();
                }

                @Override
                public ICraftingLink link() {
                    if (onReceiptLink != null) onReceiptLink.run();
                    return result.link();
                }
            };
        }

        @Override
        public Future<ICraftingPlan> beginPlan(AEKey key, long amount) {
            return planning == null ? super.beginPlan(key, amount) : planning.get();
        }

        @Override
        public void markForUpdate() {
            notifications++;
            if (notification != null) notification.run();
        }

        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }
    }
}
