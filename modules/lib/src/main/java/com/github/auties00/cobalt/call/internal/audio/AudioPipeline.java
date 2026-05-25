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
 * Bridges the raw-PCM ports of an {@link ActiveCall} and the encoded {@link OpusPacket} ports of
 * the RTP transport, forming a full-duplex audio path.
 *
 * <p>The outbound path runs from microphone capture to the wire. {@link ActiveCall#takeOutboundAudio()}
 * yields one mic {@link AudioFrame}; that frame is passed through the speexdsp {@link EchoCanceller}
 * against the latest decoded far-end PCM to remove residual echo leaking from the speaker back into
 * the mic; the cleaned PCM is passed through the speexdsp {@link AudioPreprocessor} for denoise, AGC,
 * and VAD, whose voice-activity verdict becomes {@link OpusPacket#voiceActive()}; libopus encodes the
 * resulting frame; and the encoded {@link OpusPacket} is handed to the {@link #outboundSink}, which the
 * call layer wires to the RTP transport's send side.
 *
 * <p>The inbound path runs from the wire to playback. The RTP layer hands a decoded {@link OpusPacket}
 * to {@link #feedInboundPacket(OpusPacket)}; libopus decodes it to PCM; the PCM is stored as the latest
 * far-end reference so the next mic frame can subtract it during echo cancellation; and the PCM is
 * wrapped as an {@link AudioFrame} and pushed to {@link ActiveCall#deliverInboundAudio(AudioFrame)} for
 * playback.
 *
 * <p>The pipeline owns two virtual threads spawned by {@link #start()}: a capture thread that drains the
 * call's outbound queue and a decoder thread that drains the inbound packet queue. {@link #close()}
 * interrupts both threads, joins them, and frees the native codec, AEC, and preprocessor resources; it
 * is idempotent.
 *
 * @implNote This implementation confines all speexdsp state to the capture thread because the library
 * is not thread-safe: the {@link EchoCanceller} and {@link AudioPreprocessor} are touched only there,
 * while the decoder thread publishes the far-end reference through {@link #latestFarEnd}, an
 * {@link AtomicReference} the capture thread reads.
 */
public final class AudioPipeline implements AutoCloseable {
    /**
     * Marks the end of the inbound stream so the decoder thread can terminate.
     *
     * <p>{@link #close()} offers this instance into {@link #inboundPackets} so the decoder thread
     * unblocks from {@link LinkedBlockingQueue#take()} and exits without waiting for an interrupt.
     *
     * @implNote This implementation builds the sentinel from {@link DataUtils#EMPTY_BYTE_ARRAY} and
     * {@link Long#MIN_VALUE} so it is identity-comparable against real packets, which always carry a
     * non-empty payload.
     */
    private static final OpusPacket SENTINEL =
            new OpusPacket(DataUtils.EMPTY_BYTE_ARRAY, Long.MIN_VALUE, false);

    /**
     * Holds the call whose audio this pipeline drives.
     */
    private final ActiveCall call;

    /**
     * Receives every encoded outbound packet.
     *
     * <p>The call layer wires this consumer to the RTP transport's send side.
     */
    private final Consumer<OpusPacket> outboundSink;

    /**
     * Holds the active configuration, never mutated after construction.
     */
    private final AudioPipelineOptions options;

    /**
     * Holds the Opus encoder used by the capture thread.
     */
    private final OpusEncoder encoder;

    /**
     * Holds the Opus decoder used by the decoder thread.
     */
    private final OpusDecoder decoder;

    /**
     * Holds the speexdsp echo canceller, or {@code null} when AEC is disabled.
     */
    private final EchoCanceller aec;

    /**
     * Holds the speexdsp preprocessor, or {@code null} when no preprocessor feature (denoise, AGC, or
     * VAD) is enabled.
     */
    private final AudioPreprocessor preprocessor;

    /**
     * Holds the latest decoded far-end PCM frame used as the AEC reference for the next mic frame.
     *
     * <p>The decoder thread writes this reference and the capture thread reads it, so it is published
     * through an {@link AtomicReference}.
     */
    private final AtomicReference<short[]> latestFarEnd = new AtomicReference<>();

    /**
     * Holds inbound packets fed by {@link #feedInboundPacket(OpusPacket)} and drained by the decoder
     * thread.
     */
    private final LinkedBlockingQueue<OpusPacket> inboundPackets = new LinkedBlockingQueue<>();

    /**
     * Holds the capture thread that drains {@link ActiveCall#takeOutboundAudio()} and encodes, or
     * {@code null} until {@link #start()} runs.
     */
    private Thread captureThread;

    /**
     * Holds the decoder thread that drains {@link #inboundPackets} and pushes PCM back into the call,
     * or {@code null} until {@link #start()} runs.
     */
    private Thread decoderThread;

    /**
     * Indicates whether the pipeline is running, set {@code true} when {@link #start()} returns and
     * cleared by {@link #close()}.
     */
    private volatile boolean running;

    /**
     * Constructs a pipeline for the given call, sink, and options.
     *
     * <p>The native codec, AEC, and preprocessor state is allocated eagerly so allocation failures
     * surface here rather than on the first frame. If any native allocation fails, the resources
     * already allocated by this constructor are released before the failure propagates.
     *
     * @param call         the call to drive
     * @param outboundSink the consumer for encoded outbound packets
     * @param options      the configuration; see {@link AudioPipelineOptions#defaults()}
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
     * Returns the active configuration supplied at construction.
     *
     * @return the options used at construction
     */
    public AudioPipelineOptions options() {
        return options;
    }

    /**
     * Starts the capture and decoder virtual threads.
     *
     * <p>This method is idempotent; once the pipeline is running, subsequent calls are no-ops.
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
     *
     * <p>The packet is enqueued and the method returns immediately. When the pipeline is not running
     * or has been closed, the packet is silently dropped.
     *
     * @param packet the packet from the RTP layer
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
     * Stops the capture and decoder threads and frees native resources.
     *
     * <p>This method clears the running flag, enqueues {@link #SENTINEL} so the decoder thread
     * terminates, interrupts the capture thread, joins both threads with a bounded wait, and releases
     * the native codec, AEC, and preprocessor resources. It is idempotent; when the pipeline is not
     * running it only releases native resources.
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
     * Drains captured mic frames, processes and encodes them, and forwards the result to the sink.
     *
     * <p>This method runs on {@link #captureThread} until the call ends or {@link #close()} interrupts
     * it. For each captured frame it applies echo cancellation and the preprocessor, encodes the cleaned
     * PCM with libopus, and delivers an {@link OpusPacket} carrying the running presentation timestamp
     * and the VAD verdict to {@link #outboundSink}. Encoder and sink failures for a single frame are
     * absorbed so one bad frame does not tear down the stream.
     *
     * @implNote This implementation derives the per-frame presentation step from
     * {@code 1000 * frameSize / sampleRate} so the timestamp advances in whole milliseconds matching the
     * configured frame duration; a frame whose sample count does not match the configured frame size is
     * skipped.
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
     * Runs echo cancellation on a captured mic frame against the latest far-end reference.
     *
     * <p>The frame is returned unchanged when AEC is disabled or no far-end reference has yet arrived,
     * and an AEC failure falls back to the unprocessed frame. A frame whose length does not match the
     * configured frame size is rejected.
     *
     * @param mic the captured mic frame
     * @return the cleaned mic frame, the input itself when AEC is disabled or has no reference yet, or
     *         {@code null} when the frame length is not the configured frame size
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
     * Runs the preprocessor over a cleaned mic frame in place and reports voice activity.
     *
     * <p>When no preprocessor is configured, or when the preprocessor fails, this method reports voice
     * activity so the encoder always emits a frame rather than silently dropping audio.
     *
     * @param frame the cleaned mic frame, mutated in place
     * @return {@code true} if the preprocessor reported voice activity or no preprocessor verdict is
     *         available, {@code false} otherwise
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
     * Drains inbound packets, decodes them, and pushes the resulting PCM back into the call.
     *
     * <p>This method runs on {@link #decoderThread} until {@link #SENTINEL} arrives. For each packet it
     * decodes the payload with libopus, publishes the decoded PCM as the far-end AEC reference via
     * {@link #latestFarEnd}, and delivers an {@link AudioFrame} to
     * {@link ActiveCall#deliverInboundAudio(AudioFrame)}. Decode failures and empty results are skipped,
     * and a delivery failure for a single frame is absorbed.
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
     * Builds and configures the speexdsp preprocessor from the options.
     *
     * <p>Denoise, AGC, and VAD are each enabled per the options, and when both this preprocessor and
     * the AEC are present the two are linked so the preprocessor can suppress residual echo. If
     * configuration fails after allocation, the partially configured preprocessor is released before
     * the failure propagates.
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
     * Pushes the configured encoder settings into the Opus encoder.
     *
     * <p>This method applies the target bitrate, complexity, DTX, and in-band FEC settings, and when FEC
     * is enabled also applies the expected packet-loss percentage that tunes FEC redundancy.
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
     * Releases the native encoder, decoder, AEC, and preprocessor resources.
     *
     * <p>Each resource is closed independently and any failure to close one is swallowed so a single
     * failure does not prevent the others from being released. The method is safe to call when the
     * pipeline never started.
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
