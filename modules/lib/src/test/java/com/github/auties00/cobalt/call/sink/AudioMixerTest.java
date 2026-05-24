package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AudioMixer}.
 */
class AudioMixerTest {
    /**
     * Two peers' frames sum at sample boundaries.
     */
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

    /**
     * Sums clip at int16 boundaries.
     */
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

    /**
     * Removing a peer drops their queue without producing a
     * mixed frame for it.
     */
    @Test
    void removePeerDropsQueue() throws InterruptedException {
        var mixer = new AudioMixer();
        var aSink = mixer.addPeer("A");
        mixer.addPeer("B");
        aSink.write(new AudioFrame(new short[]{42}, 0L));
        // Without B contributing, no mix is emitted yet.
        // Remove B and write another A frame — now A is the sole
        // peer, the queue assembles.
        mixer.removePeer("B");
        aSink.write(new AudioFrame(new short[]{43}, 10L));
        var mixed = mixer.mixedOutput().next();
        assertEquals(42, mixed.pcm()[0]);
    }

    /**
     * Null peer key is rejected.
     */
    @Test
    void rejectsNullPeerKey() {
        var mixer = new AudioMixer();
        assertThrows(NullPointerException.class, () -> mixer.addPeer(null));
        assertThrows(NullPointerException.class, () -> mixer.removePeer(null));
    }
}
