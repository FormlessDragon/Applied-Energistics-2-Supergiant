package ae2.container;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SlotSemantics {
    private static final Map<String, SlotSemantic> REGISTRY = new ConcurrentHashMap<>();

    public static final SlotSemantic STORAGE = register("STORAGE", false);
    public static final SlotSemantic PLAYER_INVENTORY = register("PLAYER_INVENTORY", true, 2000);
    public static final SlotSemantic PLAYER_HOTBAR = register("PLAYER_HOTBAR", true, 1000);
    public static final SlotSemantic TOOLBOX = register("TOOLBOX", true, 3000);
    public static final SlotSemantic CONFIG = register("CONFIG", false);
    public static final SlotSemantic BLANK_PATTERN = register("BLANK_PATTERN", false);
    public static final SlotSemantic ENCODED_PATTERN = register("ENCODED_PATTERN", false);
    public static final SlotSemantic PROCESSING_INPUTS = register("PROCESSING_INPUTS", false);
    public static final SlotSemantic PROCESSING_OUTPUTS = register("PROCESSING_OUTPUTS", false);
    public static final SlotSemantic VIEW_CELL = register("VIEW_CELL", false);
    public static final SlotSemantic MAGNET_PICKUP_CONFIG = register("MAGNET_PICKUP_CONFIG", false);
    public static final SlotSemantic MAGNET_INSERT_CONFIG = register("MAGNET_INSERT_CONFIG", false);
    public static final SlotSemantic PORTABLE_CELL_PICKUP_FILTER = register("PORTABLE_CELL_PICKUP_FILTER", false);
    public static final SlotSemantic WIRELESS_SINGULARITY = register("WIRELESS_SINGULARITY", false);
    public static final SlotSemantic UPGRADE = register("UPGRADE", false, 100);
    public static final SlotSemantic STORAGE_CELL = register("STORAGE_CELL", false);
    public static final SlotSemantic MACHINE_INPUT = register("MACHINE_INPUT", false);
    public static final SlotSemantic MACHINE_OUTPUT = register("MACHINE_OUTPUT", false);
    public static final SlotSemantic CANER_CONTENT = register("CANER_CONTENT", false);
    public static final SlotSemantic CANER_CONTAINER = register("CANER_CONTAINER", false);
    public static final SlotSemantic INSCRIBER_PLATE_TOP = register("INSCRIBER_PLATE_TOP", false);
    public static final SlotSemantic INSCRIBER_PLATE_BOTTOM = register("INSCRIBER_PLATE_BOTTOM", false);
    public static final SlotSemantic MACHINE_CRAFTING_GRID = register("MACHINE_CRAFTING_GRID", false);
    public static final SlotSemantic CRAFTING_GRID = register("CRAFTING_GRID", true);
    public static final SlotSemantic CRAFTING_RESULT = register("CRAFTING_RESULT", false);
    public static final SlotSemantic MISSING_INGREDIENT = register("MISSING_INGREDIENT", true);
    public static final SlotSemantic REQUEST = register("REQUEST", true);

    private SlotSemantics() {
    }

    public static SlotSemantic register(String id, boolean playerSide) {
        return register(id, playerSide, 0);
    }

    public static SlotSemantic register(String id, boolean playerSide, int quickMovePriority) {
        SlotSemantic semantic = new SlotSemantic(id, playerSide, quickMovePriority);
        SlotSemantic existing = REGISTRY.putIfAbsent(semantic.id(), semantic);
        if (existing != null) {
            throw new IllegalArgumentException("Semantic with id " + semantic.id() + " was already registered");
        }
        return semantic;
    }

    public static SlotSemantic getOrThrow(String key) {
        SlotSemantic semantic = REGISTRY.get(key);
        if (semantic == null) {
            throw new IllegalArgumentException("Unknown slot semantic: " + key);
        }
        return semantic;
    }

    @Nullable
    public static SlotSemantic get(String key) {
        return REGISTRY.get(key);
    }
}
