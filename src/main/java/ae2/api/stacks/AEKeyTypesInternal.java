package ae2.api.stacks;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public final class AEKeyTypesInternal {
    private static final Object2ObjectLinkedOpenHashMap<ResourceLocation, AEKeyType> byId = new Object2ObjectLinkedOpenHashMap<>();
    private static final Object2IntMap<AEKeyType> rawIds = new Object2IntOpenHashMap<>();
    private static final ObjectList<AEKeyType> byRawId = new ObjectArrayList<>();
    private static final ObjectLinkedOpenHashSet<AEKeyType> allTypes = new ObjectLinkedOpenHashSet<>();

    static {
        rawIds.defaultReturnValue(-1);
        register(AEKeyType.items());
        register(AEKeyType.fluids());
    }

    private AEKeyTypesInternal() {
    }

    public static @Nullable AEKeyType byId(int id) {
        if (id < 0 || id >= byRawId.size()) {
            return null;
        }
        return byRawId.get(id);
    }

    public static int getId(AEKeyType keyType) {
        return rawIds.getInt(keyType);
    }

    public static AEKeyType get(ResourceLocation id) {
        return byId.get(id);
    }

    public static Set<AEKeyType> getAllTypes() {
        return Collections.unmodifiableSet(allTypes);
    }

    public static void register(AEKeyType keyType) {
        Preconditions.checkState(!byId.containsKey(keyType.getId()), "Duplicate key type id %s", keyType.getId());
        byId.put(keyType.getId(), keyType);
        rawIds.put(keyType, byRawId.size());
        byRawId.add(keyType);
        allTypes.add(keyType);
    }
}
