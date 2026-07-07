package com.github.auties00.cobalt.calls2.dsp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

/**
 * The decoder seam the {@link LiveNetEq} jitter buffer pulls each rendered audio frame through, one
 * decoder per registered payload type.
 *
 * <p>A decoder turns one codec packet into PCM, conceals a lost frame, and reports its output format so the
 * jitter buffer can size the rendered frame. {@link #decode(byte[], int, boolean)} decodes a packet,
 * optionally reconstructing the previous frame from this packet's in-band forward-error-correction copy;
 * {@link #conceal(int)} synthesizes a concealment frame with no input packet, the codec's own packet-loss
 * concealment; {@link #reset()} clears decoder state across a discontinuity; and {@link #sampleRate()} and
 * {@link #channels()} report the output geometry. The jitter buffer owns the decision of when to decode
 * versus conceal; the decoder only executes the per-frame DSP.
 *
 * <p>The hierarchy is sealed for exhaustive matching across the payload types NetEq registers:
 * {@link OpusAudioDecoder} wraps libopus and is the default; {@link MLowAudioDecoder} is the MLow CELP
 * low-bitrate speech decoder; {@link ComfortNoiseDecoder} generates RFC 3389 comfort noise for a
 * discontinuous-transmission gap; and {@link DtmfDecoder} renders RFC 4733 telephone-event tones. A decoder
 * is single-writer: the jitter buffer drives one instance from its pull thread.
 *
 * @implNote This interface mirrors the {@code concerto::AudioDecoder} registry the
 * {@code concerto::DecoderDatabase} (fn7284) of the wa-voip WASM module {@code ff-tScznZ8P} maps payload
 * types onto: {@code SplitAudio} dispatches a packet to the decoder its payload type resolves to, and the
 * NetEq get-audio loop ({@code GetAudioInternal} fn7521) calls the decoder for a normal decode or the
 * concealment entry for codec PLC. Comfort noise and DTMF are pseudo-decoders in the same registry. The
 * concrete audio decoders reach their codec kernels through native bindings; comfort noise and DTMF are
 * pure-Java synthesis.
 */
public sealed interface AudioDecoder extends AutoCloseable
        permits OpusAudioDecoder, MLowAudioDecoder, ComfortNoiseDecoder, DtmfDecoder {
    /**
     * Decodes one codec packet into PCM, optionally reconstructing the previous frame from in-band FEC.
     *
     * @implSpec Implementations must return exactly {@code frameSamples} times {@link #channels()} samples.
     * When {@code fec} is {@code true} the supplied packet is the one after a lost packet and the decoder
     * must extract the in-band copy of the lost frame from it; when {@code false} the packet is decoded
     * normally.
     *
     * @param payload      the codec packet bytes; never {@code null}
     * @param frameSamples the per-channel sample count to produce
     * @param fec          whether to reconstruct the previous frame from this packet's in-band FEC
     * @return the decoded PCM, {@code frameSamples * channels} signed 16-bit samples
     * @throws NullPointerException     if {@code payload} is {@code null}
     * @throws IllegalStateException    if the decoder is closed
     * @throws WhatsAppCallException     if decoding fails
     */
    short[] decode(byte[] payload, int frameSamples, boolean fec);

    /**
     * Pairs one decoded frame with its receive-side voice-activity verdict.
     *
     * @param pcm         the decoded interleaved PCM, {@code frameSamples * channels} signed 16-bit samples
     * @param voiceActive whether the decoded packet carried active speech
     */
    record DecodedFrame(short[] pcm, boolean voiceActive) {
    }

    /**
     * Decodes one codec packet and reports its voice-activity verdict in a single call.
     *
     * <p>Pairs the {@link #decode(byte[], int, boolean)} output with the packet's own voice-activity
     * verdict: a normal decode reads {@link #packetHasVoiceActivity(byte[])}, while a
     * forward-error-correction reconstruction ({@code fec == true}) carries no packet of its own and reports
     * inactive. The result is a fresh immutable {@link DecodedFrame}, so the fused call adds no shared
     * decoder state and keeps the single-writer contract of {@link #decode(byte[], int, boolean)}.
     *
     * @implSpec The default implementation invokes {@link #decode(byte[], int, boolean)} then, when
     * {@code fec} is {@code false}, {@link #packetHasVoiceActivity(byte[])}; an implementation may override to
     * fuse the two into one native round-trip provided the returned PCM and verdict remain identical to the
     * two separate calls.
     *
     * @param payload      the codec packet bytes; never {@code null}
     * @param frameSamples the per-channel sample count to produce
     * @param fec          whether to reconstruct the previous frame from this packet's in-band FEC
     * @return the decoded PCM paired with its voice-activity verdict
     * @throws NullPointerException  if {@code payload} is {@code null}
     * @throws IllegalStateException if the decoder is closed
     * @throws WhatsAppCallException  if decoding fails
     */
    default DecodedFrame decodeWithVoiceActivity(byte[] payload, int frameSamples, boolean fec) {
        var pcm = decode(payload, frameSamples, fec);
        var voiceActive = !fec && packetHasVoiceActivity(payload);
        return new DecodedFrame(pcm, voiceActive);
    }

    /**
     * Synthesizes one concealment frame with no input packet, the codec's packet-loss concealment.
     *
     * @implSpec Implementations must return exactly {@code frameSamples} times {@link #channels()} samples,
     * extrapolated from decoder state; a decoder that has decoded nothing yet may return silence.
     *
     * @param frameSamples the per-channel sample count to produce
     * @return the concealment PCM, {@code frameSamples * channels} signed 16-bit samples
     * @throws IllegalStateException if the decoder is closed
     * @throws WhatsAppCallException  if concealment fails
     */
    short[] conceal(int frameSamples);

    /**
     * Reports whether the given codec packet carries active speech, the per-packet receive-side
     * voice-activity verdict.
     *
     * <p>The verdict is read from the packet's own classification, not from the decoded PCM, so a
     * comfort-noise or otherwise inactive packet reports inactive even when it decodes to non-silent
     * samples. It is the value the receive path records for audio-level metering and mixing for a frame
     * produced by a normal decode of this packet; a concealment or forward-error-correction reconstruction
     * carries no packet of its own and is treated as inactive by the caller.
     *
     * @implSpec Implementations must derive the verdict from the packet itself, never from a fixed value:
     * a codec whose packets carry a voice-activity flag (the Opus per-SILK-frame VAD bits, or the MLow TOC
     * voice-activity bit) reads that flag, and a pseudo-decoder whose payloads never carry speech (comfort
     * noise, telephone events) returns {@code false}. An empty or malformed packet is reported inactive
     * rather than throwing.
     *
     * @param payload the codec packet bytes; never {@code null}
     * @return whether the packet is voice-active
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    boolean packetHasVoiceActivity(byte[] payload);

    /**
     * Clears decoder state so the next decode does not extrapolate across a stream discontinuity.
     *
     * @implSpec Implementations must return the decoder to the state it would hold immediately after
     * construction, without releasing native resources.
     */
    void reset();

    /**
     * Returns the decoder's output sample rate in hertz.
     *
     * @return the output sample rate
     */
    int sampleRate();

    /**
     * Returns the decoder's output channel count.
     *
     * @return the output channel count, {@code 1} for mono
     */
    int channels();

    /**
     * Releases the decoder's native state and any owned resources.
     *
     * @implSpec Implementations must be idempotent; after closing, every other method throws
     * {@link IllegalStateException}.
     */
    @Override
    void close();
}
