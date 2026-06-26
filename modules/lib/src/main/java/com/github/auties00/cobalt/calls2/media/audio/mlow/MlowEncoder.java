package com.github.auties00.cobalt.calls2.media.audio.mlow;

import com.github.auties00.cobalt.calls2.media.audio.mlow.encode.CoreEncoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.encode.LbQuantParams;
import com.github.auties00.cobalt.calls2.media.audio.mlow.encode.ParamEncoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.encode.Vad;
import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowRangeEncoder;

/**
 * End-to-end low-band speech encoder for the MLow codec, the port of the master per-frame and per-packet
 * encode loop {@code smpl_core_encode} ({@code smpl_core_encoder.c}) together with the SMPL-only packet
 * finalization of {@code opus_smpl_encode_native} ({@code opus_smpl_encode.c}), the exact inverse of
 * {@link MlowDecoder}.
 *
 * <p>This is the payoff stage of the MLow encode pipeline: it wires the bit-exact parameter back end
 * ({@link ParamEncoder}) and the range coder ({@link MlowRangeEncoder}) into one call that turns a frame's (or
 * a 60 ms packet's three frames') quantized low-band parameters into a coded MLow packet whose bytes the C
 * encoder produces and that {@link MlowDecoder} decodes back. For each internal 20 ms frame it range-encodes
 * the full low-band parameter set ({@link ParamEncoder#encodeFrame}), then finalizes the range coder over the
 * whole payload buffer ({@link MlowRangeEncoder#finish()}), strips the trailing zero bytes the decoder fills
 * in, and prepends the TOC byte ({@link #encodeToc}).
 *
 * <p>Two encode entry points are bit-exact. {@link #encodeFrames(LbQuantParams[], boolean, int, boolean,
 * boolean, boolean, boolean)} takes the per-frame {@link LbQuantParams} the analysis-by-synthesis front end
 * produces and emits the exact packet bytes (and exposes the final {@code rng} register for the
 * {@code encFinalRange} cross-check). {@link #encode(short[])} is the full mic-in path: it runs the
 * voice-activity detector ({@link Vad}) and the per-packet PCM-to-bitstream orchestrator ({@link CoreEncoder},
 * the port of {@code smpl_core_encode} plus {@code smpl_base_encode}) to turn raw 16 kHz PCM into the
 * range-coded payload, then finalizes the packet exactly as {@link #encodeFrames} does. The orchestrator wires
 * the whole feature-extraction front end (high-pass, windowing, LPC analysis, perceptual weighting, open-loop
 * pitch, voice-activity and signal-mode decision, the bitrate controller, the CELP analysis-by-synthesis, and
 * the residual-energy quantizer) and serializes each frame in the same loop so the bitrate-controller feedback
 * sees the real per-frame bit count.
 *
 * <p>This encoder is stateful across the frames of a packet and across packets of one continuous stream: the
 * wrapped {@link ParamEncoder} threads the conditional-coding predictors and the pitch-lag carry from one
 * frame to the next. Construct one encoder per logical stream and feed it packets in order; {@link #reset()}
 * returns the pipeline to the freshly constructed state, the equivalent of {@code smpl_core_encoder_init}.
 *
 * <p>Scope is the SMPL 16 kHz, 60 ms (and the 10/20 ms sub-cases), mono, low-band, active-voice path at either
 * rate class: the high-rate (9600 bps) four-subframe path and the low-rate (6000 bps) two-subframe path,
 * selected by the bitrate the encoder is constructed at (see {@link #MlowEncoder(int)}). The high band
 * (32/48 kHz), the inband forward error correction, the SID/DTX comfort-noise frames, and the CELT fallback are
 * out of scope and not emitted. This type is stateful per stream and is not thread-safe.
 *
 * @implNote This implementation reproduces the SMPL branch of {@code opus_smpl_encode_native}: it initializes
 * the range coder over {@code data + 1} (the byte after the reserved TOC slot), runs the per-frame loop, then
 * the {@code ret = (ec_tell + 7) >> 3; ec_enc_done(); while (ret > 2 && data[ret] == 0) ret--;} finalize and
 * trailing-zero strip, and writes the TOC byte that {@code gen_smpl_toc} would write for a single-frame
 * SMPL-only packet. The {@code ec_enc_done} runs over the full buffer (not a shrunk one), matching the
 * SMPL-only path where the range coder owns the whole payload; the trailing zeros are stripped because the
 * decoder's range coder fills missing bytes with zero. The conditional-coding flag fed to {@link ParamEncoder}
 * is computed exactly as {@code smpl_core_encode} computes it: {@code (voiced == prev_voiced) && (numframe >
 * 0)}, with {@code prev_voiced} reset to the unvoiced state at the start of each packet.
 */
public final class MlowEncoder {
    /**
     * Sample-clock rate of the CELP core in kilohertz, the native {@code SMPL_CELP_FS_KHZ}; the per-frame and
     * per-subframe sample counts are products of it.
     */
    private static final int CELP_FS_KHZ = 16;

    /**
     * Maximum payload size in bytes the range coder is allowed to fill, the native {@code max_data_bytes - 1}
     * upper bound; sized generously above the worst-case 60 ms high-rate packet so the encoder never busts the
     * buffer on the in-scope inputs.
     */
    private static final int MAX_PAYLOAD_BYTES = 1275;

    /**
     * Default target bitrate of the PCM-in scope in bits per second, the native {@code OPUS_SET_BITRATE} value
     * for {@code voip 16000 1 9600}; selects the high-rate path.
     */
    private static final int DEFAULT_BITRATE_BPS = 9600;

    /**
     * Low-rate decision threshold in bits per second for the 16 kHz / 60 ms packet, the native
     * {@code smpl_low_rate_thr[0][2]}.
     *
     * <p>The PCM-in path selects the low-rate mode when the target bitrate is at or below this threshold (the
     * native {@code lowRate = mainBitRate <= low_rate_thr}); at the locked 16 kHz / 60 ms / mono scope the
     * threshold is {@code 8700}, so the 9600 bps target stays high rate and the 6000 bps target selects low rate.
     */
    private static final int LOW_RATE_THR_60MS_16K = 8700;

    /**
     * Samples per internal 20 ms frame at 16 kHz, the native {@code framelen}.
     */
    private static final int FRAME_LEN = 320;

    /**
     * The per-frame low-band parameter serializer used by {@link #encodeFrames}, threading conditional-coding and
     * pitch-lag state across the frames of a packet and across packets.
     */
    private final ParamEncoder paramEncoder;

    /**
     * The voice-activity detector of the PCM-in path, the native {@code smpl_VAD}, stateful across the packets of
     * a stream.
     */
    private final Vad vad;

    /**
     * The per-packet PCM-to-bitstream orchestrator of the PCM-in path, the native {@code smpl_core_encode},
     * stateful across the packets of a stream.
     */
    private final CoreEncoder coreEncoder;

    /**
     * The target bitrate in bits per second of the PCM-in path, the native {@code enc_status->mainBitRate}.
     *
     * <p>Fixed at construction; selects the rate class against {@link #LOW_RATE_THR_60MS_16K}. The
     * {@link #encode(short[])} path reads it to set the TOC {@code low_rate} bit and the orchestrator wires the
     * subframe geometry from it.
     */
    private final int bitRate;

    /**
     * Whether the PCM-in path runs the low-rate mode, the native {@code lowRate} of {@code smpl_enc_api.c}.
     *
     * <p>Derived once at construction from {@link #bitRate} against {@link #LOW_RATE_THR_60MS_16K}; written into
     * the TOC byte of every packet {@link #encode(short[])} emits.
     */
    private final boolean lowRate;

    /**
     * The final value of the range-coder {@code rng} register after the most recent {@link #encodeFrames}
     * call, the native {@code enc.rng} the Opus layer reports as {@code rangeFinal}.
     *
     * <p>Exposed through {@link #lastFinalRange()} for the {@code encFinalRange} cross-check against the C
     * encoder; it is a per-packet diagnostic, not part of the encode contract.
     */
    private long lastFinalRange;

    /**
     * Constructs an MLow low-band encoder at the default high-rate bitrate, with a freshly constructed parameter
     * back end and cleared state.
     *
     * <p>The wrapped parameter serializer starts in its reset state, ready to encode the first packet of a
     * stream. The target bitrate is the default {@value #DEFAULT_BITRATE_BPS} bps high-rate value; use
     * {@link #MlowEncoder(int)} to encode at a different bitrate (for example the 6000 bps low-rate mode).
     */
    public MlowEncoder() {
        this(DEFAULT_BITRATE_BPS);
    }

    /**
     * Constructs an MLow low-band encoder at a given target bitrate, with a freshly constructed parameter back
     * end and cleared state.
     *
     * <p>The bitrate selects the rate class against the 8700 bps 60 ms threshold: the 9600 bps target stays high
     * rate (four 5 ms subframes per 20 ms frame), the 6000 bps target selects low rate (two 10 ms subframes per
     * 20 ms frame), and the TOC {@code low_rate} bit and subframe geometry are wired from that decision. The
     * wrapped parameter serializer starts in its reset state, ready to encode the first packet of a stream.
     *
     * @param bitRate the target bitrate in bits per second, the native {@code OPUS_SET_BITRATE} value
     */
    public MlowEncoder(int bitRate) {
        this.bitRate = bitRate;
        this.lowRate = bitRate <= LOW_RATE_THR_60MS_16K;
        this.paramEncoder = new ParamEncoder();
        this.vad = new Vad();
        this.coreEncoder = new CoreEncoder(bitRate);
    }

    /**
     * Re-targets the encoder's bitrate mid-stream, the native rewrite of {@code mainBitRate} the encoder API
     * performs each rate-control round.
     *
     * <p>Delegates to {@link CoreEncoder#updateTargetBitrate(int)} so the next packet's
     * {@link com.github.auties00.cobalt.calls2.media.audio.mlow.encode.BitrateController} sees the new target and
     * steers the instantaneous bitrate toward it through its feedback loop. A call that does not change the target
     * leaves the encode byte-for-byte identical to the fixed-rate path. The rate class
     * ({@link #lowRate}) and the TOC {@code low_rate} bit are not re-derived: the native {@code smpl_enc_api.c}
     * fixes the rate class once at codec open and only the controller target varies, so the rate-class bit written
     * into every packet keeps the construction value while the adaptive target tracks the engine estimate.
     *
     * @param bps the new target bitrate in bits per second, the native {@code mainBitRate}
     */
    public void updateTargetBitrate(int bps) {
        coreEncoder.updateTargetBitrate(bps);
    }

    /**
     * Returns this encoder to its freshly constructed state, the equivalent of {@code smpl_core_encoder_init}.
     *
     * <p>Resets the wrapped parameter serializer. Call this between independent encode sessions; do not call it
     * between the packets of one continuous stream, which must thread state.
     */
    public void reset() {
        paramEncoder.reset();
        vad.reset();
        coreEncoder.reset();
    }

    /**
     * Returns the final {@code rng} register of the most recent {@link #encodeFrames} call, the native
     * {@code enc.rng} reported as {@code encFinalRange}.
     *
     * <p>Two encoders that produced identical bytes also agree on this value; it is the standard Opus
     * cross-check that the range coders ran in lockstep. The value is meaningful only after at least one
     * {@link #encodeFrames} call.
     *
     * @return the final range register as a 32-bit unsigned value held in a {@code long}
     */
    public long lastFinalRange() {
        return lastFinalRange;
    }

    /**
     * Encodes one MLow packet from its per-frame quantized low-band parameters, the SMPL branch of
     * {@code smpl_core_encode} plus {@code opus_smpl_encode_native}.
     *
     * <p>Range-encodes every supplied frame's parameters in order through {@link ParamEncoder#encodeFrame},
     * threading the conditional-coding flag ({@code (voiced == prevVoiced) && (frame > 0)} with
     * {@code prevVoiced} reset to unvoiced at the packet start), finalizes the range coder, strips the trailing
     * zero bytes, and prepends the TOC byte. A 60 ms packet supplies three frames; a 20 ms packet supplies
     * one. The cross-frame predictors on this encoder advance, so packets must be supplied in stream order.
     *
     * @param frames             the per-frame quantized parameters, one entry per internal 20 ms frame
     * @param vad                {@code true} when the packet was produced with detected voice activity, the
     *                           native {@code vad->VAD}
     * @param sampleRateHz       the internal sample rate in hertz, {@code 16000} for the low-band scope
     * @param lowRate            {@code true} for the low-rate mode, {@code false} for high rate
     * @param fec                {@code true} when the packet carries an inband FEC frame; {@code false} for the
     *                           in-scope path
     * @param codedAsActiveVoice {@code true} when the frames are coded as if they may contain voiced energy,
     *                           the native {@code coded_as_active_voice}
     * @param stereo             {@code true} when the packet carries stereo information; {@code false} for the
     *                           mono scope
     * @return the complete MLow packet bytes, beginning with the TOC byte
     * @throws IllegalArgumentException if {@code frames} is empty or its length is not a valid frame count, or
     *                                  if {@code sampleRateHz} is above the 16 kHz low-band scope
     */
    public byte[] encodeFrames(LbQuantParams[] frames, boolean vad, int sampleRateHz, boolean lowRate,
                               boolean fec, boolean codedAsActiveVoice, boolean stereo) {
        if (frames == null || frames.length == 0) {
            throw new IllegalArgumentException("no frames to encode");
        }
        if (sampleRateHz > 16000) {
            throw new IllegalArgumentException(
                    "high-band encode (fs " + sampleRateHz + " Hz) is out of low-band scope");
        }
        int numFrames = frames.length;
        int packetLenMs = packetLenMsForFrames(numFrames);
        int framelen = (packetLenMs > 10 ? 2 : 1) * 10 * CELP_FS_KHZ;
        int numSubfr = 1 << (1 - (lowRate ? 1 : 0) + (packetLenMs > 10 ? 1 : 0));

        byte[] buffer = new byte[1 + MAX_PAYLOAD_BYTES];
        MlowRangeEncoder encoder = new MlowRangeEncoder(buffer, 1, MAX_PAYLOAD_BYTES);

        int prevVoiced = 0;
        for (int frame = 0; frame < numFrames; frame++) {
            LbQuantParams params = frames[frame];
            int voiced = params.voiced() ? 1 : 0;
            boolean condCoding = (voiced == prevVoiced) && (frame > 0);
            paramEncoder.encodeFrame(encoder, params, framelen, numSubfr, codedAsActiveVoice, condCoding,
                    lowRate, frame, prevVoiced, false);
            prevVoiced = voiced;
        }

        int ret = (encoder.tell() + 7) >> 3;
        encoder.finish();
        lastFinalRange = encoder.finalRange();

        // Strip trailing zeros: the decoder's range coder fills missing bytes with zero. ret indexes into the
        // payload (1-based over data[] in the native code where data points past the TOC); here the payload
        // starts at buffer[1], so payload byte index r maps to buffer[1 + r].
        while (ret > 2 && buffer[1 + ret - 1] == 0) {
            ret--;
        }

        buffer[0] = encodeToc(false, vad, sampleRateHz, packetLenMs, lowRate, fec, codedAsActiveVoice, stereo);
        byte[] packet = new byte[1 + ret];
        System.arraycopy(buffer, 0, packet, 0, 1 + ret);
        return packet;
    }

    /**
     * Encodes one MLow packet from 16-bit PCM, the PCM-in entry point mirroring {@link MlowDecoder#decode}, the
     * SMPL branch of {@code opus_smpl_encode_native} over {@code smpl_Encode} and {@code smpl_core_encode}.
     *
     * <p>Runs the voice-activity detector over the packet, then the per-packet orchestrator
     * ({@link CoreEncoder#encodePacket}) which runs the full analysis-by-synthesis front end and serializes every
     * frame in order, then finalizes the range coder, strips the trailing zero bytes, and prepends the TOC byte.
     * The packet length is the number of internal 20 ms frames the input spans: {@value #FRAME_LEN} samples per
     * frame, so a 60 ms packet is {@value #FRAME_LEN}{@code  * 3} samples. The cross-packet predictors advance, so
     * packets must be supplied in stream order. The final {@code rng} register is exposed through
     * {@link #lastFinalRange()} for the {@code encFinalRange} cross-check.
     *
     * <p>Scope is the SMPL 16 kHz / mono active-voice path at the configured bitrate (high rate at
     * {@value #DEFAULT_BITRATE_BPS} bps, low rate at 6000 bps); the input must be a whole number of 20 ms frames
     * at 16 kHz mono.
     *
     * @param pcm the input PCM samples for one packet at 16 kHz mono, one internal frame per
     *            {@value #FRAME_LEN} samples
     * @return the complete MLow packet bytes, beginning with the TOC byte
     * @throws IllegalArgumentException if {@code pcm} is empty or its length is not a multiple of
     *                                  {@value #FRAME_LEN}
     */
    public byte[] encode(short[] pcm) {
        if (pcm == null || pcm.length == 0 || pcm.length % FRAME_LEN != 0) {
            throw new IllegalArgumentException(
                    "PCM-in MLow encode requires a whole number of " + FRAME_LEN + "-sample 20 ms frames");
        }
        int framesPerPacket = pcm.length / FRAME_LEN;
        int packetLenMs = packetLenMsForFrames(framesPerPacket);
        boolean lowRate = this.lowRate;

        Vad.VadDecision vadDecision = vad.processPacket(pcm, FRAME_LEN, framesPerPacket,
                Vad.ACTIVITY_NO_DECISION, false);

        byte[] buffer = new byte[1 + MAX_PAYLOAD_BYTES];
        MlowRangeEncoder encoder = new MlowRangeEncoder(buffer, 1, MAX_PAYLOAD_BYTES);

        coreEncoder.encodePacket(pcm, FRAME_LEN, framesPerPacket, vadDecision, encoder);

        int ret = (encoder.tell() + 7) >> 3;
        encoder.finish();
        lastFinalRange = encoder.finalRange();

        while (ret > 2 && buffer[1 + ret - 1] == 0) {
            ret--;
        }

        buffer[0] = encodeToc(vadDecision.sidFrame(), vadDecision.vad(), 16000, packetLenMs, lowRate, false,
                vadDecision.codedAsActiveVoice(), false);
        byte[] packet = new byte[1 + ret];
        System.arraycopy(buffer, 0, packet, 0, 1 + ret);
        return packet;
    }

    /**
     * Maps an internal frame count to the packet length in milliseconds, the inverse of the native
     * {@code num_frames = (packet_len_ms + 10) / 20}.
     *
     * @param numFrames the internal 20 ms frame count; 1, 3, or 6
     * @return the packet length in milliseconds; 20, 60, or 120
     * @throws IllegalArgumentException if {@code numFrames} is not a valid frame count
     */
    private static int packetLenMsForFrames(int numFrames) {
        return switch (numFrames) {
            case 1 -> 20;
            case 3 -> 60;
            case 6 -> 120;
            default -> throw new IllegalArgumentException("unsupported internal frame count: " + numFrames);
        };
    }

    /**
     * Builds the MLow TOC byte for a single-frame SMPL-only packet, {@code smpl_encode_toc} in
     * {@code smpl_param_coding.c}.
     *
     * <p>Packs the eight bit-fields exactly as the native encoder does, including the
     * {@code (FEC == 1) || ((VAD == 0) && (coded_as_active_voice == 1))} hangover bit and the
     * {@code (fs == 16000) ? 0 : (1 << 5)} sample-rate flag. The decode-side {@link MlowTocByte#decode(int)}
     * reconstructs every field from the returned byte.
     *
     * @param sid                {@code true} for a SID frame; {@code false} for the in-scope path
     * @param vad                {@code true} when the packet was produced with detected voice activity
     * @param sampleRateHz       the internal sample rate in hertz, {@code 16000}, {@code 32000}, or
     *                           {@code 48000}
     * @param packetLenMs        the packet length in milliseconds; 10, 20, 60, or 120
     * @param lowRate            {@code true} for the low-rate mode, {@code false} for high rate
     * @param fec                {@code true} when the packet carries an inband FEC frame
     * @param codedAsActiveVoice {@code true} when the frames are coded as if they may contain voiced energy
     * @param stereo             {@code true} when the packet carries stereo information
     * @return the packed TOC byte in the low eight bits of the returned {@code byte}
     * @throws IllegalArgumentException if {@code sampleRateHz} or {@code packetLenMs} is not a defined value
     */
    static byte encodeToc(boolean sid, boolean vad, int sampleRateHz, int packetLenMs, boolean lowRate,
                          boolean fec, boolean codedAsActiveVoice, boolean stereo) {
        int b = 0;
        b += (sid ? 1 : 0) << 7;
        b += (vad ? 1 : 0) << 6;
        if (sampleRateHz != 16000 && sampleRateHz != 32000 && sampleRateHz != 48000) {
            throw new IllegalArgumentException("unsupported sample rate: " + sampleRateHz);
        }
        b += sampleRateHz == 16000 ? 0 : (1 << 5);
        int frameSel = switch (packetLenMs) {
            case 10 -> 0;
            case 20 -> 1;
            case 60 -> 2;
            case 120 -> 3;
            default -> throw new IllegalArgumentException("unsupported packet length: " + packetLenMs);
        };
        b += frameSel << 3;
        b += (lowRate ? 1 : 0) << 2;
        b += (fec || (!vad && codedAsActiveVoice)) ? (1 << 1) : 0;
        b += stereo ? 1 : 0;
        return (byte) b;
    }
}
