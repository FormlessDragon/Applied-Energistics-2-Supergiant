package ae2.parts.automation.special;

import ae2.api.config.IncludeExclude;
import ae2.api.stacks.AEKey;
import ae2.util.Platform;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Locale;

final class ModPriorityList implements IPartitionList {
    private final ObjectOpenHashSet<String> modIds = new ObjectOpenHashSet<>();

    ModPriorityList(String expression) {
        if (expression == null) {
            return;
        }
        for (String token : expression.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                this.modIds.add(normalize(token));
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isListed(AEKey input) {
        String modId = normalize(input.getModId());
        return this.modIds.contains(modId) || this.modIds.contains(normalize(Platform.getModName(modId)));
    }

    @Override
    public boolean isEmpty() {
        return this.modIds.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return List.of();
    }

    @Override
    public boolean matchesFilter(AEKey key, IncludeExclude mode) {
        return isEmpty() || isListed(key);
    }
}
