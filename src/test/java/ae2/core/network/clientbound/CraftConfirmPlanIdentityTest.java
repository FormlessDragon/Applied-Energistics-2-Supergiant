package ae2.core.network.clientbound;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.me.crafting.CraftingPlanSummary;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftConfirmPlanIdentityTest {
    private static CraftingPlanSummary plan(long bytes) {
        return new CraftingPlanSummary(bytes, false, List.of());
    }

    private static void deliver(Minecraft minecraft, CraftConfirmPlanPacket packet) {
        var buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new CraftConfirmPlanPacket();
            decoded.fromBytes(buffer);
            assertEquals(0, buffer.readableBytes());
            decoded.handleClient(minecraft);
        } finally {
            buffer.release();
            assertEquals(0, buffer.refCnt());
        }
    }

    private static FixtureContainer container() throws Exception {
        var result = allocate(FixtureContainer.class);
        result.windowId = 55;
        result.setValidContainer(true);
        result.planSession = UUID.randomUUID().toString();
        set(result, "clientPlanRequest", UUID.randomUUID());
        set(result, "lastPlanRevision", -1L);
        return result;
    }

    private static Object invoke(Object instance, String name, ContainerCraftConfirm.PlanRequest request) throws Exception {
        var method = request == null ? ContainerCraftConfirm.class.getDeclaredMethod(name)
            : ContainerCraftConfirm.class.getDeclaredMethod(name, ContainerCraftConfirm.PlanRequest.class);
        method.setAccessible(true);
        return request == null ? method.invoke(instance) : method.invoke(instance, request);
    }

    private static Field field(String name) throws Exception {
        var field = ContainerCraftConfirm.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(Object instance, String name) throws Exception {
        return field(name).get(instance);
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        field(name).set(instance, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    @Test
    void nativeHandlerBindsFirstReplyReplanAndReopenedWindow() throws Exception {
        var client = container();
        var minecraft = allocate(Minecraft.class);
        minecraft.player = allocate(EntityPlayerSP.class);
        minecraft.player.openContainer = client;
        var request = (UUID) get(client, "clientPlanRequest");
        String session = UUID.randomUUID().toString();
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, UUID.randomUUID(), 100, plan(100)));
        deliver(minecraft, new CraftConfirmPlanPacket(54, session, request, 1, plan(100)));
        assertNull(client.getPlan());
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, request, 1, null));
        assertEquals(session, get(client, "boundPlanSession"));
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, request, 1, plan(10)));
        assertEquals(10, client.getPlan().usedBytes());

        var next = (ContainerCraftConfirm.PlanRequest) invoke(client, "beginClientReplan", null);
        assertNull(client.getPlan());
        assertEquals(request, next.previousRequest());
        assertNotEquals(request, next.request());
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, request, 200, plan(100)));
        deliver(minecraft, new CraftConfirmPlanPacket(55, UUID.randomUUID().toString(), next.request(), 2, plan(100)));
        assertNull(client.getPlan());
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 2, null));
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 2, plan(20)));
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 2, null));
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 1, plan(100)));
        assertEquals(20, client.getPlan().usedBytes());

        // Bad payloads must not be mistaken for a valid "calculating" notification.
        for (boolean trailing : new boolean[]{false, true}) {
            var buffer = Unpooled.buffer();
            try {
                new CraftConfirmPlanPacket(55, session, next.request(), 3, null).toBytes(buffer);
                if (trailing) buffer.writeByte(0);
                else buffer.writerIndex(buffer.writerIndex() - 1);
                var decoded = new CraftConfirmPlanPacket();
                decoded.fromBytes(buffer);
                decoded.handleClient(minecraft);
                assertEquals(20, client.getPlan().usedBytes());
            } finally {
                buffer.release();
                assertEquals(0, buffer.refCnt());
            }
        }
        var reopened = container();
        minecraft.player.openContainer = reopened;
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 3, plan(30)));
        assertNull(reopened.getPlan());
        client.setValidContainer(false);
        minecraft.player.openContainer = client;
        deliver(minecraft, new CraftConfirmPlanPacket(55, session, next.request(), 3, plan(30)));
        assertEquals(20, client.getPlan().usedBytes());
    }

    @Test
    void serverRejectsWrongSessionAndReplayedReplan() throws Exception {
        var server = container();
        var first = UUID.randomUUID();
        var next = UUID.randomUUID();
        var gson = new Gson();
        var action = new ContainerCraftConfirm.PlanRequest(server.planSession, null, first);
        String payload = gson.toJson(action);
        assertTrue(payload.length() <= 256);
        invoke(server, "requestPlan", gson.fromJson(payload, ContainerCraftConfirm.PlanRequest.class));
        assertEquals(first, get(server, "serverPlanRequest"));
        invoke(server, "requestPlan", new ContainerCraftConfirm.PlanRequest(UUID.randomUUID().toString(), null, next));
        assertEquals(first, get(server, "serverPlanRequest"));
        var replan = new ContainerCraftConfirm.PlanRequest(server.planSession, first, next);
        payload = gson.toJson(replan);
        assertTrue(payload.length() <= 256);
        invoke(server, "replan", gson.fromJson(payload, ContainerCraftConfirm.PlanRequest.class));
        assertEquals(next, get(server, "serverPlanRequest"));
        assertEquals(1, server.replans);
        invoke(server, "replan", replan);
        invoke(server, "requestPlan", action);
        assertEquals(next, get(server, "serverPlanRequest"));
        assertEquals(1, server.replans);
    }

    @Test
    void returningFromCraftingTreeStartsNewRevision() throws Exception {
        var server = container();
        var old = new CompletableFuture<ICraftingPlan>();
        var replacement = new CompletableFuture<ICraftingPlan>();
        set(server, "job", old);
        server.setPlan(plan(10));
        set(server, "cachedMergeableCpus", new Reference2BooleanOpenHashMap<>());
        server.setJob(replacement);
        assertTrue(old.isCancelled());
        assertSame(replacement, get(server, "job"));
        assertNull(server.getPlan());
        assertEquals(1L, get(server, "planRevision"));
    }

    private static class FixtureContainer extends ContainerCraftConfirm {
        int replans;

        private FixtureContainer() {
            super(null, null);
        }

        @Override
        public EntityPlayer getPlayer() {
            return null;
        }

        @Override
        public void replan() {
            this.replans++;
        }
    }
}
