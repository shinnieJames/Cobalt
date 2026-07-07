package com.github.auties00.cobalt.calls2.platform;

import com.github.auties00.cobalt.calls2.platform.VideoCaptureDriver.State;
import com.github.auties00.cobalt.calls2.platform.VideoCaptureDriver.VideoCaptureCapability;
import com.github.auties00.cobalt.calls2.platform.VideoCaptureDriver.VideoSink;
import com.github.auties00.cobalt.calls2.stream.VideoFrame;
import com.github.auties00.cobalt.calls2.stream.VideoOutput;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Production {@link DesktopCaptureDriver} backing the engine's screen-share capture onto a host video
 * source.
 *
 * <p>This driver owns the four-state lifecycle and the frame-forwarding contract of
 * {@link DesktopCaptureDriver} and pumps frames from a host {@link VideoOutput} acting as the screen
 * surface. {@link #initDriver(String, VideoSink)} binds a source for the named surface through the
 * installed {@link ScreenSourceFactory} and latches the engine sink;
 * {@link #start(VideoCaptureCapability)} spins a virtual-thread capture pump that repeatedly
 * {@linkplain VideoOutput#take() pulls} frames and forwards each through
 * {@link #sendVideoToSink(VideoFrame)}; {@link #setConfig(VideoCaptureCapability)} updates the capture
 * geometry in place and re-seeds the frame-size baseline so the next differently-sized frame is
 * accepted rather than dropped; {@link #stop()} ends the pump and the bound source. The frame-drop and
 * sink-error handling match {@link LiveVideoCaptureDriver}.
 *
 * <p>The host surface is supplied rather than opened here: an embedder or the engine installs a
 * {@link ScreenSourceFactory} that turns a surface identifier into a {@link VideoOutput}, which an
 * embedder typically fulfils with a screen-backed source from the public stream factories.
 *
 * @implNote This implementation reproduces the engine driver's guard checks and frame-drop behavior
 * from xplat/wa-voip/platforms/wasm/drivers/WasmDesktopCaptureDriver.cpp: {@code init_driver} requires
 * {@code kVoid}, {@code start} requires {@code kInitialized}, {@code set_config} is rejected while
 * {@code kVoid} (engine string "[set_config] state_ == kVoid"), {@code stop} accepts
 * {@code kRunning}/{@code kInterrupted}/{@code kInitialized}, and {@code send_video_data_to_sink} drops
 * a frame whose size changed, refuses a frame when not running or when the sink is null, and swallows a
 * throwing sink. The pjlib reader thread is replaced by a single virtual-thread pump per the Cobalt
 * threading model.
 */
public final class LiveDesktopCaptureDriver implements DesktopCaptureDriver {
    /**
     * Logs lifecycle transitions, dropped frames, and sink errors, mirroring the engine driver's
     * structured logging.
     */
    private static final System.Logger LOGGER = System.getLogger(LiveDesktopCaptureDriver.class.getName());

    /**
     * Names the result code a forwarded frame returns when the sink accepts it.
     *
     * <p>Matches the engine convention where the {@code video_sink_fn_} returns {@code 0} on success.
     */
    private static final int SINK_OK = 0;

    /**
     * Names the sentinel a dropped frame returns when it never reaches the sink.
     *
     * <p>Distinguishes a host-side drop (wrong state, no sink, or changed size) from a sink-reported
     * error, which carries the sink's own non-zero code.
     */
    private static final int SINK_DROPPED = -1;

    /**
     * Turns a surface identifier into the host {@link VideoOutput} a {@link LiveDesktopCaptureDriver}
     * captures from.
     *
     * <p>This is the seam between the driver's state machine and the concrete screen-capture backend: the
     * engine or embedder installs a factory that opens the named display, window, or capture target and
     * returns it as a {@link VideoOutput} whose {@link VideoOutput#take()} yields raw frames. It is a SAM
     * so a lambda or a method reference to a stream factory satisfies it.
     */
    @FunctionalInterface
    public interface ScreenSourceFactory {
        /**
         * Opens the named screen surface and returns it as a frame source.
         *
         * <p>Binds the operating-system display or window identified by {@code surfaceId} and returns a
         * {@link VideoOutput} whose {@link VideoOutput#take()} delivers its captured frames. The returned
         * source is owned by the driver and {@linkplain VideoOutput#shutdown() shut down} when the driver
         * stops.
         *
         * @param surfaceId the implementation-defined surface identifier; never {@code null}
         * @return the opened surface as a frame source; never {@code null}
         * @throws IllegalStateException if the surface cannot be opened
         */
        VideoOutput open(String surfaceId);
    }

    /**
     * Builds the host {@link VideoOutput} for a bound surface identifier.
     */
    private final ScreenSourceFactory sourceFactory;

    /**
     * Guards every state transition and the {@code state}/{@code sink}/{@code source}/{@code lastWidth}/
     * {@code lastHeight} fields against the engine driver and the capture pump racing.
     */
    private final ReentrantLock lock;

    /**
     * Holds the current lifecycle state, only ever mutated under {@link #lock}.
     */
    private State state;

    /**
     * Holds the latched engine sink, or {@code null} before {@link #initDriver(String, VideoSink)} and
     * after a discard.
     */
    private VideoSink sink;

    /**
     * Holds the bound host frame source, or {@code null} while {@link State#VOID}.
     */
    private VideoOutput source;

    /**
     * Holds the capture pump thread spun by {@link #start(VideoCaptureCapability)}, or {@code null} while
     * not {@link State#RUNNING}.
     */
    private Thread pump;

    /**
     * Holds the width of the most recently forwarded frame, or {@code -1} before any frame is forwarded
     * or right after a {@link #setConfig(VideoCaptureCapability)} re-seeds the baseline.
     */
    private int lastWidth;

    /**
     * Holds the height of the most recently forwarded frame, or {@code -1} before any frame is forwarded
     * or right after a {@link #setConfig(VideoCaptureCapability)} re-seeds the baseline.
     */
    private int lastHeight;

    /**
     * Creates a screen-share capture driver that binds its host source through the given factory.
     *
     * <p>The driver starts in {@link State#VOID} holding no surface and no sink.
     *
     * @param sourceFactory the factory that opens a named surface as a {@link VideoOutput}; never
     *                      {@code null}
     * @throws NullPointerException if {@code sourceFactory} is {@code null}
     */
    public LiveDesktopCaptureDriver(ScreenSourceFactory sourceFactory) {
        this.sourceFactory = Objects.requireNonNull(sourceFactory, "sourceFactory cannot be null");
        this.lock = new ReentrantLock();
        this.state = State.VOID;
        this.lastWidth = -1;
        this.lastHeight = -1;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public State state() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param surfaceId {@inheritDoc}
     * @param sink      {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void initDriver(String surfaceId, VideoSink sink) {
        Objects.requireNonNull(surfaceId, "surfaceId cannot be null");
        Objects.requireNonNull(sink, "sink cannot be null");
        lock.lock();
        try {
            if (state != State.VOID) {
                throw new IllegalStateException("init_driver requires VOID, was " + state);
            }
            this.source = Objects.requireNonNull(sourceFactory.open(surfaceId),
                    "screen source factory returned null");
            this.sink = sink;
            this.lastWidth = -1;
            this.lastHeight = -1;
            this.state = State.INITIALIZED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param capability {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void start(VideoCaptureCapability capability) {
        Objects.requireNonNull(capability, "capability cannot be null");
        lock.lock();
        try {
            if (state != State.INITIALIZED) {
                throw new IllegalStateException("start requires INITIALIZED, was " + state);
            }
            this.state = State.RUNNING;
            this.pump = Thread.ofVirtual()
                    .name("calls2-desktop-capture-" + capability.width() + "x" + capability.height())
                    .start(this::pumpLoop);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param capability {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void setConfig(VideoCaptureCapability capability) {
        Objects.requireNonNull(capability, "capability cannot be null");
        lock.lock();
        try {
            if (state == State.VOID) {
                throw new IllegalStateException("set_config requires a bound surface, was VOID");
            }
            this.lastWidth = -1;
            this.lastHeight = -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param frame {@inheritDoc}
     * @return {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public int sendVideoToSink(VideoFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        VideoSink target;
        lock.lock();
        try {
            if (state != State.RUNNING) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "send_video_data_to_sink dropped: driver not running, state " + state);
                return SINK_DROPPED;
            }
            if (sink == null) {
                LOGGER.log(System.Logger.Level.WARNING, "send_video_data_to_sink dropped: sink is null");
                return SINK_DROPPED;
            }
            if (lastWidth != -1 && (frame.width() != lastWidth || frame.height() != lastHeight)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "send_video_data_to_sink dropped: frame size changed from "
                                + lastWidth + "x" + lastHeight + " to "
                                + frame.width() + "x" + frame.height());
                return SINK_DROPPED;
            }
            this.lastWidth = frame.width();
            this.lastHeight = frame.height();
            target = sink;
        } finally {
            lock.unlock();
        }
        try {
            var code = target.accept(frame);
            if (code != SINK_OK) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "send_video_data_to_sink: sink returned error code " + code);
            }
            return code;
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "send_video_data_to_sink: exception calling sink", e);
            return SINK_DROPPED;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void stop() {
        Thread toJoin;
        VideoOutput toClose;
        lock.lock();
        try {
            if (state == State.VOID) {
                throw new IllegalStateException("stop requires INITIALIZED, RUNNING, or INTERRUPTED");
            }
            if (state == State.INITIALIZED) {
                return;
            }
            this.state = State.INITIALIZED;
            toJoin = pump;
            this.pump = null;
            this.lastWidth = -1;
            this.lastHeight = -1;
            toClose = source;
            this.source = null;
        } finally {
            lock.unlock();
        }
        if (toJoin != null) {
            toJoin.interrupt();
        }
        if (toClose != null) {
            toClose.shutdown();
        }
    }

    /**
     * Pulls frames from the bound source and forwards them until the driver leaves {@link State#RUNNING}.
     *
     * <p>Runs on the capture pump virtual thread spun by {@link #start(VideoCaptureCapability)}. Each
     * iteration {@linkplain VideoOutput#take() takes} the next frame from the source and forwards it
     * through {@link #sendVideoToSink(VideoFrame)}; a {@code null} frame signals the source ended and
     * exits the loop, and an interrupt from {@link #stop()} exits the loop. The loop reads {@code state}
     * and {@code source} under {@link #lock} so a concurrent {@link #stop()} is observed cleanly.
     */
    private void pumpLoop() {
        while (true) {
            VideoOutput current;
            lock.lock();
            try {
                if (state != State.RUNNING) {
                    return;
                }
                current = source;
            } finally {
                lock.unlock();
            }
            if (current == null) {
                return;
            }
            VideoFrame frame;
            try {
                frame = current.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // TODO: surface an OS surface revocation (user stops sharing / shared window closes) under a
            //  running capture as State.INTERRUPTED. The recovered WASM WasmDesktopCaptureDriver
            //  (drivers/WasmDesktopCaptureDriver.cpp, fns 11879/11881/11882/11884/11886/11890) never
            //  writes state_ = kInterrupted in any of its methods, including set_config; kInterrupted
            //  exists only so stop() tolerates it (the "[stop] state_ != kRunning, kInterrupted,
            //  kInitialized" guard), and the spec state-machine table has no transition whose target is
            //  kInterrupted (rev-platforms-drivers.json; SPEC.md marks the kRunning->kInterrupted edge
            //  "OPEN"). The "interrupted" machinery that IS in this build is the call/peer layer (peer
            //  interrupted by phone call), not the driver-level surface-revocation state, and no host
            //  downcall to interrupt the driver exists in the import table. Wiring this edge on a null
            //  take() (end-of-stream) or a capture fault would invent a transition WhatsApp's WASM does
            //  not have, so this loop exits cleanly instead; resolve only once a capture backend exposes
            //  a distinguishable surface-revocation signal and the engine-vs-host owner of the edge is
            //  recovered.
            if (frame == null) {
                return;
            }
            sendVideoToSink(frame);
        }
    }
}
