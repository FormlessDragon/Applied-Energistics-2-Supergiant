package appeng.util.ctl;

import appeng.api.stacks.GenericStack;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.PacketBuffer;

import java.util.List;
import java.util.Map;

public class AEItemStackSet {

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

    protected void addInternal(GenericStack stack) {
        Entry entry = new Entry(stack, entryList.size());
        entryList.add(entry);
        entries.put(entry, entry);
    }

    public GenericStack get(int id) {
        return new GenericStack(entryList.get(id).stack().what(), entryList.get(id).stack().amount());
    }

    public void writeToBuffer(final ByteBuf buf) {
        buf.writeInt(entryList.size());
        for (Entry entry : entryList) {
            try {
                GenericStack.writeBuffer(entry.stack, new PacketBuffer(buf));
            } catch (Throwable ignored) {
            }
        }
    }

    public void fromBuffer(final ByteBuf buf) {
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            addInternal(GenericStack.readBuffer(new PacketBuffer(buf)));
        }
    }

    private record Entry(GenericStack stack, int id) {

        @Override
        public int hashCode() {
            return stack.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof Entry entry)) {
                return false;
            }
            return stack.equals(entry.stack);
        }

    }

}