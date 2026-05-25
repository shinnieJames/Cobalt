package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.video.VideoSource;

/**
 * Creates {@link VideoSource}s that capture the system's screen for screen-sharing in a call.
 *
 * <p>This is a static factory with no instances. {@link #primary()} detects the running platform and
 * captures its conventional default display. The named factories cover the cases the platform
 * default does not: {@link #x11(String)}, {@link #wayland(String)}, and {@link #drm(String)} for the
 * Linux display servers, {@link #macOs(int)} for a specific macOS screen, and
 * {@link #windowsDesktop()} or {@link #windowsWindow(String)} for the whole Windows desktop or a
 * single window. Each factory selects the libavdevice screen-grab input format conventional for its
 * platform:
 *
 * <ul>
 *   <li>Linux X11: {@code xcbgrab}</li>
 *   <li>Linux Wayland: {@code pipewiregrab}</li>
 *   <li>Linux DRM: {@code kmsgrab} (requires root)</li>
 *   <li>macOS: {@code avfoundation}</li>
 *   <li>Windows: {@code gdigrab}</li>
 * </ul>
 *
 * <p>Every returned source is a {@link Camera} pointed at a screen-grab device, so it produces I420
 * frames through the same libavdevice, libavcodec, and libswscale pipeline that camera capture uses.
 *
 * @apiNote Wire one of these sources into a call to share a screen or a window. Prefer
 * {@link #primary()} for the simple "share my main display" case, and reach for a named factory only
 * when the platform default is wrong, for example on Wayland (where {@link #primary()} assumes X11)
 * or to pick one monitor of a multi-display setup.
 */
public final class Screen {
    /**
     * Prevents instantiation of this static factory.
     *
     * @throws AssertionError always, since the class is not instantiable
     */
    private Screen() {
        throw new AssertionError("Screen is not instantiable");
    }

    /**
     * Returns a source capturing the platform's default screen.
     *
     * <p>Selects the macOS screen at index {@code 0}, the whole Windows desktop, or the X11 display
     * {@code ":0.0"} according to the running platform. On Linux this assumes X11; a Wayland session
     * must instead use {@link #wayland(String)}.
     *
     * @return a source capturing the primary display
     */
    public static VideoSource primary() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return macOs(0);
        if (os.contains("win")) return windowsDesktop();
        return x11(":0.0");
    }

    /**
     * Returns a source capturing an X11 display through the {@code xcbgrab} input format.
     *
     * @param display the X11 display string (for example {@code ":0.0"})
     * @return a source capturing the given X11 display
     */
    public static VideoSource x11(String display) {
        return new Camera("xcbgrab", display);
    }

    /**
     * Returns a source capturing a Wayland source through the {@code pipewiregrab} input format.
     *
     * @param node the PipeWire node name to capture
     * @return a source capturing the given PipeWire node
     */
    public static VideoSource wayland(String node) {
        return new Camera("pipewiregrab", node);
    }

    /**
     * Returns a source capturing a Linux DRM card through the {@code kmsgrab} input format.
     *
     * <p>Capturing a DRM card requires elevated privileges (root or {@code CAP_SYS_ADMIN}).
     *
     * @param card the DRM card path (for example {@code "/dev/dri/card0"})
     * @return a source capturing the given DRM card
     */
    public static VideoSource drm(String card) {
        return new Camera("kmsgrab", card);
    }

    /**
     * Returns a source capturing a macOS screen through the {@code avfoundation} input format.
     *
     * <p>Passing only the screen index yields a video-only capture with no audio.
     *
     * @param screenIndex the AVFoundation screen index
     * @return a source capturing the given macOS screen
     */
    public static VideoSource macOs(int screenIndex) {
        return new Camera("avfoundation", screenIndex + ":");
    }

    /**
     * Returns a source capturing the entire Windows desktop through the {@code gdigrab} input
     * format.
     *
     * @return a source capturing the Windows desktop
     */
    public static VideoSource windowsDesktop() {
        return new Camera("gdigrab", "desktop");
    }

    /**
     * Returns a source capturing a single Windows window by title through the {@code gdigrab} input
     * format.
     *
     * @param title the title of the window to capture
     * @return a source capturing the given Windows window
     */
    public static VideoSource windowsWindow(String title) {
        return new Camera("gdigrab", "title=" + title);
    }
}
