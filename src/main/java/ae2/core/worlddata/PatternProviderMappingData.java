package ae2.core.worlddata;

import ae2.core.AELog;
import ae2.crafting.pattern.RecipeTypeUid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PatternProviderMappingData extends AESavedData {
    public static final String ID = "ae2_pattern_provider_mappings";
    /** Directory payloads expose only this many UIDs; persistent mappings are unbounded. */
    public static final int DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE = 3;
    /** @deprecated mappings are no longer capped; use {@link #DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE}. */
    @Deprecated
    public static final int MAX_RECIPE_TYPES_PER_PROVIDER = DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE;
    private static final String TAG_MAPPINGS = "mappings";
    private static final String TAG_RECIPE_TYPE = "recipeType";
    private static final String TAG_PROVIDERS = "providers";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITION = "pos";
    private static final String TAG_SIDE = "side";
    private static final long INVALID_UID_WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong LAST_INVALID_UID_WARNING = new AtomicLong(Long.MIN_VALUE);

    private final Map<ProviderReference, LinkedHashSet<String>> recipeTypesByProvider = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<ProviderReference>> providersByRecipeType = new LinkedHashMap<>();
    private long revision;

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

    public BindResult bind(String recipeType, ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");
        String normalizedRecipeType = RecipeTypeUid.requireValid(recipeType);

        if (containsMapping(normalizedRecipeType, reference)) {
            return BindResult.ALREADY_BOUND;
        }

        Set<String> currentRecipeTypes = this.recipeTypesByProvider.get(reference);
        if (currentRecipeTypes != null) {
            validateProviderMappings(reference, currentRecipeTypes);
        }

        addMapping(this.recipeTypesByProvider, this.providersByRecipeType, normalizedRecipeType, reference);
        recordChange();
        return BindResult.ADDED;
    }

    public boolean unbindAll(ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");

        LinkedHashSet<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        if (recipeTypes == null) {
            return false;
        }

        validateProviderMappings(reference, recipeTypes);
        removeProviderMappings(reference, recipeTypes);
        recordChange();
        return true;
    }

    public boolean unbind(ProviderReference reference, String recipeType) {
        Objects.requireNonNull(reference, "reference");
        String normalizedRecipeType = RecipeTypeUid.requireValid(recipeType);

        if (!removeMapping(normalizedRecipeType, reference)) {
            return false;
        }

        recordChange();
        return true;
    }

    public boolean replaceProviderMappings(ProviderReference reference, Collection<String> recipeTypes) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipeTypes, "recipeTypes");

        LinkedHashSet<String> normalizedRecipeTypes = new LinkedHashSet<>();
        for (String recipeType : recipeTypes) {
            normalizedRecipeTypes.add(RecipeTypeUid.requireValid(recipeType));
        }

        LinkedHashSet<String> currentRecipeTypes = this.recipeTypesByProvider.get(reference);
        if ((currentRecipeTypes == null && normalizedRecipeTypes.isEmpty())
            || (currentRecipeTypes != null && currentRecipeTypes.equals(normalizedRecipeTypes))) {
            return false;
        }

        if (currentRecipeTypes != null) {
            validateProviderMappings(reference, currentRecipeTypes);
            removeProviderMappings(reference, currentRecipeTypes);
        }
        for (String recipeType : normalizedRecipeTypes) {
            addMapping(this.recipeTypesByProvider, this.providersByRecipeType, recipeType, reference);
        }
        recordChange();
        return true;
    }

    public List<ProviderReference> getReferences(String recipeType) {
        Set<ProviderReference> references = this.providersByRecipeType.get(RecipeTypeUid.requireValid(recipeType));
        return references == null ? Collections.emptyList() : List.copyOf(references);
    }

    public Set<String> getRecipeTypes(ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");

        Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        return recipeTypes == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(recipeTypes));
    }

    public int getRecipeTypeCount(ProviderReference reference) {
        Objects.requireNonNull(reference, "reference");
        Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        return recipeTypes == null ? 0 : recipeTypes.size();
    }

    public List<String> getRecipeTypePreview(ProviderReference reference) {
        return getRecipeTypePage(reference, 0, DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE);
    }

    /** Returns a bounded preview, promoting UIDs that match the normalized query while retaining bind order. */
    public List<String> getRecipeTypePreview(ProviderReference reference, String normalizedQuery) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(normalizedQuery, "normalizedQuery");
        Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        if (recipeTypes == null || recipeTypes.isEmpty()) {
            return List.of();
        }
        List<String> preview = new ArrayList<>(DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE);
        if (!normalizedQuery.isEmpty()) {
            for (String uid : recipeTypes) {
                if (uid.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) {
                    preview.add(uid);
                    if (preview.size() == DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE) {
                        return List.copyOf(preview);
                    }
                }
            }
        }
        for (String uid : recipeTypes) {
            if (!preview.contains(uid)) {
                preview.add(uid);
                if (preview.size() == DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE) {
                    break;
                }
            }
        }
        return List.copyOf(preview);
    }

    public List<String> getRecipeTypePage(ProviderReference reference, int page, int pageSize) {
        Objects.requireNonNull(reference, "reference");
        if (page < 0 || pageSize <= 0 || page > Integer.MAX_VALUE / pageSize) {
            throw new IllegalArgumentException("Invalid recipe type mapping page");
        }
        Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        if (recipeTypes == null || recipeTypes.isEmpty()) {
            return List.of();
        }
        int from = page * pageSize;
        if (from >= recipeTypes.size()) {
            return List.of();
        }
        int to = Math.min(recipeTypes.size(), from + pageSize);
        return List.copyOf(new ArrayList<>(recipeTypes).subList(from, to));
    }

    public long getRevision() {
        return this.revision;
    }

    private boolean containsMapping(String recipeType, ProviderReference reference) {
        Set<ProviderReference> providers = this.providersByRecipeType.get(recipeType);
        Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        boolean providerIndexContains = providers != null && providers.contains(reference);
        boolean recipeTypeIndexContains = recipeTypes != null && recipeTypes.contains(recipeType);
        if (providerIndexContains != recipeTypeIndexContains) {
            throw inconsistentIndexes(recipeType, reference);
        }
        return providerIndexContains;
    }

    private boolean removeMapping(String recipeType, ProviderReference reference) {
        if (!containsMapping(recipeType, reference)) {
            return false;
        }

        LinkedHashSet<ProviderReference> providers = this.providersByRecipeType.get(recipeType);
        LinkedHashSet<String> recipeTypes = this.recipeTypesByProvider.get(reference);
        if (!Objects.requireNonNull(providers, "providers").remove(reference)
            || !Objects.requireNonNull(recipeTypes, "recipeTypes").remove(recipeType)) {
            throw inconsistentIndexes(recipeType, reference);
        }
        if (providers.isEmpty()) {
            this.providersByRecipeType.remove(recipeType);
        }
        if (recipeTypes.isEmpty()) {
            this.recipeTypesByProvider.remove(reference);
        }
        return true;
    }

    private void validateProviderMappings(ProviderReference reference, Set<String> recipeTypes) {
        for (String recipeType : recipeTypes) {
            Set<ProviderReference> providers = this.providersByRecipeType.get(recipeType);
            if (providers == null || !providers.contains(reference)) {
                throw inconsistentIndexes(recipeType, reference);
            }
        }
    }

    private void removeProviderMappings(ProviderReference reference, Set<String> recipeTypes) {
        for (String recipeType : recipeTypes) {
            LinkedHashSet<ProviderReference> providers = this.providersByRecipeType.get(recipeType);
            if (!Objects.requireNonNull(providers, "providers").remove(reference)) {
                throw inconsistentIndexes(recipeType, reference);
            }
            if (providers.isEmpty()) {
                this.providersByRecipeType.remove(recipeType);
            }
        }
        this.recipeTypesByProvider.remove(reference);
    }

    private void recordChange() {
        this.revision++;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        Objects.requireNonNull(nbt, "nbt");
        Map<ProviderReference, LinkedHashSet<String>> loadedRecipeTypesByProvider = new LinkedHashMap<>();
        Map<String, LinkedHashSet<ProviderReference>> loadedProvidersByRecipeType = new LinkedHashMap<>();
        NBTTagList mappingTags = nbt.getTagList(TAG_MAPPINGS, NBT.TAG_COMPOUND);
        int invalidRecipeTypeCount = 0;
        for (int i = 0; i < mappingTags.tagCount(); i++) {
            NBTTagCompound mappingTag = mappingTags.getCompoundTagAt(i);
            if (!mappingTag.hasKey(TAG_RECIPE_TYPE, NBT.TAG_STRING)) {
                invalidRecipeTypeCount++;
                continue;
            }

            String recipeType = RecipeTypeUid.normalize(mappingTag.getString(TAG_RECIPE_TYPE));
            if (recipeType == null) {
                invalidRecipeTypeCount++;
                continue;
            }

            NBTTagList providerTags = mappingTag.getTagList(TAG_PROVIDERS, NBT.TAG_COMPOUND);
            for (int j = 0; j < providerTags.tagCount(); j++) {
                ProviderReference reference = ProviderReference.read(providerTags.getCompoundTagAt(j));
                addMapping(loadedRecipeTypesByProvider, loadedProvidersByRecipeType, recipeType, reference);
            }
        }
        if (invalidRecipeTypeCount > 0 && shouldLogInvalidUidWarning(System.nanoTime())) {
            AELog.warn("Skipped %d pattern provider mapping entries with invalid recipe type UIDs",
                invalidRecipeTypeCount);
        }

        if (!this.recipeTypesByProvider.equals(loadedRecipeTypesByProvider)
            || !this.providersByRecipeType.equals(loadedProvidersByRecipeType)) {
            this.recipeTypesByProvider.clear();
            this.recipeTypesByProvider.putAll(loadedRecipeTypesByProvider);
            this.providersByRecipeType.clear();
            this.providersByRecipeType.putAll(loadedProvidersByRecipeType);
            this.revision++;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        validateIndexes();
        NBTTagList mappingTags = new NBTTagList();
        for (Map.Entry<String, LinkedHashSet<ProviderReference>> entry : this.providersByRecipeType.entrySet()) {
            String recipeType = RecipeTypeUid.requireValid(entry.getKey());
            if (!recipeType.equals(entry.getKey())) {
                throw new IllegalStateException("Pattern provider mapping contains a non-canonical recipe type UID");
            }

            NBTTagCompound mappingTag = new NBTTagCompound();
            mappingTag.setString(TAG_RECIPE_TYPE, recipeType);

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

    private void validateIndexes() {
        for (Map.Entry<String, LinkedHashSet<ProviderReference>> entry : this.providersByRecipeType.entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new IllegalStateException("Pattern provider mapping contains an empty provider set");
            }
            for (ProviderReference reference : entry.getValue()) {
                Set<String> recipeTypes = this.recipeTypesByProvider.get(reference);
                if (recipeTypes == null || !recipeTypes.contains(entry.getKey())) {
                    throw inconsistentIndexes(entry.getKey(), reference);
                }
            }
        }
        for (Map.Entry<ProviderReference, LinkedHashSet<String>> entry : this.recipeTypesByProvider.entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new IllegalStateException("Pattern provider mapping contains an empty recipe type set");
            }
            validateProviderMappings(entry.getKey(), entry.getValue());
        }
    }

    private static void addMapping(Map<ProviderReference, LinkedHashSet<String>> recipeTypesByProvider,
                                   Map<String, LinkedHashSet<ProviderReference>> providersByRecipeType,
                                   String recipeType, ProviderReference reference) {
        boolean providerAdded = providersByRecipeType.computeIfAbsent(recipeType, ignored -> new LinkedHashSet<>())
            .add(reference);
        boolean recipeTypeAdded = recipeTypesByProvider.computeIfAbsent(reference, ignored -> new LinkedHashSet<>())
            .add(recipeType);
        if (providerAdded != recipeTypeAdded) {
            throw inconsistentIndexes(recipeType, reference);
        }
    }

    private static boolean shouldLogInvalidUidWarning(long now) {
        while (true) {
            long previous = LAST_INVALID_UID_WARNING.get();
            long elapsed = now - previous;
            if (previous != Long.MIN_VALUE && elapsed >= 0 && elapsed < INVALID_UID_WARNING_INTERVAL_NANOS) {
                return false;
            }
            if (LAST_INVALID_UID_WARNING.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private static IllegalStateException inconsistentIndexes(String recipeType, ProviderReference reference) {
        return new IllegalStateException(
            "Pattern provider mapping indexes disagree for recipe type " + recipeType + " and provider " + reference);
    }

    public enum BindResult {
        ADDED,
        ALREADY_BOUND
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
