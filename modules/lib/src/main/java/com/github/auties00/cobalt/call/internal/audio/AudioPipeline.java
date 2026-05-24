package com.github.auties00.cobalt.call.internal.audio;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusDecoder;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusEncoder;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusPacket;
import com.github.auties00.cobalt.call.internal.audio.processing.AudioPreprocessor;
import com.github.auties00.cobalt.call.internal.audio.processing.EchoCanceller;
import com.github.auties00.cobalt.util.DataUtils;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Full-duplex audio pipeline — bridges the raw-PCM ports on
 * {@link ActiveCall} and the encoded {@link OpusPacket} ports of the
 * RTP transport (#78).
 *
 * <h2>Outbound (capture → wire)</h2>
 *
 * <ol>
 *   <li>{@link ActiveCall#takeOutboundAudio()} yields a 10 ms mic
 *       {@link AudioFrame}.</li>
 *   <li>The frame is run through the speexdsp
 *       {@link EchoCanceller} against the latest decoded far-end PCM
 *       (the residual echo from the speaker leaking back into the
 *       mic).</li>
 *   <li>The cleaned PCM is run through the speexdsp
 *       {@link AudioPreprocessor} for denoise + AGC + VAD; the VAD
 *       result becomes {@link OpusPacket#voiceActive()}.</li>
 *   <li>libopus encodes the resulting frame.</li>
 *   <li>The encoded {@link OpusPacket} is handed to the
 *       {@code outboundSink} {@link Consumer} — typically wired to
 *       the RTP transport's send side.</li>
 * </ol>
 *
 * <h2>Inbound (wire → playback)</h2>
 *
 * <ol>
 *   <li>The RTP layer hands a decoded {@link OpusPacket} to
 *       {@link #feedInboundPacket}.</li>
 *   <li>libopus decodes the packet to PCM.</li>
 *   <li>The PCM becomes the latest far-end reference for the AEC,
 *       so the next mic frame can subtract it.</li>
 *   <li>The PCM is wrapped as an {@link AudioFrame} and pushed to
 *       {@link ActiveCall#deliverInboundAudio} for the app/companion
 *       module to play.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 *
 * <p>The pipeline owns two virtual threads that {@link #start()}
 * spawns: a capture thread (drains the call's outbound queue) and a
 * decoder thread (drains the inbound packet queue). speexdsp is not
 * thread-safe, so the AEC + preprocessor are touched only from the
 * capture thread; the decoder thread updates the AEC far-reference
 * via an {@link AtomicReference}, which the capture thread reads.
 *
 * <p>{@link #close()} interrupts both threads, joins them, and frees
 * the native codec/AEC/preprocessor resources. Idempotent.
 */
public final class AudioPipeline implements AutoCloseable {
    /**
     * Sentinel pushed into {@link #inboundPackets} by
     * {@link #close()} so the decoder thread unblocks from
     * {@code take()} and exits cleanly.
     */
    private static final OpusPacket SENTINEL =
            new OpusPacket(DataUtils.EMPTY_BYTE_ARRAY, Long.MIN_VALUE, false);

    /**
     * The call whose audio we're driving.
     */
    private final ActiveCall call;

    /**
     * Where outbound encoded packets are delivered. Wired to the RTP
     * transport's send side by the call layer.
     */
    private final Consumer<OpusPacket> outboundSink;

    /**
     * Active configuration — never mutated after construction.
     */
    private final AudioPipelineOptions options;

    /**
     * Opus encoder used by the capture thread.
     */
    private final OpusEncoder encoder;

    /**
     * Opus decoder used by the decoder thread.
     */
    private final OpusDecoder decoder;

    /**
     * Speexdsp echo canceller — null when AEC is disabled.
     */
    private final EchoCanceller aec;

    /**
     * Speexdsp preprocessor — null when no preprocessor feature
     * (denoise/AGC/VAD) is enabled.
     */
    private final AudioPreprocessor preprocessor;

    /**
     * Latest decoded far-end PCM frame, used as the AEC reference
     * when the next mic frame arrives. Updated by the decoder
     * thread, read by the capture thread.
     */
    private final AtomicReference<short[]> latestFarEnd = new AtomicReference<>();

    /**
     * Inbound packet queue — fed by {@link #feedInboundPacket}, drained
     * by the decoder thread.
     */
    private final LinkedBlockingQueue<OpusPacket> inboundPackets = new LinkedBlockingQueue<>();

    /**
     * Capture thread — drains {@link ActiveCall#takeOutboundAudio} and
     * encodes; {@code null} until {@link #start()}.
     */
    private Thread captureThread;

    /**
     * Decoder thread — drains {@link #inboundPackets} and pushes PCM
     * back into the call; {@code null} until {@link #start()}.
     */
    private Thread decoderThread;

    /**
     * {@code true} after {@link #start()} returns and before
     * {@link #close()}.
     */
    private volatile boolean running;

    /**
     * Constructs a new pipeline. Native codec/AEC state is allocated
     * eagerly so failures surface here, not on the first frame.
     *
     * @param call         the {@link ActiveCall} to drive
     * @param outboundSink consumer for encoded outbound packets
     * @param options      configuration; see
     *                     {@link AudioPipelineOptions#defaults()}
     * @throws NullPointerException if any argument is {@code null}
     */
    public AudioPipeline(ActiveCall call, Consumer<OpusPacket> outboundSink, AudioPipelineOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.outboundSink = Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.encoder = new OpusEncoder(options.sampleRate(), options.channels(), options.application());
        try {
            configureEncoder();
            this.decoder = new OpusDecoder(options.sampleRate(), options.channels());
            try {
                this.aec = options.aecEnabled()
                        ? new EchoCanceller(options.frameSize(), options.aecFilterLength(), options.sampleRate())
                        : null;
                try {
                    this.preprocessor = options.preprocessorEnabled()
                            ? buildPreprocessor()
                            : null;
                } catch (RuntimeException e) {
                    if (aec != null) aec.close();
                    throw e;
                }
            } catch (RuntimeException e) {
                decoder.close();
                throw e;
            }
        } catch (RuntimeException e) {
            encoder.close();
            throw e;
        }
    }

    /**
     * Returns the active configuration.
     *
     * @return the options used at construction
     */
    public AudioPipelineOptions options() {
        return options;
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
                .name("audio-pipeline-capture")
                .start(this::captureLoop);
        decoderThread = Thread.ofVirtual()
                .name("audio-pipeline-decode")
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
    public void feedInboundPacket(OpusPacket packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (!running) {
            return;
        }
        inboundPackets.offer(packet);
    }

    /**
     * Stops the threads and frees native resources. Idempotent.
     */
    @Override
    public synchronized void close() {
        if (!running) {
            closeNatives();
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
        closeNatives();
    }

    /**
     * Capture loop body — runs on {@link #captureThread} until the
     * call ends or {@link #close()} interrupts.
     */
    private void captureLoop() {
        long ptsMs = 0;
        var frameDurMs = 1000L * options.frameSize() / options.sampleRate();
        while (running) {
            AudioFrame frame;
            try {
                frame = call.takeOutboundAudio();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (frame == null) {
                return;
            }
            var processed = processCaptureFrame(frame.pcm());
            var voiceActive = processed != null && runPreprocessor(processed);
            if (processed == null) {
                continue;
            }
            byte[] payload;
            try {
                payload = encoder.encode(processed, options.frameSize());
            } catch (RuntimeException _) {
                continue;
            }
            try {
                outboundSink.accept(new OpusPacket(payload, ptsMs, voiceActive));
            } catch (RuntimeException _) {
            }
            ptsMs += frameDurMs;
        }
    }

    /**
     * Runs AEC against the latest far-end reference.
     *
     * @param mic the captured mic frame
     * @return the cleaned mic frame, or the input itself when AEC is
     *         disabled or no far-end reference has yet arrived
     */
    private short[] processCaptureFrame(short[] mic) {
        if (mic.length != options.frameSize()) {
            return null;
        }
        if (aec == null) {
            return mic;
        }
        var farEnd = latestFarEnd.get();
        if (farEnd == null) {
            return mic;
        }
        try {
            return aec.cancel(mic, farEnd);
        } catch (RuntimeException _) {
            return mic;
        }
    }

    /**
     * Runs the preprocessor over the cleaned mic frame in place.
     *
     * @param frame the cleaned mic frame, mutated in place
     * @return whether the preprocessor reported voice activity
     *         ({@code true}) — when VAD is off, conservatively
     *         returns {@code true} so the encoder always emits
     */
    private boolean runPreprocessor(short[] frame) {
        if (preprocessor == null) {
            return true;
        }
        try {
            return preprocessor.process(frame);
        } catch (RuntimeException _) {
            return true;
        }
    }

    /**
     * Decoder loop body — runs on {@link #decoderThread} until the
     * sentinel arrives.
     */
    private void decoderLoop() {
        while (running) {
            OpusPacket packet;
            try {
                packet = inboundPackets.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (packet == SENTINEL) {
                return;
            }
            short[] pcm;
            try {
                pcm = decoder.decode(packet.payload(), options.frameSize());
            } catch (RuntimeException _) {
                continue;
            }
            if (pcm.length == 0) {
                continue;
            }
            latestFarEnd.set(pcm);
            try {
                call.deliverInboundAudio(new AudioFrame(pcm, packet.ptsMs()));
            } catch (RuntimeException _) {
            }
        }
    }

    /**
     * Builds and configures the speexdsp preprocessor per the
     * options, linking it to the AEC for residual-echo suppression
     * when both are enabled.
     *
     * @return the configured preprocessor
     */
    private AudioPreprocessor buildPreprocessor() {
        var pp = new AudioPreprocessor(options.frameSize(), options.sampleRate());
        try {
            if (options.denoise()) {
                pp.setDenoise(true);
                pp.setNoiseSuppressDb(options.noiseSuppressDb());
            }
            if (options.agc()) {
                pp.setAgc(true);
                pp.setAgcTarget(options.agcTarget());
            }
            if (options.vad()) {
                pp.setVad(true);
            }
            if (aec != null) {
                pp.linkEchoState(aec);
            }
        } catch (RuntimeException e) {
            pp.close();
            throw e;
        }
        return pp;
    }

    /**
     * Pushes the configured target bitrate, complexity, DTX, FEC,
     * and packet-loss-percent settings into the encoder.
     */
    private void configureEncoder() {
        encoder.setBitrate(options.targetBitrateBps());
        encoder.setComplexity(options.complexity());
        encoder.setUseDTX(options.useDtx());
        encoder.setUseInbandFEC(options.useInbandFec());
        if (options.useInbandFec()) {
            encoder.setPacketLossPercent(options.expectedPacketLossPct());
        }
    }

    /**
     * Closes all native resources idempotently — encoder, decoder,
     * AEC, preprocessor.
     */
    private void closeNatives() {
        try {
            encoder.close();
        } catch (Throwable _) {
        }
        try {
            decoder.close();
        } catch (Throwable _) {
        }
        if (aec != null) {
            try {
                aec.close();
            } catch (Throwable _) {
            }
        }
        if (preprocessor != null) {
            try {
                preprocessor.close();
            } catch (Throwable _) {
            }
        }
    }
}
