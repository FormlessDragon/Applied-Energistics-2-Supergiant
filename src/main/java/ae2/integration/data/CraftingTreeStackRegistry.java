package ae2.integration.data;

import ae2.api.stacks.GenericStack;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.PacketBuffer;

import java.util.List;
import java.util.Map;

public class CraftingTreeStackRegistry {
    private final Map<Entry, Entry> entries = new Object2ObjectOpenHashMap<>();
    private final List<Entry> entryList = new ObjectArrayList<>();

    public int add(GenericStack stack) {
        GenericStack keyStack = new GenericStack(stack.what(), 1);
        Entry entry = entries.get(new Entry(keyStack, -1));
        if (entry == null) {
            entry = new Entry(keyStack, entryList.size());
            entries.put(entry, entry);
            entryList.add(entry);
        }
        return entry.id();
    }

    public GenericStack get(int id) {
        Entry entry = entryList.get(id);
        return new GenericStack(entry.stack().what(), entry.stack().amount());
    }

    public void write(ByteBuf buf) {
        buf.writeInt(entryList.size());
        for (Entry entry : entryList) {
            GenericStack.writeBuffer(entry.stack, new PacketBuffer(buf));
        }
    }

    public void read(ByteBuf buf) {
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            addInternal(GenericStack.readBuffer(new PacketBuffer(buf)));
        }
    }

    private void addInternal(GenericStack stack) {
        Entry entry = new Entry(stack, entryList.size());
        entryList.add(entry);
        entries.put(entry, entry);
    }

    private record Entry(GenericStack stack, int id) {
        @Override
        public int hashCode() {
            return stack.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Entry entry && stack.equals(entry.stack);
        }
    }
}
