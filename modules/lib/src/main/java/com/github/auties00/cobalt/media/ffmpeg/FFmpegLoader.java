package com.github.auties00.cobalt.media.ffmpeg;

import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;

/**
 * Loads the FFmpeg shared libraries that the jextract-generated {@code Ffmpeg} bindings dispatch through.
 *
 * <p>The generated bindings resolve symbols via
 * {@code SymbolLookup.loaderLookup().or(defaultLookup())}, so they observe any library already
 * brought into the JVM through {@link System#load(String)}. Calling
 * {@link NativeLibLoader#load(String, Arena)} for each FFmpeg shared library walks the loader's
 * resolution order (classpath bundle, then on-disk cache, then network download) and finishes
 * with a {@code System.load(absolutePath)}, after which every subsequent {@code Ffmpeg.*} call
 * resolves through the loader's symbol table.
 *
 * <p>Loading is idempotent and lazy: the first call triggers the work and later calls return
 * immediately, since {@link NativeLibLoader} caches by library name and this class guards on a
 * one-shot flag.
 *
 * <p>The libraries load in dependency order so that each library's dependencies are already
 * present when it is loaded: libavutil first (every other library depends on it), then
 * libswscale and libswresample together with the codec implementations they wrap (opus, vpx,
 * openh264, webp), then libavcodec, and finally the libraries that build on it (libavformat,
 * libavfilter, libavdevice).
 *
 * @implNote This implementation loads dependencies explicitly even though most dynamic linkers
 * pull them in automatically, because hosts with restrictive RPATHs otherwise fail symbol
 * resolution with "library not found" errors.
 */
public final class FFmpegLoader {
    /**
     * Tracks whether the libraries have been loaded, flipping to {@code true} after the first
     * successful boot.
     *
     * @implNote This implementation is {@code volatile} so that the double-checked guard in
     * {@link #ensureLoaded()} observes the flip without holding the class monitor on the fast
     * path.
     */
    private static volatile boolean loaded;

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws AssertionError always, since the class exposes only static members
     */
    private FFmpegLoader() {
        throw new AssertionError("FFmpegLoader is not instantiable");
    }

    /**
     * Ensures every FFmpeg shared library referenced by the bindings is loaded into the JVM.
     *
     * <p>The first invocation loads all required libraries in dependency order and marks the
     * loader booted; concurrent and later invocations return without repeating the work. Callers
     * invoke this before issuing their first {@code Ffmpeg.*} call.
     *
     * @throws UnsatisfiedLinkError if any required library cannot be resolved for this platform
     *                              classifier
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
     * Returns whether the FFmpeg libraries have been loaded into this JVM.
     *
     * @return {@code true} once {@link #ensureLoaded()} has completed successfully
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
