package ae2.client.gui.me.search;

import java.util.List;
import java.util.function.Predicate;

final class AndSearchPredicate<T> implements Predicate<T> {
    private final List<Predicate<T>> terms;

    private AndSearchPredicate(List<Predicate<T>> terms) {
        this.terms = terms;
    }

    public static <T> Predicate<T> of(List<Predicate<T>> predicates) {
        if (predicates.isEmpty()) {
            return t -> true;
        }
        if (predicates.size() == 1) {
            return predicates.getFirst();
        }
        return new AndSearchPredicate<>(predicates);
    }

    @Override
    public boolean test(T entry) {
        for (var term : terms) {
            if (!term.test(entry)) {
                return false;
            }
        }

        return true;
    }
}
