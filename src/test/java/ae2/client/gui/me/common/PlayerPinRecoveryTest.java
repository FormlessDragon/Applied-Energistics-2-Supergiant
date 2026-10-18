package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.core.AEConfig;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPinRecoveryTest {
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

    private static NBTTagCompound root() {
        var root = new NBTTagCompound();
        root.setInteger("version", 2);
        root.setTag("playerPinSlots", new NBTTagList());
        return root;
    }

    private static NBTTagCompound slot(int index, String kind, int variant) {
        var stack = new ItemStack(Items.APPLE);
        var tag = new NBTTagCompound();
        tag.setInteger("variant", variant);
        stack.setTagCompound(tag);
        var key = stack.writeToNBT(new NBTTagCompound());
        // Use the actual registered ID, independent of its spelling in this build.
        key.setString(AEKey.TYPE_FIELD, AEKeyType.items().getId().toString());
        var slot = new NBTTagCompound();
        slot.setInteger("slotIndex", index);
        slot.setString("kind", kind);
        slot.setTag("key", key);
        return slot;
    }

    @Test
    void partialRecoveryKeepsFirstValidRecordsButCannotOverwriteSource() throws Exception {
        var file = folder.resolve("pins.dat").toFile();
        var root = root();
        var list = root.getTagList("playerPinSlots", 10);
        list.appendTag(slot(0, "AUTO", 0));
        list.appendTag(slot(1, "UNKNOWN_KIND", 1));
        list.appendTag(slot(0, "AUTO", 2));
        list.appendTag(slot(2, "AUTO", 3));
        list.appendTag(new NBTTagCompound());
        CompressedStreamTools.safeWrite(root, file);
        var original = Files.readAllBytes(file.toPath());
        var data = PlayerPinStorage.load(file);
        assertFalse(data.writable());
        assertEquals(List.of(0, 2), data.slots().stream().map(PlayerPinStorage.PinSlot::slotIndex).toList());
        assertEquals(3, data.diagnostics().size());
        PlayerPinStorage.save(file, data.rows(), data.slots());
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));

        root.setInteger("version", 99);
        root.setString("futurePayload", "must survive");
        CompressedStreamTools.safeWrite(root, file);
        original = Files.readAllBytes(file.toPath());
        assertFalse(PlayerPinStorage.load(file).writable());
        PlayerPinStorage.save(file, 0, List.of());
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));

        assertFalse(PlayerPinStorage.loadFromTag(new NBTTagCompound()).writable());
        var malformed = root();
        malformed.setString("playerPinRows", "bad");
        assertFalse(PlayerPinStorage.loadFromTag(malformed).writable());
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        assertFalse(PlayerPinStorage.load(file).writable());
        PlayerPinStorage.save(file, 0, List.of());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
    }

    @Test
    void fullCurrentFileRoundTripPreservesMissingContentAndBindsWriteTarget() throws Exception {
        var file = folder.resolve("pins.dat").toFile();
        var root = root();
        var list = root.getTagList("playerPinSlots", 10);
        for (int i = 0; i < 71; i++) list.appendTag(slot(i, "AUTO", i));
        var unknown = slot(71, "AUTO", 71);
        var key = unknown.getCompoundTag("key");
        key.setString(AEKey.TYPE_FIELD, "missing:resource");
        key.setString("opaque", "missing addon payload");
        list.appendTag(unknown);
        CompressedStreamTools.safeWrite(root, file);
        var data = PlayerPinStorage.load(file);
        assertTrue(data.writable());
        assertEquals(72, data.slots().size());
        var other = folder.resolve("other-server.dat").toFile();
        PlayerPinStorage.save(other, data.rows(), data.slots());
        assertFalse(other.exists(), "a load cannot authorize a different file");
        PlayerPinStorage.save(file, data.rows(), data.slots());
        var saved = CompressedStreamTools.read(file);
        assertEquals(unknown, saved.getTagList("playerPinSlots", 10).getCompoundTagAt(71));
        assertEquals(data.slots(), PlayerPinStorage.load(file).slots());
        assertTrue(PlayerPinStorage.load(other).writable());
        PlayerPinStorage.save(other, 0, List.of());
        assertTrue(other.isFile());
        assertTrue(PlayerPinStorage.load(other).slots().isEmpty());
    }
}
