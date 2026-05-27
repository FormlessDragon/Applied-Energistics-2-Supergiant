package appeng.core.gui.locator;

import appeng.parts.AEBasePart;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class GuiHostLocators {
    private static final Map<String, Registration<?>> REGISTRY = new Object2ObjectOpenHashMap<>();

    static {
        register(TileLocator.class, TileLocator::writeToPacket, TileLocator::readFromPacket);
        register(PartLocator.class, PartLocator::writeToPacket, PartLocator::readFromPacket);
        register(InventoryItemLocator.class, InventoryItemLocator::writeToPacket, InventoryItemLocator::readFromPacket);
        register(BaublesItemLocator.class, BaublesItemLocator::writeToPacket, BaublesItemLocator::readFromPacket);
    }

    private GuiHostLocators() {
    }

    public static synchronized <T extends GuiHostLocator> void register(Class<T> locatorClass,
                                                                        BiConsumer<T, PacketBuffer> packetWriter,
                                                                        Function<PacketBuffer, T> packetReader) {
        String classKey = locatorClass.getName();
        if (REGISTRY.containsKey(classKey)) {
            throw new IllegalStateException("GuiHostLocator type " + classKey + " is already registered.");
        }
        REGISTRY.put(classKey, new Registration<>(packetWriter, packetReader));
    }

    private static synchronized Registration<?> getRegistration(String classKey) {
        var registration = REGISTRY.get(classKey);
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered container locator class: " + classKey);
        }
        return registration;
    }

    @SuppressWarnings("unchecked")
    public static <T extends GuiHostLocator> void writeToPacket(PacketBuffer buf, T locator) {
        var classKey = locator.getClass().getName();
        var registration = (Registration<T>) getRegistration(classKey);
        buf.writeString(classKey);
        registration.writeToPacket.accept(locator, buf);
    }

    public static GuiHostLocator readFromPacket(PacketBuffer buf) {
        var classKey = buf.readString(32767);
        var registration = getRegistration(classKey);
        return registration.readFromPacket.apply(buf);
    }

    public static GuiHostLocator forTile(net.minecraft.tileentity.TileEntity tileEntity) {
        if (tileEntity.getWorld() == null) {
            throw new IllegalArgumentException("Cannot open a tile entity that is not in a world");
        }
        return new TileLocator(tileEntity.getPos());
    }

    public static GuiHostLocator forPart(AEBasePart part) {
        var pos = part.getHost().getLocation();
        return new PartLocator(pos.getPos(), part.getSide());
    }

    public static ItemGuiHostLocator forStack(ItemStack stack) {
        return new StackItemLocator(stack);
    }

    public static ItemGuiHostLocator forItemUseContext(EntityPlayer player, EnumHand hand, BlockPos pos,
                                                       EnumFacing side, float hitX, float hitY, float hitZ) {
        return new InventoryItemLocator(
            getPlayerInventorySlotFromHand(player, hand),
            createItemUseHitResult(pos, side, hitX, hitY, hitZ));
    }

    public static RayTraceResult createItemUseHitResult(BlockPos pos, EnumFacing side, float hitX, float hitY,
                                                        float hitZ) {
        return new RayTraceResult(new Vec3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ), side, pos);
    }

    public static ItemGuiHostLocator forHand(EntityPlayer player, EnumHand hand) {
        return forInventorySlot(getPlayerInventorySlotFromHand(player, hand));
    }

    public static ItemGuiHostLocator forInventorySlot(int inventorySlot) {
        return new InventoryItemLocator(inventorySlot, null);
    }

    public static ItemGuiHostLocator forInventorySlot(int inventorySlot, RayTraceResult hitResult) {
        return new InventoryItemLocator(inventorySlot, hitResult);
    }

    public static ItemGuiHostLocator forBaubleSlot(int baubleSlot) {
        return new BaublesItemLocator(baubleSlot, null);
    }

    private static int getPlayerInventorySlotFromHand(EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.isEmpty()) {
            throw new IllegalArgumentException("Cannot open an item-inventory with empty hands");
        }

        int invSize = player.inventory.getSizeInventory();
        for (int i = 0; i < invSize; i++) {
            if (player.inventory.getStackInSlot(i) == heldItem) {
                return i;
            }
        }

        throw new IllegalArgumentException("Could not find item held in hand " + hand + " in player inventory");
    }

    private record Registration<T extends GuiHostLocator>(
        BiConsumer<T, PacketBuffer> writeToPacket,
        Function<PacketBuffer, T> readFromPacket) {
    }
}
