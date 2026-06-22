package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

final class OreDictSearchPredicate implements Predicate<AEKey> {
    private final String term;
    private final Map<AEKeyType, List<String>> oreDictCache = new Reference2ObjectOpenHashMap<>();

    OreDictSearchPredicate(String term) {
        this.term = term.toLowerCase(Locale.ROOT);
    }

    private List<String> getOreDictEntriesMatchingTerm(AEKeyType keyType) {
        var matches = new ObjectArrayList<String>();
        keyType.getTagNames().forEach(tagName -> {
            if (matchesTerm(tagName)) {
                matches.add(tagName);
            }
        });
        return matches;
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
    public boolean test(AEKey what) {
        List<String> oreDictNames = oreDictCache.computeIfAbsent(what.getType(), this::getOreDictEntriesMatchingTerm);

        for (String oreDictName : oreDictNames) {
            if (what.isOD(oreDictName)) {
                return true;
            }
        }

        return false;
    }
}
