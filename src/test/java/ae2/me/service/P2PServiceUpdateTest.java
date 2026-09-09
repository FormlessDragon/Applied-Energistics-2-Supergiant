package ae2.me.service;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.events.GridEvent;
import ae2.items.parts.PartItem;
import ae2.me.GridNode;
import ae2.parts.p2p.P2PTunnelPart;
import com.google.gson.stream.JsonWriter;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class P2PServiceUpdateTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void continuousChangesKeepQueriesCurrentAndNotifyEachFinalMemberOnce() {
        var service = new P2PService(new TestGrid());
        var input = new Tunnel(false, (short) 7);
        service.addNode(input.node, null);
        var outputs = new Tunnel[1000];
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = new Tunnel(true, (short) 7);
            service.addNode(outputs[i].node, null);
        }
        assertEquals(1000, service.getOutputs((short) 7, Tunnel.class).count());
        assertEquals(0, input.networkChanges);
        service.onServerEndTick();
        assertEquals(1, input.networkChanges);

        for (int i = 0; i < 500; i++) {
            service.removeNode(outputs[i].node);
        }
        for (int i = 500; i < 1000; i++) {
            service.updateFreq(outputs[i], (short) 9);
        }
        assertEquals(0, service.getOutputs((short) 7, Tunnel.class).count());
        assertEquals(1, input.networkChanges);
        service.onServerEndTick();
        assertEquals(2, input.networkChanges);
        service.onServerEndTick();
        assertEquals(2, input.networkChanges);
        for (int i = 500; i < 1000; i++) {
            assertEquals(1, outputs[i].configChanges);
            service.updateFreq(outputs[i], (short) 9);
            assertEquals(1, outputs[i].configChanges);
        }
    }

    @Test
    void removalAndReentryDuringNotificationDoNotCallDetachedMembers() {
        var service = new P2PService(new TestGrid());
        var first = new Tunnel(true, (short) 1);
        var removed = new Tunnel(true, (short) 1);
        var input = new Tunnel(false, (short) 1);
        service.addNode(first.node, null);
        service.addNode(removed.node, null);
        service.addNode(input.node, null);
        first.onChange = () -> {
            service.removeNode(removed.node);
            service.onServerEndTick();
        };
        service.onServerEndTick();
        assertEquals(1, first.networkChanges);
        assertEquals(0, removed.networkChanges);
        assertEquals(1, service.getOutputs((short) 1, Tunnel.class).count());
        service.onServerEndTick();
        assertEquals(1, first.networkChanges);
    }

    private static final class Tunnel extends P2PTunnelPart<Tunnel> {
        private final GridNode node;
        private final boolean output;
        private short frequency;
        private int configChanges;
        private int networkChanges;
        private Runnable onChange;

        private Tunnel(boolean output, short frequency) {
            super(new PartItem<>(Tunnel.class, item -> new Tunnel(false, (short) 0)));
            this.output = output;
            this.frequency = frequency;
            this.node = new GridNode(null, this, (owner, node) -> {
            }, EnumSet.of(GridFlags.REQUIRE_CHANNEL));
        }

        @Override
        public boolean isOutput() {
            return this.output;
        }

        @Override
        public short getFrequency() {
            return this.frequency;
        }

        @Override
        public void setFrequency(short frequency) {
            this.frequency = frequency;
        }

        @Override
        public boolean supportsMultipleInputs() {
            return true;
        }

        @Override
        public void onTunnelConfigChange() {
            this.configChanges++;
        }

        @Override
        public void onTunnelNetworkChange() {
            this.networkChanges++;
            if (this.onChange != null) {
                this.onChange.run();
            }
        }
    }

    private static final class TestGrid implements IGrid {
        public <C extends IGridService> C getService(Class<C> type) {
            throw new UnsupportedOperationException();
        }

        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        public Iterable<Class<?>> getMachineClasses() {
            return List.of();
        }

        public Iterable<IGridNode> getMachineNodes(Class<?> type) {
            return List.of();
        }

        public <T> Set<T> getMachines(Class<T> type) {
            return Set.of();
        }

        public <T> Set<T> getActiveMachines(Class<T> type) {
            return Set.of();
        }

        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        public boolean isEmpty() {
            return true;
        }

        public IGridNode getPivot() {
            throw new UnsupportedOperationException();
        }

        public int size() {
            return 0;
        }

        public void export(JsonWriter writer) {
            throw new UnsupportedOperationException();
        }
    }
}
