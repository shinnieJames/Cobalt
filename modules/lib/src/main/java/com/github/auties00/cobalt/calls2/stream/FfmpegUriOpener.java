package com.github.auties00.cobalt.calls2.stream;

import com.github.auties00.cobalt.util.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.util.ffmpeg.Ffmpeg;
import com.github.auties00.cobalt.util.ffmpeg.FFmpegError;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Opens a media stream addressed by a URI under a timeout watchdog, shared by the URI-backed audio and
 * video sources.
 *
 * <p>The audio and video URI sources differ only in which stream they decode, which is the base class's
 * concern; how the demuxer is opened is identical, so it lives here. The open allocates the demuxer
 * context, installs the {@link FfmpegIoWatchdog} on it before connecting (the only point at which
 * libavformat adopts the interrupt callback for the connect and probe), sets the protocol timeout and
 * reconnection options, and arms the watchdog around the connect and the stream probe so neither blocks
 * past the configured timeout.
 *
 * <p>The accepted schemes are restricted to a fixed allowlist; an opaque URI, a URI with no scheme, or a
 * scheme outside the allowlist is rejected before any connection is attempted, so an embedder cannot turn
 * an arbitrary application-supplied string into a request to an unintended local file or internal service.
 */
final class FfmpegUriOpener {
    /**
     * Holds the schemes an embedder may open, rejecting every other so an application-supplied URI cannot
     * reach an unintended protocol.
     *
     * @implNote This implementation allows the local-file scheme and the network streaming schemes the
     * bundled FFmpeg build typically enables; a scheme the build does not actually carry a protocol for
     * still fails at open, so the allowlist is the outer bound rather than a guarantee of support.
     */
    private static final Set<String> PERMITTED_SCHEMES =
            Set.of("file", "http", "https", "rtsp", "rtmp", "rtmps", "srt", "tcp", "udp");

    /**
     * Holds the user-agent advertised to protocols that send one, identifying the client to a server.
     */
    private static final String USER_AGENT = "Cobalt";

    /**
     * Prevents instantiation of this static helper.
     */
    private FfmpegUriOpener() {
        throw new AssertionError("FfmpegUriOpener cannot be instantiated");
    }

    /**
     * Opens and probes the given URI under the watchdog, returning its demuxer context.
     *
     * <p>Validates the scheme and timeout, allocates the demuxer context, installs the watchdog, sets the
     * protocol timeout and resilience options, and arms the watchdog around the connect and the stream
     * probe. The option dictionary is freed on every path, including failure, so its native allocation
     * does not leak. On a watchdog-aborted connect or probe the failure message names the timeout.
     *
     * @param arena     the source's lifetime arena that owns the native allocations the open makes
     * @param watchdog  the timeout watchdog installed on the context and armed around each blocking call
     * @param uri       the media stream to open
     * @param ioTimeout the maximum time the connect, probe, or a later read may block; must be positive
     * @return the opened and probed {@code AVFormatContext} pointer, reinterpreted to its layout size
     * @throws NullPointerException     if {@code uri} or {@code ioTimeout} is {@code null}
     * @throws IllegalArgumentException if {@code ioTimeout} is not positive, or the scheme is not permitted
     * @throws IllegalStateException    if the stream cannot be opened or probed
     */
    static MemorySegment open(Arena arena, FfmpegIoWatchdog watchdog, URI uri, Duration ioTimeout) {
        requirePermittedScheme(uri);
        Objects.requireNonNull(ioTimeout, "ioTimeout cannot be null");
        if (ioTimeout.isZero() || ioTimeout.isNegative()) {
            throw new IllegalArgumentException("ioTimeout must be positive, got " + ioTimeout);
        }

        var formatCtx = FFmpegError.requireNonNull("avformat_alloc_context",
                Ffmpeg.avformat_alloc_context());
        watchdog.installOn(formatCtx);

        var formatPtr = arena.allocate(ValueLayout.ADDRESS);
        formatPtr.set(ValueLayout.ADDRESS, 0L, formatCtx);
        var url = arena.allocateFrom(uri.toString());
        var options = arena.allocate(ValueLayout.ADDRESS);
        setTimeoutOptions(arena, options, ioTimeout);
        try {
            watchdog.arm(ioTimeout);
            var opened = Ffmpeg.avformat_open_input(formatPtr, url, MemorySegment.NULL, options);
            if (opened < 0) {
                throw new IllegalStateException("avformat_open_input(" + uri + ") failed: "
                        + describeFailure(watchdog, opened, ioTimeout));
            }
            var openedCtx = formatPtr.get(ValueLayout.ADDRESS, 0L)
                    .reinterpret(AVFormatContext.layout().byteSize());
            watchdog.arm(ioTimeout);
            var probed = Ffmpeg.avformat_find_stream_info(openedCtx, MemorySegment.NULL);
            if (probed < 0) {
                throw new IllegalStateException("avformat_find_stream_info(" + uri + ") failed: "
                        + describeFailure(watchdog, probed, ioTimeout));
            }
            watchdog.disarm();
            return openedCtx;
        } finally {
            Ffmpeg.av_dict_free(options);
        }
    }

    /**
     * Describes a failed open or probe, naming a watchdog timeout where one occurred.
     *
     * @param watchdog  the watchdog whose {@linkplain FfmpegIoWatchdog#fired() fired} state distinguishes
     *                  a timeout from a protocol error
     * @param code      the negative libavformat return code
     * @param ioTimeout the configured timeout, named when the watchdog fired
     * @return a human-readable failure description
     */
    private static String describeFailure(FfmpegIoWatchdog watchdog, int code, Duration ioTimeout) {
        return watchdog.fired() ? "timed out after " + ioTimeout : FFmpegError.describe(code);
    }

    /**
     * Sets the protocol read-write timeout and connection-resilience options on the open dictionary.
     *
     * <p>Sets {@code rw_timeout} and the protocol-specific {@code timeout}, both in microseconds, as a
     * fail-fast hint that complements the watchdog, and enables transparent reconnection for streamed HTTP
     * so a transient drop is ridden out rather than ending the source.
     *
     * @param arena     the arena that owns the option key and value strings
     * @param options   the {@code AVDictionary} double pointer the options are set into
     * @param ioTimeout the timeout to express as the protocol read-write timeout; must be positive
     */
    private static void setTimeoutOptions(Arena arena, MemorySegment options, Duration ioTimeout) {
        var micros = Long.toString(Math.max(1L, ioTimeout.toNanos() / 1000L));
        setOption(arena, options, "rw_timeout", micros);
        setOption(arena, options, "timeout", micros);
        setOption(arena, options, "reconnect", "1");
        setOption(arena, options, "reconnect_streamed", "1");
        setOption(arena, options, "user_agent", USER_AGENT);
    }

    /**
     * Sets one key-value option on the open dictionary.
     *
     * @param arena   the arena that owns the key and value strings
     * @param options the {@code AVDictionary} double pointer the option is set into
     * @param key     the option name
     * @param value   the option value
     * @throws IllegalStateException if libavformat rejects the option
     */
    private static void setOption(Arena arena, MemorySegment options, String key, String value) {
        FFmpegError.check("av_dict_set(" + key + ")",
                Ffmpeg.av_dict_set(options, arena.allocateFrom(key), arena.allocateFrom(value), 0));
    }

    /**
     * Validates that the URI carries a scheme on the {@link #PERMITTED_SCHEMES} allowlist.
     *
     * @param uri the URI to validate
     * @throws NullPointerException     if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the URI has no scheme or its scheme is not permitted
     */
    private static void requirePermittedScheme(URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");
        var scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("uri has no scheme: " + uri);
        }
        if (!PERMITTED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "uri scheme '" + scheme + "' is not permitted; allowed schemes are " + PERMITTED_SCHEMES);
        }
    }
}
