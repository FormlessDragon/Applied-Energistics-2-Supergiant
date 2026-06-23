package ae2.core.worlddata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PatternProviderMappingData extends AESavedData {
    public static final String ID = "ae2_pattern_provider_mappings";
    private static final String TAG_MAPPINGS = "mappings";
    private static final String TAG_RECIPE_TYPE = "recipeType";
    private static final String TAG_PROVIDERS = "providers";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITION = "pos";
    private static final String TAG_SIDE = "side";

    private final Map<String, List<ProviderReference>> mappings = new LinkedHashMap<>();

    public PatternProviderMappingData() {
        this(ID);
    }

    public PatternProviderMappingData(String name) {
        super(name);
    }

    public static PatternProviderMappingData get(World world) {
        if (!(world instanceof WorldServer worldServer)) {
            throw new IllegalStateException("PatternProviderMappingData requires a server world");
        }

        MinecraftServer server = worldServer.getMinecraftServer();
        if (server == null) {
            throw new IllegalStateException("PatternProviderMappingData requires a server");
        }

        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            throw new IllegalStateException("PatternProviderMappingData requires an overworld");
        }

        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            throw new IllegalStateException("PatternProviderMappingData requires world storage");
        }

        PatternProviderMappingData result =
            (PatternProviderMappingData) storage.getOrLoadData(PatternProviderMappingData.class, ID);
        if (result == null) {
            result = new PatternProviderMappingData();
            storage.setData(ID, result);
        }
        return result;
    }

    public void bind(String recipeType, ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");

        List<ProviderReference> references = this.mappings.computeIfAbsent(recipeType,
            ignored -> new ArrayList<>());
        if (references.contains(reference)) {
            return;
        }

        references.add(reference);
        markDirty();
    }

    public boolean unbindAll(ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");

        boolean changed = false;
        var iterator = this.mappings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<ProviderReference>> entry = iterator.next();
            if (entry.getValue().remove(reference)) {
                changed = true;
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            markDirty();
        }
        return changed;
    }

    public List<ProviderReference> getReferences(String recipeType) {
        List<ProviderReference> references = this.mappings.get(recipeType);
        return references == null ? Collections.emptyList() : List.copyOf(references);
    }

    public Set<String> getRecipeTypes(ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");

        Set<String> recipeTypes = new LinkedHashSet<>();
        for (Map.Entry<String, List<ProviderReference>> entry : this.mappings.entrySet()) {
            if (entry.getValue().contains(reference)) {
                recipeTypes.add(entry.getKey());
            }
        }
        return Set.copyOf(recipeTypes);
    }

    public boolean removeUnavailableReferences(Set<ProviderReference> availableReferences) {
        Objects.requireNonNull(availableReferences, "availableReferences");

        boolean changed = false;
        var iterator = this.mappings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<ProviderReference>> entry = iterator.next();
            if (entry.getValue().removeIf(reference -> !availableReferences.contains(reference))) {
                changed = true;
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            markDirty();
        }
        return changed;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.mappings.clear();
        NBTTagList mappingTags = nbt.getTagList(TAG_MAPPINGS, NBT.TAG_COMPOUND);
        for (int i = 0; i < mappingTags.tagCount(); i++) {
            NBTTagCompound mappingTag = mappingTags.getCompoundTagAt(i);
            if (!mappingTag.hasKey(TAG_RECIPE_TYPE, NBT.TAG_STRING)) {
                continue;
            }

            String recipeType = mappingTag.getString(TAG_RECIPE_TYPE).trim();
            if (recipeType.isEmpty()) {
                continue;
            }

            NBTTagList providerTags = mappingTag.getTagList(TAG_PROVIDERS, NBT.TAG_COMPOUND);
            List<ProviderReference> references = new ArrayList<>(providerTags.tagCount());
            for (int j = 0; j < providerTags.tagCount(); j++) {
                ProviderReference reference = ProviderReference.read(providerTags.getCompoundTagAt(j));
                if (!references.contains(reference)) {
                    references.add(reference);
                }
            }
            if (!references.isEmpty()) {
                this.mappings.put(recipeType, references);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList mappingTags = new NBTTagList();
        for (Map.Entry<String, List<ProviderReference>> entry : this.mappings.entrySet()) {
            NBTTagCompound mappingTag = new NBTTagCompound();
            mappingTag.setString(TAG_RECIPE_TYPE, entry.getKey());

            NBTTagList providerTags = new NBTTagList();
            for (ProviderReference reference : entry.getValue()) {
                providerTags.appendTag(reference.write(new NBTTagCompound()));
            }
            mappingTag.setTag(TAG_PROVIDERS, providerTags);
            mappingTags.appendTag(mappingTag);
        }
        compound.setTag(TAG_MAPPINGS, mappingTags);
        return compound;
    }

    public record ProviderReference(int dimension, long pos, int side) {
        private NBTTagCompound write(NBTTagCompound tag) {
            tag.setInteger(TAG_DIMENSION, this.dimension);
            tag.setLong(TAG_POSITION, this.pos);
            tag.setInteger(TAG_SIDE, this.side);
            return tag;
        }

        private static ProviderReference read(NBTTagCompound tag) {
            return new ProviderReference(tag.getInteger(TAG_DIMENSION), tag.getLong(TAG_POSITION),
                tag.getInteger(TAG_SIDE));
        }
    }
}
