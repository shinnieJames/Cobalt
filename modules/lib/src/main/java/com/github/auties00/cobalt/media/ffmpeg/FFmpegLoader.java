package com.github.auties00.cobalt.media.ffmpeg;

import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;

/**
 * Bootstrap for the FFmpeg shared libraries the toolkit's
 * jextract'd {@link Ffmpeg} bindings dispatch through.
 *
 * <p>The bindings resolve symbols via
 * {@code SymbolLookup.loaderLookup().or(defaultLookup())} — i.e. they
 * see anything already loaded into the JVM via
 * {@link System#load(String)}. Calling {@link NativeLibLoader#load} on
 * each FFmpeg shared library walks the resolution order
 * (classpath bundle → on-disk cache → GitHub download) and ends
 * with a {@code System.load(absolutePath)}, after which every
 * subsequent {@code Ffmpeg.*} call resolves through the loader's
 * symbol table.
 *
 * <p>Loading is idempotent and lazy — the first call from any
 * toolkit class triggers the load; subsequent calls are no-ops
 * since {@code NativeLibLoader} caches per library name.
 *
 * <p>Order matters: libavutil first (everyone depends on it), then
 * libswscale / libswresample (used by libavcodec), then libavcodec
 * (used by libavformat / libavdevice), then libavformat /
 * libavdevice (the leaves). On most platforms the dynamic linker
 * pulls dependencies automatically, but explicit ordering avoids
 * subtle "library not found" failures on hosts with restrictive
 * RPATHs.
 */
public final class FFmpegLoader {
    /**
     * Latch flipping {@code true} after the first successful boot.
     */
    private static volatile boolean loaded;

    /**
     * Prevents instantiation.
     */
    private FFmpegLoader() {
        throw new AssertionError("FFmpegLoader is not instantiable");
    }

    /**
     * Ensures every FFmpeg shared library the toolkit's bindings
     * reference is loaded into the JVM. Call this from any toolkit
     * class before issuing the first {@code Ffmpeg.*} call.
     *
     * @throws UnsatisfiedLinkError if any required library cannot
     *                              be resolved on this classifier
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (FFmpegLoader.class) {
            if (loaded) {
                return;
            }
            var arena = Arena.global();
            NativeLibLoader.load("ffmpeg-avutil", arena);
            NativeLibLoader.load("ffmpeg-swresample", arena);
            NativeLibLoader.load("ffmpeg-swscale", arena);
            NativeLibLoader.load("opus", arena);
            NativeLibLoader.load("vpx", arena);
            NativeLibLoader.load("openh264", arena);
            NativeLibLoader.load("webp", arena);
            NativeLibLoader.load("ffmpeg-avcodec", arena);
            NativeLibLoader.load("ffmpeg-avformat", arena);
            NativeLibLoader.load("ffmpeg-avfilter", arena);
            NativeLibLoader.load("ffmpeg-avdevice", arena);
            loaded = true;
        }
    }

    /**
     * Returns whether the FFmpeg libraries have been successfully
     * loaded into this JVM yet.
     *
     * @return {@code true} once {@link #ensureLoaded()} has
     *         succeeded
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
