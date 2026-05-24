package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioSource} backed by the OS microphone. Captures
 * 16 kHz mono signed-16-bit PCM by default — the exact profile
 * Cobalt's call wire uses, so no resampling is required when the
 * OS supports the rate (most do).
 *
 * <p>Lifecycle: {@link #open()} acquires the OS mic line;
 * {@link #close()} releases it. Use with try-with-resources.
 *
 * <p>Mono only — most platforms expose the default mic as mono;
 * the OS picks one channel of a stereo mic by mixing or selecting.
 */
public final class Microphone implements AudioSource, AutoCloseable {
    /**
     * Default capture sample rate matching the WhatsApp wire profile.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Default samples per emitted frame (10 ms at 16 kHz).
     */
    public static final int DEFAULT_FRAME_SIZE = 160;

    /**
     * Sample rate of the underlying capture line.
     */
    private final int sampleRate;

    /**
     * Samples per emitted frame.
     */
    private final int frameSize;

    /**
     * Frame duration in ms, cached for pts arithmetic.
     */
    private final long frameDurationMs;

    /**
     * Optional mixer override; the JVM-default mic is used when
     * {@code null}.
     */
    private final Mixer.Info preferredMixer;

    /**
     * Reusable read buffer for the line.
     */
    private byte[] readBuffer;

    /**
     * The opened line — {@code null} before {@link #open()} and
     * after {@link #close()}.
     */
    private TargetDataLine line;

    /**
     * Monotonic timestamp of the next frame.
     */
    private long ptsMs;

    /**
     * Constructs a microphone for the WhatsApp default profile —
     * 16 kHz mono, 160 samples per frame, JVM-default mixer.
     */
    public Microphone() {
        this(DEFAULT_SAMPLE_RATE, DEFAULT_FRAME_SIZE, null);
    }

    /**
     * Constructs a microphone with explicit format and mixer.
     *
     * @param sampleRate     sample rate in Hz (e.g. 16000, 48000)
     * @param frameSize      samples per emitted frame
     * @param preferredMixer specific mixer to use, or {@code null}
     *                       for the JVM default
     * @throws IllegalArgumentException if {@code sampleRate} or
     *                                  {@code frameSize} is &lt; 1
     */
    public Microphone(int sampleRate, int frameSize, Mixer.Info preferredMixer) {
        if (sampleRate < 1) {
            throw new IllegalArgumentException("sampleRate must be ≥ 1");
        }
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be ≥ 1");
        }
        this.sampleRate = sampleRate;
        this.frameSize = frameSize;
        this.frameDurationMs = 1000L * frameSize / sampleRate;
        this.preferredMixer = preferredMixer;
    }

    /**
     * Opens the underlying capture line and starts capture. Must
     * be called before {@link #next()}.
     *
     * @throws LineUnavailableException if no compatible line is
     *                                  available on the running
     *                                  platform
     */
    public synchronized void open() throws LineUnavailableException {
        if (line != null) {
            return;
        }
        var format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, 1, 2, sampleRate, false);
        var info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine acquired;
        if (preferredMixer != null) {
            acquired = (TargetDataLine) AudioSystem.getMixer(preferredMixer).getLine(info);
        } else {
            acquired = (TargetDataLine) AudioSystem.getLine(info);
        }
        acquired.open(format, frameSize * 2 * 4);
        acquired.start();
        this.line = acquired;
        this.readBuffer = new byte[frameSize * 2];
    }

    @Override
    public AudioFrame next() throws InterruptedException {
        var l = line;
        if (l == null) {
            throw new IllegalStateException("Microphone not opened");
        }
        var total = 0;
        while (total < readBuffer.length) {
            var n = l.read(readBuffer, total, readBuffer.length - total);
            if (n < 0) {
                return null;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            total += n;
        }
        var pcm = new short[frameSize];
        ByteBuffer.wrap(readBuffer).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(pcm);
        var pts = ptsMs;
        ptsMs += frameDurationMs;
        return new AudioFrame(pcm, pts);
    }

    @Override
    public synchronized void close() {
        var l = line;
        if (l == null) {
            return;
        }
        try {
            l.stop();
        } catch (Throwable ignored) {
        }
        try {
            l.close();
        } catch (Throwable ignored) {
        }
        line = null;
    }
}
