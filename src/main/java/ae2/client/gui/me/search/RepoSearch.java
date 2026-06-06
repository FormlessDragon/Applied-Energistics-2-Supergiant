package ae2.client.gui.me.search;

import ae2.container.me.common.GridInventoryEntry;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

public class RepoSearch {

    private final Long2BooleanMap cache = new Long2BooleanOpenHashMap();
    private final AEKeySearch search = new AEKeySearch();

    public RepoSearch() {
    }

    public String getSearchString() {
        return search.getSearchString();
    }

    public void setSearchString(String searchString) {
        if (searchString == null) {
            searchString = "";
        }
        if (!searchString.equals(this.search.getSearchString())) {
            this.search.setSearchString(searchString);
            this.cache.clear();
        }
    }

    public boolean matches(GridInventoryEntry entry) {
        return cache.computeIfAbsent(entry.serial(), s -> entry.what() != null && search.matches(entry.what()));
    }
}
