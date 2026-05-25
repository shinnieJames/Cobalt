package com.github.auties00.cobalt.call.internal.video;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.frame.video.VideoFrame;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bridges the raw-I420 video ports of an {@link ActiveCall} and the encoded {@link VideoPacket} ports
 * of the RTP transport, running both directions concurrently.
 *
 * <p>On the outbound path the pipeline drains one I420 {@link VideoFrame} from
 * {@link ActiveCall#takeOutboundVideo()}, forces a keyframe when {@link #requestKeyframe()} has been
 * called since the previous frame, encodes through the {@link VideoCodec} (which for VP8 may emit
 * any number of packets and for H.264 emits at most one), and hands each resulting
 * {@link VideoPacket} to the outbound sink wired to the RTP transport. On the inbound path the RTP
 * layer hands a decoded {@link VideoPacket} to {@link #feedInboundPacket(VideoPacket)}, the codec
 * decodes it to I420, and the resulting {@link VideoFrame} is pushed to
 * {@link ActiveCall#deliverInboundVideo(VideoFrame)} for rendering. The BWE driver reconfigures the
 * encoder at runtime through {@link #adjustBitrate(int)}.
 *
 * <p>The pipeline owns two virtual threads: a capture thread that drains
 * {@link ActiveCall#takeOutboundVideo()} and encodes, and a decoder thread that drains the inbound
 * queue and decodes. The encoder is touched only from the capture thread and the decoder only from
 * the decoder thread, so the two independent codec halves never contend. {@link #close()} interrupts
 * both threads, joins them, and frees the codec, and is idempotent.
 *
 * @implNote This implementation deliberately swallows per-frame {@link RuntimeException}s from the
 * codec and the outbound sink rather than tearing the call down: a single bad frame must not end an
 * otherwise healthy call, and a failed forced-keyframe encode leaves the keyframe-pending flag set so
 * the next frame retries.
 */
public final class VideoPipeline implements AutoCloseable {
    /**
     * Holds the sentinel packet used to wake the decoder thread on shutdown.
     *
     * <p>{@link #close()} offers this instance into {@link #inboundPackets} so the decoder thread
     * unblocks from {@code take()} and exits; the decoder loop compares by identity, so its content
     * is never decoded.
     */
    private static final VideoPacket SENTINEL =
            new VideoPacket(new byte[]{0}, Long.MIN_VALUE, 1, 1, false);

    /**
     * Holds the call whose video this pipeline drives.
     */
    private final ActiveCall call;

    /**
     * Holds the consumer that receives encoded outbound packets.
     *
     * <p>Wired to the RTP transport's send side by the call layer.
     */
    private final Consumer<VideoPacket> outboundSink;

    /**
     * Holds the active configuration, fixed at construction.
     */
    private final VideoPipelineOptions options;

    /**
     * Holds the codec that owns the encoder and decoder.
     */
    private final VideoCodec codec;

    /**
     * Holds the inbound packet queue, fed by {@link #feedInboundPacket(VideoPacket)} and drained by
     * the decoder thread.
     */
    private final LinkedBlockingQueue<VideoPacket> inboundPackets = new LinkedBlockingQueue<>();

    /**
     * Holds whether the next encoded frame must be a forced keyframe.
     *
     * <p>Set by {@link #requestKeyframe()} and cleared once a keyframe has been driven into the
     * encoder.
     */
    private final AtomicBoolean keyframePending;

    /**
     * Holds the capture thread that drains {@link ActiveCall#takeOutboundVideo()} and encodes;
     * {@code null} until {@link #start()}.
     */
    private Thread captureThread;

    /**
     * Holds the decoder thread that drains {@link #inboundPackets} and pushes frames back into the
     * call; {@code null} until {@link #start()}.
     */
    private Thread decoderThread;

    /**
     * Holds whether the pipeline is running, {@code true} between {@link #start()} and
     * {@link #close()}.
     */
    private volatile boolean running;

    /**
     * Constructs a new pipeline over the given call, sink, codec, and options.
     *
     * <p>The codec is supplied already allocated so allocation failures surface at the call site
     * rather than on the first frame. The codec's resolution must match the options; the pipeline
     * takes ownership of the codec and closes it on {@link #close()}.
     *
     * @param call         the {@link ActiveCall} to drive
     * @param outboundSink the consumer for encoded outbound packets
     * @param codec        the codec adapter, whose lifetime the pipeline takes over
     * @param options      the configuration, typically {@link VideoPipelineOptions#defaults()}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the codec's width or height disagrees with {@code options}
     */
    public VideoPipeline(ActiveCall call, Consumer<VideoPacket> outboundSink,
                         VideoCodec codec, VideoPipelineOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.outboundSink = Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        if (codec.width() != options.width() || codec.height() != options.height()) {
            throw new IllegalArgumentException(
                    "codec resolution " + codec.width() + "x" + codec.height()
                            + " does not match options " + options.width() + "x" + options.height());
        }
        this.keyframePending = new AtomicBoolean(options.keyframeOnStart());
    }

    /**
     * Returns the active configuration.
     *
     * @return the options supplied at construction
     */
    public VideoPipelineOptions options() {
        return options;
    }

    /**
     * Returns the codec adapter.
     *
     * <p>Exposed so callers can pattern-match for codec-specific tuning. The codec's lifetime is
     * bounded by this pipeline and callers must not close it directly.
     *
     * @return the codec
     */
    public VideoCodec codec() {
        return codec;
    }

    /**
     * Starts the capture and decoder virtual threads.
     *
     * <p>Idempotent; subsequent calls while running are no-ops.
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        captureThread = Thread.ofVirtual()
                .name("video-pipeline-capture")
                .start(this::captureLoop);
        decoderThread = Thread.ofVirtual()
                .name("video-pipeline-decode")
                .start(this::decoderLoop);
    }

    /**
     * Hands one inbound encoded packet to the decoder thread and returns immediately.
     *
     * <p>The packet is enqueued for the decoder thread. If the pipeline is not running or has been
     * closed, the packet is silently dropped.
     *
     * @param packet the packet from the RTP layer
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    public void feedInboundPacket(VideoPacket packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (!running) {
            return;
        }
        inboundPackets.offer(packet);
    }

    /**
     * Marks the next outbound frame as needing to be a keyframe.
     *
     * <p>Drivers such as an RTCP PLI/FIR receiver or an app-level I-frame request call this; the
     * keyframe is emitted at the next encode boundary, not retroactively.
     */
    public void requestKeyframe() {
        keyframePending.set(true);
    }

    /**
     * Pushes a new target bitrate into the encoder.
     *
     * <p>Drivers such as a GCC trendline estimator or a REMB receiver call this with the latest
     * bandwidth estimate; the change takes effect from the next encoded frame.
     *
     * @param targetBitrateBps the new target bitrate in bits per second; must be at least {@code 1}
     * @throws IllegalArgumentException if {@code targetBitrateBps} is less than {@code 1}
     */
    public void adjustBitrate(int targetBitrateBps) {
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be >= 1");
        }
        codec.setBitrate(targetBitrateBps);
    }

    /**
     * Stops the capture and decoder threads and frees the codec.
     *
     * <p>Clears the running flag, wakes the decoder thread with {@link #SENTINEL}, interrupts and
     * joins both threads with a bounded wait, then closes the codec. Idempotent; when already stopped
     * it just closes the codec.
     */
    @Override
    public synchronized void close() {
        if (!running) {
            try {
                codec.close();
            } catch (Throwable _) {
            }
            return;
        }
        running = false;
        inboundPackets.offer(SENTINEL);
        if (captureThread != null) {
            captureThread.interrupt();
        }
        try {
            if (captureThread != null) captureThread.join(2_000);
            if (decoderThread != null) decoderThread.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            codec.close();
        } catch (Throwable _) {
        }
    }

    /**
     * Runs the capture loop body on {@link #captureThread} until the call ends or {@link #close()}
     * interrupts.
     *
     * <p>Each iteration takes one outbound {@link VideoFrame}, discards it if its I420 length does not
     * match {@link VideoCodec#frameByteSize()}, encodes it (forcing a keyframe when one is pending),
     * and forwards each resulting packet to the outbound sink. A {@code null} frame ends the loop. A
     * failed encode of a forced keyframe re-arms {@link #keyframePending} so the next frame retries.
     */
    private void captureLoop() {
        while (running) {
            VideoFrame frame;
            try {
                frame = call.takeOutboundVideo();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (frame == null) {
                return;
            }
            if (frame.yuvI420().length != codec.frameByteSize()) {
                continue;
            }
            var forceKey = keyframePending.compareAndSet(true, false);
            try {
                var packets = codec.encode(frame.yuvI420(), frame.ptsMs(), forceKey);
                for (var packet : packets) {
                    try {
                        outboundSink.accept(packet);
                    } catch (RuntimeException _) {
                    }
                }
            } catch (RuntimeException _) {
                if (forceKey) {
                    keyframePending.set(true);
                }
            }
        }
    }

    /**
     * Runs the decoder loop body on {@link #decoderThread} until {@link #SENTINEL} arrives.
     *
     * <p>Each iteration blocks for one inbound packet, exits on the sentinel, decodes the packet, and
     * delivers any resulting {@link VideoFrame} to {@link ActiveCall#deliverInboundVideo(VideoFrame)}.
     * Packets that decode to {@code null} or raise a {@link RuntimeException} are skipped.
     */
    private void decoderLoop() {
        while (running) {
            VideoPacket packet;
            try {
                packet = inboundPackets.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (packet == SENTINEL) {
                return;
            }
            VideoFrame decoded;
            try {
                decoded = codec.decode(packet.payload(), packet.ptsMs());
            } catch (RuntimeException _) {
                continue;
            }
            if (decoded == null) {
                continue;
            }
            try {
                call.deliverInboundVideo(decoded);
            } catch (RuntimeException _) {
            }
        }
    }
}
