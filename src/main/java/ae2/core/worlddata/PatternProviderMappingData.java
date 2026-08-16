package ae2.core.worlddata;

import ae2.core.AELog;
import ae2.core.AEConfig;
import ae2.integration.Integrations;
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
import java.nio.charset.StandardCharsets;

public class PatternProviderMappingData extends AESavedData {
    public static final String ID = "ae2_pattern_provider_mappings";
    /**
     * Directory payloads expose only this many UIDs.
     */
    public static final int DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE = 3;
    public static final int MAX_RECIPE_TYPE_UID_UTF16_LENGTH = 256;
    public static final int MAX_RECIPE_TYPE_UID_UTF8_BYTES = 1024;
    private static final String TAG_MAPPINGS = "mappings";
    private static final String TAG_RECIPE_TYPE = "recipeType";
    private static final String TAG_PROVIDERS = "providers";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITION = "pos";
    private static final String TAG_SIDE = "side";
    private static final long INVALID_UID_WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong LAST_INVALID_UID_WARNING = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_MAPPING_INDEX_WARNING = new AtomicLong(Long.MIN_VALUE);

    private final Map<ProviderReference, LinkedHashSet<String>> recipeTypesByProvider = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<ProviderReference>> providersByRecipeType = new LinkedHashMap<>();
    private long revision;

    public PatternProviderMappingData() {
        this(ID);
    }

    public PatternProviderMappingData(String name) {
        super(name);
    }

    public static String normalizeRecipeTypeUid(String recipeTypeUid) {
        return requireRecipeTypeUid(Objects.requireNonNull(recipeTypeUid, "recipe type UID").trim());
    }

    public static String requireRecipeTypeUid(String recipeTypeUid) {
        Objects.requireNonNull(recipeTypeUid, "recipe type UID");
        if (recipeTypeUid.isEmpty()) {
            throw new IllegalArgumentException("Recipe type UID must not be empty");
        }
        if (recipeTypeUid.length() > MAX_RECIPE_TYPE_UID_UTF16_LENGTH) {
            throw new IllegalArgumentException("Recipe type UID exceeds " + MAX_RECIPE_TYPE_UID_UTF16_LENGTH
                + " UTF-16 characters");
        }
        int utf8Bytes = recipeTypeUid.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > MAX_RECIPE_TYPE_UID_UTF8_BYTES) {
            throw new IllegalArgumentException("Recipe type UID exceeds " + MAX_RECIPE_TYPE_UID_UTF8_BYTES
                + " UTF-8 bytes");
        }
        return recipeTypeUid;
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
        recipeType = normalizeRecipeTypeUid(recipeType);

        if (!isMappingEnabled()) {
            return BindResult.DISABLED;
        }

        if (containsMapping(recipeType, reference)) {
            return BindResult.ALREADY_BOUND;
        }

        Set<String> currentRecipeTypes = this.recipeTypesByProvider.get(reference);
        if (currentRecipeTypes != null) {
            validateProviderMappings(reference, currentRecipeTypes);
            if (currentRecipeTypes.size() >= getMappingLimit()) {
                return BindResult.LIMIT_REACHED;
            }
        }

        addMapping(this.recipeTypesByProvider, this.providersByRecipeType, recipeType, reference);
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
        recipeType = normalizeRecipeTypeUid(recipeType);

        if (!removeMapping(recipeType, reference)) {
            return false;
        }

        recordChange();
        return true;
    }

    public void replaceProviderMappings(ProviderReference reference, Collection<String> recipeTypes) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipeTypes, "recipeTypes");

        if (!isMappingEnabled()) {
            return;
        }

        int mappingLimit = getMappingLimit();

        LinkedHashSet<String> replacementRecipeTypes = new LinkedHashSet<>();
        for (String recipeType : recipeTypes) {
            if (replacementRecipeTypes.size() == mappingLimit) {
                AELog.warn("Truncating pattern provider mapping refresh at %d recipe types for provider %s",
                    mappingLimit, reference);
                break;
            }
            replacementRecipeTypes.add(normalizeRecipeTypeUid(recipeType));
        }

        LinkedHashSet<String> currentRecipeTypes = this.recipeTypesByProvider.get(reference);
        if ((currentRecipeTypes == null && replacementRecipeTypes.isEmpty())
            || (currentRecipeTypes != null && currentRecipeTypes.equals(replacementRecipeTypes))) {
            return;
        }

        if (currentRecipeTypes != null) {
            validateProviderMappings(reference, currentRecipeTypes);
            removeProviderMappings(reference, currentRecipeTypes);
        }
        for (String recipeType : replacementRecipeTypes) {
            addMapping(this.recipeTypesByProvider, this.providersByRecipeType, recipeType, reference);
        }
        recordChange();
    }

    public List<ProviderReference> getReferences(String recipeType) {
        Set<ProviderReference> references = this.providersByRecipeType.get(Objects.requireNonNull(recipeType,
            "recipe type UID"));
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

    /**
     * Returns a bounded preview, promoting UIDs that match the normalized query while retaining bind order.
     */
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

    public static boolean isMappingEnabled() {
        return AEConfig.instance().getPatternProviderMappingLimit() > 0
            && Integrations.hei().isEnabled();
    }

    public static int getMappingLimit() {
        return AEConfig.instance().getPatternProviderMappingLimit();
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
        int cappedMappingCount = 0;
        for (int i = 0; i < mappingTags.tagCount(); i++) {
            NBTTagCompound mappingTag = mappingTags.getCompoundTagAt(i);
            if (!mappingTag.hasKey(TAG_RECIPE_TYPE, NBT.TAG_STRING)) {
                invalidRecipeTypeCount++;
                continue;
            }

            String storedRecipeType = mappingTag.getString(TAG_RECIPE_TYPE);
            String recipeType;
            try {
                recipeType = requireRecipeTypeUid(storedRecipeType);
            } catch (RuntimeException e) {
                invalidRecipeTypeCount++;
                continue;
            }

            NBTTagList providerTags = mappingTag.getTagList(TAG_PROVIDERS, NBT.TAG_COMPOUND);
            for (int j = 0; j < providerTags.tagCount(); j++) {
                ProviderReference reference = ProviderReference.read(providerTags.getCompoundTagAt(j));
                Set<String> loadedRecipeTypes = loadedRecipeTypesByProvider.get(reference);
                if (getMappingLimit() > 0
                    && loadedRecipeTypes != null
                    && !loadedRecipeTypes.contains(recipeType)
                    && loadedRecipeTypes.size() >= getMappingLimit()) {
                    cappedMappingCount++;
                    continue;
                }
                addMapping(loadedRecipeTypesByProvider, loadedProvidersByRecipeType, recipeType, reference);
            }
        }
        if ((invalidRecipeTypeCount > 0 || cappedMappingCount > 0)
            && shouldLogInvalidUidWarning(System.nanoTime())) {
            AELog.warn("Skipped %d invalid pattern provider mapping entries and %d entries over the per-provider limit",
                invalidRecipeTypeCount, cappedMappingCount);
        }

        if (!this.recipeTypesByProvider.equals(loadedRecipeTypesByProvider)
            || !this.providersByRecipeType.equals(loadedProvidersByRecipeType)) {
            this.recipeTypesByProvider.clear();
            this.recipeTypesByProvider.putAll(loadedRecipeTypesByProvider);
            this.providersByRecipeType.clear();
            this.providersByRecipeType.putAll(loadedProvidersByRecipeType);
            this.revision++;
        }
        if (invalidRecipeTypeCount > 0 || cappedMappingCount > 0) {
            markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        Map<String, LinkedHashSet<ProviderReference>> mappings = sanitizeIndexesForWrite();
        NBTTagList mappingTags = new NBTTagList();
        for (Map.Entry<String, LinkedHashSet<ProviderReference>> entry : mappings.entrySet()) {
            String recipeType = entry.getKey();

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

    private Map<String, LinkedHashSet<ProviderReference>> sanitizeIndexesForWrite() {
        Map<String, LinkedHashSet<ProviderReference>> validMappings = new LinkedHashMap<>();
        int skippedMappingCount = 0;
        for (Map.Entry<String, LinkedHashSet<ProviderReference>> entry : this.providersByRecipeType.entrySet()) {
            String recipeType = entry.getKey();
            Set<ProviderReference> references = entry.getValue();
            if (recipeType == null || references == null || references.isEmpty()) {
                skippedMappingCount++;
                continue;
            }
            try {
                requireRecipeTypeUid(recipeType);
            } catch (RuntimeException e) {
                skippedMappingCount++;
                continue;
            }

            LinkedHashSet<ProviderReference> validReferences = new LinkedHashSet<>();
            for (ProviderReference reference : references) {
                Set<String> recipeTypes = reference == null ? null : this.recipeTypesByProvider.get(reference);
                if (recipeTypes == null || !recipeTypes.contains(recipeType)) {
                    skippedMappingCount++;
                    continue;
                }
                validReferences.add(reference);
            }
            if (!validReferences.isEmpty()) {
                validMappings.put(recipeType, validReferences);
            } else {
                skippedMappingCount++;
            }
        }

        for (Map.Entry<ProviderReference, LinkedHashSet<String>> entry : this.recipeTypesByProvider.entrySet()) {
            ProviderReference reference = entry.getKey();
            Set<String> recipeTypes = entry.getValue();
            if (reference == null || recipeTypes == null || recipeTypes.isEmpty()) {
                skippedMappingCount++;
                continue;
            }
            for (String recipeType : recipeTypes) {
                LinkedHashSet<ProviderReference> references = validMappings.get(recipeType);
                if (references == null || !references.contains(reference)) {
                    skippedMappingCount++;
                }
            }
        }

        if (skippedMappingCount == 0) {
            return validMappings;
        }

        if (shouldLogWarning(LAST_MAPPING_INDEX_WARNING, System.nanoTime())) {
            AELog.warn("Skipped %d inconsistent pattern provider mapping index entries while saving",
                skippedMappingCount);
        }

        this.providersByRecipeType.clear();
        this.recipeTypesByProvider.clear();
        for (Map.Entry<String, LinkedHashSet<ProviderReference>> entry : validMappings.entrySet()) {
            for (ProviderReference reference : entry.getValue()) {
                addMapping(this.recipeTypesByProvider, this.providersByRecipeType, entry.getKey(), reference);
            }
        }
        markDirty();
        return validMappings;
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
        return shouldLogWarning(LAST_INVALID_UID_WARNING, now);
    }

    private static boolean shouldLogWarning(AtomicLong lastWarning, long now) {
        while (true) {
            long previous = lastWarning.get();
            long elapsed = now - previous;
            if (previous != Long.MIN_VALUE && elapsed >= 0 && elapsed < INVALID_UID_WARNING_INTERVAL_NANOS) {
                return false;
            }
            if (lastWarning.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private static IllegalStateException inconsistentIndexes(String recipeType, ProviderReference reference) {
        return new IllegalStateException(
            "Pattern provider mapping indexes disagree for recipe type " + recipeType + " and provider " + reference);
    }

    public enum BindResult {
        DISABLED,
        ADDED,
        ALREADY_BOUND,
        LIMIT_REACHED
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
