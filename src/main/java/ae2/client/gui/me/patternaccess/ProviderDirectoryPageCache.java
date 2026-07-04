package ae2.client.gui.me.patternaccess;

import ae2.container.me.patternencode.ProviderDirectoryPage;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bounded client cache for converted provider-directory pages.
 * <p>
 * Page identity includes every protocol field that determines page contents. Repeated responses for the same key
 * therefore reuse the original immutable view and its converted GUI records.
 */
public final class ProviderDirectoryPageCache {
    public static final int DEFAULT_MAX_PAGES = 64;

    private final int maximumPages;
    private final LinkedHashMap<DirectoryPageKey, DirectoryPageView> pages =
        new LinkedHashMap<>(16, 0.75F, true);

    public ProviderDirectoryPageCache() {
        this(DEFAULT_MAX_PAGES);
    }

    ProviderDirectoryPageCache(int maximumPages) {
        if (maximumPages <= 0) {
            throw new IllegalArgumentException("Provider-select cache page limit must be positive");
        }
        this.maximumPages = maximumPages;
    }

    public DirectoryPageView put(ProviderDirectoryPage page) {
        Objects.requireNonNull(page, "page");
        DirectoryPageKey key = DirectoryPageKey.of(page);
        return getOrCreate(key, () -> new DirectoryPageView(
            key,
            page.total(),
            page.entries().stream().map(ProviderDirectoryPageCache::convert).toList()));
    }

    public DirectoryPageView getDirectoryPage(int windowId, long nonce, long revision, int page) {
        return this.pages.get(new DirectoryPageKey(windowId, nonce, revision, page));
    }

    public void clear() {
        this.pages.clear();
    }

    private DirectoryPageView getOrCreate(DirectoryPageKey key, Supplier<DirectoryPageView> factory) {
        DirectoryPageView current = this.pages.get(key);
        if (current != null) {
            return current;
        }

        DirectoryPageView created = Objects.requireNonNull(factory.get(), "created page view");
        this.pages.put(key, created);
        while (this.pages.size() > this.maximumPages) {
            DirectoryPageKey eldest = this.pages.keySet().iterator().next();
            this.pages.remove(eldest);
        }
        return created;
    }

    static GuiProviderSelect.ProviderEntry convert(ProviderDirectoryPage.Entry entry) {
        GuiProviderSelect.ProviderLocation location = null;
        if (entry.hasLocation()) {
            EnumFacing side = entry.locationSide() < 0 ? null : EnumFacing.byIndex(entry.locationSide());
            location = new GuiProviderSelect.ProviderLocation(
                entry.locationDimension(),
                BlockPos.fromLong(entry.locationPos()),
                side);
        }
        return new GuiProviderSelect.ProviderEntry(
            entry.providerId(),
            entry.icon(),
            location,
            entry.hasLocation(),
            entry.providerName(),
            entry.emptySlots(),
            entry.recipeTypeCount(),
            entry.recipeTypeUids(),
            entry.acceptsProcessingPatterns());
    }

    public record DirectoryPageKey(int windowId, long nonce, long revision, int page) {
        private static DirectoryPageKey of(ProviderDirectoryPage page) {
            return new DirectoryPageKey(page.windowId(), page.nonce(), page.directoryRevision(), page.page());
        }
    }

    public record DirectoryPageView(DirectoryPageKey key, int total,
                                    List<GuiProviderSelect.ProviderEntry> entries) {
        public DirectoryPageView {
            Objects.requireNonNull(key, "key");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

}
