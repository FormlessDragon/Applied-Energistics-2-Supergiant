package ae2.container.me.patternencode;

import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Server-bound request parameters for one filtered provider-directory page.
 *
 * @param nonce identifies the current client request generation
 * @param query provider name, location or recipe UID search text
 * @param page  zero-based page index
 * @param focus optional provider identity to promote on page zero across directory revisions
 */
public record ProviderDirectoryPageRequest(Long nonce, String query, Integer page, @Nullable Focus focus) {
    public ProviderDirectoryPageRequest {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(page, "page");
        if (nonce <= 0) {
            throw new IllegalArgumentException("Provider directory request nonce must be positive");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Provider directory request page must not be negative");
        }
        if (focus != null && page != 0) {
            throw new IllegalArgumentException("Provider directory focus is only valid for the first page");
        }
        query = ProviderPageLimits.requireBoundedText(
            "provider directory query",
            Objects.requireNonNull(query, "query").trim(),
            ProviderPageLimits.MAX_QUERY_UTF16_LENGTH,
            ProviderPageLimits.MAX_QUERY_UTF8_BYTES);
    }

    /**
     * Stable and transient identities for a provider that the client is currently presenting.
     * <p>
     * The transient id is preferred while it remains valid. The location allows the server to recover the same
     * provider after a directory rebuild assigns it a new id. Boxed components deliberately reject fields omitted by
     * deserialization.
     *
     * @param providerId transient provider id from the last directory page
     * @param dimension  provider dimension id
     * @param position   packed provider block position
     * @param side       provider side ordinal, or {@code -1} when no side applies
     */
    public record Focus(Long providerId, Integer dimension, Long position, Integer side) {
        public Focus {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(side, "side");
            if (providerId < 0) {
                throw new IllegalArgumentException("Provider directory focus id must not be negative");
            }
            if (side < -1 || side >= EnumFacing.VALUES.length) {
                throw new IllegalArgumentException("Invalid provider directory focus side: " + side);
            }
        }
    }
}
