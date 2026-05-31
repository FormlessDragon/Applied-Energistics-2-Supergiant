package ae2.api.stacks;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.Set;

public final class AEKeyTypes {
    private AEKeyTypes() {
    }

    public static synchronized void register(AEKeyType keyType) {
        Objects.requireNonNull(keyType, "keyType");
        AEKeyTypesInternal.register(keyType);
    }

    public static AEKeyType get(ResourceLocation id) {
        var result = AEKeyTypesInternal.get(id);
        if (result == null) {
            throw new IllegalArgumentException("No key type registered for id " + id);
        }
        return result;
    }

    public static Set<AEKeyType> getAll() {
        return AEKeyTypesInternal.getAllTypes();
    }
}
