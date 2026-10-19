package ae2.api.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlowSearchDTO {

    @Nullable
    public DimensionalBlockPos location;
    public int dimension;
    public BlockPos position;
    public long in;
    public long out;

    public FlowSearchDTO(DimensionalBlockPos location, long in, long out) {
        this.location = location;
        this.dimension = location.getLevel().provider.getDimension();
        this.position = location.getPos();
        this.in = in;
        this.out = out;
    }

    public FlowSearchDTO(final NBTTagCompound data) {
        this(data, null);
    }

    public FlowSearchDTO(final NBTTagCompound data, @Nullable World fallbackWorld) {
        this.readFromNBT(data, fallbackWorld);
    }

    public long netTotal() {
        return in - out;
    }

    public static List<FlowSearchDTO> readAsListFromNBT(final NBTTagCompound tag) {
        return readAsListFromNBT(tag, null);
    }

    public static void writeListToNBT(final NBTTagCompound tag, List<FlowSearchDTO> list) {
        int i = 0;
        for(FlowSearchDTO d : list) {
            NBTTagCompound data = new NBTTagCompound();
            d.writeToNBT(data);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static List<FlowSearchDTO> readAsListFromNBT(final NBTTagCompound tag, @Nullable World fallbackWorld) {
        List<FlowSearchDTO> list = new ArrayList<>();
        int i = 0;
        while(tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(new FlowSearchDTO(data, fallbackWorld));
            i++;
        }
        return list;
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setInteger("dim", this.dimension);
        data.setInteger("x", this.position.getX());
        data.setInteger("y", this.position.getY());
        data.setInteger("z", this.position.getZ());
        data.setLong("in", in);
        data.setLong("out", out);
    }

    private void readFromNBT(final NBTTagCompound data, @Nullable World fallbackWorld) {
        this.dimension = data.getInteger("dim");
        int x = data.getInteger("x");
        int y = data.getInteger("y");
        int z = data.getInteger("z");
        this.position = new BlockPos(x, y, z);
        World level = fallbackWorld != null && fallbackWorld.provider.getDimension() == this.dimension
            ? fallbackWorld : DimensionManager.getWorld(this.dimension);
        this.location = level == null ? null : new DimensionalBlockPos(level, this.position);
        this.in = data.getLong("in");
        this.out = data.getLong("out");
    }

    public Map<String, Object> toJSON() {
        return Map.of(
            "dim", this.dimension,
            "x", this.position.getX(),
            "y", this.position.getY(),
            "z", this.position.getZ(),
            "in", this.in,
            "out", this.out);
    }
}
