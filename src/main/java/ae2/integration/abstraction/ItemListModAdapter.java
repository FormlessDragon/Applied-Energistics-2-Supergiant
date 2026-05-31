package ae2.integration.abstraction;

public interface ItemListModAdapter {

    static ItemListModAdapter none() {
        return new ItemListModAdapter() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String getShortName() {
                return "HEI";
            }
        };
    }

    boolean isEnabled();

    String getShortName();

    default String getSearchText() {
        return "";
    }

    default void setSearchText(String text) {
    }

    default boolean hasSearchFocus() {
        return false;
    }
}
