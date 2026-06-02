package ae2.tile.crafting.requester;

import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RequestList {

    private final List<Request> requests;

    public RequestList(@Nullable RequestHost host, int size) {
        this.requests = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.requests.add(new Request(host, i));
        }
    }

    public int size() {
        return this.requests.size();
    }

    public Request get(int index) {
        return this.requests.get(index);
    }

    public NBTTagCompound writeToNBT() {
        var tag = new NBTTagCompound();
        for (int i = 0; i < size(); i++) {
            tag.setTag(Integer.toString(i), get(i).writeToNBT());
        }
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        for (int i = 0; i < size(); i++) {
            String key = Integer.toString(i);
            if (tag.hasKey(key, 10)) {
                get(i).readFromNBT(tag.getCompoundTag(key));
            }
        }
    }

    public void replaceFromNBT(NBTTagCompound tag) {
        for (int i = 0; i < size(); i++) {
            get(i).reset();
            String key = Integer.toString(i);
            if (tag.hasKey(key, 10)) {
                get(i).readFromNBT(tag.getCompoundTag(key));
            }
        }
    }
}
