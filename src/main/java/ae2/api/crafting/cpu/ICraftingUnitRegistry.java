package ae2.api.crafting.cpu;

import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Registry of all known crafting unit definitions.
 */
public interface ICraftingUnitRegistry {
    void register(ICraftingUnitDefinition definition);

    @Nullable
    ICraftingUnitDefinition get(ResourceLocation id);

    Collection<ICraftingUnitDefinition> getDefinitions();
}
