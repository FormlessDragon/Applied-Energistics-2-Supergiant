package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;

import java.util.Locale;
import java.util.function.Predicate;

final class NameSearchPredicate implements Predicate<AEKey> {
    private final String term;

    public NameSearchPredicate(String term) {
        this.term = term.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean test(AEKey what) {
        String displayName = what.getDisplayName().getFormattedText();
        return displayName.toLowerCase(Locale.ROOT).contains(term);
    }
}
