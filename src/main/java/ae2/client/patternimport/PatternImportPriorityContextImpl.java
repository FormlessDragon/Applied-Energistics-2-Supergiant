package ae2.client.patternimport;

import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PatternImportPriorityContextImpl implements PatternImportPriorityContext {
    private final ContainerPatternEncodingTerm container;
    @Nullable
    private final IClientRepo clientRepo;
    private final List<GenericStack> bookmarkedStacks;
    private final Set<AEKey> bookmarkedKeys;
    private final Map<AEKey, GridInventoryEntry> repoEntries;

    private PatternImportPriorityContextImpl(ContainerPatternEncodingTerm container, List<GenericStack> bookmarkedStacks) {
        this.container = Objects.requireNonNull(container, "container");
        this.clientRepo = container.getClientRepo();
        this.bookmarkedKeys = new ObjectOpenHashSet<>();
        List<GenericStack> sanitizedBookmarkedStacks = new ObjectArrayList<>();
        if (bookmarkedStacks != null) {
            for (GenericStack bookmarkedStack : bookmarkedStacks) {
                if (bookmarkedStack != null) {
                    sanitizedBookmarkedStacks.add(bookmarkedStack);
                    this.bookmarkedKeys.add(bookmarkedStack.what());
                }
            }
        }
        this.bookmarkedStacks = Collections.unmodifiableList(sanitizedBookmarkedStacks);
        this.repoEntries = new Object2ObjectOpenHashMap<>();
        if (this.clientRepo != null) {
            for (GridInventoryEntry entry : this.clientRepo.getAllEntries()) {
                if (entry == null || entry.what() == null) {
                    continue;
                }
                this.repoEntries.merge(entry.what(), entry, PatternImportPriorityContextImpl::mergeEntries);
            }
        }
    }

    public static PatternImportPriorityContext create(ContainerPatternEncodingTerm container,
                                                      List<GenericStack> bookmarkedStacks) {
        return new PatternImportPriorityContextImpl(container, bookmarkedStacks);
    }

    private static GridInventoryEntry mergeEntries(GridInventoryEntry left, GridInventoryEntry right) {
        return new GridInventoryEntry(
            left.serial(),
            left.what(),
            Math.max(left.storedAmount(), right.storedAmount()),
            Math.max(left.requestableAmount(), right.requestableAmount()),
            left.craftable() || right.craftable()
        );
    }

    @Override
    public ContainerPatternEncodingTerm getContainer() {
        return this.container;
    }

    @Override
    @Nullable
    public IClientRepo getClientRepo() {
        return this.clientRepo;
    }

    @Override
    public List<GenericStack> getBookmarkedStacks() {
        return this.bookmarkedStacks;
    }

    @Override
    @Nullable
    public GridInventoryEntry getClientRepoEntry(GenericStack candidate) {
        if (candidate == null) {
            return null;
        }
        return this.repoEntries.get(candidate.what());
    }

    @Override
    public boolean isBookmarked(GenericStack candidate) {
        if (candidate == null) {
            return false;
        }
        return this.bookmarkedKeys.contains(candidate.what());
    }

    @Override
    public boolean isCraftable(GenericStack candidate) {
        GridInventoryEntry entry = getClientRepoEntry(candidate);
        return entry != null && entry.craftable();
    }

    @Override
    public boolean isStored(GenericStack candidate) {
        GridInventoryEntry entry = getClientRepoEntry(candidate);
        return entry != null && entry.storedAmount() > 0;
    }
}
