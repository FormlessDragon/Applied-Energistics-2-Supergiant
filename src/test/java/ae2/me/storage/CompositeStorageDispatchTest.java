package ae2.me.storage;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.storage.MEStorageChangeListener;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeStorageDispatchTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static MEStorageChangeListener listener(StackChange callback) {
        return new MEStorageChangeListener() {
            @Override
            public boolean isValid(Object token) {
                return true;
            }

            @Override
            public void onStackChange(AEKey what, long delta) {
                callback.accept(what, delta);
            }

            @Override
            public void onListUpdate() {
            }
        };
    }

    private static void notifyDelta(CompositeStorage composite) throws Exception {
        Method method = CompositeStorage.class.getDeclaredMethod("notifyDelta", AEKey.class, long.class);
        method.setAccessible(true);
        method.invoke(composite, AEItemKey.of(Items.APPLE), 1L);
    }

    @Test
    void failedListenerDoesNotAbortRemainingObservers() throws Exception {
        var composite = new CompositeStorage();
        var delivered = new AtomicInteger();
        composite.addListener(listener((what, delta) -> {
            throw new IllegalStateException("controlled");
        }), null);
        composite.addListener(listener((what, delta) -> delivered.incrementAndGet()), null);

        notifyDelta(composite);
        notifyDelta(composite);

        assertEquals(2, delivered.get());
    }

    @Test
    void unregisterDuringValidationSkipsTheCurrentCallback() throws Exception {
        var composite = new CompositeStorage();
        var delivered = new AtomicInteger();
        MEStorageChangeListener[] registration = new MEStorageChangeListener[1];
        registration[0] = new MEStorageChangeListener() {
            @Override
            public boolean isValid(Object token) {
                composite.removeListener(this);
                return true;
            }

            @Override
            public void onStackChange(AEKey what, long delta) {
                delivered.incrementAndGet();
            }

            @Override
            public void onListUpdate() {
            }
        };
        composite.addListener(registration[0], registration[0]);

        notifyDelta(composite);

        assertEquals(0, delivered.get());
    }

    @FunctionalInterface
    private interface StackChange {
        void accept(AEKey what, long delta);
    }
}
