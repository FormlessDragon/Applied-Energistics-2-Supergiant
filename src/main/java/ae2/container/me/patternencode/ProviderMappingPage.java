package ae2.container.me.patternencode;

import ae2.crafting.pattern.RecipeTypeUid;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable page of a single provider's mapped recipe type UIDs. */
public record ProviderMappingPage(int windowId, long nonce, long directoryRevision, long providerId, int page,
                                  int total, List<String> recipeTypeUids) {
    public ProviderMappingPage {
        if (windowId < 0 || nonce <= 0 || directoryRevision < 0 || providerId < 0 || page < 0 || total < 0) {
            throw new IllegalArgumentException("Invalid provider mapping page header");
        }
        recipeTypeUids = List.copyOf(Objects.requireNonNull(recipeTypeUids, "recipeTypeUids"));
        if (recipeTypeUids.size() > ProviderPageLimits.PAGE_SIZE || recipeTypeUids.size() > total) {
            throw new IllegalArgumentException("Invalid provider mapping page size");
        }
        Set<String> unique = new HashSet<>(recipeTypeUids.size());
        for (String recipeTypeUid : recipeTypeUids) {
            if (!unique.add(RecipeTypeUid.requireValid(recipeTypeUid))) {
                throw new IllegalArgumentException("Duplicate provider mapping UID " + recipeTypeUid);
            }
        }
    }
}
