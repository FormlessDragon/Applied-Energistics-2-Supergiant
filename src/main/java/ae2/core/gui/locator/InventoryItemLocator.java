package ae2.core.gui.locator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class InventoryItemLocator implements ItemGuiHostLocator {
    private final int itemIndex;
    @Nullable
    private final RayTraceResult hitResult;
    private final boolean valid;

    public InventoryItemLocator(int itemIndex, @Nullable RayTraceResult hitResult) {
        this(itemIndex, hitResult, true);
    }

    private InventoryItemLocator(int itemIndex, @Nullable RayTraceResult hitResult, boolean valid) {
        this.itemIndex = itemIndex;
        this.hitResult = hitResult;
        this.valid = valid;
    }

    public static InventoryItemLocator readFromPacket(PacketBuffer buf) {
        int itemIndex = buf.readInt();
        DecodedHitResult decodedHitResult = readHitResult(buf);
        return new InventoryItemLocator(itemIndex, decodedHitResult.hitResult(), decodedHitResult.valid());
    }

    private static void writeHitResult(PacketBuffer buf, @Nullable RayTraceResult hitResult) {
        boolean hasBlockHit = hitResult != null && hitResult.typeOfHit == RayTraceResult.Type.BLOCK;
        buf.writeBoolean(hasBlockHit);
        if (!hasBlockHit) {
            return;
        }
        buf.writeBlockPos(hitResult.getBlockPos());
        buf.writeByte(hitResult.sideHit.ordinal());
        buf.writeDouble(hitResult.hitVec.x);
        buf.writeDouble(hitResult.hitVec.y);
        buf.writeDouble(hitResult.hitVec.z);
    }

    private static DecodedHitResult readHitResult(PacketBuffer buf) {
        if (!buf.readBoolean()) {
            return new DecodedHitResult(null, true);
        }
        var blockPos = buf.readBlockPos();
        int sideIndex = buf.readByte();
        var hitVec = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (sideIndex < 0 || sideIndex >= EnumFacing.VALUES.length) {
            return new DecodedHitResult(null, false);
        }
        var side = EnumFacing.VALUES[sideIndex];
        return new DecodedHitResult(new RayTraceResult(hitVec, side, blockPos), true);
    }

    @Override
    public ItemStack locateItem(EntityPlayer player) {
        if (!valid) {
            return ItemStack.EMPTY;
        }
        if (itemIndex < 0 || itemIndex >= player.inventory.getSizeInventory()) {
            return ItemStack.EMPTY;
        }
        return player.inventory.getStackInSlot(itemIndex);
    }

    public void writeToPacket(PacketBuffer buf) {
        buf.writeInt(itemIndex);
        writeHitResult(buf, hitResult);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("slot ").append(itemIndex);
        if (hitResult != null && hitResult.getBlockPos() != null) {
            result.append(" used on ").append(hitResult.getBlockPos());
        }
        return result.toString();
    }

    @Override
    public Integer getPlayerInventorySlot() {
        return itemIndex;
    }

    @Override
    @Nullable
    public RayTraceResult hitResult() {
        return hitResult;
    }

    private record DecodedHitResult(@Nullable RayTraceResult hitResult, boolean valid) {
    }
}

