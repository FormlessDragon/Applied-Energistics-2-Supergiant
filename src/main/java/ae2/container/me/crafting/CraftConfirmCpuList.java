package ae2.container.me.crafting;

import ae2.api.networking.crafting.ICraftingCPU;
import ae2.container.guisync.PacketWritable;
import ae2.text.TextComponents;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public record CraftConfirmCpuList(List<Entry> cpus) implements PacketWritable {
    public static final CraftConfirmCpuList EMPTY = new CraftConfirmCpuList(Collections.emptyList());
    private static final int MAX_CPU_LIST_ENTRIES = 1024;
    private static final int MIN_CPU_LIST_ENTRY_BYTES = 19;

    public CraftConfirmCpuList {
        cpus = List.copyOf(cpus);
    }

    @SuppressWarnings("unused")
    public CraftConfirmCpuList(ByteBuf data) {
        PacketBuffer buffer = new PacketBuffer(data);
        int count = buffer.readInt();
        if (count < 0 || count > MAX_CPU_LIST_ENTRIES
            || count > buffer.readableBytes() / MIN_CPU_LIST_ENTRY_BYTES) {
            throw new IllegalArgumentException("Invalid craft confirm CPU list entry count: " + count);
        }

        ObjectList<Entry> readCpus = new ObjectArrayList<>(count);
        for (int i = 0; i < count; i++) {
            readCpus.add(Entry.readFromPacket(buffer));
        }
        this(List.copyOf(readCpus));
    }

    public static CraftConfirmCpuList fromRecords(List<CraftingCPURecord> records, int selectedCpuSerial,
                                                  Predicate<ICraftingCPU> mergeablePredicate) {
        if (records.isEmpty()) {
            return EMPTY;
        }

        ObjectList<Entry> entries = new ObjectArrayList<>(records.size());
        for (CraftingCPURecord record : records) {
            entries.add(new Entry(
                record.getSerial(),
                record.getSize(),
                record.getProcessors(),
                record.getName(),
                record.getSerial() == selectedCpuSerial,
                mergeablePredicate.test(record.getCpu())));
        }
        return new CraftConfirmCpuList(entries);
    }

    public CraftConfirmCpuList withSelectedCpu(int serial) {
        if (this.cpus.isEmpty()) {
            return this;
        }

        ObjectList<Entry> entries = new ObjectArrayList<>(this.cpus.size());
        boolean changed = false;
        for (Entry entry : this.cpus) {
            boolean selected = entry.serial() == serial;
            if (entry.selected() != selected) {
                changed = true;
                entries.add(new Entry(
                    entry.serial(),
                    entry.storage(),
                    entry.coProcessors(),
                    entry.name(),
                    selected,
                    entry.mergeable()));
            } else {
                entries.add(entry);
            }
        }
        return changed ? new CraftConfirmCpuList(entries) : this;
    }

    @Override
    public void writeToPacket(ByteBuf data) {
        PacketBuffer buffer = new PacketBuffer(data);
        buffer.writeInt(this.cpus.size());
        for (Entry cpu : this.cpus) {
            cpu.writeToPacket(buffer);
        }
    }

    public record Entry(
        int serial,
        long storage,
        int coProcessors,
        @Nullable ITextComponent name,
        boolean selected,
        boolean mergeable) {

        private static Entry readFromPacket(PacketBuffer buffer) {
            return new Entry(
                buffer.readInt(),
                buffer.readLong(),
                buffer.readInt(),
                TextComponents.readFromPacket(buffer),
                buffer.readBoolean(),
                buffer.readBoolean());
        }

        private void writeToPacket(PacketBuffer buffer) {
            buffer.writeInt(this.serial);
            buffer.writeLong(this.storage);
            buffer.writeInt(this.coProcessors);
            TextComponents.writeToPacket(buffer, this.name);
            buffer.writeBoolean(this.selected);
            buffer.writeBoolean(this.mergeable);
        }
    }
}
