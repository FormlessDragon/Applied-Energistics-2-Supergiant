package ae2.requester;

import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RequestManager {

    @Nullable
    private final RequestHost host;
    private final List<Request> requests;

    public RequestManager(@Nullable RequestHost host, int size) {
        this.host = host;
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

    public void markChanged(int index) {
        if (this.host != null) {
            this.host.onRequestChanged(index);
        }
    }

    public void markUpdated(int index) {
        if (this.host != null) {
            this.host.onRequestUpdated(index);
        }
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
}
