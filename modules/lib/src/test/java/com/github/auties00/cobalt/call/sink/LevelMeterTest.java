package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LevelMeter}.
 */
class LevelMeterTest {
    /**
     * Snapshots track peak / rms / frame count.
     */
    @Test
    void measuresLevels() throws InterruptedException {
        var meter = new LevelMeter();
        var pcm = new short[]{0, 16384, 0, -16384, 0};
        meter.write(new AudioFrame(pcm, 0L));
        var snap = meter.snapshot();
        assertEquals(1, snap.frameCount());
        assertEquals(0.5, snap.peak(), 0.001);
        assertTrue(snap.rms() > 0);
    }

    /**
     * Frames are forwarded to the downstream sink when one is
     * provided.
     */
    @Test
    void forwardsToDownstream() throws InterruptedException {
        var captured = new ArrayList<AudioFrame>();
        AudioSink downstream = captured::add;
        var meter = new LevelMeter(downstream);
        meter.write(new AudioFrame(new short[]{1, 2, 3}, 4L));
        assertEquals(1, captured.size());
        assertEquals(4L, captured.getFirst().ptsMs());
    }

    /**
     * Empty frames don't increment the frame count.
     */
    @Test
    void emptyFrameDoesNotCount() throws InterruptedException {
        var meter = new LevelMeter();
        meter.write(new AudioFrame(new short[0], 0L));
        assertEquals(0, meter.snapshot().frameCount());
    }

    /**
     * Null frame is rejected.
     */
    @Test
    void rejectsNullFrame() {
        var meter = new LevelMeter();
        assertThrows(NullPointerException.class, () -> meter.write(null));
    }
}
