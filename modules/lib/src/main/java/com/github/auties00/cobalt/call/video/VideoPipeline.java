package com.github.auties00.cobalt.call.video;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.io.VideoFrame;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Full-duplex video pipeline — bridges the raw-I420 ports on
 * {@link ActiveCall} and the encoded {@link VideoPacket} ports of the
 * RTP transport (#78).
 *
 * <h2>Outbound (capture → wire)</h2>
 *
 * <ol>
 *   <li>{@link ActiveCall#takeOutboundVideo()} yields one I420
 *       {@link VideoFrame}.</li>
 *   <li>If {@link #requestKeyframe()} was called since the last
 *       outbound frame, the next encode forces a keyframe.</li>
 *   <li>The {@link VideoCodec} (VP8 or H.264) encodes the frame —
 *       VP8 may emit zero, one, or many packets per call; H.264
 *       emits at most one.</li>
 *   <li>Each encoded {@link VideoPacket} is handed to
 *       {@code outboundSink} — typically the RTP transport's send
 *       side.</li>
 * </ol>
 *
 * <h2>Inbound (wire → render)</h2>
 *
 * <ol>
 *   <li>The RTP layer hands a decoded {@link VideoPacket} to
 *       {@link #feedInboundPacket}.</li>
 *   <li>The codec decodes the packet to I420.</li>
 *   <li>The decoded {@link VideoFrame} is pushed to
 *       {@link ActiveCall#deliverInboundVideo} for the app/companion
 *       module to render.</li>
 * </ol>
 *
 * <h2>BWE</h2>
 *
 * <p>{@link #adjustBitrate(int)} reconfigures the encoder's target
 * bitrate at runtime. Drivers (like the GCC trendline estimator from
 * #54) call this with a fresh estimate; the codec adapter pushes the
 * change down to libvpx via {@code vpx_codec_enc_config_set} (or a
 * no-op for codecs that don't yet expose runtime adjustment).
 *
 * <h2>Threading</h2>
 *
 * <p>The pipeline owns two virtual threads: a capture thread (drains
 * {@code takeOutboundVideo()}) and a decoder thread (drains the
 * inbound packet queue). The codec is touched only from the capture
 * thread and the decoder thread, never both for the same operation —
 * encoder and decoder are independent halves.
 *
 * <p>{@link #close()} interrupts both threads, joins them, and frees
 * the codec. Idempotent.
 */
public final class VideoPipeline implements AutoCloseable {
    /**
     * Sentinel pushed into {@link #inboundPackets} by
     * {@link #close()} so the decoder thread unblocks from
     * {@code take()} and exits cleanly.
     */
    private static final VideoPacket SENTINEL =
            new VideoPacket(new byte[]{0}, Long.MIN_VALUE, 1, 1, false);

    /**
     * The call whose video we're driving.
     */
    private final ActiveCall call;

    /**
     * Where outbound encoded packets are delivered. Wired to the RTP
     * transport's send side by the call layer.
     */
    private final Consumer<VideoPacket> outboundSink;

    /**
     * Active configuration — never mutated after construction.
     */
    private final VideoPipelineOptions options;

    /**
     * The codec (VP8 or H.264) that owns the encoder and decoder.
     */
    private final VideoCodec codec;

    /**
     * Inbound packet queue — fed by {@link #feedInboundPacket},
     * drained by the decoder thread.
     */
    private final LinkedBlockingQueue<VideoPacket> inboundPackets = new LinkedBlockingQueue<>();

    /**
     * Set by {@link #requestKeyframe} so the next encoded frame
     * carries a forced keyframe; cleared after one keyframe has been
     * driven into the encoder.
     */
    private final AtomicBoolean keyframePending;

    /**
     * Capture thread — drains {@link ActiveCall#takeOutboundVideo}
     * and encodes; {@code null} until {@link #start()}.
     */
    private Thread captureThread;

    /**
     * Decoder thread — drains {@link #inboundPackets} and pushes
     * back into the call; {@code null} until {@link #start()}.
     */
    private Thread decoderThread;

    /**
     * {@code true} after {@link #start()} returns and before
     * {@link #close()}.
     */
    private volatile boolean running;

    /**
     * Constructs a new pipeline. The {@link VideoCodec} is allocated
     * eagerly so failures surface here, not on the first frame.
     *
     * @param call         the {@link ActiveCall} to drive
     * @param outboundSink consumer for encoded outbound packets
     * @param codec        the codec adapter; the pipeline takes
     *                     ownership and closes it on {@link #close()}
     * @param options      configuration; see
     *                     {@link VideoPipelineOptions#defaults()}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code codec}'s width or
     *                                  height disagrees with
     *                                  {@code options}
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
     * @return the options used at construction
     */
    public VideoPipelineOptions options() {
        return options;
    }

    /**
     * Returns the codec adapter — exposed so callers can pattern-match
     * on it for codec-specific tuning. Lifetime is bounded by this
     * pipeline; do not close the codec directly.
     *
     * @return the codec
     */
    public VideoCodec codec() {
        return codec;
    }

    /**
     * Starts the capture and decoder virtual threads. Idempotent —
     * subsequent calls are no-ops.
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
     * Hands one inbound encoded packet to the decoder thread.
     * Returns immediately. If the pipeline isn't running or has been
     * closed, the packet is silently dropped.
     *
     * @param packet the packet from the RTP layer; never {@code null}
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
     * Drivers (RTCP PLI/FIR receiver, app-level "I-frame request"
     * UI, etc.) call this; the keyframe is emitted at the next
     * encode boundary, not retroactively.
     */
    public void requestKeyframe() {
        keyframePending.set(true);
    }

    /**
     * Pushes a new target bitrate into the encoder. Drivers (GCC
     * trendline estimator, REMB receiver, etc.) call this with the
     * latest BWE.
     *
     * @param targetBitrateBps the new target bitrate in bits per
     *                         second; must be &gt;= 1
     */
    public void adjustBitrate(int targetBitrateBps) {
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
        }
        codec.setBitrate(targetBitrateBps);
    }

    /**
     * Stops the threads and frees the codec. Idempotent.
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
     * Capture loop body — runs on {@link #captureThread} until the
     * call ends or {@link #close()} interrupts.
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
            boolean forceKey = keyframePending.compareAndSet(true, false);
            try {
                var packets = codec.encode(frame.yuvI420(), frame.ptsMs(), forceKey);
                for (var packet : packets) {
                    try {
                        outboundSink.accept(packet);
                    } catch (RuntimeException _) {
                    }
                }
            } catch (RuntimeException _) {
                // If a keyframe was requested, leave the flag set so
                // the next encode retries.
                if (forceKey) {
                    keyframePending.set(true);
                }
            }
        }
    }

    /**
     * Decoder loop body — runs on {@link #decoderThread} until the
     * sentinel arrives.
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
