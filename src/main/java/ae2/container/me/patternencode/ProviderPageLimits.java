package ae2.container.me.patternencode;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Shared bounds for provider-directory page requests and responses.
 */
public final class ProviderPageLimits {
    public static final int PAGE_SIZE = 25;
    public static final int MAX_PACKET_BYTES = 256 * 1024;
    public static final int MAX_QUERY_UTF16_LENGTH = 256;
    public static final int MAX_QUERY_UTF8_BYTES = 1024;
    public static final int MAX_PROVIDER_NAME_UTF16_LENGTH = 256;
    public static final int MAX_PROVIDER_NAME_UTF8_BYTES = 1024;
    public static final int MAX_ICON_BYTES = 4096;

    private ProviderPageLimits() {
    }

    public static String requireBoundedText(String fieldName, String value, int maxUtf16Length, int maxUtf8Bytes) {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(value, fieldName);
        if (maxUtf16Length < 0 || maxUtf8Bytes < 0) {
            throw new IllegalArgumentException("Text limits must not be negative");
        }
        if (value.length() > maxUtf16Length) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxUtf16Length + " UTF-16 characters");
        }
        int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > maxUtf8Bytes) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxUtf8Bytes + " UTF-8 bytes");
        }
        return value;
    }
}
