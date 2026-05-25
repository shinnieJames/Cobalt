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
 * Captures call audio from an operating-system microphone as an {@link AudioSource}.
 *
 * <p>Opens a {@link TargetDataLine} on the configured (or JVM-default) mixer and reads signed
 * 16-bit little-endian PCM from it. The default profile captures 16 kHz mono in 160-sample frames,
 * which is exactly the format the call wire protocol expects, so a default microphone needs no
 * resampling when the host supports the rate (most do). A custom {@link #Microphone(int, int, Mixer.Info)}
 * may request another rate, frame size, or specific mixer; capturing at a rate other than 16 kHz
 * still produces frames at the requested geometry but the caller is then responsible for
 * downsampling before the frames reach the call, as {@link AudioFrame} documents.
 *
 * <p>The capture line must be acquired with {@link #open()} before the first {@link #next()} call
 * and released with {@link #close()} afterwards. Each {@link #next()} blocks until a full frame's
 * worth of samples has been read from the line, then returns it with a monotonically increasing
 * presentation timestamp.
 *
 * @apiNote Wire this source into a call to transmit live microphone audio. Prefer the no-argument
 * {@link #Microphone()} constructor: it matches the call profile and needs no resampling. Always
 * use the source inside a {@code try-with-resources} (or call {@link #close()} explicitly) so the
 * operating-system capture line is released; an unclosed line stays held against other applications.
 * The single channel is intentional, because most platforms expose the default microphone as mono
 * and downmix or select one channel of a stereo device.
 */
public final class Microphone implements AudioSource, AutoCloseable {
    /**
     * Holds the default capture sample rate, in Hz, matching the call wire profile.
     *
     * @implNote This implementation uses 16000, the rate WhatsApp's Opus call configuration runs at,
     * so a default-profile microphone feeds the encoder without resampling.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    /**
     * Holds the default sample count per emitted frame.
     *
     * @implNote This implementation uses 160, which is 10 ms at {@link #DEFAULT_SAMPLE_RATE}, the
     * frame cadence the call layer consumes.
     */
    public static final int DEFAULT_FRAME_SIZE = 160;

    /**
     * Holds the sample rate, in Hz, of the underlying capture line.
     */
    private final int sampleRate;

    /**
     * Holds the number of samples in each emitted frame.
     */
    private final int frameSize;

    /**
     * Holds the duration of one frame in milliseconds, derived from {@link #frameSize} and
     * {@link #sampleRate} and cached for presentation-timestamp arithmetic.
     */
    private final long frameDurationMs;

    /**
     * Holds the mixer to open the capture line on, or {@code null} to use the JVM-default
     * microphone.
     */
    private final Mixer.Info preferredMixer;

    /**
     * Holds the reusable byte buffer that one frame is read into before conversion to PCM samples.
     *
     * <p>Sized to {@code frameSize * 2} bytes (two bytes per signed 16-bit sample) and allocated by
     * {@link #open()}.
     */
    private byte[] readBuffer;

    /**
     * Holds the opened capture line, or {@code null} before {@link #open()} and after
     * {@link #close()}.
     */
    private TargetDataLine line;

    /**
     * Holds the presentation timestamp, in milliseconds, assigned to the next emitted frame.
     */
    private long ptsMs;

    /**
     * Constructs a microphone for the default call profile.
     *
     * <p>Equivalent to {@link #Microphone(int, int, Mixer.Info)} with {@link #DEFAULT_SAMPLE_RATE},
     * {@link #DEFAULT_FRAME_SIZE}, and a {@code null} mixer, so it captures 16 kHz mono in 160-sample
     * frames from the JVM-default microphone.
     */
    public Microphone() {
        this(DEFAULT_SAMPLE_RATE, DEFAULT_FRAME_SIZE, null);
    }

    /**
     * Constructs a microphone with an explicit capture format and mixer.
     *
     * <p>The frame duration is computed once as {@code 1000 * frameSize / sampleRate} milliseconds
     * and used to advance each frame's presentation timestamp. No capture line is acquired until
     * {@link #open()} is called.
     *
     * @param sampleRate     the capture sample rate in Hz (for example {@code 16000} or {@code 48000})
     * @param frameSize      the number of samples per emitted frame
     * @param preferredMixer the specific mixer to open the line on, or {@code null} for the JVM default
     * @throws IllegalArgumentException if {@code sampleRate} or {@code frameSize} is less than {@code 1}
     */
    public Microphone(int sampleRate, int frameSize, Mixer.Info preferredMixer) {
        if (sampleRate < 1) {
            throw new IllegalArgumentException("sampleRate must be >= 1");
        }
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be >= 1");
        }
        this.sampleRate = sampleRate;
        this.frameSize = frameSize;
        this.frameDurationMs = 1000L * frameSize / sampleRate;
        this.preferredMixer = preferredMixer;
    }

    /**
     * Opens the underlying capture line and begins capturing.
     *
     * <p>Acquires a {@link TargetDataLine} matching the configured signed 16-bit mono format from
     * the preferred mixer when one is set, or from the JVM-default device otherwise, opens it with a
     * buffer holding several frames, and starts it. Allocates the reusable read buffer. Returns
     * immediately if the line is already open, so the call is idempotent. Must be invoked before the
     * first {@link #next()}.
     *
     * @throws LineUnavailableException if no line compatible with the requested format is available
     *                                  on the running platform, or the device is in use
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

    /**
     * {@inheritDoc}
     *
     * <p>Reads from the open capture line until a full frame's worth of bytes is buffered, decodes
     * them as little-endian signed 16-bit samples, and returns them with the next presentation
     * timestamp. Returns {@code null} only when the line reports end-of-input.
     *
     * @return {@inheritDoc}
     * @throws InterruptedException  {@inheritDoc}
     * @throws IllegalStateException if {@link #open()} has not been called, or the line was closed
     */
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

    /**
     * Stops and releases the capture line.
     *
     * <p>Stops, then closes, the underlying line and clears the reference so the device is released
     * back to the operating system. Returns immediately if no line is open, and any failure while
     * stopping or closing is swallowed, so the call is idempotent and never throws.
     */
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
