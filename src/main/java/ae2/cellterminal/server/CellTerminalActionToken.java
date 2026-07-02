package ae2.cellterminal.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Confirmation token created during preview and required during execute.
 *
 * @param value Opaque token value.
 */
public record CellTerminalActionToken(String value) {
    public CellTerminalActionToken {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be empty");
        }
    }

    /**
     * Generates a token for one preview plan signature.
     *
     * @param operationId   Operation id.
     * @param contextId     Effective Cell Terminal context id.
     * @param planSignature Deterministic plan signature.
     * @return New confirmation token.
     */
    public static CellTerminalActionToken create(String operationId, String contextId, String planSignature) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(planSignature, "planSignature");
        String nonce = UUID.randomUUID().toString();
        return new CellTerminalActionToken(sha256(operationId + '\n' + contextId + '\n' + planSignature + '\n' + nonce));
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for Cell Terminal action tokens", e);
        }
    }
}
