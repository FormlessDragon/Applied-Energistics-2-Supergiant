package ae2.tile.misc;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.networking.IGrid;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.energy.IEnergyService;
import ae2.core.AEConfig;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargerEnergyBudgetTest {
    private static Object previousSide;
    private static Network constructing;

    @BeforeAll
    static void setup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        previousSide = f.get(FMLCommonHandler.instance());
        if (previousSide == null) f.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
    }

    @AfterAll
    static void cleanup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        f.set(FMLCommonHandler.instance(), previousSide);
    }

    @Test
    void chargerDoesNotOverdrawNetworkWhenLocalEnergyCoversTheItemDeficit() {
        var network = new Network();
        constructing = network;
        var charger = new TestCharger();
        var item = new RecordingItem();
        charger.getInternalInventory().setItemDirect(0, new ItemStack(item));
        charger.setInternalCurrentPower(1600);
        charger.tickingRequest(null, 1);
        assertEquals(1, item.offered);
        assertEquals(0, network.extracted);
        assertEquals(100, item.current);
        assertEquals(1599, charger.getInternalCurrentPower());
    }

    @Test
    void hugeTickGapIsCappedBeforeMultiplicationAndNetworkOnlyFillsRemainder() {
        var network = new Network();
        network.available = 500;
        constructing = network;
        var charger = new TestCharger();
        var item = new RecordingItem();
        item.current = 0;
        item.rate = 1_000_000;
        charger.getInternalInventory().setItemDirect(0, new ItemStack(item));
        charger.setInternalCurrentPower(0);
        charger.tickingRequest(null, Integer.MAX_VALUE);
        assertEquals(100, item.offered);
        assertEquals(100, network.firstRequested);
        assertEquals(100, item.current);
    }

    private static final class Network {
        final IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
            new Class<?>[]{IEnergyService.class}, (p, m, a) -> {
                if (m.getName().equals("extractAEPower")) {
                    double amount = Math.min((double) a[0], this.available);
                    if (this.firstRequested < 0) this.firstRequested = (double) a[0];
                    if (a[1] == Actionable.MODULATE) {
                        this.extracted += amount;
                        this.available -= amount;
                    }
                    return amount;
                }
                return null;
            });
        final IGrid grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (p, m, a) -> m.getName().equals("getEnergyService") ? energy : null);
        final IManagedGridNode managed = (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
            new Class<?>[]{IManagedGridNode.class}, (p, m, a) -> {
                if (m.getName().equals("getGrid")) return grid;
                if (m.getReturnType() == IManagedGridNode.class) return p;
                if (m.getReturnType() == boolean.class) return false;
                return null;
            });
        double available = Double.MAX_VALUE, extracted, firstRequested = -1;
    }

    private static final class TestCharger extends TileCharger {
        TestCharger() {
        }

        @Override
        protected IManagedGridNode createMainNode() {
            return constructing.managed;
        }

        @Override
        public ItemStack getItemFromTile() {
            return new ItemStack(Items.APPLE);
        }
    }

    private static final class RecordingItem extends Item implements IAEItemPowerStorage {
        double current = 99, rate = 2000, offered;

        public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
            offered += amount;
            current += amount;
            return 0;
        }

        public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
            return 0;
        }

        public double getAEMaxPower(ItemStack stack) {
            return 100;
        }

        public double getAECurrentPower(ItemStack stack) {
            return current;
        }

        public AccessRestriction getPowerFlow(ItemStack stack) {
            return AccessRestriction.READ_WRITE;
        }

        public double getChargeRate(ItemStack stack) {
            return rate;
        }
    }
}
