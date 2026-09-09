package ae2.core.network.clientbound;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes individual terminal packet payloads without relying on the server-wide Minecraft compression setting.
 */
final class TerminalPayloadCodec {
    private static final int COMPRESSION_THRESHOLD = 4 * 1024;
    private static final int MINIMUM_SAVINGS = 64;

    private TerminalPayloadCodec() {
    }

    static EncodedPayload encode(byte[] rawPayload, int maxUncompressedBytes) {
        if (rawPayload == null) {
            throw new IllegalArgumentException("Raw terminal payload must not be null");
        }
        validateMaximum(maxUncompressedBytes);
        if (rawPayload.length > maxUncompressedBytes) {
            throw new IllegalArgumentException("Raw terminal payload exceeds its limit: " + rawPayload.length);
        }
        if (rawPayload.length < COMPRESSION_THRESHOLD) {
            return new EncodedPayload(false, rawPayload.length, rawPayload);
        }

        byte[] compressed = compress(rawPayload);
        int savings = rawPayload.length - compressed.length;
        if (savings >= MINIMUM_SAVINGS && (long) savings * 8 >= rawPayload.length) {
            return new EncodedPayload(true, rawPayload.length, compressed);
        }
        return new EncodedPayload(false, rawPayload.length, rawPayload);
    }

    static byte[] decode(boolean compressed, int uncompressedLength, byte[] payload, int maxUncompressedBytes) {
        validateMaximum(maxUncompressedBytes);
        if (uncompressedLength < 0 || uncompressedLength > maxUncompressedBytes) {
            throw new IllegalArgumentException("Invalid terminal payload uncompressed length: " + uncompressedLength);
        }
        if (payload == null || payload.length > maxUncompressedBytes) {
            throw new IllegalArgumentException("Invalid terminal payload encoded length");
        }
        if (!compressed) {
            if (payload.length != uncompressedLength) {
                throw new IllegalArgumentException("Uncompressed terminal payload length does not match its declaration");
            }
            return payload;
        }

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(payload);
            byte[] result = new byte[uncompressedLength];
            int written = 0;
            while (!inflater.finished()) {
                if (written == result.length) {
                    int overflow = inflater.inflate(new byte[1]);
                    if (overflow > 0) {
                        throw new IllegalArgumentException("Compressed terminal payload exceeds its declared length");
                    }
                    if (inflater.finished()) {
                        break;
                    }
                    if (inflater.needsDictionary()) {
                        throw new IllegalArgumentException("Compressed terminal payload requires an unsupported dictionary");
                    }
                    if (inflater.needsInput()) {
                        throw new IllegalArgumentException("Compressed terminal payload is truncated");
                    }
                    throw new IllegalArgumentException("Compressed terminal payload made no decoding progress");
                }

                int read = inflater.inflate(result, written, result.length - written);
                if (read > 0) {
                    written += read;
                    continue;
                }
                if (inflater.needsDictionary()) {
                    throw new IllegalArgumentException("Compressed terminal payload requires an unsupported dictionary");
                }
                if (inflater.needsInput()) {
                    throw new IllegalArgumentException("Compressed terminal payload is truncated");
                }
                throw new IllegalArgumentException("Compressed terminal payload made no decoding progress");
            }
            if (written != uncompressedLength) {
                throw new IllegalArgumentException("Compressed terminal payload length does not match its declaration");
            }
            if (inflater.getRemaining() != 0) {
                throw new IllegalArgumentException("Compressed terminal payload has trailing bytes");
            }
            return result;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Invalid compressed terminal payload", e);
        } finally {
            inflater.end();
        }
    }

    private static byte[] compress(byte[] rawPayload) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(rawPayload);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, rawPayload.length));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                if (written <= 0) {
                    throw new IllegalStateException("Terminal payload compressor made no progress");
                }
                output.write(buffer, 0, written);
                if ((long) output.size() * 8 > (long) rawPayload.length * 7
                    || output.size() > rawPayload.length - MINIMUM_SAVINGS) {
                    return rawPayload;
                }
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void validateMaximum(int maxUncompressedBytes) {
        if (maxUncompressedBytes < 0) {
            throw new IllegalArgumentException("Terminal payload maximum length must not be negative");
        }
    }

    record EncodedPayload(boolean compressed, int uncompressedLength, byte[] payload) {
    }
}
