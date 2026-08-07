package ae2.container.me.patternencode;

import java.util.Objects;

/** Bounded container action requesting one provider mapping page from the current directory snapshot. */
public record ProviderMappingPageRequest(Long nonce, Long directoryRevision, Long providerId, Integer page) {
    public ProviderMappingPageRequest {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(directoryRevision, "directoryRevision");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(page, "page");
        if (nonce <= 0 || directoryRevision < 0 || providerId < 0 || page < 0) {
            throw new IllegalArgumentException("Invalid provider mapping page request");
        }
    }
}
