package ae2.block.crafting;

import ae2.api.crafting.cpu.CraftingUnitVisualDefinition;
import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

public final class CraftingUnitTypeAdapter implements ICraftingUnitType {
    private final ICraftingUnitDefinition definition;

    CraftingUnitTypeAdapter(ICraftingUnitDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public static ICraftingUnitType wrap(ICraftingUnitDefinition definition) {
        if (definition instanceof ICraftingUnitType type) {
            return type;
        }
        return new CraftingUnitTypeAdapter(definition);
    }

    @Override
    public long getStorageBytes() {
        return this.definition.storageBytes();
    }

    @Override
    public int getAcceleratorThreads() {
        return this.definition.acceleratorThreads();
    }

    @Override
    public Item getItemFromType() {
        return this.definition.getItemRepresentation();
    }

    @Override
    public ResourceLocation id() {
        return this.definition.id();
    }

    @Override
    public CraftingUnitVisualDefinition getVisualDefinition() {
        return this.definition.getVisualDefinition();
    }

    @Override
    public ResourceLocation getFamilyId() {
        return this.definition.getFamilyId();
    }
}
