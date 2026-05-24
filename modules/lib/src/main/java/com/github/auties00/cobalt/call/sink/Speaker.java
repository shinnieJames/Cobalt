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
 * An {@link AudioSink} backed by the OS speaker. Plays 16-bit
 * signed PCM at 16 kHz mono by default — the Cobalt call wire
 * format. If the call decoder emits at a different rate, wrap
 * this in a
 * {@link com.github.auties00.cobalt.call.filter.ResamplingAudioSink}.
 *
 * <p>Mono only — the OS auto-spreads to whatever channels the
 * physical speaker has.
 */
public final class Speaker implements AudioSink, AutoCloseable {
    /**
     * Default playback sample rate matching the WhatsApp wire
     * profile.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Sample rate of the underlying playback line.
     */
    private final int sampleRate;

    /**
     * Optional mixer override, or {@code null} for JVM default.
     */
    private final Mixer.Info preferredMixer;

    /**
     * The opened line — null before {@link #open()} or after
     * {@link #close()}.
     */
    private SourceDataLine line;

    /**
     * Constructs a speaker for the WhatsApp default profile.
     */
    public Speaker() {
        this(DEFAULT_SAMPLE_RATE, null);
    }

    /**
     * Constructs a speaker with explicit format and mixer.
     *
     * @param sampleRate     sample rate in Hz
     * @param preferredMixer specific mixer to use, or {@code null}
     *                       for the JVM default
     * @throws IllegalArgumentException if {@code sampleRate} is
     *                                  &lt; 1
     */
    public Speaker(int sampleRate, Mixer.Info preferredMixer) {
        if (sampleRate < 1) {
            throw new IllegalArgumentException("sampleRate must be ≥ 1");
        }
        this.sampleRate = sampleRate;
        this.preferredMixer = preferredMixer;
    }

    /**
     * Opens the underlying playback line and starts playback.
     * Must be called before {@link #write(AudioFrame)}.
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
