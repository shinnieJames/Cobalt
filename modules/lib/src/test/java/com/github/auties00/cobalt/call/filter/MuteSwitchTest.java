package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link MuteSwitch} gating over a delegate {@link AudioSource}: frames pass through unchanged
 * while unmuted, are zeroed (keeping length and pts) while muted, and the end-of-stream sentinel
 * propagates in either state. {@code constantSource} supplies a fixed frame on every pull.
 */
class MuteSwitchTest {
    @Test
    @DisplayName("Unmuted: passes the underlying frame through unchanged")
    void passThroughWhenUnmuted() throws InterruptedException {
        var inner = constantSource(new short[]{10, 20, 30}, 5L);
        var ms = new MuteSwitch(inner);
        var got = ms.next();
        assertArrayEquals(new short[]{10, 20, 30}, got.pcm());
        assertEquals(5L, got.ptsMs());
        assertFalse(ms.muted());
    }

    @Test
    @DisplayName("Muted: returns a zeroed frame with the same length and pts")
    void zeroPcmWhenMuted() throws InterruptedException {
        var inner = constantSource(new short[]{10, 20, 30}, 7L);
        var ms = new MuteSwitch(inner);
        ms.setMuted(true);
        assertTrue(ms.muted());
        var got = ms.next();
        assertArrayEquals(new short[]{0, 0, 0}, got.pcm());
        assertEquals(7L, got.ptsMs());
    }

    @Test
    @DisplayName("End-of-stream sentinel propagates regardless of mute state")
    void propagatesNullEndOfStream() throws InterruptedException {
        AudioSource inner = () -> null;
        var ms = new MuteSwitch(inner);
        ms.setMuted(true);
        assertNull(ms.next());
    }

    @Test
    @DisplayName("Constructor rejects a null delegate")
    void rejectsNullDelegate() {
        assertThrows(NullPointerException.class, () -> new MuteSwitch(null));
    }

    private static AudioSource constantSource(short[] pcm, long ptsMs) {
        return () -> new AudioFrame(pcm.clone(), ptsMs);
    }
}
