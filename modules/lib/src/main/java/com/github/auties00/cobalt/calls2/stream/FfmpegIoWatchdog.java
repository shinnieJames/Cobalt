package com.github.auties00.cobalt.calls2.stream;

import com.github.auties00.cobalt.util.ffmpeg.AVFormatContext;
import com.github.auties00.cobalt.util.ffmpeg.AVIOInterruptCB;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Objects;

/**
 * Bounds blocking FFmpeg input operations with an {@code AVIOInterruptCB} deadline watchdog.
 *
 * <p>FFmpeg polls the installed callback during every blocking demux or protocol operation, including
 * {@code avformat_open_input}, {@code avformat_find_stream_info}, and each {@code av_read_frame}. The
 * callback returns non-zero to abort the current operation once the armed deadline has elapsed or the
 * owner has {@linkplain #cancel() cancelled} the source. This is the protocol-agnostic hard timeout a
 * network-backed media source needs so that a stalled connect or a mid-stream read does not block the
 * call's decode thread forever; a local-file source never installs or arms one.
 *
 * <p>The deadline is written by {@link #arm(Duration)} and {@link #disarm()} on the same thread that
 * drives the blocking FFmpeg call, immediately before and after that call, so arming is lock-free. The
 * cancel flag is set from another thread by {@link #cancel()} during teardown and is read by the
 * callback, so it is {@code volatile}; setting it unblocks an in-flight read promptly. The native upcall
 * stub is allocated in the supplied {@link Arena}, so the arena must outlive every FFmpeg call that can
 * invoke the callback: the owning source frees that arena only after it has cancelled and joined its
 * decode work.
 */
final class FfmpegIoWatchdog {
    /**
     * The disarmed deadline sentinel, far enough in the future that the callback never trips on it.
     *
     * <p>A disarmed watchdog leaves {@link #deadlineNanos} at this value, so the callback's elapsed-time
     * comparison is always negative and it returns {@code 0} (continue) until the source is armed or
     * cancelled.
     */
    private static final long NO_DEADLINE = Long.MAX_VALUE;

    /**
     * The native {@code int (*)(void*)} upcall stub FFmpeg invokes during blocking input, bound to this
     * watchdog's deadline check and owned by the source's arena.
     */
    private final MemorySegment callbackStub;

    /**
     * The {@link System#nanoTime()} instant at which the current operation is due to abort, or
     * {@link #NO_DEADLINE} while disarmed.
     *
     * <p>Written on the decode thread by {@link #arm(Duration)} and {@link #disarm()} just around a
     * blocking call and read by the callback on that same thread.
     */
    private volatile long deadlineNanos = NO_DEADLINE;

    /**
     * Whether the source has been permanently cancelled, after which the callback aborts every operation.
     */
    private volatile boolean cancelled;

    /**
     * Whether the most recently armed window aborted a blocking call, distinguishing a timeout abort from
     * a clean end-of-stream.
     *
     * <p>Reset by {@link #arm(Duration)} and set by the callback when it trips, so the decode loop can
     * tell a watchdog-induced {@code av_read_frame} failure apart from a genuine end-of-input, both of
     * which surface as a negative return code.
     */
    private volatile boolean fired;

    /**
     * Allocates the interrupt-callback upcall stub bound to this watchdog's deadline check.
     *
     * <p>The callback returns {@code 1} to abort when the source has been cancelled or the armed deadline
     * has elapsed, and {@code 0} to continue otherwise. The stub lives for the lifetime of {@code arena},
     * which the owning source closes only after it stops issuing FFmpeg calls.
     *
     * @param arena the source's lifetime arena that owns the upcall stub
     * @throws NullPointerException if {@code arena} is {@code null}
     */
    FfmpegIoWatchdog(Arena arena) {
        Objects.requireNonNull(arena, "arena cannot be null");
        AVIOInterruptCB.callback.Function function = opaque -> {
            if (cancelled || System.nanoTime() - deadlineNanos >= 0) {
                fired = true;
                return 1;
            }
            return 0;
        };
        this.callbackStub = AVIOInterruptCB.callback.allocate(function, arena);
    }

    /**
     * Installs this watchdog's callback into a freshly allocated demuxer context before it is opened.
     *
     * <p>Copies the upcall stub into the context's embedded {@code interrupt_callback} struct so the
     * subsequent {@code avformat_open_input}, {@code avformat_find_stream_info}, and {@code av_read_frame}
     * calls on that context poll this watchdog. Must be called on a context obtained from
     * {@code avformat_alloc_context}, before {@code avformat_open_input}, since open honors a callback only
     * when it is already present on the passed-in context.
     *
     * @param formatCtx the demuxer context to install the callback on
     * @throws NullPointerException if {@code formatCtx} is {@code null}
     */
    void installOn(MemorySegment formatCtx) {
        Objects.requireNonNull(formatCtx, "formatCtx cannot be null");
        var callback = AVFormatContext.interrupt_callback(formatCtx);
        AVIOInterruptCB.callback(callback, callbackStub);
        AVIOInterruptCB.opaque(callback, MemorySegment.NULL);
    }

    /**
     * Starts a fresh timeout window for the blocking call about to be issued.
     *
     * <p>Clears the {@linkplain #fired() fired} flag and sets the deadline to the given duration from now.
     * Call it immediately before a blocking FFmpeg input call and pair it with {@link #disarm()} once the
     * call returns.
     *
     * @param timeout the maximum time the upcoming call may block; must be positive
     * @throws NullPointerException     if {@code timeout} is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is zero or negative
     */
    void arm(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive, got " + timeout);
        }
        fired = false;
        deadlineNanos = System.nanoTime() + timeout.toNanos();
    }

    /**
     * Clears the timeout window after a blocking call has returned, so a later non-FFmpeg pause does not
     * trip the callback.
     */
    void disarm() {
        deadlineNanos = NO_DEADLINE;
    }

    /**
     * Returns whether the most recently armed window aborted its blocking call.
     *
     * <p>Read after a blocking call returns a failure to tell a timeout abort apart from a clean
     * end-of-input.
     *
     * @return {@code true} if the callback tripped the deadline during the last armed window
     */
    boolean fired() {
        return fired;
    }

    /**
     * Permanently aborts any current and future blocking call on the watched context.
     *
     * <p>Called during source teardown so a decode thread parked inside {@code av_read_frame} returns at
     * once instead of waiting out its full timeout, letting the owner release the native context promptly.
     */
    void cancel() {
        cancelled = true;
    }
}
