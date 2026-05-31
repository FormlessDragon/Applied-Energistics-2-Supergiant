package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;
import ae2.container.me.common.GridInventoryEntry;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

final class ItemIdSearchPredicate implements Predicate<GridInventoryEntry> {
    private final String term;

    public ItemIdSearchPredicate(String term) {
        this.term = term.toLowerCase();
    }

    @Override
    public boolean test(GridInventoryEntry gridInventoryEntry) {
        AEKey what = Objects.requireNonNull(gridInventoryEntry.what());
        var id = what.getId().toString();
        return id.toLowerCase(Locale.ROOT).contains(term);
    }
}

