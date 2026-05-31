package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.container.me.common.GridInventoryEntry;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class OreDictSearchPredicate implements Predicate<GridInventoryEntry> {
    private final String term;
    private final Map<AEKeyType, List<String>> oreDictCache = new Reference2ObjectOpenHashMap<>();

    OreDictSearchPredicate(String term) {
        this.term = term.toLowerCase(Locale.ROOT);
    }

    private List<String> getOreDictEntriesMatchingTerm(AEKeyType keyType) {
        return keyType.getTagNames()
                      .filter(this::matchesTerm)
                      .collect(Collectors.toList());
    }

    private boolean matchesTerm(String oreDictName) {
        String normalized = oreDictName.toLowerCase(Locale.ROOT);
        if (term.contains(":")) {
            return normalized.contains(term);
        }

        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            return normalized.substring(0, separator).contains(term)
                || normalized.substring(separator + 1).contains(term);
        }

        return normalized.contains(term);
    }

    @Override
    public boolean test(GridInventoryEntry entry) {
        AEKey what = Objects.requireNonNull(entry.what());
        List<String> oreDictNames = oreDictCache.computeIfAbsent(what.getType(), this::getOreDictEntriesMatchingTerm);

        for (String oreDictName : oreDictNames) {
            if (what.isTagged(oreDictName)) {
                return true;
            }
        }

        return false;
    }
}
