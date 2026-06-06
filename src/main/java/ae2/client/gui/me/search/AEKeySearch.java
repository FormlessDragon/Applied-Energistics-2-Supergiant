package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public class AEKeySearch {

    private final Map<AEKey, String> tooltipCache = new WeakHashMap<>();
    private String searchString = "";
    private Predicate<AEKey> search = key -> true;

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        if (searchString == null) {
            searchString = "";
        }
        if (!searchString.equals(this.searchString)) {
            this.search = fromString(searchString);
            this.searchString = searchString;
        }
    }

    public boolean matches(AEKey key) {
        return search.test(key);
    }

    private Predicate<AEKey> fromString(String searchString) {
        if (searchString.trim().isEmpty()) {
            return key -> true;
        }

        var orParts = searchString.split("\\|");

        if (orParts.length == 1) {
            return AndSearchPredicate.of(getPredicates(orParts[0]));
        } else {
            var orPartFilters = new ObjectArrayList<Predicate<AEKey>>(orParts.length);

            for (String orPart : orParts) {
                orPartFilters.add(AndSearchPredicate.of(getPredicates(orPart)));
            }

            return OrSearchPredicate.of(orPartFilters);
        }
    }

    private List<Predicate<AEKey>> getPredicates(String query) {
        var terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        var predicateFilters = new ObjectArrayList<Predicate<AEKey>>(terms.length);

        for (String part : terms) {
            if (part.isEmpty()) {
                continue;
            }

            if (part.startsWith("@")) {
                predicateFilters.add(new ModSearchPredicate(part.substring(1)));
            } else if (part.startsWith("$")) {
                predicateFilters.add(new OreDictSearchPredicate(part.substring(1)));
            } else if (part.startsWith("#")) {
                predicateFilters.add(new TooltipsSearchPredicate(part.substring(1), tooltipCache));
            } else if (part.startsWith("&")) {
                predicateFilters.add(new ItemIdSearchPredicate(part.substring(1)));
            } else if (part.startsWith("*")) {
                predicateFilters.add(new ItemIdSearchPredicate(part.substring(1)));
            } else {
                predicateFilters.add(new NameSearchPredicate(part));
            }
        }

        return predicateFilters;
    }
}
