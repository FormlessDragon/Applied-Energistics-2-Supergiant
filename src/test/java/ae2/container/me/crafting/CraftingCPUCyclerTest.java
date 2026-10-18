package ae2.container.me.crafting;

import ae2.api.config.CpuSelectionMode;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.crafting.ICraftingService;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingCPUCyclerTest {
    private static int activeRecordCount(CraftingCPUCycler cycler) throws Exception {
        Field field = CraftingCPUCycler.class.getDeclaredField("activeCpuRecords");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(cycler)).size();
    }

    private static Object primitiveDefault(Method method) {
        if (!method.getReturnType().isPrimitive()) {
            return null;
        }
        if (method.getReturnType() == boolean.class) {
            return false;
        }
        if (method.getReturnType() == long.class) {
            return 0L;
        }
        if (method.getReturnType() == double.class) {
            return 0D;
        }
        if (method.getReturnType() == float.class) {
            return 0F;
        }
        if (method.getReturnType() == char.class) {
            return '\0';
        }
        return 0;
    }

    @Test
    void removedCpusLeaveActiveIndexAndRejoiningCpuGetsNewSerial() throws Exception {
        var fixture = new Fixture();
        var firstCpu = fixture.cpu(1);
        fixture.cpus.add(firstCpu);
        fixture.cpus.add(fixture.cpu(2));

        var lastSelection = new AtomicReference<CraftingCPURecord>();
        var cycler = new CraftingCPUCycler(cpu -> true, (selected, available) -> lastSelection.set(selected));
        cycler.detectAndSendChanges(fixture.grid);
        int firstSerial = cycler.cpus().get(0).getSerial();
        assertEquals(2, activeRecordCount(cycler));
        assertEquals(2, cycler.cpus().size());

        fixture.cpus.clear();
        cycler.detectAndSendChanges(fixture.grid);
        assertEquals(0, activeRecordCount(cycler));
        assertEquals(0, cycler.cpus().size());

        fixture.cpus.add(firstCpu);
        cycler.detectAndSendChanges(fixture.grid);
        assertEquals(1, activeRecordCount(cycler));
        assertEquals(1, cycler.cpus().size());
        int rejoinedSerial = cycler.cpus().get(0).getSerial();
        assertTrue(rejoinedSerial > firstSerial);
        assertNotNull(lastSelection.get());

        var secondFixture = new Fixture();
        secondFixture.cpus.add(firstCpu);
        cycler.detectAndSendChanges(secondFixture.grid);
        assertEquals(1, activeRecordCount(cycler));
        assertTrue(cycler.cpus().get(0).getSerial() > rejoinedSerial);
    }

    @Test
    void stableRefreshKeepsOneRecordPerFilteredCpu() throws Exception {
        var fixture = new Fixture();
        for (int i = 0; i < 64; i++) {
            fixture.cpus.add(fixture.cpu(i));
        }

        var cycler = new CraftingCPUCycler(cpu -> true, (selected, available) -> {
        });
        cycler.detectAndSendChanges(fixture.grid);
        List<CraftingCPURecord> first = List.copyOf(cycler.cpus());
        cycler.detectAndSendChanges(fixture.grid);

        assertEquals(64, activeRecordCount(cycler));
        assertEquals(first.stream().map(CraftingCPURecord::getSerial).toList(),
            cycler.cpus().stream().map(CraftingCPURecord::getSerial).toList());
    }

    private static final class Fixture {
        private final Set<ICraftingCPU> cpus = new LinkedHashSet<>();
        private final ICraftingService service = (ICraftingService) Proxy.newProxyInstance(
            Fixture.class.getClassLoader(), new Class<?>[]{ICraftingService.class}, (proxy, method, args) -> {
                if (method.getName().equals("getCpus")) {
                    return ImmutableSet.copyOf(cpus);
                }
                return primitiveDefault(method);
            });
        private final IGrid grid = (IGrid) Proxy.newProxyInstance(
            Fixture.class.getClassLoader(), new Class<?>[]{IGrid.class}, (proxy, method, args) -> {
                if (method.getName().equals("getCraftingService") || method.getName().equals("getService")) {
                    return service;
                }
                return primitiveDefault(method);
            });

        private ICraftingCPU cpu(int id) {
            return (ICraftingCPU) Proxy.newProxyInstance(
                Fixture.class.getClassLoader(), new Class<?>[]{ICraftingCPU.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getAvailableStorage" -> (long) id;
                        case "getCoProcessors" -> id;
                        case "getSelectionMode" -> CpuSelectionMode.ANY;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "cpu-" + id;
                        default -> primitiveDefault(method);
                    };
                });
        }
    }
}
