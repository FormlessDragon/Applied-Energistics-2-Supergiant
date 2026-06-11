package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;

import java.util.Locale;
import java.util.function.Predicate;

final class ItemIdSearchPredicate implements Predicate<AEKey> {
    private final String term;

    public ItemIdSearchPredicate(String term) {
        this.term = term.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean test(AEKey what) {
        var keyId = what.getId();
        if (keyId == null) {
            return false;
        }

        var id = keyId.toString();
        return id.toLowerCase(Locale.ROOT).contains(term);
    }
}
