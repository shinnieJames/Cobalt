package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MuteSwitch}.
 */
class MuteSwitchTest {
    /**
     * Unmuted: passes the underlying frame through unchanged.
     */
    @Test
    void passThroughWhenUnmuted() throws InterruptedException {
        var inner = constantSource(new short[]{10, 20, 30}, 5L);
        var ms = new MuteSwitch(inner);
        var got = ms.next();
        assertArrayEquals(new short[]{10, 20, 30}, got.pcm());
        assertEquals(5L, got.ptsMs());
        assertFalse(ms.muted());
    }

    /**
     * Muted: returns a zeroed frame with the same length and pts.
     */
    @Test
    void zeroPcmWhenMuted() throws InterruptedException {
        var inner = constantSource(new short[]{10, 20, 30}, 7L);
        var ms = new MuteSwitch(inner);
        ms.setMuted(true);
        assertTrue(ms.muted());
        var got = ms.next();
        assertArrayEquals(new short[]{0, 0, 0}, got.pcm());
        assertEquals(7L, got.ptsMs());
    }

    /**
     * End-of-stream sentinel propagates regardless of mute state.
     */
    @Test
    void propagatesNullEndOfStream() throws InterruptedException {
        AudioSource inner = () -> null;
        var ms = new MuteSwitch(inner);
        ms.setMuted(true);
        assertNull(ms.next());
    }

    /**
     * Constructor rejects a null delegate.
     */
    @Test
    void rejectsNullDelegate() {
        assertThrows(NullPointerException.class, () -> new MuteSwitch(null));
    }

    /**
     * Returns a source that always produces the same frame.
     *
     * @param pcm   payload
     * @param ptsMs timestamp
     * @return the source
     */
    private static AudioSource constantSource(short[] pcm, long ptsMs) {
        return () -> new AudioFrame(pcm.clone(), ptsMs);
    }
}
