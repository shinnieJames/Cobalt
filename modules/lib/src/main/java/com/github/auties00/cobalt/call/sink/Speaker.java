package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Plays call audio through the operating system's speaker.
 *
 * <p>This sink renders signed 16-bit mono PCM through a {@link SourceDataLine}, defaulting to the
 * 16 kHz wire rate of a Cobalt call. The line must be opened with {@link #open()} before frames are
 * written; each {@link #write(AudioFrame)} converts the frame's samples to little-endian bytes and
 * pushes them to the line, blocking while the line's buffer is full so playback paces the call.
 * Closing drains and releases the line. The sink is mono only; the operating system spreads the
 * single channel across whatever channels the physical speaker has.
 *
 * @apiNote Wire this in as the inbound sink of a call to hear the remote participant. If the call
 * decoder emits at a rate other than the default, wrap this sink in a
 * {@link com.github.auties00.cobalt.call.filter.ResamplingAudioSink} so the samples reach the line at
 * the rate it was opened with, otherwise audio plays at the wrong pitch.
 */
public final class Speaker implements AudioSink, AutoCloseable {
    /**
     * Names the default playback sample rate, matching the WhatsApp call wire profile of 16 kHz.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Holds the sample rate, in hertz, the playback line is opened at.
     */
    private final int sampleRate;

    /**
     * Holds the mixer to acquire the line from, or {@code null} to use the JVM default output device.
     */
    private final Mixer.Info preferredMixer;

    /**
     * Holds the opened playback line, or {@code null} before {@link #open()} and after
     * {@link #close()}.
     */
    private SourceDataLine line;

    /**
     * Constructs a speaker for the WhatsApp default profile on the JVM default output device.
     */
    public Speaker() {
        this(DEFAULT_SAMPLE_RATE, null);
    }

    /**
     * Constructs a speaker with an explicit sample rate and output device.
     *
     * @param sampleRate     the playback sample rate in hertz; must be at least 1
     * @param preferredMixer the mixer to acquire the line from, or {@code null} for the JVM default
     *                       output device
     * @throws IllegalArgumentException if {@code sampleRate} is less than 1
     */
    public Speaker(int sampleRate, Mixer.Info preferredMixer) {
        if (sampleRate < 1) {
            throw new IllegalArgumentException("sampleRate must be ≥ 1");
        }
        this.sampleRate = sampleRate;
        this.preferredMixer = preferredMixer;
    }

    /**
     * Opens the playback line and starts playback.
     *
     * <p>Acquires a signed 16-bit mono line at the configured sample rate, from the preferred mixer
     * when one was supplied or the JVM default device otherwise, opens it, and starts it so written
     * frames play immediately. Calling this when the line is already open does nothing, so repeated
     * calls are harmless. {@link #write(AudioFrame)} requires the line to have been opened first.
     *
     * @throws LineUnavailableException if no compatible line is available on the running platform
     * @implNote This implementation sizes the line buffer at one tenth of a second of samples, that
     * is {@code sampleRate / 10} frames of two bytes each, trading a small latency floor for
     * resilience to scheduling jitter on the writing thread.
     */
    public synchronized void open() throws LineUnavailableException {
        if (line != null) {
            return;
        }
        var format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, 1, 2, sampleRate, false);
        var info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine acquired;
        if (preferredMixer != null) {
            acquired = (SourceDataLine) AudioSystem.getMixer(preferredMixer).getLine(info);
        } else {
            acquired = (SourceDataLine) AudioSystem.getLine(info);
        }
        acquired.open(format, sampleRate / 10 * 2);
        acquired.start();
        this.line = acquired;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the frame's samples to little-endian bytes and writes them to the playback line,
     * blocking while the line buffer is full so playback paces the producer.
     *
     * @throws IllegalStateException if the line has not been opened with {@link #open()}
     */
    @Override
    public void write(AudioFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        var l = line;
        if (l == null) {
            throw new IllegalStateException("Speaker not opened");
        }
        var pcm = frame.pcm();
        var bytes = new byte[pcm.length * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().put(pcm);
        l.write(bytes, 0, bytes.length);
    }

    /**
     * Drains pending samples, stops the line, and releases it.
     *
     * <p>Drains so buffered audio finishes playing, then stops and closes the line and clears the
     * reference. Each step ignores failures so a fault in one does not leak the line. Closing when no
     * line is open does nothing, so this is idempotent.
     */
    @Override
    public synchronized void close() {
        var l = line;
        if (l == null) {
            return;
        }
        try {
            l.drain();
        } catch (Throwable ignored) {
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
