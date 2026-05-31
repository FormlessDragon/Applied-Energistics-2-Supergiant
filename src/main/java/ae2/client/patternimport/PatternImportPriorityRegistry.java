package ae2.client.patternimport;

import ae2.api.client.PatternImportPriority;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PatternImportPriorityRegistry {
    private static final Map<String, PatternImportPriority> PRIORITIES = new Object2ObjectLinkedOpenHashMap<>();
    private static boolean initialized;

    private PatternImportPriorityRegistry() {
    }

    public static synchronized void register(PatternImportPriority priority) {
        ensureClientSide();
        ensureInitialized();
        addPriority(priority);
    }

    public static synchronized List<PatternImportPriority> getRegistered() {
        ensureClientSide();
        ensureInitialized();
        return Collections.unmodifiableList(new ObjectArrayList<>(PRIORITIES.values()));
    }

    @Nullable
    public static synchronized PatternImportPriority getById(String id) {
        ensureClientSide();
        ensureInitialized();
        return PRIORITIES.get(id);
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (PatternImportPriority priority : DefaultPatternImportPriorities.getDefaults()) {
            addPriority(priority);
        }
    }

    private static void addPriority(PatternImportPriority priority) {
        Objects.requireNonNull(priority, "priority");
        String id = Objects.requireNonNull(priority.getId(), "priorityId");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Pattern import priority id must not be empty");
        }
        if (PRIORITIES.putIfAbsent(id, priority) != null) {
            throw new IllegalArgumentException("Duplicate pattern import priority registration: " + id);
        }
    }

    private static void ensureClientSide() {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            throw new IllegalStateException(
                "Pattern import priorities are client-only. Register and query them from client-side code only.");
        }
    }
}
