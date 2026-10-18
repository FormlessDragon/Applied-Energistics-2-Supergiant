package ae2.api.features;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.AEConfig;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.worldgen.meteorite.MeteoritesWorldData;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveRecoveryTest {
    private static Object previousSide;
    @TempDir
    Path folder;

    @BeforeAll
    static void setup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        previousSide = field.get(FMLCommonHandler.instance());
        if (previousSide == null) field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
    }

    @AfterAll
    static void cleanup() throws Exception {
        var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), previousSide);
    }

    private static NBTTagCompound meteorites(byte[] bytes) {
        var completed = new NBTTagCompound();
        completed.setLong("meteorite", 123);
        completed.setByteArray("chunks", bytes);
        var list = new NBTTagList();
        list.appendTag(completed);
        var root = new NBTTagCompound();
        root.setTag("completed", list);
        return root;
    }

    private static NBTTagCompound mapping(int[] ids, UUID... profiles) {
        int[] parts = new int[profiles.length * 4];
        for (int i = 0; i < profiles.length; i++) {
            long high = profiles[i].getMostSignificantBits(), low = profiles[i].getLeastSignificantBits();
            parts[i * 4] = (int) (high >> 32);
            parts[i * 4 + 1] = (int) high;
            parts[i * 4 + 2] = (int) (low >> 32);
            parts[i * 4 + 3] = (int) low;
        }
        var tag = new NBTTagCompound();
        tag.setIntArray("playerIds", ids);
        tag.setIntArray("profileIds", parts);
        return tag;
    }

    @Test
    void completeResourceListsRoundTripIncludingNullAndUnknownKeys() {
        var random = new Random(190439L);
        for (int count : new int[]{0, 1024, 1025, 2049}) {
            var inventory = new ListCraftingInventory(ignored -> {
            });
            for (int i = 0; i < count; i++) {
                var stack = new ItemStack(Items.APPLE);
                var tag = new NBTTagCompound();
                tag.setInteger("variant", i);
                stack.setTagCompound(tag);
                inventory.list.add(AEItemKey.of(stack), random.nextLong(1, 100000));
            }
            var encoded = inventory.writeToNBT();
            var restored = new ListCraftingInventory(ignored -> {
            });
            restored.readFromNBT(encoded);
            assertEquals(count, restored.list.size());
            for (var entry : inventory.list) assertEquals(entry.getLongValue(), restored.list.get(entry.getKey()));
        }
        var unknown = new NBTTagCompound();
        unknown.setString(AEKey.TYPE_FIELD, "missing:resource");
        unknown.setLong("#", 71);
        unknown.setString("opaque", "preserve this payload");
        var encoded = new NBTTagList();
        encoded.appendTag(new NBTTagCompound());
        encoded.appendTag(unknown);
        var decoded = GenericStack.readList(encoded);
        assertNull(decoded.get(0));
        assertEquals(encoded, GenericStack.writeList(decoded));
        assertThrows(UnsupportedOperationException.class, () -> decoded.clear());
    }

    @Test
    void malformedResourceListCannotClearTheExistingInventoryOrNotifyAPartialReplacement() {
        var notifications = new int[1];
        var inventory = new ListCraftingInventory(_ -> notifications[0]++);
        var key = AEItemKey.of(Items.CARROT);
        inventory.insert(key, 7, Actionable.MODULATE);
        var malformed = new NBTTagList();
        malformed.appendTag(new NBTTagString("not a resource compound"));
        assertThrows(IllegalArgumentException.class, () -> inventory.readFromNBT(malformed));
        assertEquals(7, inventory.list.get(key));
        assertEquals(1, notifications[0]);
        var saved = inventory.writeToNBT();
        inventory.readFromNBT(saved);
        assertEquals(7, inventory.list.get(key));
        assertEquals(2, notifications[0]);
        inventory.readFromNBT(null);
        assertTrue(inventory.list.isEmpty(), "null retains the existing empty-input contract");
    }

    @Test
    void shortCompletionBitmapsRoundTripAndCorruptionCannotPublishOrSave() {
        for (int bit : new int[]{-1, 0, 1, 40, 79, 80, 81, 87}) {
            var bits = new BitSet();
            if (bit >= 0) bits.set(bit);
            var source = meteorites(bits.toByteArray());
            var data = new MeteoritesWorldData();
            data.readFromNBT(source);
            var saved = data.writeToNBT(new NBTTagCompound());
            var completed = saved.getTagList("completed", 10);
            if (bit >= 0 && bit <= 80) {
                assertEquals(1, completed.tagCount());
                assertEquals(bits, BitSet.valueOf(completed.getCompoundTagAt(0).getByteArray("chunks")));
            } else assertEquals(0, completed.tagCount());
            var again = new MeteoritesWorldData();
            again.readFromNBT(saved);
            assertEquals(saved, again.writeToNBT(new NBTTagCompound()));
        }
        var data = new MeteoritesWorldData();
        data.markGenerated(4, 5);
        assertThrows(IllegalArgumentException.class, () -> data.readFromNBT(meteorites(new byte[12])));
        assertThrows(IllegalStateException.class, () -> data.markGenerated(6, 7));
        assertThrows(IllegalStateException.class, () -> data.writeToNBT(new NBTTagCompound()));
        var wrongType = new NBTTagCompound();
        wrongType.setString("completed", "corrupted");
        assertThrows(IllegalArgumentException.class, () -> data.readFromNBT(wrongType));
        data.readFromNBT(meteorites(new byte[]{1}));
        assertFalse(data.hasGenerated(4, 5));
        assertTrue(data.markGenerated(6, 7));
    }

    @Test
    void mappingValidatesBothDirectionsAndNeverWrapsOrMutatesUnbound() throws Exception {
        UUID a = new UUID(1, 2), b = new UUID(3, 4);
        var valid = mapping(new int[]{5, 9}, a, b);
        var registry = new PlayerRegistryInternal();
        registry.readFromNBT(valid);
        assertEquals(a, registry.getProfileId(5));
        assertEquals(9, registry.getPlayerId(b));
        assertThrows(NullPointerException.class, () -> registry.getPlayerId(new UUID(5, 6)));
        assertEquals(valid, registry.writeToNBT(new NBTTagCompound()));
        var field = PlayerRegistryInternal.class.getDeclaredField("mapping");
        field.setAccessible(true);
        for (var bad : List.of(mapping(new int[]{5, 9}, a, a), mapping(new int[]{5, 5}, a, b),
            mapping(new int[]{-1}, a), mapping(new int[]{1}, a, b), new NBTTagCompound())) {
            registry.readFromNBT(valid);
            assertThrows(IllegalStateException.class, () -> registry.readFromNBT(bad));
            assertEquals(Map.of(a, 5, b, 9), field.get(registry), "failed parse must retain the previous table");
            assertThrows(IllegalStateException.class, () -> registry.getProfileId(5));
            assertThrows(IllegalStateException.class, () -> registry.getPlayerId(b));
            assertThrows(IllegalStateException.class, () -> registry.writeToNBT(new NBTTagCompound()));
        }
        registry.readFromNBT(mapping(new int[]{Integer.MAX_VALUE}, a));
        assertEquals(Integer.MAX_VALUE, registry.getPlayerId(a));
        var error = assertThrows(IllegalStateException.class, () -> registry.getPlayerId(b));
        assertTrue(error.getMessage().contains("exhausted"));
        var reopened = new PlayerRegistryInternal();
        reopened.readFromNBT(registry.writeToNBT(new NBTTagCompound()));
        assertEquals(a, reopened.getProfileId(Integer.MAX_VALUE));
    }

    @Test
    void nativeStorageCatchCannotTurnFailedMappingIntoWritablePartialData() throws Exception {
        var file = folder.resolve("players.dat");
        var root = new NBTTagCompound();
        root.setTag("data", mapping(new int[]{5, 5}, new UUID(1, 2), new UUID(3, 4)));
        try (var output = Files.newOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, output);
        }
        var bytes = Files.readAllBytes(file);
        var handler = (ISaveHandler) Proxy.newProxyInstance(ISaveHandler.class.getClassLoader(),
            new Class<?>[]{ISaveHandler.class}, (p, m, a) -> m.getName().equals("getMapFileFromName")
                ? ("players".equals(a[0]) ? file.toFile() : null) : null);
        var storage = new MapStorage(handler);
        var data = (PlayerRegistryInternal) storage.getOrLoadData(PlayerRegistryInternal.class, "players");
        assertNotNull(data);
        assertSame(data, storage.getOrLoadData(PlayerRegistryInternal.class, "players"));
        assertThrows(IllegalStateException.class, () -> data.getProfileId(5));
        data.markDirty();
        assertThrows(IllegalStateException.class, () -> data.save(file.toFile()));
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }
}
