package ae2.me.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.core.AEConfig;
import ae2.items.misc.GenericResourcePackageItem;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalStoragePackageRecoveryTest {
    private static Object previousSide;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.register();
        AEConfig.init();
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) {
            field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
                (proxy, method, args) -> method.getName().equals("getSide") ? Side.SERVER : null));
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    @Test
    void failedLeftoverReturnRestoresTheOriginalPackage() {
        var key = AEItemKey.of(Items.APPLE);
        var handler = new RaceHandler(GenericResourcePackageItem.wrap(key, 10));
        var storage = ExternalStorageFacade.of(handler);

        assertEquals(0, storage.extract(key, 5, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(10, GenericResourcePackageItem.unwrap(handler.getStackInSlot(0)).amount());
    }

    @Test
    void partialLeftoverReturnRestoresOnlyTheUnacceptedAmount() {
        var key = AEItemKey.of(Items.APPLE);
        var handler = new PartialRaceHandler(key, GenericResourcePackageItem.wrap(key, 10));
        var storage = ExternalStorageFacade.of(handler);

        assertEquals(0, storage.extract(key, 5, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(8, GenericResourcePackageItem.unwrap(handler.getStackInSlot(0)).amount());
        assertEquals(2, GenericResourcePackageItem.unwrap(handler.getStackInSlot(1)).amount());
    }

    private static final class RaceHandler extends ItemStackHandler {
        private RaceHandler(ItemStack initial) {
            super(1);
            setStackInSlot(0, initial);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate) {
                return ItemStack.EMPTY;
            }
            var resource = GenericResourcePackageItem.unwrap(stack);
            if (resource != null && resource.amount() == 10) {
                return super.insertItem(slot, stack, false);
            }
            return stack;
        }
    }

    private static final class PartialRaceHandler extends ItemStackHandler {
        private final AEItemKey key;

        private PartialRaceHandler(AEItemKey key, ItemStack initial) {
            super(2);
            this.key = key;
            setStackInSlot(0, initial);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate) {
                return ItemStack.EMPTY;
            }
            var resource = GenericResourcePackageItem.unwrap(stack);
            if (slot == 0 && resource != null && resource.what().equals(key) && resource.amount() == 5) {
                setStackInSlot(1, GenericResourcePackageItem.wrap(key, 2));
                return GenericResourcePackageItem.wrap(key, 3);
            }
            if (slot == 0 && resource != null && resource.what().equals(key) && resource.amount() == 8) {
                return super.insertItem(slot, stack, false);
            }
            return stack;
        }
    }
}
