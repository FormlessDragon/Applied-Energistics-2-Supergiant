package ae2.api.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlowSearchDTO {

    public DimensionalBlockPos location;
    public long in;
    public long out;

    public FlowSearchDTO(DimensionalBlockPos location, long in, long out) {
        this.location = location;
        this.in = in;
        this.out = out;
    }

    public FlowSearchDTO(final NBTTagCompound data) {
        this.readFromNBT(data);
    }

    public long netTotal() {
        return in - out;
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setInteger("dim", location.getLevel().provider.getDimension());
        data.setInteger("x", location.getPos().getX());
        data.setInteger("y", location.getPos().getY());
        data.setInteger("z", location.getPos().getZ());
        data.setLong("in", in);
        data.setLong("out", out);
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

    public static List<FlowSearchDTO> readAsListFromNBT(final NBTTagCompound tag) {
        List<FlowSearchDTO> list = new ArrayList<>();
        int i = 0;
        while(tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(new FlowSearchDTO(data));
            i++;
        }
        return list;
    }

    private void readFromNBT(final NBTTagCompound data) {
        int dim = data.getInteger("dim");
        int x = data.getInteger("x");
        int y = data.getInteger("y");
        int z = data.getInteger("z");
        this.location = new DimensionalBlockPos(DimensionManager.getWorld(dim), x, y, z);
        this.in = data.getLong("in");
        this.out = data.getLong("out");
    }

    public Map<String, Object> toJSON() {
        final DimensionalBlockPos location = this.location;
        return Map.of(
            "dim", location.getLevel().provider.getDimension(),
            "x", location.getPos().getX(),
            "y", location.getPos().getY(),
            "z", location.getPos().getZ(),
            "in", this.in,
            "out", this.out);
    }
}
