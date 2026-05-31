package ae2.core.gui.locator;

import ae2.integration.modules.baubles.BaublesIntegration;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BaublesItemLocator(int baubleSlot, @Nullable RayTraceResult hitResult) implements ItemGuiHostLocator {
    public static BaublesItemLocator readFromPacket(PacketBuffer buf) {
        return new BaublesItemLocator(buf.readInt(), readHitResult(buf));
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

    @Nullable
    private static RayTraceResult readHitResult(PacketBuffer buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        var blockPos = buf.readBlockPos();
        var side = net.minecraft.util.EnumFacing.VALUES[buf.readByte()];
        var hitVec = new net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new RayTraceResult(hitVec, side, blockPos);
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
}
