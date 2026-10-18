package ae2.tile.crafting;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.IPatternDetailsDecoder;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.helpers.externalstorage.GenericStackInv;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileMolecularAssemblerEscrowTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        AEConfig.init();
        try {
            var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
            field.setAccessible(true);
            if (field.get(FMLCommonHandler.instance()) == null) {
                field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                    IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
                    (proxy, method, args) -> method.getName().equals("getSide") ? Side.SERVER : null));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not initialize FML side delegate", e);
        }
    }

    private static void withDecoder(PatternDecode decode, CheckedAction action) throws Exception {
        var decoder = new IPatternDetailsDecoder() {
            @Override
            public boolean isEncodedPattern(ItemStack stack) {
                return false;
            }

            @Override
            public IPatternDetails decodePattern(AEItemKey key, World world) {
                return null;
            }

            @Override
            public IPatternDetails decodePattern(ItemStack stack, World world) {
                try {
                    return decode.decode(stack, world);
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
        var field = PatternDetailsHelper.class.getDeclaredField("DECODERS");
        field.setAccessible(true);
        var decoders = (List<IPatternDetailsDecoder>) field.get(null);
        decoders.add(decoder);
        try {
            action.run();
        } finally {
            decoders.remove(decoder);
        }
    }

    private static World decoderWorld() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (World) ((Unsafe) field.get(null))
            .allocateInstance(WorldServer.class);
    }

    private static void rebuildPatterns(TileMolecularAssembler assembler) throws Exception {
        var method = TileMolecularAssembler.class.getDeclaredMethod("updatePatterns");
        method.setAccessible(true);
        method.invoke(assembler);
    }

    private static Deque<GenericStack> completedQueue(TileMolecularAssembler assembler) throws Exception {
        return (Deque<GenericStack>) field("completedOutputs").get(assembler);
    }

    private static void refill(TileMolecularAssembler assembler) throws Exception {
        var method = TileMolecularAssembler.class.getDeclaredMethod("refillOutputBuffer");
        method.setAccessible(true);
        method.invoke(assembler);
    }

    private static MEStorage storage(Insertion insertion) {
        return (MEStorage) Proxy.newProxyInstance(MEStorage.class.getClassLoader(),
            new Class<?>[]{MEStorage.class}, (proxy, method, args) -> {
                if (method.getName().equals("insert")) return insertion.insert((AEKey) args[0], (long) args[1]);
                return null;
            });
    }

    private static GenericStackInv outputBuffer(TileMolecularAssembler assembler) throws Exception {
        return (GenericStackInv) field("outputBuffer").get(assembler);
    }

    private static boolean inject(TileMolecularAssembler assembler, MEStorage storage) throws Exception {
        var method = TileMolecularAssembler.class.getDeclaredMethod("injectIntoNetwork", MEStorage.class);
        method.setAccessible(true);
        return (boolean) method.invoke(assembler, storage);
    }

    private static KeyCounter[] appleInputs() {
        var holder = new KeyCounter[]{new KeyCounter()};
        holder[0].add(AEItemKey.of(Items.APPLE), 7);
        return holder;
    }

    private static byte[] nbtBytes(NBTTagCompound tag) throws IOException {
        var bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static NBTTagCompound validPendingData() {
        var data = new NBTTagCompound();
        data.setTag("cachedInputs", GenericStack.writeList(List.of(
            new GenericStack(AEItemKey.of(Items.APPLE), 7))));
        data.setTag("cachedOutputs", GenericStack.writeList(List.of(
            new GenericStack(AEItemKey.of(Items.CARROT), 1))));
        data.setInteger("pendingCrafts", 1);
        data.setDouble("progress", 63);
        data.setTag("mainOutput", GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.CARROT), 1)));
        data.setTag("currentPattern", new ItemStack(Items.PAPER).writeToNBT(new NBTTagCompound()));
        return data;
    }

    private static IAssemblerPattern pushPattern() {
        return pushPattern(() -> {
        });
    }

    private static IAssemblerPattern pushPattern(Runnable duringAssemble) {
        return (IAssemblerPattern) Proxy.newProxyInstance(IAssemblerPattern.class.getClassLoader(),
            new Class<?>[]{IAssemblerPattern.class}, (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "assemble" -> {
                    duringAssemble.run();
                    yield new ItemStack(Items.CARROT);
                }
                case "getInputs" -> new IPatternDetails.IInput[0];
                case "getDefinition" -> AEItemKey.of(Items.PAPER);
                default -> null;
            });
    }

    private static <T extends TileMolecularAssembler> T pendingAssembler(T assembler) throws Exception {
        addStack(assembler, "cachedInputs", new GenericStack(AEItemKey.of(Items.APPLE), 7));
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1));
        field("pendingCrafts").setInt(assembler, 1);
        field("currentPattern").set(assembler, Proxy.newProxyInstance(
            IAssemblerPattern.class.getClassLoader(), new Class<?>[]{IAssemblerPattern.class},
            (proxy, method, args) -> null));
        return assembler;
    }

    private static boolean complete(TileMolecularAssembler assembler) throws Exception {
        var method = TileMolecularAssembler.class.getDeclaredMethod("tryCompleteCraft", int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(assembler, 10);
    }

    private static Field field(String name) throws Exception {
        var field = TileMolecularAssembler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static void addStack(TileMolecularAssembler assembler, String fieldName, GenericStack stack)
        throws Exception {
        Field field = TileMolecularAssembler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((List<GenericStack>) field.get(assembler)).add(stack);
    }

    @Test
    void cachedInputsArePersistedAndReturnedOnBreak() throws Exception {
        var assembler = new TileMolecularAssembler();
        addStack(assembler, "cachedInputs", new GenericStack(AEItemKey.of(Items.APPLE), 7));
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1));

        var data = new NBTTagCompound();
        assembler.saveAdditional(data);
        var savedInputs = GenericStack.readList(data.getTagList("cachedInputs", 10));
        assertEquals(7, savedInputs.get(0).amount());

        var drops = new ArrayList<ItemStack>();
        assembler.addAdditionalDrops(drops);

        assertEquals(1, drops.size());
        assertEquals(Items.APPLE, drops.get(0).getItem());
        assertEquals(7, drops.get(0).getCount());
    }

    @Test
    void unavailableWorldRoundTripRetainsProgressAndInputCustody() throws Exception {
        var original = pendingAssembler(new TileMolecularAssembler());
        field("currentPatternStack").set(original, new ItemStack(Items.PAPER));
        field("progress").setDouble(original, 63);
        field("currentMainOutput").set(original, new GenericStack(AEItemKey.of(Items.CARROT), 1));
        var saved = new NBTTagCompound();
        original.saveAdditional(saved);
        var restored = new TileMolecularAssembler();
        restored.loadTag(saved);
        var resaved = new NBTTagCompound();
        restored.saveAdditional(resaved);
        for (var name : new String[]{"cachedInputs", "cachedOutputs", "pendingCrafts", "progress",
            "currentPattern", "mainOutput"}) {
            assertEquals(saved.getTag(name), resaved.getTag(name), name);
        }
        var drops = new ArrayList<ItemStack>();
        restored.addAdditionalDrops(drops);
        assertEquals(Items.APPLE, drops.getFirst().getItem());
        assertEquals(7, drops.getFirst().getCount());
    }

    @Test
    void completionListenerSeesOutputsWithoutRefundableInputs() throws Exception {
        var assembler = pendingAssembler(new SavingAssembler());
        assembler.capture = true;
        assertTrue(complete(assembler));
        assertFalse(assembler.snapshot.hasKey("cachedInputs"));
        assertFalse(assembler.snapshot.hasKey("cachedOutputs"));
        assertEquals(0, assembler.snapshot.getInteger("pendingCrafts"));
        assertEquals(Items.CARROT, ((AEItemKey) GenericStack.readList(
            assembler.snapshot.getTagList("outputBuffer", 10)).getFirst().what()).getItem());
    }

    @Test
    void fullBufferCompletesIntoDurableOutputQueueWithoutRefundableInputs() throws Exception {
        var assembler = pendingAssembler(new TileMolecularAssembler());
        var buffer = outputBuffer(assembler);
        for (int slot = 0; slot < buffer.size(); slot++) {
            buffer.setStack(slot, new GenericStack(AEItemKey.of(Items.POTATO), 64));
        }
        assertTrue(complete(assembler));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertTrue(((List<?>) field("cachedInputs").get(assembler)).isEmpty());
        assertEquals(1, completedQueue(assembler).size());
        assertEquals(AEItemKey.of(Items.CARROT), completedQueue(assembler).getFirst().what());
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        var restored = new ActiveAssembler();
        restored.loadTag(saved);
        assertEquals(1, completedQueue(restored).size());
        assertTrue(restored.isBusy());
        assertEquals(64, outputBuffer(restored).getAmount(0));
        var resaved = new NBTTagCompound();
        restored.saveAdditional(resaved);
        assertEquals(saved.getTag("completedOutputs"), resaved.getTag("completedOutputs"));
        var drops = new ArrayList<ItemStack>();
        restored.addAdditionalDrops(drops);
        assertEquals(1, drops.size());
        assertEquals(Items.CARROT, drops.getFirst().getItem());
        assertEquals(1, drops.getFirst().getCount());
    }

    @Test
    void completedOutputsLargerThanBufferDrainAcrossSeveralPassesExactlyOnce() throws Exception {
        var assembler = pendingAssembler(new ActiveAssembler());
        ((List<?>) field("cachedOutputs").get(assembler)).clear();
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1300));
        for (int variant = 0; variant < 11; variant++) {
            addStack(assembler, "cachedOutputs", new GenericStack(
                AEItemKey.of(new ItemStack(Items.DYE, 1, variant)), 1));
        }
        assertTrue(complete(assembler));
        assertTrue(assembler.isBusy());
        var delivered = new KeyCounter();
        int passes = 0;
        while (!completedQueue(assembler).isEmpty() || !outputBuffer(assembler).isEmpty()) {
            assertTrue(passes++ < 10, "completed queue must make progress");
            inject(assembler, storage((key, amount) -> {
                delivered.add(key, amount);
                return amount;
            }));
            refill(assembler);
        }
        assertEquals(1300, delivered.get(AEItemKey.of(Items.CARROT)));
        for (int variant = 0; variant < 11; variant++) {
            assertEquals(1, delivered.get(AEItemKey.of(new ItemStack(Items.DYE, 1, variant))));
        }
        assertEquals(0, delivered.get(AEItemKey.of(Items.APPLE)));
        assertFalse(assembler.isBusy());
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertTrue(((List<?>) field("cachedInputs").get(assembler)).isEmpty());
    }

    @Test
    void completedQueueKeepsTickingWhileOutputBufferCannotDrain() throws Exception {
        var assembler = pendingAssembler(new ActiveAssembler());
        ((List<?>) field("cachedOutputs").get(assembler)).clear();
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1300));
        assertTrue(complete(assembler));
        assertEquals(TickRateModulation.IDLE, assembler.tickingRequest(null, 1));
        assertEquals(660, completedQueue(assembler).getFirst().amount());
        assertEquals(64, outputBuffer(assembler).getAmount(0));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertEquals(0, field("progress").getDouble(assembler));
    }

    @Test
    void completionSaveCallbackSeesOverflowQueueInsteadOfRefundableInputs() throws Exception {
        var assembler = pendingAssembler(new SavingAssembler());
        ((List<?>) field("cachedOutputs").get(assembler)).clear();
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1300));
        assembler.capture = true;
        assertTrue(complete(assembler));
        assertFalse(assembler.snapshot.hasKey("cachedInputs"));
        assertFalse(assembler.snapshot.hasKey("cachedOutputs"));
        assertEquals(660, GenericStack.readList(
            assembler.snapshot.getTagList("completedOutputs", 10)).getFirst().amount());
        var restored = new ActiveAssembler();
        restored.loadTag(assembler.snapshot);
        assertEquals(660, completedQueue(restored).getFirst().amount());
        assertEquals(64, outputBuffer(restored).getAmount(0));
    }

    @Test
    void malformedCompletedQueueRetainsOriginalAndStopsDelivery() throws Exception {
        for (boolean wrongType : new boolean[]{false, true}) {
            var data = new NBTTagCompound();
            if (wrongType) data.setString("completedOutputs", "invalid queue");
            else {
                var invalid = GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.CARROT), 1));
                invalid.setLong(GenericStack.AMOUNT_FIELD, -1);
                var list = new NBTTagList();
                list.appendTag(invalid);
                data.setTag("completedOutputs", list);
            }
            var restored = new ActiveAssembler();
            restored.loadTag(data);
            assertTrue(restored.isBusy());
            assertFalse(inject(restored, storage((key, amount) -> {
                throw new AssertionError("invalid completed queue must pause delivery");
            })));
            var saved = new NBTTagCompound();
            restored.saveAdditional(saved);
            assertEquals(data.getTag("completedOutputs"), saved.getTag("completedOutputs"));
            var drops = new ArrayList<ItemStack>();
            restored.addAdditionalDrops(drops);
            assertEquals(1, drops.size());
            assertEquals(data.getTag("completedOutputs"),
                drops.getFirst().getTagCompound().getTag("assembler_completed_outputs"));
        }
    }

    @Test
    void blockedRefillDoesNotReplaceQueueOrNotifyUntilBufferChanges() throws Exception {
        var assembler = pendingAssembler(new CountingSaveAssembler());
        ((List<?>) field("cachedOutputs").get(assembler)).clear();
        addStack(assembler, "cachedOutputs", new GenericStack(AEItemKey.of(Items.CARROT), 1300));
        assertTrue(complete(assembler));
        var queue = completedQueue(assembler);
        int saves = assembler.saves;
        for (int attempt = 0; attempt < 20; attempt++) refill(assembler);
        Assertions.assertSame(queue, completedQueue(assembler));
        Assertions.assertSame(queue, field("blockedOutputQueue").get(assembler));
        assertEquals(saves, assembler.saves);
        assertEquals(660, queue.getFirst().amount());
        // A rejected external offer restores identical contents and must not invalidate the failed refill.
        assertFalse(inject(assembler, storage((key, amount) -> 0)));
        saves = assembler.saves;
        refill(assembler);
        assertEquals(saves, assembler.saves);
        Assertions.assertSame(queue, completedQueue(assembler));
        // Successful removal changes buffer contents and permits the frozen queue to advance.
        assertTrue(inject(assembler, storage((key, amount) -> amount)));
        refill(assembler);
        assertEquals(20, completedQueue(assembler).getFirst().amount());
        assertEquals(64, outputBuffer(assembler).getAmount(0));
        assertTrue(assembler.saves > saves);
    }

    @Test
    void outputCallbackInventoryChangeCannotBeOverwrittenByOldRemainder() throws Exception {
        var assembler = new ActiveAssembler();
        var buffer = outputBuffer(assembler);
        buffer.setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        assertFalse(inject(assembler, storage((key, amount) -> {
            buffer.setStack(0, new GenericStack(AEItemKey.of(Items.APPLE), 2));
            return 3;
        })));
        assertEquals(AEItemKey.of(Items.APPLE), buffer.getKey(0));
        assertEquals(2, buffer.getAmount(0));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(7, saved.getCompoundTag("outputTransfer").getCompoundTag("offered")
                             .getLong(GenericStack.AMOUNT_FIELD));
        assertTrue(assembler.isBusy());
        assertFalse(inject(assembler, storage((key, amount) -> {
            throw new AssertionError("changed inventory with unknown receipt must not retry");
        })));
    }

    @Test
    void sameCapacityUpgradeKeepsPatternIndexButInvalidatesPreparedPush() throws Exception {
        var assembler = new SlotCountAssembler();
        rebuildPatterns(assembler);
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var version = field("terminalContentsVersion").getLong(assembler);
        var generation = field("craftGeneration").get(assembler);
        int saves = assembler.saves;
        assembler.onUpgradesChanged();
        assertEquals(List.of(pattern), field("patterns").get(assembler));
        assertEquals(version, field("terminalContentsVersion").getLong(assembler));
        Assertions.assertNotSame(generation, field("craftGeneration").get(assembler));
        assertEquals(saves + 1, assembler.saves);
        var duringUpgrade = pushPattern(assembler::onUpgradesChanged);
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(duringUpgrade);
        var holder = appleInputs();
        assertFalse(assembler.pushPattern(duringUpgrade, holder, 1));
        assertEquals(7, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
    }

    @Test
    void changedCapacityUpgradeRebuildsAndNormalPatternEditsStillInvalidateIndex() throws Exception {
        var assembler = new SlotCountAssembler();
        rebuildPatterns(assembler);
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        long version = field("terminalContentsVersion").getLong(assembler);
        assembler.slots = 18;
        assembler.onUpgradesChanged();
        assertTrue(((List<?>) field("patterns").get(assembler)).isEmpty());
        assertEquals(18, field("indexedPatternSlots").getInt(assembler));
        assertEquals(version + 1, field("terminalContentsVersion").getLong(assembler));
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        assembler.onChangeInventory(assembler.getPatternInventory(), 0);
        assertTrue(((List<?>) field("patterns").get(assembler)).isEmpty());
        assertEquals(version + 2, field("terminalContentsVersion").getLong(assembler));
    }

    @Test
    void patternDecodeFailurePublishesCompleteReplacementAndKeepsOldSnapshotStable() throws Exception {
        var assembler = new ActiveAssembler();
        var inv = assembler.getPatternInventory();
        inv.setItemDirect(0, new ItemStack(Items.PAPER));
        inv.setItemDirect(1, new ItemStack(Items.BOOK));
        var old = (List<IAssemblerPattern>) field("patterns").get(assembler);
        var oldPattern = pushPattern();
        old.add(oldPattern);
        assembler.setWorld(decoderWorld());
        var accepted = pushPattern();
        withDecoder((stack, world) -> {
            if (stack.getItem() == Items.PAPER) throw new IllegalStateException("decoder fixture");
            return stack.getItem() == Items.BOOK ? accepted : null;
        }, () -> rebuildPatterns(assembler));
        assertEquals(List.of(accepted), field("patterns").get(assembler));
        assertEquals(List.of(oldPattern), old);
        assertFalse(assembler.containsPattern(AEItemKey.of(Items.PAPER)));
        assertTrue(assembler.containsPattern(AEItemKey.of(Items.BOOK)));
    }

    @Test
    void nestedPatternEditPublishesLatestSnapshotWithoutOuterOverwrite() throws Exception {
        var assembler = new ActiveAssembler();
        assembler.getPatternInventory().setItemDirect(0, new ItemStack(Items.PAPER));
        assembler.setWorld(decoderWorld());
        var outer = pushPattern();
        var latest = pushPattern();
        boolean[] nested = {false};
        long version = field("terminalContentsVersion").getLong(assembler);
        withDecoder((stack, world) -> {
            if (stack.getItem() == Items.PAPER && !nested[0]) {
                nested[0] = true;
                assembler.getPatternInventory().setItemDirect(0, new ItemStack(Items.BOOK));
                return outer;
            }
            return stack.getItem() == Items.BOOK ? latest : null;
        }, () -> rebuildPatterns(assembler));
        assertTrue(nested[0]);
        assertEquals(List.of(latest), field("patterns").get(assembler));
        assertEquals(version + 1, field("terminalContentsVersion").getLong(assembler));
        assertTrue(assembler.containsPattern(AEItemKey.of(Items.BOOK)));
        assertFalse(assembler.containsPattern(AEItemKey.of(Items.PAPER)));
    }

    @Test
    void pushOverflowRejectsBeforeTakingInputCustody() throws Exception {
        var assembler = new ActiveAssembler();
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
        holder[0].add(AEItemKey.of(Items.APPLE), Long.MAX_VALUE);
        holder[1].add(AEItemKey.of(Items.APPLE), 1);
        var before = new NBTTagCompound();
        assembler.saveAdditional(before);
        assertFalse(assembler.pushPattern(pattern, holder, 1));
        assertEquals(Long.MAX_VALUE, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(1, holder[1].get(AEItemKey.of(Items.APPLE)));
        var after = new NBTTagCompound();
        assembler.saveAdditional(after);
        assertEquals(before, after);
    }

    @Test
    void acceptedPushPublishesEscrowBeforeSaveAndClearsCaller() throws Exception {
        var assembler = new ActiveAssembler();
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = new KeyCounter[]{new KeyCounter()};
        holder[0].add(AEItemKey.of(Items.APPLE), 7);
        assertTrue(assembler.pushPattern(pattern, holder, 1));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(7, GenericStack.readList(saved.getTagList("cachedInputs", 10)).getFirst().amount());
        assertEquals(1, saved.getInteger("pendingCrafts"));
    }

    @Test
    void clearDuringRecipeRejectsOuterPushWithoutTakingInputs() throws Exception {
        var assembler = new ActiveAssembler();
        var pattern = pushPattern(assembler::clearContent);
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = appleInputs();
        assertFalse(assembler.pushPattern(pattern, holder, 1));
        assertEquals(7, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertTrue(((List<?>) field("cachedInputs").get(assembler)).isEmpty());
    }

    @Test
    void nestedPushCannotTakeTheOuterInputHolder() throws Exception {
        var assembler = new ActiveAssembler();
        var nested = pushPattern();
        var holder = appleInputs();
        var pattern = pushPattern(() -> assertFalse(assembler.pushPattern(nested, holder, 1)));
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(nested);
        assertTrue(assembler.pushPattern(pattern, holder, 1));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(1, field("pendingCrafts").getInt(assembler));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(7, GenericStack.readList(saved.getTagList("cachedInputs", 10)).getFirst().amount());
    }

    @Test
    void recipeExceptionReleasesPreparationGuardForNextPush() throws Exception {
        var assembler = new ActiveAssembler();
        var broken = pushPattern(() -> {
            throw new IllegalStateException("recipe fixture");
        });
        var valid = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(broken);
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(valid);
        var holder = appleInputs();
        assertThrows(IllegalStateException.class, () -> assembler.pushPattern(broken, holder, 1));
        assertEquals(7, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertTrue(assembler.pushPattern(valid, holder, 1));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
    }

    @Test
    void postCommitSaveFailureStillReturnsSuccessfulPushReceipt() throws Exception {
        var assembler = new FailingSaveAssembler();
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = appleInputs();
        assembler.fail = true;
        assertTrue(assembler.pushPattern(pattern, holder, 1));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(1, field("pendingCrafts").getInt(assembler));
        assertTrue(field("awake").getBoolean(assembler), "wake notification still runs after save failure");
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(7, GenericStack.readList(saved.getTagList("cachedInputs", 10)).getFirst().amount());
        assertEquals(1, GenericStack.readList(saved.getTagList("cachedOutputs", 10)).getFirst().amount());
    }

    @Test
    void postCommitSaveFailureCannotTurnCompletedOutputsBackIntoInputs() throws Exception {
        var assembler = pendingAssembler(new FailingSaveAssembler());
        assembler.fail = true;
        assertTrue(complete(assembler));
        assertEquals(0, field("pendingCrafts").getInt(assembler));
        assertTrue(((List<?>) field("cachedInputs").get(assembler)).isEmpty());
        assertTrue(((List<?>) field("cachedOutputs").get(assembler)).isEmpty());
        var buffer = (GenericStackInv) field("outputBuffer").get(assembler);
        assertEquals(AEItemKey.of(Items.CARROT), buffer.getKey(0));
        assertEquals(1, buffer.getAmount(0));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertFalse(saved.hasKey("cachedInputs"));
        assertEquals(1, GenericStack.readList(saved.getTagList("outputBuffer", 10)).getFirst().amount());
    }

    @Test
    void schedulingFailureAfterCommitStillReturnsSuccessfulPushReceipt() throws Exception {
        var assembler = new FailingWakeAssembler();
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = appleInputs();
        assertTrue(assembler.pushPattern(pattern, holder, 1));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(1, assembler.saveCalls);
        assertEquals(1, field("pendingCrafts").getInt(assembler));
    }

    @Test
    void fatalPostCommitErrorContinuesToPropagate() throws Exception {
        var fatal = new OutOfMemoryError("fatal notification fixture");
        var assembler = new ActiveAssembler() {
            @Override
            public void saveChanges() {
                throw fatal;
            }
        };
        var pattern = pushPattern();
        ((List<IAssemblerPattern>) field("patterns").get(assembler)).add(pattern);
        var holder = appleInputs();
        assertEquals(fatal, assertThrows(OutOfMemoryError.class, () -> assembler.pushPattern(pattern, holder, 1)));
        assertFalse(field("preparingPush").getBoolean(assembler));
        assertEquals(0, holder[0].get(AEItemKey.of(Items.APPLE)));
        assertEquals(1, field("pendingCrafts").getInt(assembler));
    }

    @Test
    void outputPartialReceiptRestoresOnlyRemainderAndRejectsNestedTransfer() throws Exception {
        var assembler = new ActiveAssembler();
        var buffer = outputBuffer(assembler);
        buffer.setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        int[] calls = {0};
        var storage = storage((key, amount) -> {
            calls[0]++;
            assertEquals(7, amount);
            assertNull(buffer.getStack(0));
            assertTrue(assembler.isBusy());
            assertFalse(inject(assembler, storage((nestedKey, nestedAmount) -> {
                throw new AssertionError("nested output transfer");
            })));
            var snapshot = new NBTTagCompound();
            assembler.saveAdditional(snapshot);
            assertTrue(snapshot.hasKey("outputTransfer"));
            assertFalse(snapshot.hasKey("outputBuffer"));
            return 3;
        });
        assertTrue(inject(assembler, storage));
        assertEquals(1, calls[0]);
        assertEquals(4, buffer.getAmount(0));
        assertNull(field("outputTransfer").get(assembler));
        assertTrue(inject(assembler, storage((key, amount) -> amount)));
        assertTrue(buffer.isEmpty());
    }

    @Test
    void uncertainOutputReceiptIsPreservedAcrossSaveAndNeverAutomaticallyRetried() throws Exception {
        for (int receipt : new int[]{-1, 8, 99}) {
            var assembler = new ActiveAssembler();
            outputBuffer(assembler).setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
            var target = storage((key, amount) -> {
                if (receipt == 99) throw new IllegalStateException("target may have mutated");
                return receipt;
            });
            assertFalse(inject(assembler, target));
            assertFalse(field("injectingOutput").getBoolean(assembler));
            assertTrue(outputBuffer(assembler).isEmpty());
            var saved = new NBTTagCompound();
            assembler.saveAdditional(saved);
            var transfer = saved.getCompoundTag("outputTransfer");
            assertEquals("unconfirmed", transfer.getString("state"));
            assertEquals(7, transfer.getCompoundTag("offered").getLong(GenericStack.AMOUNT_FIELD));
            var restored = new ActiveAssembler();
            restored.loadTag(saved);
            assertTrue(restored.isBusy());
            assertFalse(inject(restored, storage((key, amount) -> {
                throw new AssertionError("unconfirmed transfer retried");
            })));
            var resaved = new NBTTagCompound();
            restored.saveAdditional(resaved);
            assertEquals(transfer, resaved.getTag("outputTransfer"));
            var drops = new ArrayList<ItemStack>();
            restored.addAdditionalDrops(drops);
            assertEquals(1, drops.size());
            assertEquals(AEItems.MISSING_CONTENT.item(), drops.getFirst().getItem());
            assertEquals(transfer, drops.getFirst().getTagCompound().getTag("assembler_output_transfer"));
        }
    }

    @Test
    void outputRejectionRestoresWholeOfferWithoutLeavingJournal() throws Exception {
        var assembler = new ActiveAssembler();
        outputBuffer(assembler).setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        assertFalse(inject(assembler, storage((key, amount) -> 0)));
        assertEquals(7, outputBuffer(assembler).getAmount(0));
        assertNull(field("outputTransfer").get(assembler));
    }

    @Test
    void clearAndReplacementDuringOutputCallbackCannotReceiveOldRemainder() throws Exception {
        var assembler = new ActiveAssembler();
        var buffer = outputBuffer(assembler);
        buffer.setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        var drops = new ArrayList<ItemStack>();
        assertFalse(inject(assembler, storage((key, amount) -> {
            assembler.addAdditionalDrops(drops);
            assembler.clearContent();
            buffer.setStack(0, new GenericStack(AEItemKey.of(Items.APPLE), 2));
            return 3;
        })));
        assertEquals(AEItemKey.of(Items.APPLE), buffer.getKey(0));
        assertEquals(2, buffer.getAmount(0));
        assertEquals(1, drops.size());
        assertTrue(drops.getFirst().getTagCompound().hasKey("assembler_output_transfer"));
        assertFalse(field("injectingOutput").getBoolean(assembler));
    }

    @Test
    void reloadDuringOutputCallbackPreservesSnapshotInsteadOfApplyingOldReceipt() throws Exception {
        var assembler = new ActiveAssembler();
        outputBuffer(assembler).setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        var snapshot = new NBTTagCompound();
        assertFalse(inject(assembler, storage((key, amount) -> {
            assembler.saveAdditional(snapshot);
            assembler.loadTag(snapshot);
            return 3;
        })));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(snapshot.getTag("outputTransfer"), saved.getTag("outputTransfer"));
        assertTrue(outputBuffer(assembler).isEmpty());
        assertTrue(assembler.isBusy());
        assertFalse(field("injectingOutput").getBoolean(assembler));
    }

    @Test
    void malformedTransferJournalRemainsOpaqueAndStopsOutputDelivery() throws Exception {
        var data = new NBTTagCompound();
        data.setString("outputTransfer", "invalid-but-preserved");
        var assembler = new ActiveAssembler();
        assembler.loadTag(data);
        assertTrue(assembler.isBusy());
        assertFalse(inject(assembler, storage((key, amount) -> {
            throw new AssertionError("malformed transfer retried");
        })));
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(data.getTag("outputTransfer"), saved.getTag("outputTransfer"));
    }

    @Test
    void outputReceiptDoesNotSleepAndWakeForRejectionOrPartialAcceptance() throws Exception {
        for (long accepted : new long[]{0, 3, 7}) {
            var assembler = new SchedulingAssembler();
            outputBuffer(assembler).setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
            assembler.transitions.clear();
            assertEquals(accepted > 0, inject(assembler, storage((key, amount) -> {
                assertTrue(assembler.transitions.isEmpty(), "no scheduling inside target callback");
                return accepted;
            })));
            assertEquals(accepted == 7 ? List.of("sleepDevice") : List.of(), assembler.transitions);
            assertEquals(accepted < 7, field("awake").getBoolean(assembler));
        }
    }

    @Test
    void snapshotBeforeTargetCallRecordsHeldPhaseWithoutAuthorizingReplay() throws Exception {
        var assembler = new PreTransferSnapshotAssembler();
        outputBuffer(assembler).setStack(0, new GenericStack(AEItemKey.of(Items.CARROT), 7));
        assembler.capture = true;
        assertTrue(inject(assembler, storage((key, amount) -> {
            assertEquals("held", assembler.snapshot.getCompoundTag("outputTransfer").getString("state"));
            var inside = new NBTTagCompound();
            assembler.saveAdditional(inside);
            assertEquals("unconfirmed", inside.getCompoundTag("outputTransfer").getString("state"));
            return amount;
        })));
        var restored = new ActiveAssembler();
        restored.loadTag(assembler.snapshot);
        assertTrue(outputBuffer(restored).isEmpty());
        assertEquals(assembler.snapshot.getTag("outputTransfer"), field("outputTransfer").get(restored));
        assertTrue(restored.isBusy());
        assertFalse(inject(restored, storage((key, amount) -> {
            throw new AssertionError("persisted held snapshot is not a durable target receipt");
        })));
    }

    @Test
    void savedCustodyBeyond1024EntriesIsNotTruncated() throws Exception {
        var data = new NBTTagCompound();
        var list = new NBTTagList();
        for (int i = 0; i < 1025; i++) {
            list.appendTag(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.APPLE), i + 1)));
        }
        data.setTag("cachedInputs", list);
        data.setTag("cachedOutputs", list.copy());
        data.setInteger("pendingCrafts", 1);
        data.setTag("mainOutput", GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.APPLE), 1)));
        data.setTag("currentPattern", new ItemStack(Items.PAPER).writeToNBT(new NBTTagCompound()));
        var assembler = new TileMolecularAssembler();
        assembler.loadTag(data);
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(list, saved.getTag("cachedInputs"));
        assertEquals(list, saved.getTag("cachedOutputs"));
        assertEquals(1025, ((List<?>) field("cachedInputs").get(assembler)).size());
    }

    @Test
    void invalidCustodyRetainsWholeBatchAndDropsRecoveryData() throws Exception {
        for (boolean unknown : new boolean[]{false, true}) {
            var data = new NBTTagCompound();
            var inputs = new NBTTagList();
            var invalid = GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.APPLE), 7));
            if (unknown) invalid.setString("id", "ae2_audit:unregistered_resource");
            else invalid.setLong(GenericStack.AMOUNT_FIELD, -7);
            inputs.appendTag(invalid);
            data.setTag("cachedInputs", inputs);
            data.setTag("cachedOutputs", GenericStack.writeList(List.of(
                new GenericStack(AEItemKey.of(Items.CARROT), 1))));
            data.setInteger("pendingCrafts", 1);
            data.setDouble("progress", 63);
            var assembler = new ActiveAssembler();
            assembler.loadTag(data);
            assertTrue(assembler.isBusy());
            assertFalse(complete(assembler));
            var saved = new NBTTagCompound();
            assembler.saveAdditional(saved);
            for (var key : data.getKeySet()) assertEquals(data.getTag(key), saved.getTag(key), key);
            var drops = new ArrayList<ItemStack>();
            assembler.addAdditionalDrops(drops);
            assertEquals(1, drops.size());
            assertEquals(AEItems.MISSING_CONTENT.item(), drops.getFirst().getItem());
            var recovery = drops.getFirst().getTagCompound().getCompoundTag("assembler_pending_craft");
            for (var key : data.getKeySet()) assertEquals(data.getTag(key), recovery.getTag(key), key);
        }
    }

    @Test
    void wrongListTypeSurvivesSaveWithoutBecomingEmptyCustody() throws Exception {
        var data = new NBTTagCompound();
        data.setString("cachedInputs", "invalid-but-preserved");
        data.setInteger("pendingCrafts", 1);
        var assembler = new TileMolecularAssembler();
        assembler.loadTag(data);
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        assertEquals(data.getTag("cachedInputs"), saved.getTag("cachedInputs"));
        assertTrue(assembler.isBusy());
    }

    @Test
    void invalidMetadataRetainsOriginalBatchInsteadOfPublishingDefaults() throws Exception {
        List<Consumer<NBTTagCompound>> corruptions = List.of(
            tag -> tag.setString("pendingCrafts", "one"),
            tag -> tag.setInteger("pendingCrafts", -1),
            tag -> tag.setInteger("pendingCrafts", 0),
            tag -> tag.removeTag("pendingCrafts"),
            tag -> tag.setString("progress", "sixty-three"),
            tag -> tag.setDouble("progress", Double.NaN),
            tag -> tag.setDouble("progress", Double.POSITIVE_INFINITY),
            tag -> tag.setDouble("progress", -1),
            tag -> tag.setString("mainOutput", "invalid"),
            tag -> tag.removeTag("mainOutput"),
            tag -> tag.getCompoundTag("mainOutput").setLong(GenericStack.AMOUNT_FIELD, 0),
            tag -> tag.getCompoundTag("mainOutput").setString("id", "ae2_audit:unknown"),
            tag -> tag.setString("currentPattern", "invalid"),
            tag -> tag.removeTag("currentPattern"),
            tag -> tag.getCompoundTag("currentPattern").setString("id", "ae2_audit:unknown"),
            tag -> tag.removeTag("cachedOutputs"));
        for (int index = 0; index < corruptions.size(); index++) {
            var data = validPendingData();
            corruptions.get(index).accept(data);
            var assembler = new ActiveAssembler();
            assembler.loadTag(data);
            assertTrue(assembler.isBusy(), "case " + index);
            assertFalse(complete(assembler), "case " + index);
            assertTrue(((List<?>) field("cachedInputs").get(assembler)).isEmpty());
            var saved = new NBTTagCompound();
            assembler.saveAdditional(saved);
            for (var key : new String[]{"cachedInputs", "cachedOutputs", "pendingCrafts", "progress",
                "currentPattern", "mainOutput"}) {
                var expectedField = new NBTTagCompound();
                var actualField = new NBTTagCompound();
                if (data.hasKey(key)) expectedField.setTag(key, data.getTag(key));
                if (saved.hasKey(key)) actualField.setTag(key, saved.getTag(key));
                Assertions.assertArrayEquals(nbtBytes(expectedField), nbtBytes(actualField),
                    "case " + index + ": " + key);
            }
            var drops = new ArrayList<ItemStack>();
            assembler.addAdditionalDrops(drops);
            assertEquals(1, drops.size());
            assertEquals(AEItems.MISSING_CONTENT.item(), drops.getFirst().getItem());
            Assertions.assertArrayEquals(nbtBytes(data), nbtBytes(
                drops.getFirst().getTagCompound().getCompoundTag("assembler_pending_craft")));
        }
    }

    @Test
    void validIdleAndZeroInputCraftDoNotBecomeUnresolved() throws Exception {
        var idle = new ActiveAssembler();
        var idleData = new NBTTagCompound();
        idle.saveAdditional(idleData);
        idle.loadTag(idleData);
        assertNull(field("unresolvedCraft").get(idle));
        assertFalse(idle.isBusy());

        var pending = validPendingData();
        pending.removeTag("cachedInputs");
        // Progress may overshoot the completion threshold; do not invent an upper cap.
        pending.setDouble("progress", 125);
        var assembler = new ActiveAssembler();
        assembler.loadTag(pending);
        assertNull(field("unresolvedCraft").get(assembler));
        assertEquals(1, field("pendingCrafts").getInt(assembler));
        assertEquals(125, field("progress").getDouble(assembler));
        assertEquals(1, ((List<?>) field("cachedOutputs").get(assembler)).size());
        var saved = new NBTTagCompound();
        assembler.saveAdditional(saved);
        for (var key : pending.getKeySet()) assertEquals(pending.getTag(key), saved.getTag(key), key);
    }

    private interface PatternDecode {
        IPatternDetails decode(ItemStack stack, World world) throws Exception;
    }

    private interface CheckedAction {
        void run() throws Exception;
    }

    private interface Insertion {
        long insert(AEKey key, long amount) throws Exception;
    }

    private static class ActiveAssembler extends TileMolecularAssembler {
        @Override
        protected IManagedGridNode createMainNode() {
            return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
                new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == IManagedGridNode.class) return proxy;
                    if (method.getName().equals("isActive")) return true;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        }
    }

    private static final class PreTransferSnapshotAssembler extends ActiveAssembler {
        private boolean capture;
        private NBTTagCompound snapshot;

        @Override
        public void saveChanges() {
            if (capture && snapshot == null) {
                snapshot = new NBTTagCompound();
                saveAdditional(snapshot);
            }
        }
    }

    private static final class SchedulingAssembler extends TileMolecularAssembler {
        private final List<String> transitions = new ArrayList<>();

        @Override
        protected IManagedGridNode createMainNode() {
            return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
                new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == IManagedGridNode.class) return proxy;
                    if (method.getName().equals("isActive")) return true;
                    if (method.getName().equals("ifPresent")) {
                        var tickType = IGrid.class.getMethod("getTickManager").getReturnType();
                        var ticks = Proxy.newProxyInstance(tickType.getClassLoader(), new Class<?>[]{tickType},
                            (tickProxy, tickMethod, tickArgs) -> {
                                transitions.add(tickMethod.getName());
                                return null;
                            });
                        var grid = Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                            new Class<?>[]{IGrid.class}, (gridProxy, gridMethod, gridArgs) ->
                                gridMethod.getName().equals("getTickManager") ? ticks : null);
                        ((BiConsumer<Object, Object>) args[0]).accept(grid, null);
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        }
    }

    private static final class FailingWakeAssembler extends TileMolecularAssembler {
        private int saveCalls;

        @Override
        public void saveChanges() {
            saveCalls++;
        }

        @Override
        protected IManagedGridNode createMainNode() {
            return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
                new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == IManagedGridNode.class) return proxy;
                    if (method.getName().equals("isActive")) return true;
                    if (method.getName().equals("ifPresent")) throw new IllegalStateException("wake fixture");
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        }
    }

    private static final class FailingSaveAssembler extends ActiveAssembler {
        private boolean fail;

        @Override
        public void saveChanges() {
            if (fail) throw new IllegalStateException("postcommit save fixture");
        }
    }

    private static final class SlotCountAssembler extends ActiveAssembler {
        private int slots = 9;
        private int saves;

        @Override
        public int getActivePatternSlots() {
            return slots;
        }

        @Override
        public void saveChanges() {
            saves++;
        }
    }

    private static final class CountingSaveAssembler extends ActiveAssembler {
        private int saves;

        @Override
        public void saveChanges() {
            saves++;
        }
    }

    private static final class SavingAssembler extends TileMolecularAssembler {
        private boolean capture;
        private NBTTagCompound snapshot;

        @Override
        public void saveChanges() {
            if (capture) {
                snapshot = new NBTTagCompound();
                saveAdditional(snapshot);
            }
        }
    }
}
