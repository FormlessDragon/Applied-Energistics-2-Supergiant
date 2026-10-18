package ae2.tile.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.AEConfig;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.tile.crafting.requester.LinkState;
import ae2.tile.crafting.requester.RequestList;
import ae2.tile.crafting.requester.RequesterStorageTracker;
import ae2.tile.crafting.requester.StatusState;
import ae2.util.SettingsFrom;
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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterCustodyTest {
    private static Object previousSide;
    private static AEKey apple, carrot;

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
        apple = AEItemKey.of(Items.APPLE);
        carrot = AEItemKey.of(Items.CARROT);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        f.set(FMLCommonHandler.instance(), previousSide);
    }

    private static void setLink(TileRequester tile, int slot, ICraftingLink link) throws Exception {
        var f = TileRequester.class.getDeclaredField("requestStatus");
        f.setAccessible(true);
        ((StatusState[]) f.get(tile))[slot] = new LinkState(link);
    }

    private static ICraftingLink link(Runnable onCancel) {
        return (ICraftingLink) Proxy.newProxyInstance(ICraftingLink.class.getClassLoader(),
            new Class<?>[]{ICraftingLink.class}, (p, m, a) -> switch (m.getName()) {
                case "cancel" -> {
                    onCancel.run();
                    yield null;
                }
                case "isCanceled", "isDone", "isStandalone" -> false;
                case "equals" -> p == a[0];
                case "hashCode" -> System.identityHashCode(p);
                default -> null;
            });
    }

    @Test
    void changedAndImportedConfigRetainsStockAndRejectsOldLinksBeforeCancelCallback() throws Exception {
        var tile = new TestTile();
        var link = link(() -> {
            assertSame(tile.owner, Thread.currentThread());
            assertEquals(0, tile.insertCraftedItems(tile.oldLink, apple, 7, Actionable.MODULATE));
            // A second configuration change during cancellation must retain the old stock too.
            tile.getRequests().get(0).updateConfiguredStack(new GenericStack(carrot, 20));
            throw new IllegalStateException("extension cancel callback");
        });
        tile.oldLink = link;
        setLink(tile, 0, link);
        assertEquals(10, tile.insertCraftedItems(link, apple, 10, Actionable.SIMULATE));
        assertEquals(0, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(10, tile.insertCraftedItems(link, apple, 10, Actionable.MODULATE));
        tile.getStorageTracker().setKnownAmount(0, apple, 90);
        tile.getStorageTracker().setTotalAmount(0, apple, 100);
        assertDoesNotThrow(() -> tile.onRequestChanged(0));
        assertEquals(10, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(0, tile.getStorageTracker().getKnownAmount(0, apple));
        assertEquals(0, tile.getStorageTracker().getRemainingTotalAmount(0, apple));
        var settings = new NBTTagCompound();
        settings.setTag("requester_requests", new RequestList(null, tile.getRequests().size()).writeToNBT());
        tile.importSettings(SettingsFrom.MEMORY_CARD, settings, null);
        assertEquals(10, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(0, tile.insertCraftedItems(link, apple, 7, Actionable.MODULATE));
        // Idle resumes returning held resources even when the replacement config is empty/disabled.
        assertSame(StatusState.EXPORT, StatusState.IDLE.handle(tile, 0));
        tile.acceptance = 4;
        StatusState.EXPORT.handle(tile, 0);
        assertEquals(4, tile.exported);
        assertEquals(6, tile.getStorageTracker().getBufferedAmount(0, apple));
        tile.acceptance = 0;
        StatusState.EXPORT.handle(tile, 0);
        assertEquals(6, tile.getStorageTracker().getBufferedAmount(0, apple));
        tile.acceptance = Long.MAX_VALUE;
        StatusState.EXPORT.handle(tile, 0);
        assertEquals(10, tile.exported);
        assertEquals(0, tile.getStorageTracker().getBufferedAmount(0, apple));
    }

    @Test
    void custodySurvivesSameVersionSaveUnloadAndDropThenClear() throws Exception {
        var tile = new TestTile();
        var link = link(() -> {
        });
        setLink(tile, 0, link);
        assertEquals(37, tile.insertCraftedItems(link, apple, 37, Actionable.MODULATE));
        tile.onRequestChanged(0);
        var saved = new NBTTagCompound();
        tile.saveAdditional(saved);
        tile.onChunkUnloaded();
        assertEquals(37, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(0, tile.insertCraftedItems(link, apple, 1, Actionable.MODULATE));
        var loaded = new TestTile();
        loaded.loadTag(saved);
        assertEquals(37, loaded.getStorageTracker().getBufferedAmount(0, apple));
        var drops = new ArrayList<ItemStack>();
        loaded.addAdditionalDrops(drops);
        assertEquals(2, drops.size());
        var resources = drops.stream().map(GenericResourcePackageItem::unwrap).filter(Objects::nonNull).toList();
        assertEquals(List.of(new GenericStack(apple, 37)), resources);
        assertEquals(1, drops.stream().filter(stack -> stack.hasTagCompound()
            && stack.getTagCompound().hasKey("requester_submission")).count());
        assertEquals(37, loaded.getStorageTracker().getBufferedAmount(0, apple), "drop collection precedes clear");
        loaded.clearContent();
        assertEquals(0, loaded.getStorageTracker().getBufferedAmount(0, apple));
        var again = new ArrayList<ItemStack>();
        loaded.addAdditionalDrops(again);
        assertTrue(again.isEmpty());
    }

    @Test
    void fixedSeedResetAndExportSequenceConservesAcceptedResourceAndCannotOverflow() {
        var tracker = new RequesterStorageTracker(1, i -> {
        });
        var random = new Random(650006L);
        long accepted = 0, exported = 0;
        for (int i = 0; i < 256; i++) {
            accepted += tracker.addPending(0, apple, random.nextInt(1, 200));
            if (i % 2 == 0) tracker.clearConfiguration(0);
            long sent = Math.min(random.nextInt(200), tracker.getBufferedAmount(0, apple));
            tracker.markExported(0, apple, sent);
            exported += sent;
            assertEquals(accepted, exported + tracker.getBufferedAmount(0, apple));
        }
        tracker.clear(0);
        assertEquals(Long.MAX_VALUE - 1, tracker.addPending(0, apple, Long.MAX_VALUE - 1));
        assertEquals(1, tracker.addPending(0, apple, 10));
        assertEquals(0, tracker.addPending(0, apple, 1));
        assertEquals(0, tracker.addPending(0, carrot, 1));
        tracker.clearConfiguration(0);
        assertEquals(Long.MAX_VALUE, tracker.getBufferedAmount(0, apple));
        var restored = new RequesterStorageTracker(1, i -> {
        });
        restored.readFromNBT(tracker.writeToNBT());
        assertEquals(Long.MAX_VALUE, restored.getBufferedAmount(0, apple));
        assertThrows(UnsupportedOperationException.class, () -> restored.getBufferedStacks(0).clear());
    }

    @Test
    void postCommitSaveFailurePreservesReceiptAndRetriesOnlyNotification() throws Exception {
        var tile = new TestTile();
        var link = link(() -> {
        });
        setLink(tile, 0, link);
        tile.saveNotification = () -> {
            throw new IllegalStateException("save fixture");
        };
        assertEquals(12, tile.insertCraftedItems(link, apple, 12, Actionable.MODULATE));
        assertEquals(12, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(1, tile.saveCalls);
        tile.saveNotification = null;
        tile.handleRequests();
        assertEquals(2, tile.saveCalls);
        assertEquals(12, tile.getStorageTracker().getBufferedAmount(0, apple));
        tile.handleRequests();
        assertEquals(2, tile.saveCalls);
        assertEquals(0, tile.exported);
    }

    @Test
    void nestedReceiptSaveIsDeferredWithoutLosingEitherReceipt() throws Exception {
        var tile = new TestTile();
        var link = link(() -> {
        });
        setLink(tile, 0, link);
        tile.saveNotification = () -> {
            tile.saveNotification = null;
            assertEquals(3, tile.insertCraftedItems(link, apple, 3, Actionable.MODULATE));
        };
        assertEquals(5, tile.insertCraftedItems(link, apple, 5, Actionable.MODULATE));
        assertEquals(8, tile.getStorageTracker().getBufferedAmount(0, apple));
        assertEquals(1, tile.saveCalls);
        tile.handleRequests();
        assertEquals(2, tile.saveCalls);
        assertEquals(8, tile.getStorageTracker().getBufferedAmount(0, apple));
    }

    @Test
    void simulateAndRejectedReceiptDoNotNotifyAndFatalSavePropagates() throws Exception {
        var tile = new TestTile();
        var link = link(() -> {
        });
        setLink(tile, 0, link);
        assertEquals(7, tile.insertCraftedItems(link, apple, 7, Actionable.SIMULATE));
        assertEquals(0, tile.saveCalls);
        var fatal = new OutOfMemoryError("save fixture");
        tile.saveNotification = () -> {
            throw fatal;
        };
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
            () -> tile.insertCraftedItems(link, apple, 7, Actionable.MODULATE)));
        assertEquals(7, tile.getStorageTracker().getBufferedAmount(0, apple));
        tile.saveNotification = null;
        assertEquals(0, tile.insertCraftedItems(link, carrot, 1, Actionable.MODULATE));
        assertEquals(1, tile.saveCalls);
        assertEquals(1, tile.insertCraftedItems(link, apple, 1, Actionable.MODULATE));
        assertEquals(2, tile.saveCalls);
    }

    private static final class TestTile extends TileRequester {
        final Thread owner = Thread.currentThread();
        ICraftingLink oldLink;
        long acceptance, exported;
        private Runnable saveNotification;
        private int saveCalls;

        @Override
        public void saveChanges() {
            saveCalls++;
            if (saveNotification != null) saveNotification.run();
        }

        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }

        @Override
        public long insert(AEKey key, long amount) {
            assertSame(owner, Thread.currentThread());
            long accepted = Math.min(acceptance, amount);
            exported += accepted;
            return accepted;
        }
    }
}
