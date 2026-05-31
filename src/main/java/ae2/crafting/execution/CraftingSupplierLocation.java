package ae2.crafting.execution;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

public record CraftingSupplierLocation(int dimensionId, int x, int y, int z) {
    public static CraftingSupplierLocation read(PacketBuffer data) {
        return new CraftingSupplierLocation(data.readInt(), data.readInt(), data.readInt(), data.readInt());
    }

    public void write(PacketBuffer data) {
        data.writeInt(this.dimensionId);
        data.writeInt(this.x);
        data.writeInt(this.y);
        data.writeInt(this.z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}
