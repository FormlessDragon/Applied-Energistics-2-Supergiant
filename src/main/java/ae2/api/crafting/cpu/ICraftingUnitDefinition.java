package ae2.api.crafting.cpu;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Stable description of a crafting unit type.
 * <p>
 * This is the primary external extension point for adding new CPU block types that reuse AE's cluster logic.
 */
public interface ICraftingUnitDefinition {

    /**
     * Stable identifier used for registration, model lookup and persistence.
     */
    ResourceLocation id();

    /**
     * Bytes contributed by one block of this definition to its cluster.
     */
    long storageBytes();

    /**
     * Co-processor threads contributed by one block of this definition to its cluster.
     */
    int acceleratorThreads();

    /**
     * Item used as the block's item representation.
     */
    Item getItemRepresentation();

    /**
     * Client rendering contract for formed rendering.
     */
    CraftingUnitVisualDefinition getVisualDefinition();

    /**
     * Family identifier used to gate cluster compatibility between unrelated crafting unit implementations.
     */
    ResourceLocation getFamilyId();
}
