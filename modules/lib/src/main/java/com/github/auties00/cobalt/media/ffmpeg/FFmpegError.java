package com.github.auties00.cobalt.media.ffmpeg;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Helpers for translating libav* {@code int} return codes into
 * Java exceptions with human-readable messages.
 *
 * <p>FFmpeg's convention is that any negative return value is an
 * error code; positive values denote success or, for some calls,
 * the number of bytes / frames written. The error code itself is a
 * negated {@code errno} or one of the {@code AVERROR_*} sentinels;
 * {@code av_strerror(int, char*, size_t)} resolves it to a string.
 */
public final class FFmpegError {
    /**
     * Buffer size used when calling {@code av_strerror}. 256 bytes
     * is what FFmpeg's own examples allocate.
     */
    private static final int ERROR_BUFFER_SIZE = 256;

    /**
     * Prevents instantiation.
     */
    private FFmpegError() {
        throw new AssertionError("FFmpegError is not instantiable");
    }

    /**
     * Throws an {@link IllegalStateException} if {@code result} is
     * negative, with a message formed from the FFmpeg error string.
     *
     * @param context human-readable description of what call
     *                produced the result (used in the error
     *                message)
     * @param result  the libav* return code
     * @return the result (returned for fluent chaining when
     *         non-negative)
     */
    public static int check(String context, int result) {
        if (result < 0) {
            throw new IllegalStateException(context + " failed: " + describe(result));
        }
        return result;
    }

    /**
     * Returns the FFmpeg error string for a libav* error code.
     *
     * @param code the error code
     * @return the error string, or {@code "errno=" + code} if the
     *         lookup itself fails
     */
    public static String describe(int code) {
        try (var arena = Arena.ofConfined()) {
            var buf = arena.allocate(ERROR_BUFFER_SIZE);
            if (Ffmpeg.av_strerror(code, buf, ERROR_BUFFER_SIZE) < 0) {
                return "errno=" + code;
            }
            return buf.getString(0L) + " (" + code + ")";
        }
    }

    /**
     * Returns whether a libav* return code denotes the EOF
     * sentinel — the canonical way callers signal "end of stream"
     * to the {@link com.github.auties00.cobalt.call.frame.audio.AudioSource} /
     * {@link com.github.auties00.cobalt.call.frame.video.VideoSource}
     * contract by returning {@code null}.
     *
     * @param code the libav* return code
     * @return {@code true} if {@code code == AVERROR_EOF}
     */
    public static boolean isEof(int code) {
        return code == Ffmpeg.AVERROR_EOF();
    }

    /**
     * The platform's {@code EAGAIN} value, used to recognise
     * libav*'s {@code AVERROR(EAGAIN)} return — the "I need more
     * input before producing output" signal from
     * {@code avcodec_receive_frame} / {@code avcodec_receive_packet}.
     * macOS / FreeBSD set EAGAIN to 35; Linux / Windows / *BSD set
     * it to 11.
     */
    private static final int EAGAIN_VALUE =
            System.getProperty("os.name", "").toLowerCase().contains("mac") ? 35 : 11;

    /**
     * Returns whether a libav* return code denotes
     * {@code AVERROR(EAGAIN)} — a normal flow-control signal from
     * the codec pipeline meaning "send more input first".
     *
     * @param code the libav* return code
     * @return {@code true} if {@code code == -EAGAIN}
     */
    public static boolean isAgain(int code) {
        return code == -EAGAIN_VALUE;
    }

    /**
     * Throws if the given pointer is {@link MemorySegment#NULL} —
     * libav* allocators return null when they fail
     * (no exception, no errno).
     *
     * @param context human-readable description of the call
     * @param ptr     the segment to check
     * @return the segment (returned for fluent chaining)
     */
    public static MemorySegment requireNonNull(String context, MemorySegment ptr) {
        if (ptr == null || ptr.address() == 0L) {
            throw new IllegalStateException(context + " returned null");
        }
        return ptr;
    }
}
