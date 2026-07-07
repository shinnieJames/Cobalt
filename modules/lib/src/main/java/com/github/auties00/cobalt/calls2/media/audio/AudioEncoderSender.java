package com.github.auties00.cobalt.calls2.media.audio;

import com.github.auties00.cobalt.calls2.platform.AudioReaderPump;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Drives the audio send path: it aggregates encoded frames into one packet per send, attaches the
 * audio-level extension, applies the group end-to-end transform, retains the packet for redundancy, and
 * hands it to the transport.
 *
 * <p>This is the sender half of the audio media engine. Captured PCM arrives one fixed block at a time
 * from the {@link AudioReaderPump}, which this class consumes by implementing
 * {@link AudioReaderPump.AudioBlockSink}. Each block is encoded through the {@link FrameEncoder} seam into
 * an {@link EncodedAudioFrame}, then buffered: the engine packs a configurable number of consecutive
 * encoded frames (the frames-per-packet count, {@code 1..6}) into a single RTP payload through the
 * {@link FramePacker} seam before sending, which amortizes the per-packet header and transport overhead
 * across several short Opus frames. Once the buffer holds a full frames-per-packet group, the sender
 * combines them and ships one packet; an empty frame (silence the encoder suppressed to a zero-length
 * payload) is not buffered and instead flushes any partial group so a trailing frame is not stranded.
 *
 * <p>For each combined payload the sender derives an {@link AudioLevelRtpExtension} from the loudest
 * frame in the group and its voice-activity verdict, so the packet advertises its level to a mixer or
 * selective-forwarding unit. The level extension and the combined codec payload are assembled into the
 * outbound media payload, which then takes one of two end-to-end-confidentiality paths: a group call
 * seals the payload with SFrame through the {@link SFrameTransform} seam so the relay forwards opaque
 * ciphertext, while a one-to-one call leaves the payload to the shared-key hop-by-hop SRTP applied
 * downstream in the transport and supplies no SFrame transform. The finished packet is retained in the
 * {@link StreamPacketCache} keyed by its extended sequence so the redundancy schemes can replay or
 * protect it, and is handed to the {@link MediaPacketSink} for transmission.
 *
 * <p>The sender runs entirely on the reader pump's virtual thread: {@link #accept(short[], int)} is
 * invoked once per captured block on that thread, and all buffering, combining, sealing, caching, and
 * sending happen inline before it returns. It is therefore single-threaded and holds no internal lock;
 * the collaborators it calls own their own concurrency. A call's sender is created once and used for the
 * call's lifetime.
 *
 * @implNote This implementation ports {@code put_frame_imp} and its frames-per-packet aggregation from
 * the wa-voip WASM module {@code ff-tScznZ8P} ({@code rev-media-audio}): it buffers up to the negotiated
 * frames-per-packet ({@code codec_param.setting.frm_per_pkt}, {@code 1..6}) encoded Opus frames, combines
 * them with the Opus repacketizer ({@code wa_opus_repacketizer_init} / {@code _cat} /
 * {@code _out_range}), attaches the audio-level RTP extension ({@code create_rtp_extender(audio_level)}),
 * hands the payload to the SFrame transform on the group path
 * ({@code wa_sframe_secureframe_c_api.cc}) and then to {@code encode_rtp} ->
 * {@code pjmedia_transport_send_rtp}. The native lazy-encode raw-PCM cache and the adaptive-complexity
 * controller live in the codec unit behind the {@link FrameEncoder} seam, so this orchestrator buffers
 * already-encoded frames rather than raw PCM; the repacketizer, SFrame transform, and transport are
 * reached through narrow functional seams bound at wiring time to the codec, the
 * {@code calls2.media.sframe} transform, and the {@code calls2.net.transport} send path, keeping this
 * class pure orchestration. The SFrame seam is supplied only for a group call, matching the live finding
 * that {@code wa_sframe_encrypt} fires solely on the SFU/group media path while a one-to-one call relies
 * on shared-key SRTP. The redundancy senders (in-band FEC, out-of-band NACK FEC, MLow RED) draw from the
 * {@link StreamPacketCache} this sender populates and are driven by the codec/rate-control unit, not from
 * here.
 */
public final class AudioEncoderSender implements AudioReaderPump.AudioBlockSink {
    /**
     * Lowest legal frames-per-packet count.
     *
     * <p>A value of one sends every encoded frame immediately with no aggregation.
     */
    public static final int MIN_FRAMES_PER_PACKET = 1;

    /**
     * Highest legal frames-per-packet count.
     *
     * <p>Matches the native {@code fpp_6} ceiling; the engine never packs more than six Opus frames into
     * one payload.
     */
    public static final int MAX_FRAMES_PER_PACKET = 6;

    /**
     * The fixed Opus priming frames shipped once at the start of the send stream before any captured audio.
     *
     * <p>The sequence is the WASM priming frame, the second priming frame, then three discontinuous-transmission
     * frames; it primes the peer's Opus decoder and marks the start of this client's media so the peer locks
     * onto the stream. Each entry is the raw Opus payload that takes the normal packetize, optional-SFrame, and
     * end-to-end SRTP path, exactly like an encoded frame.
     */
    private static final byte[][] PREAMBLE_FRAMES = {
            new byte[]{0x32, 0x36, 0x26, 0x2B, 0x4A, (byte) 0xCB, 0x1B, 0x5F, (byte) 0xBA, (byte) 0x91, 0x68,
                    0x7E, (byte) 0xB8, 0x50, (byte) 0x93, 0x58, (byte) 0xE6, (byte) 0xD0, (byte) 0xA3,
                    (byte) 0xA9, (byte) 0xD7, 0x1D, (byte) 0x81, (byte) 0x8C},
            new byte[]{(byte) 0x90, (byte) 0xB8, 0x14, 0x14, (byte) 0xC4},
            new byte[]{0x10},
            new byte[]{0x10},
            new byte[]{0x10},
    };

    /**
     * Encodes one captured PCM block into a codec frame.
     *
     * <p>Implemented by the audio codec unit over its libopus binding; the sender hands each block from
     * the capture pump to this seam and aggregates the returned {@link EncodedAudioFrame}s. An
     * implementation owns the encoder state and the lazy-encode and adaptive-complexity behaviour; this
     * seam exposes only the per-block encode the sender needs.
     */
    @FunctionalInterface
    public interface FrameEncoder {
        /**
         * Encodes one block of captured PCM samples into a codec frame.
         *
         * @param pcm    the captured samples; never {@code null}
         * @param length the number of valid samples at the start of {@code pcm}
         * @return the encoded frame, including its voice-activity and discontinuity flags; never
         * {@code null}
         */
        EncodedAudioFrame encode(short[] pcm, int length);
    }

    /**
     * Combines a group of encoded frames into one aggregated codec payload.
     *
     * <p>Implemented by the codec unit over the Opus repacketizer; the sender passes the buffered
     * frames-per-packet group and receives the single combined payload that goes into one RTP packet. A
     * group of exactly one frame yields that frame's payload unchanged.
     */
    @FunctionalInterface
    public interface FramePacker {
        /**
         * Combines the encoded frames of one frames-per-packet group into a single payload.
         *
         * @param frames the encoded frames to combine, in send order; never {@code null} and never empty
         * @return the combined codec payload bytes; never {@code null}
         */
        byte[] pack(List<EncodedAudioFrame> frames);
    }

    /**
     * Seals one media payload with the group end-to-end SFrame transform.
     *
     * <p>Implemented over the {@code calls2.media.sframe} secure-frame transform; the sender invokes it
     * only on a group call, so a one-to-one call supplies no instance and the payload is left for
     * shared-key SRTP downstream. The seam takes the assembled media payload and returns the SFrame
     * ciphertext-and-trailer bytes.
     */
    @FunctionalInterface
    public interface SFrameTransform {
        /**
         * Seals one media payload, returning the SFrame frame bytes.
         *
         * @param payload the plaintext media payload to seal; never {@code null}
         * @return the sealed SFrame frame; never {@code null}
         */
        byte[] seal(byte[] payload);
    }

    /**
     * Transmits one finished audio media packet over the active transport.
     *
     * <p>Implemented by the transport unit; the sender hands it the assembled payload (already SFrame
     * sealed on the group path) together with the packet's extended sequence and voice-activity flag,
     * which the transport packetizes into RTP, applies hop-by-hop SRTP to, and sends. The sender does not
     * build the RTP header itself, since the SSRC, sequence wire form, and SRTP keys are transport state.
     */
    @FunctionalInterface
    public interface MediaPacketSink {
        /**
         * Sends one audio media payload as an RTP packet.
         *
         * @param payload          the media payload to send, SFrame sealed on the group path; never
         *                         {@code null}
         * @param extendedSequence the 32-bit RTP extended sequence number assigned to the packet
         * @param level            the audio-level extension to attach; never {@code null}
         */
        void send(byte[] payload, long extendedSequence, AudioLevelRtpExtension level);
    }

    /**
     * The codec encode seam each captured block is passed through.
     */
    private final FrameEncoder encoder;

    /**
     * The repacketizer seam that combines a frames-per-packet group into one payload.
     */
    private final FramePacker packer;

    /**
     * The group SFrame transform, or {@code null} on a one-to-one call.
     *
     * <p>When {@code null}, the assembled payload is sent in the clear for shared-key SRTP to protect
     * downstream; when present, every payload is sealed before caching and sending.
     */
    private final SFrameTransform sframe;

    /**
     * The transport send seam each finished packet is handed to.
     */
    private final MediaPacketSink sink;

    /**
     * The cache retaining each sent packet for the redundancy schemes.
     */
    private final StreamPacketCache packetCache;

    /**
     * The number of encoded frames packed into one outbound packet.
     */
    private final int framesPerPacket;

    /**
     * The encoded frames buffered toward the current frames-per-packet group.
     *
     * <p>Filled by successive {@link #accept(short[], int)} calls and drained when it reaches
     * {@link #framesPerPacket} or an empty frame forces an early flush.
     */
    private final List<EncodedAudioFrame> pending;

    /**
     * The next RTP extended sequence number to assign to an outbound packet.
     *
     * <p>Advanced by one per sent packet; widened to 32 bits so the redundancy windows order packets
     * across the 16-bit RTP sequence rollover.
     */
    private long nextExtendedSequence;

    /**
     * Whether the call-start Opus priming preamble has been shipped.
     *
     * <p>Set on the first {@link #accept(short[], int)} so the preamble precedes the first encoded frame and
     * is sent exactly once for the call.
     */
    private boolean preambleSent;

    /**
     * Constructs an audio encoder-sender wiring the codec, packer, optional SFrame transform, transport,
     * and packet cache.
     *
     * @param encoder         the codec encode seam; never {@code null}
     * @param packer          the repacketizer seam combining a frames-per-packet group; never {@code null}
     * @param sframe          the group SFrame transform, or {@code null} for a one-to-one call
     * @param sink            the transport send seam; never {@code null}
     * @param packetCache     the cache retaining sent packets for redundancy; never {@code null}
     * @param framesPerPacket the number of encoded frames per packet, in {@code [1, 6]}
     * @throws NullPointerException     if {@code encoder}, {@code packer}, {@code sink}, or
     *                                  {@code packetCache} is {@code null}
     * @throws IllegalArgumentException if {@code framesPerPacket} is outside {@code [1, 6]}
     */
    public AudioEncoderSender(FrameEncoder encoder,
                              FramePacker packer,
                              SFrameTransform sframe,
                              MediaPacketSink sink,
                              StreamPacketCache packetCache,
                              int framesPerPacket) {
        this.encoder = Objects.requireNonNull(encoder, "encoder cannot be null");
        this.packer = Objects.requireNonNull(packer, "packer cannot be null");
        this.sframe = sframe;
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
        this.packetCache = Objects.requireNonNull(packetCache, "packetCache cannot be null");
        if (framesPerPacket < MIN_FRAMES_PER_PACKET || framesPerPacket > MAX_FRAMES_PER_PACKET) {
            throw new IllegalArgumentException(
                    "framesPerPacket must be in [" + MIN_FRAMES_PER_PACKET + ", " + MAX_FRAMES_PER_PACKET
                            + "]: " + framesPerPacket);
        }
        this.framesPerPacket = framesPerPacket;
        this.pending = new ArrayList<>(framesPerPacket);
        this.nextExtendedSequence = 0;
    }

    /**
     * Returns the number of encoded frames packed into one outbound packet.
     *
     * @return the configured frames-per-packet count
     */
    public int framesPerPacket() {
        return framesPerPacket;
    }

    /**
     * Returns whether this sender seals payloads with the group SFrame transform.
     *
     * @return {@code true} on a group call with an SFrame transform, {@code false} on a one-to-one call
     */
    public boolean isGroupSecured() {
        return sframe != null;
    }

    /**
     * Encodes one captured PCM block and sends a packet once a full frames-per-packet group is buffered.
     *
     * <p>Encodes the block through the codec seam. An empty frame (silence the encoder suppressed
     * entirely, a zero-length payload) is not buffered; it flushes any partial group so a trailing frame
     * is shipped rather than stranded, then returns. Otherwise the frame is appended to the pending group,
     * and when the group reaches the frames-per-packet count it is combined, level-tagged, sealed on the
     * group path, cached, and sent.
     *
     * @implNote This implementation keys the flush on the frame payload's emptiness, matching
     * {@code put_frame_imp} (fn4124) of the wa-voip WASM module {@code ff-tScznZ8P}
     * ({@code audio/audio_encoder_sender.cc}): inside the frames-per-packet aggregation branch (taken when
     * the frame type is normal speech) the native code resets the partial group and passes the frame
     * straight through when {@code frame->payload == NULL || frame->payload_len == 0}, and otherwise
     * appends the frame's bytes to the group buffer and returns without sending until the group reaches
     * the configured frame count. A non-empty sub-threshold comfort-noise frame is therefore aggregated,
     * not flushed; the flush trigger is payload emptiness, not the discontinuity classification.
     *
     * @param block  the captured samples from the reader pump; never {@code null}
     * @param length the number of valid samples at the start of {@code block}
     */
    @Override
    public void accept(short[] block, int length) {
        if (!preambleSent) {
            preambleSent = true;
            sendPreamble();
        }
        var frame = encoder.encode(block, length);
        if (frame.isEmpty()) {
            flush();
            return;
        }
        pending.add(frame);
        if (pending.size() >= framesPerPacket) {
            flush();
        }
    }

    /**
     * Combines, seals, caches, and sends the currently buffered frames-per-packet group.
     *
     * <p>Does nothing when no frames are buffered. Otherwise packs the buffered frames into one payload,
     * derives the audio-level extension from the group, seals the payload on the group path, retains the
     * finished packet in the cache keyed by its extended sequence, hands it to the transport, advances the
     * sequence, and clears the buffer. Exposed so the call teardown can flush a trailing partial group
     * before the sender is discarded.
     */
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        var combined = packer.pack(pending);
        var level = levelFor(pending);
        var payload = sframe == null ? combined : sframe.seal(combined);
        var sequence = nextExtendedSequence++;
        packetCache.store(sequence, payload, true);
        // TODO: wire MLowRedPacker - when enable_mlow_red is set, instantiate MLowRedPacker (from server-pushed mlow_red_redundancy_level/stream_mtu/samplesPerFrame) and call pack(packetCache, payload, sequence) here in the group-seal/send path, using its output as the RTP payload passed to sink.send
        sink.send(payload, sequence, level);
        pending.clear();
    }

    /**
     * Ships the call-start Opus priming preamble through the send path before the first encoded frame.
     *
     * <p>Sends each {@link #PREAMBLE_FRAMES} entry as its own packet, advancing the extended sequence and
     * caching it like an encoded frame, with the silence audio level. On a group call each priming payload is
     * SFrame sealed; on a one-to-one call it is sent in the clear for end-to-end SRTP downstream, exactly as a
     * real frame.
     *
     * @implNote This implementation reproduces {@code sendAudioPreamble} of the reference client: the WASM
     * priming frame, the second priming frame, and three discontinuous-transmission frames, sent once before
     * the speech stream so the peer's Opus decoder is primed and the peer locks onto this client's stream;
     * without it a peer that receives the speech RTP can still tear the call down. The frames carry the
     * silence level and are not voice-active.
     */
    private void sendPreamble() {
        var silence = new AudioLevelRtpExtension(AudioLevelRtpExtension.SILENCE_LEVEL, false);
        for (var frame : PREAMBLE_FRAMES) {
            var payload = sframe == null ? frame : sframe.seal(frame);
            var sequence = nextExtendedSequence++;
            packetCache.store(sequence, payload, true);
            sink.send(payload, sequence, silence);
        }
    }

    /**
     * Derives the audio-level extension for a frames-per-packet group from its loudest frame.
     *
     * <p>The packet advertises one level for the whole group, so the loudest frame governs: the level is
     * the minimum {@code -dBov} magnitude across the group (the loudest sample, since a smaller magnitude
     * is louder), taken from each frame's {@linkplain EncodedAudioFrame#levelDbov() measured level}, and
     * the voice-activity flag is set when any frame in the group is voice-active. A group every frame of
     * which measures silence reports {@link AudioLevelRtpExtension#SILENCE_LEVEL}.
     *
     * @implNote This implementation reproduces the windowed level the wa-voip WASM module
     * {@code ff-tScznZ8P} selects in {@code wa_audio_level_rtp_ext_utils.cc} (the getter inlined into the
     * audio-encoder-sender packetizer fn4295): it scans the per-frame levels and keeps the minimum
     * {@code -dBov} magnitude. The native getter then zeroes the low {@code num_lsb_to_zero} bits of the
     * selected level; that masking is not applied here because the {@code num_lsb_to_zero} default is
     * unrecoverable from the WASM (the param parser binds the name to a struct slot without a compiled
     * immediate, and the key is absent from the 759-key voip-settings union), and a default of {@code 0}
     * (which leaves the level unmasked) is the documented assumption. The native window spans the whole
     * audio-level history (capacity {@code audio_level_capacity}, duration
     * {@code audio_level_history_duration_ms}); this sender takes the minimum over the frames-per-packet
     * group it is about to ship, which is the slice of that history the packet covers.
     *
     * @param frames the buffered frames-per-packet group; never empty
     * @return the audio-level extension to attach to the combined packet
     */
    private AudioLevelRtpExtension levelFor(List<EncodedAudioFrame> frames) {
        var voiceActive = false;
        var level = AudioLevelRtpExtension.SILENCE_LEVEL;
        for (var frame : frames) {
            voiceActive |= frame.voiceActive();
            level = Math.min(level, frame.levelDbov());
        }
        return new AudioLevelRtpExtension(level, voiceActive);
    }
}
