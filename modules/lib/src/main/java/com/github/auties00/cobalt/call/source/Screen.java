package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.video.VideoSource;

/**
 * Factory helpers for capturing the system's screen as a
 * {@link VideoSource}. {@link #primary()} auto-detects the running
 * platform and grabs the conventional default display; the
 * named factories ({@link #x11(String)}, {@link #wayland(String)},
 * {@link #drm(String)}, {@link #macOs(int)},
 * {@link #windowsDesktop()}, {@link #windowsWindow(String)}) cover
 * the cases where the platform-default isn't right (Wayland,
 * specific monitor on a multi-display setup, single window
 * recording on Windows).
 *
 * <p>Indev choice:
 *
 * <ul>
 *   <li>Linux X11: {@code xcbgrab}</li>
 *   <li>Linux Wayland: {@code pipewiregrab}</li>
 *   <li>Linux DRM: {@code kmsgrab} (requires root)</li>
 *   <li>macOS: {@code avfoundation}</li>
 *   <li>Windows: {@code gdigrab}</li>
 * </ul>
 *
 * <p>Each returned source produces I420 frames via the same
 * libavdevice → libavcodec → libswscale pipeline that
 * {@link Camera} uses.
 */
public final class Screen {
    /**
     * Prevents instantiation.
     */
    private Screen() {
        throw new AssertionError("Screen is not instantiable");
    }

    /**
     * Captures the platform's default screen / primary display.
     * On Linux this assumes X11 with display {@code ":0.0"} —
     * Wayland users should call {@link #wayland(String)}.
     *
     * @return the source
     */
    public static VideoSource primary() {
        var os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return macOs(0);
        if (os.contains("win")) return windowsDesktop();
        return x11(":0.0");
    }

    /**
     * Captures the X11 display via libavdevice's
     * {@code xcbgrab} indev.
     *
     * @param display the display string (e.g. {@code ":0.0"})
     * @return the source
     */
    public static VideoSource x11(String display) {
        return new Camera("xcbgrab", display);
    }

    /**
     * Captures a Wayland source via libavdevice's
     * {@code pipewiregrab} indev.
     *
     * @param node the PipeWire node name
     * @return the source
     */
    public static VideoSource wayland(String node) {
        return new Camera("pipewiregrab", node);
    }

    /**
     * Captures a Linux DRM card via libavdevice's
     * {@code kmsgrab} indev. Requires root or
     * {@code CAP_SYS_ADMIN}.
     *
     * @param card the DRM card path
     *             (e.g. {@code "/dev/dri/card0"})
     * @return the source
     */
    public static VideoSource drm(String card) {
        return new Camera("kmsgrab", card);
    }

    /**
     * Captures a macOS screen via libavdevice's
     * {@code avfoundation} indev. Passing only the screen index
     * yields a video-only capture.
     *
     * @param screenIndex the AVFoundation screen index
     * @return the source
     */
    public static VideoSource macOs(int screenIndex) {
        return new Camera("avfoundation", screenIndex + ":");
    }

    /**
     * Captures the entire Windows desktop via libavdevice's
     * {@code gdigrab} indev.
     *
     * @return the source
     */
    public static VideoSource windowsDesktop() {
        return new Camera("gdigrab", "desktop");
    }

    /**
     * Captures a specific Windows window by title via
     * libavdevice's {@code gdigrab} indev.
     *
     * @param title the window title
     * @return the source
     */
    public static VideoSource windowsWindow(String title) {
        return new Camera("gdigrab", "title=" + title);
    }
}
