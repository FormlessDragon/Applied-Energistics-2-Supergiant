package ae2.me.service;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.events.GridEvent;
import ae2.core.AEConfig;
import ae2.me.energy.GridEnergyStorage;
import ae2.me.energy.StoredEnergyAmount;
import ae2.tile.networking.TileController;
import ae2.tile.networking.TileEnergyCell;
import ae2.tile.storage.TileMEChest;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinPowerStorageTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        AEConfig.init();
    }

    @Test
    void builtInLinearCapabilitiesMatchSimulationAcrossDrainAndFill() {
        var buffer = new CountingGridStorage();
        buffer.addNode();
        var sources = List.of(buffer, new EnergyCell(), new Controller());
        for (int i = 0; i < sources.size(); i++) {
            var storage = sources.get(i);
            double capacity = storage.getAEMaxPower();
            assertEquals(0, storage.injectAEPower(capacity / 2, Actionable.MODULATE));
            double[] requests = {0, StoredEnergyAmount.MIN_AMOUNT / 2, StoredEnergyAmount.MIN_AMOUNT, capacity / 4};
            for (double request : requests) {
                assertEquals(storage.extractAEPower(request, Actionable.SIMULATE, PowerMultiplier.ONE),
                    storage.getExtractableAEPower(request));
                assertEquals(request - storage.injectAEPower(request, Actionable.SIMULATE),
                    storage.getReceivableAEPower(request));
                assertEquals(capacity / 2, storage.getAECurrentPower());
            }
            storage.extractAEPower(capacity / 2, Actionable.MODULATE, PowerMultiplier.ONE);
            assertEquals(0, storage.getExtractableAEPower(0));
            assertEquals(capacity, storage.getReceivableAEPower(capacity));
            storage.injectAEPower(capacity, Actionable.MODULATE);
            assertEquals(capacity, storage.getExtractableAEPower(capacity));
            assertEquals(0, storage.getReceivableAEPower(0));
        }
    }

    @Test
    void inherentGridDrainAndRefillNeverSimulateOrRebuildTotals() {
        var buffer = new CountingGridStorage();
        buffer.addNode();
        var group = new EnergyStorageGroup(new EnergyPowerStatistics());
        group.register(buffer);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        double capacity = buffer.getAEMaxPower();
        assertEquals(0, cache.injectPower(capacity, Actionable.MODULATE));
        for (int i = 0; i < 100; i++) {
            assertEquals(1, cache.extractPower(1, Actionable.MODULATE));
            assertEquals(0, cache.injectPower(1, Actionable.MODULATE));
            assertEquals(0, cache.getEnergyDemand(capacity));
        }
        assertEquals(0, buffer.simulations);
        assertEquals(0, cache.getTotalsRebuildCount());
    }

    @Test
    void privateChestBufferUsesDirectPowerAlongsidePublicEnergyCells() {
        var chest = new Chest();
        chest.setInternalCurrentPower(20);
        var cell = new EnergyCell();
        var group = new EnergyStorageGroup(new EnergyPowerStatistics());
        group.register(chest);
        group.register(cell);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertFalse(chest.isAEPublicPowerStorage());
        assertEquals(AccessRestriction.WRITE, chest.getPowerFlow());
        assertEquals(0, cache.getStoredPower());
        assertEquals(cell.getAEMaxPower(), cache.getMaximumPower());
        assertEquals(cell.getAEMaxPower(), cache.getEnergyDemand(Double.MAX_VALUE));
        assertEquals(0, cache.injectPower(10, Actionable.SIMULATE));
        assertEquals(20, chest.getAECurrentPower());
        assertEquals(0, cache.injectPower(10, Actionable.MODULATE));
        assertEquals(10, cell.getAECurrentPower());
        assertEquals(20, chest.getAECurrentPower());
        assertEquals(10, cache.extractPower(10, Actionable.MODULATE));
        assertEquals(0, chest.extractions);

        assertEquals(chest.getAEMaxPower() - 20, chest.getExternalPowerDemand(PowerUnit.AE, Double.MAX_VALUE));
        assertEquals(0, chest.injectExternalPower(PowerUnit.AE, 10, Actionable.SIMULATE));
        assertEquals(20, chest.getAECurrentPower());
        assertEquals(0, chest.injectExternalPower(PowerUnit.AE, 10, Actionable.MODULATE));
        assertEquals(30, chest.getAECurrentPower());
        assertEquals(5, chest.extractAEPower(5, Actionable.MODULATE, PowerMultiplier.ONE));
        cache.refreshStorage(group, chest);
        assertEquals(25, chest.getAECurrentPower());
        assertEquals(0, cache.getStoredPower());
        assertEquals(cell.getAEMaxPower(), cache.getEnergyDemand(Double.MAX_VALUE));
        assertEquals(1, chest.extractions);
    }

    @Test
    void explicitlyPublicWriteOnlyBufferRetainsRegistrationAndChargingContract() {
        var receiver = new Chest();
        receiver.setInternalPublicPowerStorage(true);
        receiver.setInternalCurrentPower(20);
        var group = new EnergyStorageGroup(new EnergyPowerStatistics());
        group.register(receiver);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertTrue(group.contains(receiver));
        assertEquals(AccessRestriction.WRITE, receiver.getPowerFlow());
        assertEquals(receiver.getAEMaxPower() - 20, cache.getEnergyDemand(Double.MAX_VALUE));
        assertEquals(0, cache.injectPower(10, Actionable.SIMULATE));
        assertEquals(20, receiver.getAECurrentPower());
        assertEquals(0, cache.injectPower(10, Actionable.MODULATE));
        assertEquals(30, receiver.getAECurrentPower());
        assertEquals(0, cache.getStoredPower());
        assertEquals(0, cache.getMaximumPower());
        assertEquals(0, cache.extractPower(10, Actionable.MODULATE));
        assertEquals(0, receiver.extractions);
        group.unregister(receiver);
        assertFalse(group.contains(receiver));
        cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(0, cache.getEnergyDemand(Double.MAX_VALUE));
        assertEquals(10, cache.injectPower(10, Actionable.MODULATE));
    }

    private static final class CountingGridStorage extends GridEnergyStorage {
        private int simulations;

        private CountingGridStorage() {
            super(new EventGrid());
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            if (mode == Actionable.SIMULATE) {
                this.simulations++;
            }
            return super.injectAEPower(amount, mode);
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            if (mode == Actionable.SIMULATE) {
                this.simulations++;
            }
            return super.extractAEPower(amount, mode, multiplier);
        }
    }

    private static final class EnergyCell extends TileEnergyCell {
        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }
    }

    private static final class Controller extends TileController {
        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }
    }

    private static final class Chest extends TileMEChest {
        private int extractions;

        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }

        @Override
        protected double extractAEPower(double amount, Actionable mode) {
            this.extractions++;
            return super.extractAEPower(amount, mode);
        }
    }

    private static final class EventGrid implements IGrid {
        @Override
        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        @Override
        public <C extends IGridService> C getService(Class<C> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return List.of();
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> type) {
            return List.of();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> type) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> type) {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public IGridNode getPivot() {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void export(JsonWriter writer) {
            throw new UnsupportedOperationException();
        }
    }
}
