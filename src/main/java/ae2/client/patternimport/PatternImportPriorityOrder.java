package ae2.client.patternimport;

import ae2.api.client.PatternImportPriorities;
import ae2.api.client.PatternImportPriority;
import ae2.core.AEConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class PatternImportPriorityOrder {
    private PatternImportPriorityOrder() {
    }

    public static List<String> getOrderedIds() {
        List<String> configuredIds = new ObjectArrayList<>();
        Collections.addAll(configuredIds, AEConfig.instance().getPatternImportPriorityOrder());

        List<String> repairedIds = repairIds(configuredIds);
        if (!repairedIds.equals(configuredIds)) {
            AEConfig.instance().setPatternImportPriorityOrder(repairedIds);
        }
        return repairedIds;
    }

    public static List<PatternImportPriority> getOrderedPriorities() {
        List<PatternImportPriority> ordered = new ObjectArrayList<>();
        for (String id : getOrderedIds()) {
            PatternImportPriority priority = PatternImportPriorities.getById(id);
            if (priority != null) {
                ordered.add(priority);
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    public static void moveToFront(String priorityId) {
        List<String> currentOrder = new ObjectArrayList<>(getOrderedIds());
        if (!currentOrder.remove(priorityId)) {
            return;
        }
        currentOrder.addFirst(priorityId);
        AEConfig.instance().setPatternImportPriorityOrder(currentOrder);
    }

    public static List<String> repairIds(List<String> configuredIds) {
        List<String> registeredIds = new ObjectArrayList<>();
        for (PatternImportPriority priority : PatternImportPriorities.getRegistered()) {
            registeredIds.add(priority.getId());
        }

        Set<String> knownIds = new ObjectLinkedOpenHashSet<>(registeredIds);
        Set<String> seenIds = new ObjectLinkedOpenHashSet<>();
        List<String> repaired = new ObjectArrayList<>();
        if (configuredIds != null) {
            for (String configuredId : configuredIds) {
                if (configuredId != null && knownIds.contains(configuredId) && seenIds.add(configuredId)) {
                    repaired.add(configuredId);
                }
            }
        }
        for (String registeredId : registeredIds) {
            if (seenIds.add(registeredId)) {
                repaired.add(registeredId);
            }
        }
        return Collections.unmodifiableList(repaired);
    }
}
