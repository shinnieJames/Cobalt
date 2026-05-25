package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link AudioMixer}: per-sample summing of concurrent peer streams, int16 clipping, peer
 * add/remove lifecycle, and null-key rejection.
 */
class AudioMixerTest {
    @Test
    void mixesTwoPeers() throws InterruptedException {
        var mixer = new AudioMixer();
        var aSink = mixer.addPeer("A");
        var bSink = mixer.addPeer("B");

        aSink.write(new AudioFrame(new short[]{100, 200, 300}, 0L));
        bSink.write(new AudioFrame(new short[]{50, 50, 50}, 0L));

        var mixed = mixer.mixedOutput().next();
        assertNotNull(mixed);
        assertEquals(150, mixed.pcm()[0]);
        assertEquals(250, mixed.pcm()[1]);
        assertEquals(350, mixed.pcm()[2]);
        assertEquals(0L, mixed.ptsMs());
        assertEquals(1, mixer.mixedFrameCount());
    }

    @Test
    void clipsAtInt16() throws InterruptedException {
        var mixer = new AudioMixer();
        var aSink = mixer.addPeer("A");
        var bSink = mixer.addPeer("B");
        aSink.write(new AudioFrame(new short[]{Short.MAX_VALUE}, 0L));
        bSink.write(new AudioFrame(new short[]{Short.MAX_VALUE}, 0L));
        var mixed = mixer.mixedOutput().next();
        assertEquals(Short.MAX_VALUE, mixed.pcm()[0]);
    }

    @Test
    void removePeerDropsQueue() throws InterruptedException {
        var mixer = new AudioMixer();
        var aSink = mixer.addPeer("A");
        mixer.addPeer("B");
        aSink.write(new AudioFrame(new short[]{42}, 0L));
        // A mix is emitted only once every active peer has contributed a frame; with B silent,
        // removing B leaves A as the sole peer so its queued frame can assemble.
        mixer.removePeer("B");
        aSink.write(new AudioFrame(new short[]{43}, 10L));
        var mixed = mixer.mixedOutput().next();
        assertEquals(42, mixed.pcm()[0]);
    }

    @Test
    void rejectsNullPeerKey() {
        var mixer = new AudioMixer();
        assertThrows(NullPointerException.class, () -> mixer.addPeer(null));
        assertThrows(NullPointerException.class, () -> mixer.removePeer(null));
    }
}
