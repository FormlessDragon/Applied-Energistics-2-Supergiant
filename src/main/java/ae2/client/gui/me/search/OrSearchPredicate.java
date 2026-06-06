package ae2.client.gui.me.search;

import java.util.List;
import java.util.function.Predicate;

final class OrSearchPredicate<T> implements Predicate<T> {
    private final List<Predicate<T>> terms;

    private OrSearchPredicate(List<Predicate<T>> terms) {
        this.terms = terms;
    }

    public static <T> Predicate<T> of(List<Predicate<T>> filters) {
        if (filters.isEmpty()) {
            return t -> false;
        }
        if (filters.size() == 1) {
            return filters.getFirst();
        }
        return new OrSearchPredicate<>(filters);
    }

    @Override
    public boolean test(T entry) {
        for (var term : terms) {
            if (term.test(entry)) {
                return true;
            }
        }

        return false;
    }
}
