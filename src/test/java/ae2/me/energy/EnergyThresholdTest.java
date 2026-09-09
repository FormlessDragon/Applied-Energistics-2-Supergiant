package ae2.me.energy;

import ae2.api.networking.energy.IEnergyWatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EnergyThresholdTest {
    @Test
    void equalThresholdsWithHashCollisionsRemainDistinct() {
        IEnergyWatcher first = new TestWatcher();
        IEnergyWatcher second = new TestWatcher();

        EnergyThreshold firstThreshold = new EnergyThreshold(10, first);
        EnergyThreshold secondThreshold = new EnergyThreshold(10, second);

        assertNotEquals(0, firstThreshold.compareTo(secondThreshold));
    }

    private static final class TestWatcher implements IEnergyWatcher {
        @Override
        public void add(double amount) {
        }

        @Override
        public boolean remove(double amount) {
            return false;
        }

        @Override
        public void reset() {
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }
}
