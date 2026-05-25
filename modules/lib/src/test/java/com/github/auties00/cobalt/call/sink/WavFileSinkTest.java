package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link WavFileSink}: emitting a valid 44-byte WAV header with chunk sizes patched in on
 * close, idempotent close, and rejection of writes after close.
 */
class WavFileSinkTest {
    @Test
    void writesValidWavHeader(@TempDir Path tmp) throws IOException, InterruptedException {
        var path = tmp.resolve("out.wav");
        try (var sink = new WavFileSink(path)) {
            sink.write(new AudioFrame(new short[]{1, 2, 3, 4}, 0L));
            sink.write(new AudioFrame(new short[]{5, 6, 7, 8}, 10L));
        }
        var bytes = Files.readAllBytes(path);
        // 44-byte header + 8 samples * 2 bytes each
        assertEquals(44 + 16, bytes.length);
        assertEquals('R', bytes[0]);
        assertEquals('I', bytes[1]);
        assertEquals('F', bytes[2]);
        assertEquals('F', bytes[3]);
        // ChunkSize at offset 4 = 36 + 16 = 52
        var bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(52, bb.getInt(4));
        // Subchunk2Size at offset 40 = 16
        assertEquals(16, bb.getInt(40));
        // Sample data round-trips
        var samples = new short[8];
        bb.position(44);
        bb.asShortBuffer().get(samples);
        assertArrayEquals(new short[]{1, 2, 3, 4, 5, 6, 7, 8}, samples);
    }

    @Test
    void closeIsIdempotent(@TempDir Path tmp) {
        var sink = new WavFileSink(tmp.resolve("idempotent.wav"));
        sink.close();
        sink.close();
    }

    @Test
    void writeAfterCloseThrows(@TempDir Path tmp) {
        var sink = new WavFileSink(tmp.resolve("closed.wav"));
        sink.close();
        assertThrows(IllegalStateException.class,
                () -> sink.write(new AudioFrame(new short[]{0}, 0L)));
    }
}
