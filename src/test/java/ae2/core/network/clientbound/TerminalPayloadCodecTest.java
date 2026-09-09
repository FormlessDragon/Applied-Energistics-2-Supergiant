package ae2.core.network.clientbound;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalPayloadCodecTest {
    @Test
    void adaptiveCompressionPreservesBothCompressibleAndRandomPayloads() {
        for (boolean random : new boolean[]{false, true}) {
            byte[] input = new byte[512 * 1024];
            if (random) {
                new Random(1).nextBytes(input);
            }
            var encoded = TerminalPayloadCodec.encode(input, input.length);
            assertEquals(!random, encoded.compressed());
            assertArrayEquals(input, TerminalPayloadCodec.decode(encoded.compressed(), input.length,
                encoded.payload(), input.length));
        }
    }

    @Test
    void truncatedTrailingAndIncorrectlySizedCompressedPayloadsAreRejected() {
        var input = new byte[16384];
        var encoded = TerminalPayloadCodec.encode(input, input.length);
        var payload = encoded.payload();
        assertTrue(encoded.compressed());
        assertThrows(IllegalArgumentException.class, () -> TerminalPayloadCodec.decode(true, input.length,
            Arrays.copyOf(payload, payload.length - 1), input.length));
        assertThrows(IllegalArgumentException.class, () -> TerminalPayloadCodec.decode(true, input.length,
            Arrays.copyOf(payload, payload.length + 1), input.length));
        assertThrows(IllegalArgumentException.class, () -> TerminalPayloadCodec.decode(true, input.length - 1,
            payload, input.length));
    }
}
