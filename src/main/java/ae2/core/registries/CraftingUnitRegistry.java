package ae2.core.registries;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import ae2.api.crafting.cpu.ICraftingUnitRegistry;
import ae2.block.crafting.CraftingUnitType;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class CraftingUnitRegistry implements ICraftingUnitRegistry {
    private static final CraftingUnitRegistry INSTANCE = new CraftingUnitRegistry();

    private final Map<ResourceLocation, ICraftingUnitDefinition> definitions = new Object2ObjectLinkedOpenHashMap<>();
    private final Collection<ICraftingUnitDefinition> definitionsView = Collections.unmodifiableCollection(this.definitions.values());
    private boolean initialized;

    private CraftingUnitRegistry() {
    }

    public static CraftingUnitRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void initBuiltins() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        for (CraftingUnitType type : CraftingUnitType.values()) {
            this.register(type);
        }
    }

    @Override
    public synchronized void register(ICraftingUnitDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        ResourceLocation id = Objects.requireNonNull(definition.id(), "definition.id");
        ICraftingUnitDefinition previous = this.definitions.putIfAbsent(id, definition);
        if (previous != null && previous != definition) {
            AELog.warn("Ignoring duplicate crafting unit definition registration for %s", id);
            throw new IllegalStateException("Duplicate crafting unit definition: " + id);
        }
    }

    @Override
    public synchronized @Nullable ICraftingUnitDefinition get(ResourceLocation id) {
        return this.definitions.get(id);
    }

    @Override
    public synchronized Collection<ICraftingUnitDefinition> getDefinitions() {
        return this.definitionsView;
    }
}
