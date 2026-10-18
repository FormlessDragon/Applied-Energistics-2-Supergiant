package ae2.container.me.common;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.container.implementations.ContainerRequesterTerm;
import ae2.core.AEConfig;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.RequesterTerminalHost;
import ae2.tile.crafting.TileRequester;
import ae2.tile.crafting.requester.Request;
import ae2.tile.crafting.requester.RequestList;
import ae2.tile.crafting.requester.RequestStatus;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RequesterRevisionSyncTest {
    private static Object previousSide;

    @BeforeAll
    static void bootstrap() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        previousSide = side.get(FMLCommonHandler.instance());
        if (previousSide == null) {
            side.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
                (_, method, _) -> method.getName().equals("getSide") ? Side.SERVER : null));
        }
        Bootstrap.register();
        AEConfig.init();
    }

    @AfterAll
    static void restoreSide() throws Exception {
        var side = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(FMLCommonHandler.instance(), previousSide);
    }

    private static RequesterServerView view(int size) throws Exception {
        return view(size, 12);
    }

    private static Directory directory() throws Exception {
        return directory(-1);
    }

    private static Directory directory(long initialRevision) throws Exception {
        var first = view(1, 12);
        var second = view(1, 13);
        first.synchronize(false, _ -> {
        });
        second.synchronize(false, _ -> {
        });
        var active = new LinkedHashSet<TileRequester>();
        active.add(first.getRequester());
        active.add(second.getRequester());
        long[] revision = {initialRevision};
        int[] enumerations = {0};
        Runnable[] onEnumeration = {null};
        var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (_, method, _) -> switch (method.getName()) {
                case "getActiveMachineSetRevision" -> revision[0];
                case "getActiveMachines" -> {
                    enumerations[0]++;
                    var snapshot = new LinkedHashSet<>(active);
                    Runnable callback = onEnumeration[0];
                    onEnumeration[0] = null;
                    if (callback != null) {
                        callback.run();
                    }
                    yield snapshot;
                }
                default -> null;
            });
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
            (_, method, _) -> switch (method.getName()) {
                case "isActive" -> true;
                case "grid" -> grid;
                default -> null;
            });
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var terminal = (ContainerRequesterTerm) ((Unsafe) unsafeField.get(null))
            .allocateInstance(ContainerRequesterTerm.class);
        var byRequester = new IdentityHashMap<TileRequester, RequesterServerView>();
        var byId = new Long2ObjectOpenHashMap<RequesterServerView>();
        for (var view : List.of(first, second)) {
            byRequester.put(view.getRequester(), view);
            byId.put(view.getId(), view);
        }
        setField(ContainerRequesterTerm.class, terminal, "byRequesters", byRequester);
        setField(ContainerRequesterTerm.class, terminal, "byId", byId);
        setField(ContainerRequesterTerm.class, terminal, "syncGrid", grid);
        setField(ContainerRequesterTerm.class, terminal, "host", (RequesterTerminalHost) () -> node);
        return new Directory(terminal, grid, active, first, second, byRequester, byId, new ArrayList<>(),
            revision, enumerations, onEnumeration);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static RequesterServerView view(int size, long id) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var requester = (TestRequester) ((Unsafe) field.get(null)).allocateInstance(TestRequester.class);
        requester.requests = new RequestList(null, size);
        requester.name = new TextComponentString("requester");
        return new RequesterServerView(requester, id);
    }

    @Test
    void stableMembershipSkipsEnumerationButStillRefreshesMetadataRowsAndListIdentity() throws Exception {
        var directory = directory(0);
        for (int i = 0; i < 50; i++) {
            directory.refresh(directory.sent::add);
        }
        assertEquals(1, directory.enumerations[0]);
        assertTrue(directory.sent.isEmpty());
        var requester = (TestRequester) directory.first.getRequester();
        requester.name = new TextComponentString("renamed without topology change");
        directory.second.getServer().get(0).setEnabled(false);
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.enumerations[0]);
        assertEquals(2, directory.sent.size());
        assertTrue(directory.sent.stream().anyMatch(p -> p.getRequesterId() == directory.first.getId()
            && p.isFullUpdate()));
        assertTrue(directory.sent.stream().anyMatch(p -> p.getRequesterId() == directory.second.getId()
            && !p.isFullUpdate()));
        directory.sent.clear();

        requester.requests = new RequestList(null, 1);
        assertThrows(IllegalStateException.class, () -> directory.refresh(_ -> {
            throw new IllegalStateException("list removal send failed");
        }));
        assertSame(directory.byId.get(directory.first.getId()), directory.first);
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.enumerations[0]);
        assertEquals(2, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        assertTrue(directory.sent.getLast().isFullUpdate());
        assertSame(directory.byId.get(directory.second.getId()), directory.second);
        directory.sent.clear();
        directory.refresh(directory.sent::add);
        assertTrue(directory.sent.isEmpty());
    }

    @Test
    void membershipRevisionChangeReconcilesSameSizeReplacementAndRetriesFailedRemoval() throws Exception {
        var directory = directory(0);
        directory.refresh(directory.sent::add);
        directory.active.remove(directory.first.getRequester());
        var added = view(1).getRequester();
        directory.active.add(added);
        directory.revision[0]++;
        assertThrows(IllegalStateException.class, () -> directory.refresh(_ -> {
            throw new IllegalStateException("removal failed");
        }));
        assertSame(directory.byId.get(directory.first.getId()), directory.first);
        directory.refresh(directory.sent::add);
        assertEquals(3, directory.enumerations[0]);
        assertEquals(2, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        assertTrue(directory.sent.getLast().isFullUpdate());
        assertTrue(directory.byRequester.containsKey(added));
        assertFalse(directory.byRequester.containsKey(directory.first.getRequester()));
        directory.refresh(directory.sent::add);
        assertEquals(3, directory.enumerations[0]);
    }

    @Test
    void unknownRevisionKeepsPollingAndDiscoveringMembershipChanges() throws Exception {
        var directory = directory();
        directory.refresh(directory.sent::add);
        directory.refresh(directory.sent::add);
        assertEquals(2, directory.enumerations[0]);
        directory.active.remove(directory.first.getRequester());
        directory.refresh(directory.sent::add);
        assertEquals(3, directory.enumerations[0]);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
    }

    @Test
    void revisionChangedDuringEnumerationIsNotAcknowledgedByAnOlderSnapshot() throws Exception {
        var directory = directory(0);
        directory.onEnumeration[0] = () -> {
            directory.active.remove(directory.first.getRequester());
            directory.revision[0]++;
        };
        directory.refresh(directory.sent::add);
        assertTrue(directory.byId.containsKey(directory.first.getId()));
        directory.refresh(directory.sent::add);
        assertEquals(2, directory.enumerations[0]);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        directory.refresh(directory.sent::add);
        assertEquals(2, directory.enumerations[0]);
    }

    @Test
    void renameAndSortUpdateOnlyTheirTrackerWithoutReplacingRequestIdentity() throws Exception {
        var changed = view(1);
        var stable = view(1);
        var request = changed.getServer().get(0);
        var sent = new ArrayList<RequesterSyncPacket>();
        changed.synchronize(false, sent::add);
        stable.synchronize(false, _ -> {
        });
        var requester = (TestRequester) changed.getRequester();
        requester.name = new TextComponentString("renamed");
        requester.sortBy = 42;
        assertTrue(changed.refreshMetadata());
        assertTrue(stable.refreshMetadata());
        changed.synchronize(false, sent::add);
        stable.synchronize(false, _ -> fail("unchanged requester must retain its baseline"));
        assertEquals(2, sent.size());
        assertTrue(sent.getLast().isFullUpdate());
        assertEquals(12, sent.getLast().getRequesterId());
        assertEquals("renamed", sent.getLast().getRequesterName().getUnformattedText());
        assertEquals(42, sent.getLast().getSortValue());
        assertSame(request, changed.getServer().get(0));
        assertTrue(changed.refreshMetadata());
        changed.synchronize(false, _ -> fail("metadata has already been sent"));
    }

    @Test
    void failedOrReentrantMetadataUpdateRetainsFullRetry() throws Exception {
        var view = view(1);
        view.synchronize(false, _ -> {
        });
        var requester = (TestRequester) view.getRequester();
        requester.name = new TextComponentString("first rename");
        view.refreshMetadata();
        assertThrows(IllegalStateException.class, () -> view.synchronize(false, _ -> {
            throw new IllegalStateException("failed metadata send");
        }));
        view.synchronize(false, packet -> {
            assertTrue(packet.isFullUpdate());
            assertEquals("first rename", packet.getRequesterName().getUnformattedText());
            requester.name = new TextComponentString("second rename");
            requester.sortBy = 43;
            view.refreshMetadata();
        });
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        assertTrue(sent.getFirst().isFullUpdate());
        assertEquals("second rename", sent.getFirst().getRequesterName().getUnformattedText());
        assertEquals(43, sent.getFirst().getSortValue());
        view.synchronize(false, _ -> fail("latest metadata must settle"));
    }

    @Test
    void directoryPreservesUnchangedTrackersAcrossRenameRemovalAndSameSizeReplacement() throws Exception {
        var directory = directory();
        var original = directory.first;
        var requester = (TestRequester) original.getRequester();
        directory.refresh(directory.sent::add);
        assertTrue(directory.sent.isEmpty());
        requester.name = new TextComponentString("new group");
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.sent.size());
        assertEquals(original.getId(), directory.sent.getFirst().getRequesterId());
        directory.sent.clear();

        directory.active.remove(requester);
        var lookup = ContainerRequesterTerm.class.getDeclaredMethod("getRequestTracker", long.class);
        lookup.setAccessible(true);
        assertNull(lookup.invoke(directory.terminal, original.getId()));
        assertTrue(directory.byId.containsKey(original.getId()));
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        assertFalse(directory.byId.containsKey(original.getId()));
        assertSame(directory.byId.get(directory.second.getId()), directory.second);
        directory.sent.clear();

        directory.active.add(requester);
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isFullUpdate());
        var replacement = directory.byRequester.get(requester);
        assertTrue(replacement.getId() != original.getId());
        directory.sent.clear();
        requester.requests = new RequestList(null, 1);
        assertNull(lookup.invoke(directory.terminal, replacement.getId()));
        directory.refresh(directory.sent::add);
        assertEquals(2, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        assertTrue(directory.sent.getLast().isFullUpdate());
        assertTrue(directory.byRequester.get(requester).getId() != replacement.getId());
        assertSame(directory.byId.get(directory.second.getId()), directory.second);
        assertTrue(directory.sent.stream().noneMatch(RequesterSyncPacket::isClearAll));
    }

    @Test
    void failedRemovalRetainsDirectoryEntryForRetryAndOldInteractionRemainsRejected() throws Exception {
        var directory = directory();
        directory.active.remove(directory.first.getRequester());
        assertThrows(IllegalStateException.class, () -> directory.refresh(_ -> {
            throw new IllegalStateException("remove send failed");
        }));
        assertSame(directory.byId.get(directory.first.getId()), directory.first);
        assertTrue(directory.byRequester.containsKey(directory.first.getRequester()));
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isRemoval());
        assertFalse(directory.byId.containsKey(directory.first.getId()));
        directory.sent.clear();
        directory.refresh(directory.sent::add);
        assertTrue(directory.sent.isEmpty());
    }

    @Test
    void failedAddedRequesterFullRetriesWithItsAssignedIdWithoutResendingSiblings() throws Exception {
        var directory = directory();
        var added = view(1).getRequester();
        directory.active.add(added);
        assertThrows(IllegalStateException.class, () -> directory.refresh(_ -> {
            throw new IllegalStateException("full send failed");
        }));
        var retained = directory.byRequester.get(added);
        assertNotNull(retained);
        directory.refresh(directory.sent::add);
        assertEquals(1, directory.sent.size());
        assertTrue(directory.sent.getFirst().isFullUpdate());
        assertEquals(retained.getId(), directory.sent.getFirst().getRequesterId());
        assertSame(directory.byId.get(directory.first.getId()), directory.first);
        assertSame(directory.byId.get(directory.second.getId()), directory.second);
    }

    @Test
    void stableRowsDoNotEncodeAndOnlyChangedSlotIsSent() throws Exception {
        var view = view(3);
        var keys = new ArrayList<CountingKey>();
        for (int i = 0; i < 3; i++) {
            var key = new CountingKey();
            keys.add(key);
            view.getServer().get(i).updateConfiguredStack(new GenericStack(key, 1));
        }
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        assertTrue(sent.getFirst().isFullUpdate());
        for (int tick = 0; tick < 50; tick++) {
            view.synchronize(false, sent::add);
        }
        assertEquals(1, sent.size());
        assertEquals(List.of(1, 1, 1), keys.stream().map(key -> key.writes).toList());

        view.getServer().get(1).updateAmount(2);
        view.synchronize(false, sent::add);
        assertEquals(2, sent.size());
        assertFalse(sent.getLast().isFullUpdate());
        assertEquals(1, sent.getLast().getRows().size());
        assertEquals(2, sent.getLast().getRows().get(1).getLong("amount"));
        assertEquals(List.of(1, 2, 1), keys.stream().map(key -> key.writes).toList());

        var request = view.getServer().get(1);
        request.setEnabled(false);
        request.setEnabled(true);
        view.synchronize(false, sent::add);
        assertEquals(2, sent.size());
        int writes = keys.get(1).writes;
        view.synchronize(false, sent::add);
        assertEquals(writes, keys.get(1).writes);
    }

    @Test
    void everySerializedFieldAndLoadResetInvalidatesTheBaseline() throws Exception {
        var view = view(1);
        var request = view.getServer().get(0);
        var key = new CountingKey();
        request.updateConfiguredStack(new GenericStack(key, 1));
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        List<Consumer<Request>> changes = List.of(
            r -> r.setEnabled(false),
            r -> r.setForceStart(true),
            r -> r.updateState(true, false),
            r -> r.disableWithStatus(RequestStatus.MISSING),
            r -> r.updateAmount(3),
            r -> r.setAmount(4),
            r -> r.setBatchSize(2),
            r -> r.updateBatchSize(5),
            r -> r.setClientStatus(RequestStatus.EXPORTING),
            r -> r.updateConfiguredStack(null),
            r -> r.updateConfiguredStack(new GenericStack(key, 6)),
            r -> r.readFromNBT(new NBTTagCompound())
        );
        for (var change : changes) {
            long before = request.getSyncRevision();
            change.accept(request);
            assertTrue(before != request.getSyncRevision());
            int packets = sent.size();
            view.synchronize(false, sent::add);
            assertEquals(packets + 1, sent.size());
            assertEquals(request.writeToNBT(), sent.getLast().getRows().get(0));
        }
        view.getServer().replaceFromNBT(new NBTTagCompound());
        view.synchronize(false, sent::add);
        assertEquals(request.writeToNBT(), sent.getLast().getRows().get(0));

        int packets = sent.size();
        long revision = request.getSyncRevision();
        request.setEnabled(request.isEnabled());
        request.setForceStart(request.isForceStart());
        request.setAmount(request.getAmount());
        request.setBatchSize(request.getBatchSize());
        request.setClientStatus(request.getClientStatus());
        request.updateState(request.isEnabled(), request.isForceStart());
        request.updateBatchSize(request.getBatchSize());
        request.setRequesterLocation(99, 5);
        assertEquals(revision, request.getSyncRevision());
        view.synchronize(false, sent::add);
        assertEquals(packets, sent.size());
    }

    @Test
    void failedFullAndPartialSendRetainRetryState() throws Exception {
        var view = view(1);
        var request = view.getServer().get(0);
        request.updateConfiguredStack(new GenericStack(new CountingKey(), 1));
        assertThrows(IllegalStateException.class, () -> view.synchronize(false, _ -> {
            throw new IllegalStateException("send failed");
        }));
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        assertTrue(sent.getFirst().isFullUpdate());

        request.setForceStart(true);
        assertThrows(IllegalStateException.class, () -> view.synchronize(false, _ -> {
            throw new IllegalStateException("send failed");
        }));
        view.synchronize(false, sent::add);
        assertFalse(sent.getLast().isFullUpdate());
        assertTrue(sent.getLast().getRows().get(0).getBoolean("force"));
        view.synchronize(false, _ -> fail("successful revision must not be sent twice"));
    }

    @Test
    void keySerializationReentryCannotMixFieldsOrCommitNewerRevision() throws Exception {
        var view = view(1);
        var request = view.getServer().get(0);
        var key = new CountingKey();
        request.updateConfiguredStack(new GenericStack(key, 3));
        key.onWrite = () -> {
            request.updateAmount(9);
            request.setEnabled(false);
            request.setClientStatus(RequestStatus.CPU);
            view.synchronize(false, _ -> fail("nested synchronization must defer"));
        };
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        var first = sent.getFirst().getRows().get(0);
        assertEquals(3, first.getLong("amount"));
        assertEquals(3, first.getCompoundTag("stack").getLong("#"));
        assertTrue(first.getBoolean("enabled"));
        assertEquals("IDLE", first.getString("status"));
        view.synchronize(false, sent::add);
        var next = sent.getLast().getRows().get(0);
        assertEquals(9, next.getLong("amount"));
        assertEquals("CPU", next.getString("status"));
        assertFalse(next.getBoolean("enabled"));
        assertEquals(3, first.getLong("amount"));
        assertEquals(2, key.writes);
    }

    @Test
    void failedReadStillInvalidatesPartiallyChangedFields() throws Exception {
        var view = view(1);
        var request = view.getServer().get(0);
        request.updateConfiguredStack(new GenericStack(new CountingKey(), 1));
        view.synchronize(false, _ -> {
        });
        var tag = new NBTTagCompound() {
            @Override
            public NBTTagCompound getCompoundTag(String key) {
                throw new IllegalStateException("broken stack data");
            }
        };
        tag.setBoolean("enabled", false);
        assertThrows(IllegalStateException.class, () -> request.readFromNBT(tag));
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        assertEquals(1, sent.size());
        assertFalse(sent.getFirst().getRows().get(0).getBoolean("enabled"));
    }

    @Test
    void senderReentryRetainsFullUpdateRequestAndLaterMutations() throws Exception {
        var view = view(1);
        var request = view.getServer().get(0);
        request.updateConfiguredStack(new GenericStack(new CountingKey(), 1));
        view.synchronize(false, _ -> {
        });
        request.setClientStatus(RequestStatus.CPU);
        view.synchronize(false, packet -> {
            assertFalse(packet.isFullUpdate());
            request.updateAmount(7);
            view.synchronize(true, _ -> fail("nested full must defer"));
        });
        var sent = new ArrayList<RequesterSyncPacket>();
        view.synchronize(false, sent::add);
        assertTrue(sent.getFirst().isFullUpdate());
        assertEquals(7, sent.getFirst().getRows().get(0).getLong("amount"));
        view.synchronize(false, _ -> fail("acknowledged full must settle"));
    }

    private record Directory(ContainerRequesterTerm terminal, IGrid grid, LinkedHashSet<TileRequester> active,
                             RequesterServerView first, RequesterServerView second,
                             IdentityHashMap<TileRequester, RequesterServerView> byRequester,
                             Long2ObjectOpenHashMap<RequesterServerView> byId, ArrayList<RequesterSyncPacket> sent,
                             long[] revision, int[] enumerations, Runnable[] onEnumeration) {
        void refresh(Consumer<RequesterSyncPacket> sender) throws Exception {
            setField(AbstractContainerRequester.class, this.terminal, "requesterPacketSender", sender);
            try {
                var update = ContainerRequesterTerm.class.getDeclaredMethod("updateRequesterDirectory", IGrid.class);
                update.setAccessible(true);
                update.invoke(this.terminal, this.grid);
                var sync = ContainerRequesterTerm.class.getDeclaredMethod("sendPartialUpdate");
                sync.setAccessible(true);
                sync.invoke(this.terminal);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                throw e;
            }
        }
    }

    private static final class TestRequester extends TileRequester {
        private RequestList requests;
        private ITextComponent name;
        private long sortBy;

        @Override
        public RequestList getRequests() {
            return this.requests;
        }

        @Override
        public ITextComponent getRequesterName() {
            return this.name;
        }

        @Override
        public long getRequesterSortValue() {
            return this.sortBy;
        }
    }

    private static final class CountingKey extends AEKey {
        private static final AEKeyType TYPE = new AEKeyType(
            new ResourceLocation("ae2", "request-sync-test"), CountingKey.class, new TextComponentString("test")) {
            @Override
            public AEKey readFromPacket(PacketBuffer input) {
                return null;
            }

            @Override
            public AEKey loadKeyFromTag(NBTTagCompound tag) {
                return null;
            }
        };
        private int writes;
        private Runnable onWrite;

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public Object getPrimaryKey() {
            return TYPE;
        }

        @Override
        public ResourceLocation getId() {
            return TYPE.getId();
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return this;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString("test");
        }

        @Override
        public NBTBase get(String componentId) {
            return null;
        }

        @Override
        public NBTTagCompound toTag() {
            this.writes++;
            Runnable callback = this.onWrite;
            this.onWrite = null;
            if (callback != null) {
                callback.run();
            }
            return new NBTTagCompound();
        }
    }
}
