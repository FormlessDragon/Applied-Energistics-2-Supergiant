package ae2.items.storage;

import ae2.core.definitions.AEItems;
import com.google.common.base.Objects;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.item.Item;

import java.util.Set;
import java.util.function.Supplier;

public record StorageTier(int index, String namePrefix, int bytes, double idleDrain, Supplier<Item> componentSupplier) {
    public static final Set<StorageTier> VALUES = new ObjectLinkedOpenHashSet<>();

    public static final StorageTier SIZE_1K = new StorageTier(1, "1k", toBytes(1), 0.5, AEItems.CELL_COMPONENT_1K::item);
    public static final StorageTier SIZE_4K = new StorageTier(2, "4k", toBytes(4), 1.0, AEItems.CELL_COMPONENT_4K::item);
    public static final StorageTier SIZE_16K = new StorageTier(3, "16k", toBytes(16), 1.5, AEItems.CELL_COMPONENT_16K::item);
    public static final StorageTier SIZE_64K = new StorageTier(4, "64k", toBytes(64), 2.0, AEItems.CELL_COMPONENT_64K::item);
    public static final StorageTier SIZE_256K = new StorageTier(5, "256k", toBytes(256), 2.5, AEItems.CELL_COMPONENT_256K::item);

    public StorageTier(int index, String namePrefix, int bytes, double idleDrain, Supplier<Item> componentSupplier) {
        this.index = index;
        this.namePrefix = namePrefix;
        this.bytes = bytes;
        this.idleDrain = idleDrain;
        this.componentSupplier = componentSupplier;
        VALUES.add(this);
    }

    public static int toBytes(int value) {
        return value * 1024;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StorageTier(
            int index1, String namePrefix1, int bytes1, double idleDrain1, _
        ))) return false;
        return index == index1 && bytes == bytes1 && Double.compare(idleDrain, idleDrain1) == 0 && Objects.equal(namePrefix, namePrefix1);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(index, namePrefix, bytes, idleDrain);
    }
}
