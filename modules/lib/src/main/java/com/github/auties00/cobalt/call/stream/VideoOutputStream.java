package com.github.auties00.cobalt.call.stream;

import com.github.auties00.cobalt.call.stream.capture.CameraVideoOutputStream;
import com.github.auties00.cobalt.call.stream.capture.FileVideoOutputStream;
import com.github.auties00.cobalt.call.stream.capture.ScreenVideoOutputStream;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the local outbound video of a call as a stream of {@link VideoFrame}s, together with the
 * geometry the call engine encodes and advertises that video at.
 *
 * <p>This is the write side of a call's video: the application supplies the picture it wants to
 * transmit and the call engine drains the stream, encodes each frame with H.264 or VP8 at this
 * stream's {@link #width()} by {@link #height()} at {@link #fps()} and {@link #bitrateBps()}, and ships
 * it to the peer. The application can push frames with {@link #write(VideoFrame)} for programmatic
 * sources, which is what this buffered base class is; or a static factory returns a device-backed
 * subclass whose {@link #take()} pulls frames straight from the device: {@link #fromCamera()} for a
 * webcam, {@link #fromScreen()} for screen sharing, or {@link #fromFile(Path)} for a media file.
 *
 * <p>The presence of this stream on a placed or accepted call is what makes the call a video call;
 * passing no video stream makes it audio-only. The geometry is fixed at construction: the factories
 * default to 640x480 at 30 fps with a {@linkplain #defaultBitrate(int, int, int) pixel-derived}
 * bitrate, and explicit-geometry overloads accept any even resolution of at least 2, frame rate of at
 * least 1, and bitrate of at least 1.
 *
 * <p>Frames carry I420 planar pixels as described by {@link VideoFrame}; the resolution carried by an
 * individual frame may differ from this stream's advertised {@link #width()} by {@link #height()}. The
 * buffered base stream is internally bounded, so {@link #write(VideoFrame)} blocks while the encoder is
 * behind. The application never closes the stream; the call engine ends it when the call ends, which
 * also releases any device a subclass bound.
 *
 * @apiNote {@link #fromScreen()} is the screen-sharing entry point; the engine announces it to the peer
 * as a screen-share video stream carrying the source resolution. Do not call {@link #take()} or
 * {@link #shutdown()}: they are the call engine's drain and teardown hooks.
 */
public class VideoOutputStream {
    /**
     * Holds the maximum number of buffered frames before {@link #write(VideoFrame)} blocks.
     *
     * @implNote This implementation uses 4: video frames are large, and buffering more than a few adds
     * latency without smoothing capture jitter meaningfully.
     */
    private static final int CAPACITY = 4;

    /**
     * Holds the default frame width and height in pixels for the geometry-less factories.
     */
    private static final int DEFAULT_WIDTH = 640;

    /**
     * Holds the default frame height in pixels for the geometry-less factories.
     */
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * Holds the default target frame rate for the geometry-less factories.
     */
    private static final int DEFAULT_FPS = 30;

    /**
     * Marks the end of the stream so the engine's {@link #take()} returns {@code null} once drained.
     */
    private static final VideoFrame SENTINEL = new VideoFrame(new byte[6], 2, 2, Long.MIN_VALUE);

    /**
     * Holds the bounded queue bridging the application producer and the engine drain.
     */
    private final LinkedBlockingDeque<VideoFrame> queue = new LinkedBlockingDeque<>(CAPACITY);

    /**
     * Holds the advertised frame width in pixels the engine encodes and signals this video at.
     */
    private final int width;

    /**
     * Holds the advertised frame height in pixels the engine encodes and signals this video at.
     */
    private final int height;

    /**
     * Holds the target frame rate the engine encodes this video at.
     */
    private final int fps;

    /**
     * Holds the target encoder bitrate in bits per second.
     */
    private final int bitrateBps;

    /**
     * Guards {@link #shutdown()} so the teardown runs at most once.
     *
     * <p>Exposed to device-backed subclasses so their {@link #shutdown()} override can make the same
     * end-of-stream transition atomic and idempotent.
     */
    protected final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs a stream advertising the given geometry, with no device binding.
     *
     * <p>Device-backed subclasses in {@code com.github.auties00.cobalt.call.stream.capture} invoke this
     * constructor and then override {@link #take()} to read from their device instead of the bounded
     * queue. The geometry is the resolution, frame rate, and bitrate the call engine encodes and
     * advertises this video at; a device subclass may capture at a different native resolution and the
     * encoder scales to this geometry.
     *
     * @param width      the frame width in pixels; even and at least 2
     * @param height     the frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     */
    protected VideoOutputStream(int width, int height, int fps, int bitrateBps) {
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("videoWidth must be even and >= 2, got " + width);
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("videoHeight must be even and >= 2, got " + height);
        }
        if (fps < 1) {
            throw new IllegalArgumentException("videoFps must be >= 1, got " + fps);
        }
        if (bitrateBps < 1) {
            throw new IllegalArgumentException("videoBitrateBps must be >= 1, got " + bitrateBps);
        }
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrateBps = bitrateBps;
    }

    /**
     * Returns a manually-written stream that the application fills with {@link #write(VideoFrame)}, at
     * the given geometry.
     *
     * @param width      the frame width in pixels; even and at least 2
     * @param height     the frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a new empty buffered stream advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     */
    public static VideoOutputStream buffered(int width, int height, int fps, int bitrateBps) {
        return new VideoOutputStream(width, height, fps, bitrateBps);
    }

    /**
     * Returns a manually-written stream at the given resolution, 30 fps, with the bitrate
     * {@linkplain #defaultBitrate(int, int, int) auto-derived} from the pixel count.
     *
     * @param width  the frame width in pixels; even and at least 2
     * @param height the frame height in pixels; even and at least 2
     * @return a new empty buffered stream advertising the given resolution
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2
     */
    public static VideoOutputStream buffered(int width, int height) {
        return new VideoOutputStream(width, height, DEFAULT_FPS, defaultBitrate(width, height, DEFAULT_FPS));
    }

    /**
     * Returns a manually-written stream at the default SD geometry: 640x480 at 30 fps with the
     * pixel-derived bitrate.
     *
     * @return a new empty buffered stream at the default geometry
     */
    public static VideoOutputStream buffered() {
        return buffered(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Returns a stream bound to the default camera, advertising the given geometry.
     *
     * <p>Each {@link #take()} captures one I420 frame from the platform default camera, blocking on the
     * device until a frame is available, until the call ends and the device is released. The captured
     * frames are scaled to this stream's geometry by the encoder.
     *
     * @param width      the frame width in pixels; even and at least 2
     * @param height     the frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a camera-bound stream advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if no camera is available on the running platform
     */
    public static VideoOutputStream fromCamera(int width, int height, int fps, int bitrateBps) {
        return new CameraVideoOutputStream(width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream bound to the default camera at the default SD geometry: 640x480 at 30 fps with
     * the pixel-derived bitrate.
     *
     * @return a camera-bound stream at the default geometry
     * @throws IllegalStateException if no camera is available on the running platform
     */
    public static VideoOutputStream fromCamera() {
        return fromCamera(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                defaultBitrate(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS));
    }

    /**
     * Returns a stream that shares the primary screen, advertising the given geometry.
     *
     * <p>Each {@link #take()} captures the platform default display as one I420 frame, until the call
     * ends. The engine announces this to the peer as a screen-share video stream carrying this stream's
     * geometry.
     *
     * @param width      the frame width in pixels; even and at least 2
     * @param height     the frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a screen-share stream advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static VideoOutputStream fromScreen(int width, int height, int fps, int bitrateBps) {
        return ScreenVideoOutputStream.primary(width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream that shares the primary screen at the default SD geometry: 640x480 at 30 fps
     * with the pixel-derived bitrate.
     *
     * @return a screen-share stream at the default geometry
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static VideoOutputStream fromScreen() {
        return fromScreen(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                defaultBitrate(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS));
    }

    /**
     * Returns a stream that transmits the video track of a media file, advertising the given geometry.
     *
     * <p>The file's decoded frames are scaled to this stream's geometry by the encoder.
     *
     * @param path       the media file to stream
     * @param width      the frame width in pixels; even and at least 2
     * @param height     the frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a file-bound stream advertising the given geometry
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if the file cannot be opened or has no video stream
     */
    public static VideoOutputStream fromFile(Path path, int width, int height, int fps, int bitrateBps) {
        Objects.requireNonNull(path, "path cannot be null");
        return new FileVideoOutputStream(path, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream that transmits the video track of a media file at the default SD geometry:
     * 640x480 at 30 fps with the pixel-derived bitrate.
     *
     * @param path the media file to stream
     * @return a file-bound stream at the default geometry
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no video stream
     */
    public static VideoOutputStream fromFile(Path path) {
        return fromFile(path, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                defaultBitrate(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS));
    }

    /**
     * Computes a heuristic bitrate target for a resolution and frame rate.
     *
     * <p>The estimate follows WebRTC's typical SD and HD operating points at roughly 0.1 bits per
     * pixel per frame, clamped to a 64 kbps floor.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     * @param fps    target frame rate
     * @return a starting bitrate in bits per second
     */
    private static int defaultBitrate(int width, int height, int fps) {
        return Math.max(64_000, width * height * fps / 10);
    }

    /**
     * Returns the advertised frame width in pixels the engine encodes and signals this video at.
     *
     * @return the frame width in pixels
     */
    public int width() {
        return width;
    }

    /**
     * Returns the advertised frame height in pixels the engine encodes and signals this video at.
     *
     * @return the frame height in pixels
     */
    public int height() {
        return height;
    }

    /**
     * Returns the target frame rate the engine encodes this video at.
     *
     * @return the frame rate
     */
    public int fps() {
        return fps;
    }

    /**
     * Returns the target encoder bitrate in bits per second.
     *
     * @return the target bitrate in bits per second
     */
    public int bitrateBps() {
        return bitrateBps;
    }

    /**
     * Writes one frame of local video to transmit, blocking while the encoder is behind.
     *
     * <p>After the stream has ended the frame is silently discarded. A device-backed subclass fills
     * itself through its {@link #take()} override and ignores this method, since nothing writes to it.
     *
     * @param frame the frame to transmit; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for buffer space
     */
    public void write(VideoFrame frame) throws InterruptedException {
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
     * <p>The buffered base stream returns frames previously supplied through {@link #write(VideoFrame)}.
     * A device-backed subclass overrides this hook to pull the next frame straight from its capture
     * device or decoder, returning {@code null} at end-of-stream.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public VideoFrame take() throws InterruptedException {
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
}
