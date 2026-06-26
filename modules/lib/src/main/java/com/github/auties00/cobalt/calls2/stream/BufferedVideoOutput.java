package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the bundled buffered base implementation of {@link VideoOutput}, together with the static
 * factories that construct the device-backed local video sources of a call.
 *
 * <p>This is the concrete write side of a call's video. The buffered base bridges an application
 * producer to the engine drain through a bounded queue: the application pushes the picture it wants
 * transmitted with {@link #write(VideoFrame)} and the engine pulls each frame with {@link #take()},
 * encodes it with the negotiated video codec at this source's {@link #width()} by {@link #height()} at
 * {@link #fps()} and {@link #bitrateBps()}, and ships it to the peer. The static factories return either
 * an empty buffered source, through {@link #buffered()} and its geometry overloads, or a device-backed
 * sealed subclass whose {@link #take()} pulls frames straight from the device: {@link #fromCamera()} for
 * a webcam, {@link #fromScreen()} for screen sharing, or {@link #file(Path)} for a media file.
 *
 * <p>The presence of a video source on a placed or accepted call is what makes the call a video call;
 * supplying none makes it audio-only. The geometry is fixed at construction: the factories default to
 * 640x480 at 30 fps with the recovered WhatsApp {@linkplain #DEFAULT_BITRATE_BPS initial bitrate}, and
 * explicit-geometry overloads accept any even resolution of at least {@code 2}, frame rate of at least
 * {@code 1}, and bitrate of at least {@code 1}.
 *
 * <p>Frames carry planar 4:2:0 pixels as described by {@link VideoFrame}; the resolution carried by an
 * individual frame may differ from this source's advertised {@link #width()} by {@link #height()}, and
 * the engine scales captured frames to the advertised geometry. The buffered base is internally
 * bounded, so {@link #write(VideoFrame)} blocks while the encoder is behind. The application never ends
 * the source; the engine invokes {@link #shutdown()} when the call ends, which also releases any device
 * a subclass bound.
 *
 * @apiNote {@link #fromScreen()} is the screen-sharing entry point; the engine announces it to the peer
 * as a screen-share video stream carrying the source resolution. Do not call {@link #take()} or
 * {@link #shutdown()}: they are the engine's drain and teardown hooks.
 * @implNote This implementation is the calls2 counterpart of the legacy buffered video output stream
 * base, carried forward unchanged except that the frames it relays carry the
 * {@link VideoFrame#ptsMicros()} microsecond clock rather than the legacy millisecond clock.
 */
public sealed class BufferedVideoOutput implements VideoOutput
        permits CameraVideoOutput, FfmpegVideoOutput {
    /**
     * Holds the maximum number of buffered frames before {@link #write(VideoFrame)} blocks.
     *
     * @implNote This implementation uses {@code 4}: video frames are large, and buffering more than a
     * few adds latency without smoothing capture jitter meaningfully.
     */
    private static final int CAPACITY = 4;

    /**
     * Holds the default frame width in pixels for the geometry-less factories.
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
     * Holds the default initial encoder bitrate in bits per second the factories advertise.
     *
     * <p>WhatsApp initializes the video bitrate from the bandwidth estimator independently of the picture
     * size, so this default is resolution-independent rather than pixel-derived. It mirrors the
     * authoritative encoder seed
     * {@link com.github.auties00.cobalt.calls2.media.video.VideoCodecParams#DEFAULT_INIT_TARGET_BITRATE}
     * (the recovered {@code vid_rc.max_init_bwe = 350000};
     * re/calls2-spec/captures/voip-settings-merged.json).
     *
     * @implNote This value is the advertised {@link #bitrateBps()} only; the call engine configures the
     * encoder from {@code VideoCodecParams.forResolution} and its runtime rate controller, not from this
     * advertised field, so it is informational rather than load-bearing. It is package-private so the
     * device-backed subclasses ({@link ScreenVideoOutput}) advertise the same initial bitrate.
     */
    static final int DEFAULT_BITRATE_BPS = 350_000;

    /**
     * Marks the end of the source so the engine's {@link #take()} returns {@code null} once drained.
     *
     * <p>A minimal {@code 2x2} {@link VideoPixelFormat#I420 I420} frame is used as a private identity
     * token; it is compared by reference in {@link #take()} and never decoded.
     */
    private static final VideoFrame SENTINEL =
            new VideoFrame(new byte[6], VideoPixelFormat.I420, 2, 2, Long.MIN_VALUE);

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
     * end-of-source transition atomic and idempotent.
     */
    protected final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs a source advertising the given geometry, with no device binding.
     *
     * <p>Device-backed subclasses ({@link CameraVideoOutput}, {@link ScreenVideoOutput}, and
     * {@link FileVideoOutput}) invoke this constructor and then override {@link #take()} to read from
     * their device instead of the bounded queue. The geometry is the resolution, frame rate, and
     * bitrate the engine encodes and
     * advertises this video at; a device subclass may capture at a different native resolution and the
     * encoder scales to this geometry.
     *
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     */
    protected BufferedVideoOutput(int width, int height, int fps, int bitrateBps) {
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
     * Returns a manually-written source that the application fills with {@link #write(VideoFrame)}, at
     * the given geometry.
     *
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a new empty buffered source advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     */
    public static BufferedVideoOutput buffered(int width, int height, int fps, int bitrateBps) {
        return new BufferedVideoOutput(width, height, fps, bitrateBps);
    }

    /**
     * Returns a manually-written source at the given resolution, 30 fps, with the recovered WhatsApp
     * {@linkplain #DEFAULT_BITRATE_BPS initial bitrate}.
     *
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @return a new empty buffered source advertising the given resolution
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}
     */
    public static BufferedVideoOutput buffered(int width, int height) {
        return new BufferedVideoOutput(width, height, DEFAULT_FPS, DEFAULT_BITRATE_BPS);
    }

    /**
     * Returns a manually-written source at the default SD geometry: 640x480 at 30 fps with the
     * default initial bitrate.
     *
     * @return a new empty buffered source at the default geometry
     */
    public static BufferedVideoOutput buffered() {
        return buffered(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Returns a source bound to the default camera, advertising the given geometry.
     *
     * <p>Each {@link #take()} captures one planar 4:2:0 frame from the platform default camera, blocking
     * on the device until a frame is available, until the call ends and the device is released. The
     * captured frames are scaled to this source's geometry by the encoder.
     *
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a camera-bound source advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if no camera is available on the running platform
     */
    public static BufferedVideoOutput fromCamera(int width, int height, int fps, int bitrateBps) {
        return new CameraVideoOutput(width, height, fps, bitrateBps);
    }

    /**
     * Returns a source bound to the default camera at the default SD geometry: 640x480 at 30 fps with
     * the default initial bitrate.
     *
     * @return a camera-bound source at the default geometry
     * @throws IllegalStateException if no camera is available on the running platform
     */
    public static BufferedVideoOutput fromCamera() {
        return fromCamera(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS);
    }

    /**
     * Returns a source that shares the primary screen, advertising the given geometry.
     *
     * <p>Each {@link #take()} captures the platform default display as one planar 4:2:0 frame, until the
     * call ends. The engine announces this to the peer as a screen-share video stream carrying this
     * source's geometry.
     *
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a screen-share source advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static BufferedVideoOutput fromScreen(int width, int height, int fps, int bitrateBps) {
        return ScreenVideoOutput.primary(width, height, fps, bitrateBps);
    }

    /**
     * Returns a source that shares the primary screen at the default SD geometry: 640x480 at 30 fps with
     * the default initial bitrate.
     *
     * @return a screen-share source at the default geometry
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static BufferedVideoOutput fromScreen() {
        return fromScreen(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS);
    }

    /**
     * Returns a source that transmits the video track of a media file, advertising the given geometry.
     *
     * <p>The file's decoded frames are scaled to this source's geometry by the encoder.
     *
     * @param path       the media file to stream
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a file-bound source advertising the given geometry
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if the file cannot be opened or has no video stream
     */
    public static BufferedVideoOutput file(Path path, int width, int height, int fps, int bitrateBps) {
        Objects.requireNonNull(path, "path cannot be null");
        return new FileVideoOutput(path, width, height, fps, bitrateBps);
    }

    /**
     * Returns a source that transmits the video track of a media file at the default SD geometry:
     * 640x480 at 30 fps with the default initial bitrate.
     *
     * @param path the media file to stream
     * @return a file-bound source at the default geometry
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no video stream
     */
    public static BufferedVideoOutput file(Path path) {
        return file(path, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS);
    }

    /**
     * Returns a source that transmits the video track of a media stream addressed by a URI, advertising
     * the given geometry and bounding every blocking network operation by the given timeout.
     *
     * <p>The stream's decoded frames are scaled to this source's geometry by the encoder. The accepted
     * schemes are restricted to a fixed allowlist, so an application-supplied string cannot reach an
     * unintended protocol.
     *
     * @param uri        the media stream to open
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @param ioTimeout  the maximum time any single connect, probe, or read may block; must be positive
     * @return a URI-bound source advertising the given geometry
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, the scheme is not permitted,
     *                                  {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    public static BufferedVideoOutput uri(URI uri, int width, int height, int fps, int bitrateBps,
                                          Duration ioTimeout) {
        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(ioTimeout, "ioTimeout cannot be null");
        if (ioTimeout.isNegative() || ioTimeout.isZero()) {
            throw new IllegalArgumentException("ioTimeout must be positive, got " + ioTimeout);
        }
        return new UriVideoOutput(uri, width, height, fps, bitrateBps, ioTimeout);
    }

    /**
     * Returns a source that transmits the video track of a media stream addressed by a URI, advertising
     * the given geometry with a fifteen-second timeout on every blocking network operation.
     *
     * @param uri        the media stream to open
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a URI-bound source advertising the given geometry
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the scheme is not permitted, {@code width} or {@code height} is
     *                                  odd or below {@code 2}, or {@code fps} or {@code bitrateBps} is
     *                                  below {@code 1}
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    public static BufferedVideoOutput uri(URI uri, int width, int height, int fps, int bitrateBps) {
        return uri(uri, width, height, fps, bitrateBps, Duration.ofSeconds(15));
    }

    /**
     * Returns a source that transmits the video track of a media stream addressed by a URI at the default
     * SD geometry: 640x480 at 30 fps with the default initial bitrate, with a fifteen-second timeout on every
     * blocking network operation.
     *
     * @param uri the media stream to open
     * @return a URI-bound source at the default geometry
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the URI has no scheme or its scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    public static BufferedVideoOutput uri(URI uri) {
        return uri(uri, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The buffered base accepts any planar 4:2:0 frame and enqueues it, blocking while the encoder is
     * behind. After the source has ended the frame is silently discarded. A device-backed subclass fills
     * itself through its {@link #take()} override and ignores this method, since nothing writes to it.
     *
     * @param frame {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public void write(VideoFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (closed.get()) {
            return;
        }
        queue.putLast(frame);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The buffered base returns frames previously supplied through {@link #write(VideoFrame)}. A
     * device-backed subclass overrides this hook to pull the next frame straight from its capture device
     * or decoder, returning {@code null} at end-of-stream.
     *
     * @return {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public VideoFrame take() throws InterruptedException {
        var frame = queue.takeFirst();
        return frame == SENTINEL ? null : frame;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enqueues the end-of-stream sentinel so a pending {@link #take()} returns {@code null}.
     * Idempotent. A device-backed subclass overrides this hook to also release its device and unblock a
     * {@link #take()} parked on the device.
     */
    @Override
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            queue.offerFirst(SENTINEL);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public int width() {
        return width;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public int height() {
        return height;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public int fps() {
        return fps;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public int bitrateBps() {
        return bitrateBps;
    }
}
