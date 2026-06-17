package ae2.items.storage;

import ae2.core.definitions.AEItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;

import java.util.List;
import java.util.function.Supplier;

public record StorageTier(int index, String namePrefix, int bytes, double idleDrain, Supplier<Item> componentSupplier) {
    public static final StorageTier SIZE_1K = new StorageTier(1, "1k", 1024, 0.5, AEItems.CELL_COMPONENT_1K::item);
    public static final StorageTier SIZE_4K = new StorageTier(2, "4k", 4096, 1.0, AEItems.CELL_COMPONENT_4K::item);
    public static final StorageTier SIZE_16K = new StorageTier(3, "16k", 16384, 1.5, AEItems.CELL_COMPONENT_16K::item);
    public static final StorageTier SIZE_64K = new StorageTier(4, "64k", 65536, 2.0, AEItems.CELL_COMPONENT_64K::item);
    public static final StorageTier SIZE_256K = new StorageTier(5, "256k", 262144, 2.5, AEItems.CELL_COMPONENT_256K::item);

    public static final List<StorageTier> VALUES = new ObjectArrayList<>();

    public StorageTier(int index,String namePrefix,int bytes,double idleDrain, Supplier<Item> componentSupplier) {
        this.index = index;
        this.namePrefix = namePrefix;
        this.bytes = bytes;
        this.idleDrain = idleDrain;
        this.componentSupplier = componentSupplier;
        VALUES.add(this);
    }
}
