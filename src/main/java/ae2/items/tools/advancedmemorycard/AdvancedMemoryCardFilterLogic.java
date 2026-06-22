package ae2.items.tools.advancedmemorycard;

import ae2.api.config.AdvancedMemoryCardStatusFilter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Applies Advanced Memory Card entry filtering using the same matching semantics as the GUI.
 */
public final class AdvancedMemoryCardFilterLogic {
    private AdvancedMemoryCardFilterLogic() {
    }

    public static List<AdvancedMemoryCardP2PEntry> filter(List<AdvancedMemoryCardP2PEntry> entries, Filter filter,
                                                          Function<AdvancedMemoryCardP2PEntry, String> visibleNameResolver) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(visibleNameResolver, "visibleNameResolver");
        if (filter.isDefault()) {
            return entries;
        }

        var filteredEntries = new ObjectArrayList<AdvancedMemoryCardP2PEntry>(entries.size());
        for (var entry : entries) {
            if (matches(entry, filter, visibleNameResolver)) {
                filteredEntries.add(entry);
            }
        }
        return filteredEntries;
    }

    public static boolean isUnbound(AdvancedMemoryCardP2PEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return entry.error() || entry.frequency() == 0;
    }

    private static boolean matches(AdvancedMemoryCardP2PEntry entry, Filter filter,
                                   Function<AdvancedMemoryCardP2PEntry, String> visibleNameResolver) {
        return matchesState(entry, filter.stateFilter())
            && matchesType(entry, filter.tunnelTypes())
            && matchesSearch(entry, filter.searchText(), visibleNameResolver);
    }

    private static boolean matchesState(AdvancedMemoryCardP2PEntry entry, AdvancedMemoryCardStatusFilter stateFilter) {
        return switch (stateFilter) {
            case ANY -> true;
            case INPUT -> entry.input();
            case OUTPUT -> !entry.input();
            case UNBOUND -> isUnbound(entry);
        };
    }

    private static boolean matchesType(AdvancedMemoryCardP2PEntry entry, Set<ResourceLocation> tunnelTypes) {
        return tunnelTypes.isEmpty() || tunnelTypes.contains(entry.tunnelType());
    }

    private static boolean matchesSearch(AdvancedMemoryCardP2PEntry entry, String searchText,
                                         Function<AdvancedMemoryCardP2PEntry, String> visibleNameResolver) {
        if (searchText.isEmpty()) {
            return true;
        }

        String visibleName = Objects.requireNonNull(visibleNameResolver.apply(entry), "visibleName").toLowerCase(Locale.ROOT);
        if (visibleName.contains(searchText)) {
            return true;
        }

        String frequency = Integer.toHexString(Short.toUnsignedInt(entry.frequency())).toLowerCase(Locale.ROOT);
        if (frequency.contains(searchText)) {
            return true;
        }

        String paddedFrequency = String.format(Locale.ROOT, "%04x", Short.toUnsignedInt(entry.frequency()));
        return paddedFrequency.contains(searchText);
    }

    public record Filter(AdvancedMemoryCardStatusFilter stateFilter, Set<ResourceLocation> tunnelTypes, String searchText) {
        public Filter {
            Objects.requireNonNull(stateFilter, "stateFilter");
            tunnelTypes = Set.copyOf(Objects.requireNonNull(tunnelTypes, "tunnelTypes"));
            searchText = normalizeSearchText(Objects.requireNonNull(searchText, "searchText"));
        }

        private static String normalizeSearchText(String searchText) {
            return searchText.trim().toLowerCase(Locale.ROOT);
        }

        public boolean isDefault() {
            return stateFilter == AdvancedMemoryCardStatusFilter.ANY && tunnelTypes.isEmpty() && searchText.isEmpty();
        }
    }
}
