package com.github.auties00.cobalt.call.stream.capture;

import com.github.auties00.cobalt.call.stream.VideoOutputStream;

/**
 * Captures the system's screen as the local video of a call for screen sharing.
 *
 * <p>This is the device-backed {@link VideoOutputStream} returned by
 * {@link VideoOutputStream#fromScreen()}. A screen grab is a {@link CameraVideoOutputStream} pointed at
 * a libavdevice screen-grab input format, so it produces I420 frames through the same libavdevice,
 * libavcodec, and libswscale pipeline that camera capture uses. {@link #primary()} detects the running
 * platform and captures its conventional default display. The named factories cover the cases the
 * platform default does not: {@link #x11(String)}, {@link #wayland(String)}, and {@link #drm(String)}
 * for the Linux display servers, {@link #macOs(int)} for a specific macOS screen, and
 * {@link #windowsDesktop()} or {@link #windowsWindow(String)} for the whole Windows desktop or a single
 * window. Each factory selects the screen-grab input format conventional for its platform:
 *
 * <ul>
 *   <li>Linux X11: {@code xcbgrab}</li>
 *   <li>Linux Wayland: {@code pipewiregrab}</li>
 *   <li>Linux DRM: {@code kmsgrab} (requires root)</li>
 *   <li>macOS: {@code avfoundation}</li>
 *   <li>Windows: {@code gdigrab}</li>
 * </ul>
 *
 * <p>Each factory has a geometry-less overload defaulting to 640x480 at 30 fps and an explicit-geometry
 * overload; the geometry is the resolution, frame rate, and bitrate the call engine advertises and
 * encodes the share at, independent of the captured display's native resolution.
 *
 * <p>Prefer {@link #primary()} for the simple "share my main display" case, and reach for a named
 * factory only when the platform default is wrong, for example on Wayland (where {@link #primary()}
 * assumes X11) or to pick one monitor of a multi-display setup.
 */
public final class ScreenVideoOutputStream extends CameraVideoOutputStream {
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
     * Opens a screen-grab device for the given libavdevice input-format and URL pair at the given
     * advertised geometry.
     *
     * @param indev      the libavdevice screen-grab input-format name (for example {@code "xcbgrab"})
     * @param url        the screen-grab device URL
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @throws NullPointerException     if {@code indev} or {@code url} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if the named input format is unavailable in this build, the device
     *                                  cannot be opened, or it has no video stream
     */
    private ScreenVideoOutputStream(String indev, String url, int width, int height, int fps, int bitrateBps) {
        super(indev, url, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing the platform's default screen, advertising the given geometry.
     *
     * <p>Selects the macOS screen at index {@code 0}, the whole Windows desktop, or the X11 display
     * {@code ":0.0"} according to the running platform. On Linux this assumes X11; a Wayland session
     * must instead use {@link #wayland(String, int, int, int, int)}.
     *
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the primary display
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream primary(int width, int height, int fps, int bitrateBps) {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return macOs(0, width, height, fps, bitrateBps);
        if (os.contains("win")) return windowsDesktop(width, height, fps, bitrateBps);
        return x11(":0.0", width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing the platform's default screen at the default SD geometry: 640x480 at
     * 30 fps.
     *
     * @return a stream capturing the primary display
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream primary() {
        return primary(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing an X11 display through the {@code xcbgrab} input format, advertising
     * the given geometry.
     *
     * @param display    the X11 display string (for example {@code ":0.0"})
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the given X11 display
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream x11(String display, int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("xcbgrab", display, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing an X11 display at the default SD geometry: 640x480 at 30 fps.
     *
     * @param display the X11 display string (for example {@code ":0.0"})
     * @return a stream capturing the given X11 display
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream x11(String display) {
        return x11(display, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing a Wayland source through the {@code pipewiregrab} input format,
     * advertising the given geometry.
     *
     * @param node       the PipeWire node name to capture
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the given PipeWire node
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream wayland(String node, int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("pipewiregrab", node, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing a Wayland source at the default SD geometry: 640x480 at 30 fps.
     *
     * @param node the PipeWire node name to capture
     * @return a stream capturing the given PipeWire node
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream wayland(String node) {
        return wayland(node, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing a Linux DRM card through the {@code kmsgrab} input format, advertising
     * the given geometry.
     *
     * <p>Capturing a DRM card requires elevated privileges (root or {@code CAP_SYS_ADMIN}).
     *
     * @param card       the DRM card path (for example {@code "/dev/dri/card0"})
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the given DRM card
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream drm(String card, int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("kmsgrab", card, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing a Linux DRM card at the default SD geometry: 640x480 at 30 fps.
     *
     * <p>Capturing a DRM card requires elevated privileges (root or {@code CAP_SYS_ADMIN}).
     *
     * @param card the DRM card path (for example {@code "/dev/dri/card0"})
     * @return a stream capturing the given DRM card
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream drm(String card) {
        return drm(card, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing a macOS screen through the {@code avfoundation} input format,
     * advertising the given geometry.
     *
     * <p>Passing only the screen index yields a video-only capture with no audio.
     *
     * @param screenIndex the AVFoundation screen index
     * @param width       the advertised frame width in pixels; even and at least 2
     * @param height      the advertised frame height in pixels; even and at least 2
     * @param fps         the target frame rate; at least 1
     * @param bitrateBps  the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the given macOS screen
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream macOs(int screenIndex, int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("avfoundation", screenIndex + ":", width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing a macOS screen at the default SD geometry: 640x480 at 30 fps.
     *
     * @param screenIndex the AVFoundation screen index
     * @return a stream capturing the given macOS screen
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream macOs(int screenIndex) {
        return macOs(screenIndex, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing the entire Windows desktop through the {@code gdigrab} input format,
     * advertising the given geometry.
     *
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the Windows desktop
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream windowsDesktop(int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("gdigrab", "desktop", width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing the entire Windows desktop at the default SD geometry: 640x480 at
     * 30 fps.
     *
     * @return a stream capturing the Windows desktop
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream windowsDesktop() {
        return windowsDesktop(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Returns a stream capturing a single Windows window by title through the {@code gdigrab} input
     * format, advertising the given geometry.
     *
     * @param title      the title of the window to capture
     * @param width      the advertised frame width in pixels; even and at least 2
     * @param height     the advertised frame height in pixels; even and at least 2
     * @param fps        the target frame rate; at least 1
     * @param bitrateBps the target encoder bitrate in bits per second; at least 1
     * @return a stream capturing the given Windows window
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or below 2, or
     *                                  {@code fps} or {@code bitrateBps} is below 1
     * @throws IllegalStateException    if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream windowsWindow(String title, int width, int height, int fps, int bitrateBps) {
        return new ScreenVideoOutputStream("gdigrab", "title=" + title, width, height, fps, bitrateBps);
    }

    /**
     * Returns a stream capturing a single Windows window by title at the default SD geometry: 640x480
     * at 30 fps.
     *
     * @param title the title of the window to capture
     * @return a stream capturing the given Windows window
     * @throws IllegalStateException if screen capture is unavailable on the running platform
     */
    public static ScreenVideoOutputStream windowsWindow(String title) {
        return windowsWindow(title, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, defaultBitrate());
    }

    /**
     * Computes the default pixel-derived bitrate for the default SD geometry.
     *
     * <p>Matches {@link VideoOutputStream}'s pixel-count heuristic at roughly 0.1 bits per pixel per
     * frame for the 640x480-at-30-fps default, clamped to a 64 kbps floor.
     *
     * @return the default bitrate in bits per second
     */
    private static int defaultBitrate() {
        return Math.max(64_000, DEFAULT_WIDTH * DEFAULT_HEIGHT * DEFAULT_FPS / 10);
    }
}
