package com.github.auties00.cobalt.calls2.stream;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Defines the local outbound video source of a call: the app-supplied origin of the
 * {@link VideoFrame}s the call engine encodes and transmits, together with the geometry it is encoded
 * and advertised at.
 *
 * <p>This is the write side of a call's video, and its presence on a placed or accepted call is what
 * makes the call a video call; supplying no video source makes the call audio-only. The engine
 * repeatedly {@linkplain #take() pulls} frames on a dedicated virtual thread, encodes each with the
 * negotiated video codec at this source's {@link #width()} by {@link #height()} at {@link #fps()} and
 * {@link #bitrateBps()}, and ships it to the peer. The contract has two faces. The application-facing
 * face is {@link #write(VideoFrame)}, by which a programmatic producer pushes the picture it wants
 * transmitted. The engine-facing face is {@link #take()} and {@link #shutdown()}. A device-backed
 * source instead fills itself inside its {@link #take()} from a camera, screen, or media file and
 * ignores {@link #write(VideoFrame)}.
 *
 * <p>Frames carry planar 4:2:0 pixels as described by {@link VideoFrame}; the resolution carried by an
 * individual frame may differ from this source's advertised {@link #width()} by {@link #height()},
 * and the engine scales captured frames to the advertised geometry. The geometry an implementation
 * reports is fixed for the lifetime of the source: the engine reads it once when the source is
 * installed to size the encoder and advertise the stream to the peer. An implementation decides its
 * own buffering and backpressure policy between the producer and the engine drain. The application
 * never ends the source itself; the engine invokes {@link #shutdown()} when the call ends, which an
 * implementation uses to release any device it bound.
 *
 * @apiNote An embedder implements this interface for a custom video source, or obtains a bundled
 * buffered or device-backed implementation from one of the factories on this type:
 * {@link #buffered(int, int)} for a manually-written source, {@link #fromCamera()} for live webcam capture,
 * {@link #fromScreen()} for screen sharing, {@link #file(Path)} for a local media file, and
 * {@link #uri(URI)} for a media stream addressed by URI. A buffered source has no device to probe, so it
 * requires explicit even geometry; a device-backed or media source instead detects its input's own native
 * resolution, capping the longer side to {@code 1280} and rounding both dimensions to even, so a 16:9
 * source stays 16:9 rather than being squished to a fixed default. A screen-share source is announced to
 * the peer as a screen-share video stream carrying its detected source resolution. The {@link #take()} and
 * {@link #shutdown()} methods belong to the engine; application code drives a programmatic source
 * through {@link #write(VideoFrame)} and never calls the engine-facing pair directly.
 */
public interface VideoOutput {
    /**
     * Returns a manually-written buffered source at the given resolution, 30 frames per second, with the
     * recovered WhatsApp initial bitrate.
     *
     * <p>This is the path a programmatic producer uses: the engine drains the written frames through
     * {@link #take()} on its own thread, encodes each at the source's geometry, and ships it to the peer.
     * A buffered source has no device to probe, so it requires explicit even geometry.
     *
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @return a new empty buffered source advertising the given resolution
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}
     */
    static VideoOutput buffered(int width, int height) {
        return BufferedVideoOutput.buffered(width, height);
    }

    /**
     * Returns a manually-written buffered source advertising the given geometry.
     *
     * @param width      the frame width in pixels; even and at least {@code 2}
     * @param height     the frame height in pixels; even and at least {@code 2}
     * @param fps        the target frame rate; at least {@code 1}
     * @param bitrateBps the target encoder bitrate in bits per second; at least {@code 1}
     * @return a new empty buffered source advertising the given geometry
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below {@code 2}, or
     *                                  {@code fps} or {@code bitrateBps} is below {@code 1}
     */
    static VideoOutput buffered(int width, int height, int fps, int bitrateBps) {
        return BufferedVideoOutput.buffered(width, height, fps, bitrateBps);
    }

    /**
     * Returns a source that transmits the video track of a media file, advertising its detected native
     * resolution.
     *
     * <p>The file's decoded frames are scaled to the advertised geometry by the encoder. The advertised
     * geometry is the file's own native video resolution, capped to {@code 1280} on the longer side and
     * rounded to even, so a 16:9 file is advertised as 16:9 rather than squished to a fixed default.
     *
     * @param path the media file to stream
     * @return a file-bound source advertising its detected native resolution
     * @throws NullPointerException  if {@code path} is {@code null}
     * @throws IllegalStateException if the file cannot be opened or has no video stream
     */
    static VideoOutput file(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return BufferedVideoOutput.file(path);
    }

    /**
     * Returns a source that transmits the video track of a media stream addressed by a URI, advertising its
     * detected native resolution, with a fifteen-second timeout on every blocking network operation.
     *
     * <p>Generalizes {@link #file(Path)} to any protocol the bundled FFmpeg build enables: in addition to
     * a local {@code file:} path, an {@code http:}/{@code https:} asset, an {@code rtsp:}/{@code rtmp:}/
     * {@code srt:} live stream, and so on. The stream's decoded frames are scaled to the advertised
     * geometry by the encoder, which is the stream's own native video resolution capped to {@code 1280} on
     * the longer side and rounded to even. The accepted schemes are restricted to a fixed allowlist, so an
     * application-supplied string cannot reach an unintended protocol.
     *
     * @param uri the media stream to open
     * @return a URI-bound source advertising its detected native resolution
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the URI has no scheme or its scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    static VideoOutput uri(URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");
        return BufferedVideoOutput.uri(uri);
    }

    /**
     * Returns a source that transmits the video track of a media stream addressed by a URI, advertising its
     * detected native resolution and bounding every blocking network operation by the given timeout.
     *
     * <p>If a connect, stream probe, or read makes no progress within {@code ioTimeout}, the operation is
     * aborted and the source ends with an error rather than stalling the call's encode thread. The
     * advertised geometry is the stream's own native video resolution, capped to {@code 1280} on the longer
     * side and rounded to even.
     *
     * @param uri       the media stream to open
     * @param ioTimeout the maximum time any single connect, probe, or read may block; must be positive
     * @return a URI-bound source advertising its detected native resolution
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, the URI has no scheme, or its
     *                                  scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or has no video stream
     */
    static VideoOutput uri(URI uri, Duration ioTimeout) {
        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(ioTimeout, "ioTimeout cannot be null");
        return BufferedVideoOutput.uri(uri, ioTimeout);
    }

    /**
     * Returns a source bound to the platform's default camera, advertising its detected native resolution.
     *
     * <p>Each {@link #take()} captures one planar 4:2:0 frame from the default camera, blocking on the
     * device until a frame is available, until the call ends and the device is released; the captured
     * frames are scaled to the advertised geometry by the encoder. The advertised geometry is the camera's
     * own native resolution, capped to {@code 1280} on the longer side and rounded to even.
     *
     * @return a camera-bound source advertising its detected native resolution
     * @throws IllegalStateException if no camera is available on the running platform
     */
    static VideoOutput fromCamera() {
        return BufferedVideoOutput.fromCamera();
    }

    /**
     * Returns a source bound to a named camera, advertising its detected native resolution.
     *
     * <p>Opens the named device through the platform's default libavdevice input format; the captured
     * frames are scaled to the advertised geometry by the encoder, which is the camera's own native
     * resolution capped to {@code 1280} on the longer side and rounded to even.
     *
     * @param deviceUrl the device URL or name (for example {@code "/dev/video1"} on Linux, {@code "1"} on
     *                  macOS, or {@code "Integrated Camera"} on Windows)
     * @return a camera-bound source advertising its detected native resolution
     * @throws NullPointerException  if {@code deviceUrl} is {@code null}
     * @throws IllegalStateException if the device cannot be opened or has no video stream
     */
    static VideoOutput fromCamera(String deviceUrl) {
        return BufferedVideoOutput.fromCamera(deviceUrl);
    }

    /**
     * Returns a source that shares the platform's default screen, advertising its detected native
     * resolution.
     *
     * <p>Each {@link #take()} captures the default display as one planar 4:2:0 frame, until the call ends.
     * The engine announces this to the peer as a screen-share video stream carrying the display's own
     * native resolution, capped to {@code 1280} on the longer side and rounded to even.
     *
     * @return a screen-share source advertising its detected native resolution
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    static VideoOutput fromScreen() {
        return BufferedVideoOutput.fromScreen();
    }

    /**
     * Writes one frame of local video to transmit.
     *
     * <p>Offers the frame to the engine for encoding and transmission. An implementation chooses
     * whether a full internal buffer blocks the caller (backpressure) or drops a frame, and what
     * happens after {@link #shutdown()} has run; the only universal requirement is that the frame is
     * never {@code null}. A device-backed source produces frames inside {@link #take()} and may treat
     * this method as a no-op.
     *
     * @param frame the frame to transmit; never {@code null}
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for buffer space
     */
    void write(VideoFrame frame) throws InterruptedException;

    /**
     * Returns the next frame for the engine to encode, blocking until one is available, or
     * {@code null} once the source has ended.
     *
     * <p>A buffered source returns frames previously supplied through {@link #write(VideoFrame)}; a
     * device-backed source pulls the next frame straight from its capture device or decoder. The
     * method blocks while no frame is ready and returns {@code null} exactly once the source is
     * permanently drained, after which the engine stops pulling.
     *
     * @return the next frame, or {@code null} at end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    VideoFrame take() throws InterruptedException;

    /**
     * Ends the source, unblocking a pending {@link #take()} and releasing any bound device.
     *
     * <p>Invoked by the engine when the call ends. After it runs, {@link #take()} returns {@code null}
     * and the implementation releases any capture device or decoder it held. Implementations make this
     * idempotent, since the engine may signal teardown more than once during a racing shutdown.
     */
    void shutdown();

    /**
     * Returns the frame width in pixels the engine encodes and advertises this video at.
     *
     * <p>Read once when the source is installed; even and at least {@code 2}. The engine scales
     * captured frames whose native width differs to this advertised width. A device-backed or media source
     * reports its input's detected native width, capped to {@code 1280} on the longer side and rounded to
     * even, rather than a fixed advertised one; a buffered source reports the explicit width it was given.
     *
     * @return the advertised frame width in pixels
     */
    int width();

    /**
     * Returns the frame height in pixels the engine encodes and advertises this video at.
     *
     * <p>Read once when the source is installed; even and at least {@code 2}. The engine scales
     * captured frames whose native height differs to this advertised height. A device-backed or media
     * source reports its input's detected native height, capped to {@code 1280} on the longer side and
     * rounded to even, rather than a fixed advertised one; a buffered source reports the explicit height it
     * was given.
     *
     * @return the advertised frame height in pixels
     */
    int height();

    /**
     * Returns the target frame rate the engine encodes this video at.
     *
     * <p>Read once when the source is installed; at least {@code 1}. The engine uses it to pace the
     * encoder and as the rate it advertises to the peer.
     *
     * @return the target frame rate in frames per second
     */
    int fps();

    /**
     * Returns the target encoder bitrate in bits per second.
     *
     * <p>Read once when the source is installed; at least {@code 1}. It is the encoder's starting
     * target, which the engine's rate controller may adapt downward as the network dictates.
     *
     * @return the target bitrate in bits per second
     */
    int bitrateBps();
}
