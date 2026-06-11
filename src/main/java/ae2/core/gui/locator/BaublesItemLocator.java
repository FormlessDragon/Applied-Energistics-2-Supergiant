package ae2.core.gui.locator;

import ae2.integration.modules.baubles.BaublesIntegration;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BaublesItemLocator(int baubleSlot, @Nullable RayTraceResult hitResult) implements ItemGuiHostLocator {
    private static final int INVALID_BAUBLE_SLOT = Integer.MIN_VALUE;

    public static BaublesItemLocator readFromPacket(PacketBuffer buf) {
        int baubleSlot = buf.readInt();
        DecodedHitResult decodedHitResult = readHitResult(buf);
        return new BaublesItemLocator(decodedHitResult.valid() ? baubleSlot : INVALID_BAUBLE_SLOT,
            decodedHitResult.hitResult());
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
        return BaublesIntegration.getStackInSlot(player, baubleSlot);
    }

    public void writeToPacket(PacketBuffer buf) {
        buf.writeInt(baubleSlot);
        writeHitResult(buf, hitResult);
    }

    @Override
    @NotNull
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("bauble slot ").append(baubleSlot);
        if (hitResult != null && hitResult.getBlockPos() != null) {
            result.append(" used on ").append(hitResult.getBlockPos());
        }
        return result.toString();
    }

    @Override
    @Nullable
    public RayTraceResult hitResult() {
        return hitResult;
    }

    private record DecodedHitResult(@Nullable RayTraceResult hitResult, boolean valid) {
    }
}
