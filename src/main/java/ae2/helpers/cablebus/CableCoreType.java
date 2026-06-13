package ae2.helpers.cablebus;

import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.core.AppEng;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public enum CableCoreType {
    GLASS("part/cable/core/glass"),
    COVERED("part/cable/core/covered"),
    DENSE("part/cable/core/dense_smart");

    private static final Map<AECableType, CableCoreType> CABLE_MAPPING = createCableMapping();

    private final String textureFolder;

    CableCoreType(String textureFolder) {
        this.textureFolder = textureFolder;
    }

    private static Map<AECableType, CableCoreType> createCableMapping() {
        EnumMap<AECableType, CableCoreType> result = new EnumMap<>(AECableType.class);
        result.put(AECableType.GLASS, GLASS);
        result.put(AECableType.COVERED, COVERED);
        result.put(AECableType.SMART, COVERED);
        result.put(AECableType.DENSE_COVERED, DENSE);
        result.put(AECableType.DENSE_SMART, DENSE);
        return Collections.unmodifiableMap(result);
    }

    public static CableCoreType fromCableType(AECableType cableType) {
        return CABLE_MAPPING.get(cableType);
    }

    public static Collection<ResourceLocation> getTextureDependencies() {
        ObjectLinkedOpenHashSet<ResourceLocation> result = new ObjectLinkedOpenHashSet<>();
        for (CableCoreType type : values()) {
            for (AEColor color : AEColor.values()) {
                result.add(type.getTexture(color));
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public ResourceLocation getTexture(AEColor color) {
        return AppEng.makeId(this.textureFolder + "/" + color.name().toLowerCase(Locale.ROOT));
    }
}
