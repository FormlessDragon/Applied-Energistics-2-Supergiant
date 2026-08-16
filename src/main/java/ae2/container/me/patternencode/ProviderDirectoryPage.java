package ae2.container.me.patternencode;

import ae2.api.stacks.AEItemKey;
import ae2.core.worlddata.PatternProviderMappingData;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One immutable server-selected page of provider directory entries.
 *
 * @param windowId          active container window identifier
 * @param nonce             client request generation
 * @param directoryRevision server directory revision represented by this page
 * @param page              zero-based page index
 * @param total             total number of providers in the complete directory
 * @param mappingEnabled    whether server-side pattern provider mapping is enabled
 * @param entries           entries carried by this page
 */
public record ProviderDirectoryPage(int windowId, long nonce, long directoryRevision, int page, int total,
                                    boolean mappingEnabled, List<Entry> entries) {
    public ProviderDirectoryPage {
        if (windowId < 0) {
            throw new IllegalArgumentException("Provider directory window id must not be negative");
        }
        if (nonce <= 0) {
            throw new IllegalArgumentException("Provider directory nonce must be positive");
        }
        if (directoryRevision < 0) {
            throw new IllegalArgumentException("Provider directory revision must not be negative");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Provider directory page must not be negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("Provider directory total must not be negative");
        }

        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > ProviderPageLimits.PAGE_SIZE) {
            throw new IllegalArgumentException(
                "Provider directory page exceeds " + ProviderPageLimits.PAGE_SIZE + " entries");
        }
        if (entries.size() > total) {
            throw new IllegalArgumentException("Provider directory page contains more entries than its total");
        }

        Set<Long> providerEntryIds = new HashSet<>(entries.size());
        for (Entry entry : entries) {
            Objects.requireNonNull(entry, "provider directory entry");
            if (!providerEntryIds.add(entry.providerEntryId())) {
                throw new IllegalArgumentException("Provider directory page contains duplicate provider entry id "
                    + entry.providerEntryId());
            }
        }
    }

    /**
     * Immutable display and action identity for one provider directory row.
     *
     * @param providerEntryId                current server-side provider directory identifier
     * @param icon                      optional provider icon
     * @param providerName              bounded provider name used for display and search
     * @param emptySlots                number of currently empty pattern slots
     * @param recipeTypeCount           total recipe UIDs mapped to this provider
     * @param recipeTypeUids            bounded recipe UID preview, in binding/search order
     * @param acceptsProcessingPatterns whether this provider accepts processing patterns
     * @param hasLocation               whether the location fields identify a provider position
     * @param locationDimension         provider dimension when a location is present
     * @param locationPos               packed provider block position when a location is present
     * @param locationSide              provider side ordinal, or {@code -1} when no side applies
     */
    public record Entry(long providerEntryId, @Nullable AEItemKey icon, String providerName,
                        int emptySlots, int recipeTypeCount, List<String> recipeTypeUids,
                        boolean acceptsProcessingPatterns,
                        boolean hasLocation, int locationDimension, long locationPos, int locationSide) {
        public Entry {
            if (providerEntryId < 0) {
                throw new IllegalArgumentException("Provider directory entry id must not be negative");
            }
            providerName = ProviderPageLimits.requireBoundedText(
                "provider directory provider name",
                providerName,
                ProviderPageLimits.MAX_PROVIDER_NAME_UTF16_LENGTH,
                ProviderPageLimits.MAX_PROVIDER_NAME_UTF8_BYTES);
            if (emptySlots < 0) {
                throw new IllegalArgumentException("Provider directory empty slot count must not be negative");
            }
            Objects.requireNonNull(recipeTypeUids, "recipeTypeUids");
            if (recipeTypeCount < 0 || recipeTypeUids.size() > recipeTypeCount) {
                throw new IllegalArgumentException("Invalid provider directory recipe type count");
            }

            if (recipeTypeUids.size() > PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE) {
                throw new IllegalArgumentException("Provider directory entry exceeds "
                    + PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE + " recipe type preview UIDs");
            }
            Set<String> uniqueRecipeTypeUids = new HashSet<>(recipeTypeUids.size());
            for (String recipeTypeUid : recipeTypeUids) {
                PatternProviderMappingData.requireRecipeTypeUid(recipeTypeUid);
                if (!uniqueRecipeTypeUids.add(recipeTypeUid)) {
                    throw new IllegalArgumentException("Provider directory entry contains duplicate recipe type UID "
                        + recipeTypeUid);
                }
            }
            recipeTypeUids = List.copyOf(recipeTypeUids);

            if (hasLocation) {
                if (locationSide < -1 || locationSide >= EnumFacing.VALUES.length) {
                    throw new IllegalArgumentException("Invalid provider directory location side: " + locationSide);
                }
            } else {
                locationDimension = 0;
                locationPos = 0;
                locationSide = -1;
            }
        }
    }
}
