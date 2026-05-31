package ae2.integration.abstraction;

import com.google.common.base.Strings;

public class ItemListMod {

    private static ItemListModAdapter adapter = ItemListModAdapter.none();

    private ItemListMod() {
    }

    public static boolean isEnabled() {
        return adapter.isEnabled();
    }

    public static String getShortName() {
        return adapter.getShortName();
    }

    public static String getSearchText() {
        return Strings.nullToEmpty(adapter.getSearchText());
    }

    public static void setSearchText(String text) {
        adapter.setSearchText(Strings.nullToEmpty(text));
    }

    public static boolean hasSearchFocus() {
        return adapter.hasSearchFocus();
    }

    public static void setAdapter(ItemListModAdapter adapter) {
        ItemListMod.adapter = adapter;
    }
}
