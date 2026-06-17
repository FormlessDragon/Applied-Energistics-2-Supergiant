package ae2.items.tools.advancedmemorycard;

import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record AdvancedMemoryCardP2PSnapshot(List<AdvancedMemoryCardP2PEntry> entries, int initialFocusEntryId) {
    public static final int MAX_ENTRIES = 4096;

    private static final Comparator<AdvancedMemoryCardP2PEntry> ENTRY_COMPARATOR =
        Comparator.comparing(AdvancedMemoryCardP2PEntry::input)
                  .reversed()
                  .thenComparingInt(entry -> Short.toUnsignedInt(entry.frequency()))
                  .thenComparing(AdvancedMemoryCardP2PEntry::displayNameKey)
                  .thenComparingInt(AdvancedMemoryCardP2PEntry::entryId);

    public AdvancedMemoryCardP2PSnapshot(List<AdvancedMemoryCardP2PEntry> entries) {
        this(entries, -1);
    }

    public AdvancedMemoryCardP2PSnapshot {
        entries = copyLimited(entries);
        initialFocusEntryId = normalizeFocus(entries, initialFocusEntryId);
    }

    public static AdvancedMemoryCardP2PSnapshot of(List<AdvancedMemoryCardP2PEntry> entries) {
        return of(entries, -1);
    }

    public static AdvancedMemoryCardP2PSnapshot of(List<AdvancedMemoryCardP2PEntry> entries, int initialFocusEntryId) {
        List<AdvancedMemoryCardP2PEntry> sortedEntries = entries.stream()
                                                                .sorted(ENTRY_COMPARATOR)
                                                                .limit(MAX_ENTRIES)
                                                                .toList();
        return new AdvancedMemoryCardP2PSnapshot(sortedEntries, normalizeFocus(sortedEntries, initialFocusEntryId));
    }

    public static AdvancedMemoryCardP2PSnapshot read(PacketBuffer data) {
        int initialFocusEntryId = data.readVarInt();
        int count = data.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid advanced memory card P2P entry count: " + count);
        }

        var entries = new ArrayList<AdvancedMemoryCardP2PEntry>(count);
        for (int i = 0; i < count; i++) {
            entries.add(AdvancedMemoryCardP2PEntry.read(data));
        }
        return new AdvancedMemoryCardP2PSnapshot(entries, initialFocusEntryId);
    }

    private static List<AdvancedMemoryCardP2PEntry> copyLimited(List<AdvancedMemoryCardP2PEntry> entries) {
        if (entries.size() <= MAX_ENTRIES) {
            return List.copyOf(entries);
        }
        return List.copyOf(entries.subList(0, MAX_ENTRIES));
    }

    private static int normalizeFocus(List<AdvancedMemoryCardP2PEntry> entries, int initialFocusEntryId) {
        if (initialFocusEntryId < 0) {
            return -1;
        }
        for (AdvancedMemoryCardP2PEntry entry : entries) {
            if (entry.entryId() == initialFocusEntryId) {
                return initialFocusEntryId;
            }
        }
        return -1;
    }

    public void write(PacketBuffer data) {
        data.writeVarInt(initialFocusEntryId);
        data.writeVarInt(entries.size());
        for (var entry : entries) {
            entry.write(data);
        }
    }
}
