package ae2.crafting.pattern;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Defines the canonical validation contract for processing-pattern recipe type identifiers.
 */
public final class RecipeTypeUid {
    public static final int MAX_UTF16_LENGTH = 256;
    public static final int MAX_UTF8_BYTES = 1024;

    private RecipeTypeUid() {
    }

    /**
     * Normalizes an untrusted recipe type identifier.
     *
     * @param candidate identifier received from an external boundary, or {@code null} when absent
     * @return the trimmed identifier when valid; otherwise {@code null}
     */
    @Nullable
    public static String normalize(@Nullable String candidate) {
        if (candidate == null) {
            return null;
        }

        String normalized = candidate.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_UTF16_LENGTH) {
            return null;
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return null;
        }
        return normalized;
    }

    /**
     * Normalizes an identifier that must be valid before it enters internal state or persistence.
     *
     * @param candidate required recipe type identifier
     * @return the canonical trimmed identifier
     * @throws NullPointerException     if the identifier is {@code null}
     * @throws IllegalArgumentException if the identifier is blank or exceeds either size limit
     */
    public static String requireValid(String candidate) {
        Objects.requireNonNull(candidate, "recipe type UID");
        String normalized = normalize(candidate);
        if (normalized == null) {
            throw new IllegalArgumentException("Invalid recipe type UID");
        }
        return normalized;
    }
}
