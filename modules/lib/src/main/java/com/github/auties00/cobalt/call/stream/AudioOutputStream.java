package com.github.auties00.cobalt.call.stream;

import com.github.auties00.cobalt.call.stream.capture.FileAudioOutputStream;
import com.github.auties00.cobalt.call.stream.capture.MicrophoneAudioOutputStream;
import com.github.auties00.cobalt.call.stream.capture.SilenceAudioOutputStream;
import com.github.auties00.cobalt.call.stream.capture.ToneAudioOutputStream;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the local outbound audio of a call as a stream of {@link AudioFrame}s.
 *
 * <p>This is the write side of a call's audio: the application supplies the audio it wants to
 * transmit, and the call engine drains the stream, encodes each frame with Opus, and ships it to the
 * peer. There are two ways to supply frames. The application can push them itself by calling
 * {@link #write(AudioFrame)}, which is the path a bot or a call-to-call bridge uses to feed synthetic
 * or relayed audio; this base class is exactly that manually-written stream, backed by a small bounded
 * buffer. Alternatively a static factory returns a device-backed subclass, such as
 * {@link #fromMicrophone()} or {@link #fromFile(Path)}, whose {@link #take()} pulls frames straight
 * from the device and to which the application writes nothing.
 *
 * <p>Frames carry mono 16-bit PCM as described by {@link AudioFrame}. The buffered base stream is
 * internally bounded: {@link #write(AudioFrame)} blocks while the encoder has not caught up,
 * propagating backpressure to the producer rather than growing without limit. The application never
 * closes the stream; the call engine ends it when the call ends, which also releases any device a
 * subclass bound.
 *
 * @apiNote Prefer a device factory when streaming a microphone or a file, and {@link #write(AudioFrame)}
 * only when producing frames programmatically. Do not call {@link #take()} or {@link #shutdown()}: they
 * are the call engine's drain and teardown hooks, public only because the engine lives in a different
 * package.
 */
public class AudioOutputStream {
    /**
     * Holds the maximum number of buffered frames before {@link #write(AudioFrame)} blocks.
     *
     * @implNote This implementation uses 10, roughly 100 to 200 ms of audio, enough to absorb encoder
     * scheduling jitter without adding perceptible latency.
     */
    private static final int CAPACITY = 10;

    /**
     * Marks the end of the stream so the engine's {@link #take()} returns {@code null} once drained.
     *
     * @implNote This implementation is identity-comparable against real frames, which never share this
     * instance.
     */
    private static final AudioFrame SENTINEL = new AudioFrame(new short[0], Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the application producer and the engine drain.
     */
    private final LinkedBlockingDeque<AudioFrame> queue = new LinkedBlockingDeque<>(CAPACITY);

    /**
     * Guards {@link #shutdown()} so the teardown runs at most once.
     *
     * <p>Exposed to device-backed subclasses so their {@link #shutdown()} override can make the same
     * end-of-stream transition atomic and idempotent.
     */
    protected final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs an empty buffered stream with no device binding.
     *
     * <p>Device-backed subclasses in {@code com.github.auties00.cobalt.call.media.capture} invoke this
     * constructor and then override {@link #take()} to read from their device instead of the bounded
     * queue.
     */
    protected AudioOutputStream() {
    }

    /**
     * Returns a manually-written stream that the application fills with {@link #write(AudioFrame)}.
     *
     * @return a new empty buffered stream
     */
    public static AudioOutputStream buffered() {
        return new AudioOutputStream();
    }

    /**
     * Returns a stream bound to the operating-system microphone.
     *
     * <p>Each {@link #take()} captures one 16 kHz mono frame from the default microphone, blocking on
     * the capture line until a full frame is available, until the call ends and the capture line is
     * released. The application does not write to a microphone-bound stream.
     *
     * @return a microphone-bound stream
     * @throws IllegalStateException if no capture line is available on the running platform
     */
    public static AudioOutputStream fromMicrophone() {
        return new MicrophoneAudioOutputStream();
    }

    /**
     * Returns a stream that transmits the audio track of a media file.
     *
     * <p>Each {@link #take()} decodes and resamples the next 16 kHz mono frame of the file; the stream
     * ends when the file is exhausted or the call ends. Any container the bundled FFmpeg build can
     * decode is accepted.
     *
     * @param path the media file to stream
     * @return a file-bound stream
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no audio stream
     */
    public static AudioOutputStream fromFile(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return new FileAudioOutputStream(path);
    }

    /**
     * Returns a stream that transmits continuous silence.
     *
     * @return a silence-bound stream
     */
    public static AudioOutputStream silence() {
        return new SilenceAudioOutputStream();
    }

    /**
     * Returns a stream that transmits a constant sine tone, useful for diagnostics.
     *
     * @param frequencyHz the tone frequency in hertz
     * @return a tone-bound stream
     * @throws IllegalArgumentException if {@code frequencyHz} is not positive
     */
    public static AudioOutputStream tone(double frequencyHz) {
        if (frequencyHz <= 0) {
            throw new IllegalArgumentException("frequencyHz must be positive, got " + frequencyHz);
        }
        return new ToneAudioOutputStream(frequencyHz);
    }

    /**
     * Writes one frame of local audio to transmit, blocking while the encoder is behind.
     *
     * <p>The frame is queued for the call engine to encode and send. The call blocks rather than
     * dropping the frame when the internal buffer is full, propagating backpressure. After the stream
     * has ended the frame is silently discarded. A device-backed subclass fills itself through its
     * {@link #take()} override and ignores this method, since nothing writes to it.
     *
     * @param frame the frame to transmit; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for buffer space
     */
    public void write(AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (closed.get()) {
            return;
        }
        queue.putLast(frame);
    }

    /**
     * Returns the next local frame for the engine to encode, blocking until one is available, or
     * {@code null} once the stream has ended.
     *
     * <p>The buffered base stream returns frames previously supplied through {@link #write(AudioFrame)}.
     * A device-backed subclass overrides this hook to pull the next frame straight from its capture
     * device or decoder, returning {@code null} at end-of-stream.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public AudioFrame take() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * Ends the stream, unblocking the engine drain.
     *
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #take()} returns {@code null}.
     * Idempotent. A device-backed subclass overrides this hook to also release its device and unblock a
     * {@link #take()} parked on the device.
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            queue.offerFirst(SENTINEL);
        }
    }

    /**
     * Returns whether this stream captures live acoustic audio from a microphone.
     *
     * <p>A live microphone capture carries acoustic echo (the remote party's audio leaking from the
     * speaker back into the mic) and ambient noise, so the call engine conditions it with echo
     * cancellation, denoise, automatic gain control, and voice-activity detection. Every other source
     * (a media file, a synthetic tone, silence, or frames an application writes through
     * {@link #write(AudioFrame)}) is already clean line-level audio: running mic conditioning over it
     * distorts the signal (denoise strips content, AGC pumps levels, the echo canceller subtracts an
     * unrelated reference), so the engine encodes those sources without preprocessing. The buffered base
     * stream returns {@code false}; only the microphone-bound subclass overrides this to {@code true}.
     *
     * @return {@code true} if the source is a live microphone needing acoustic conditioning, otherwise
     *         {@code false}
     */
    public boolean isLiveCapture() {
        return false;
    }
}
