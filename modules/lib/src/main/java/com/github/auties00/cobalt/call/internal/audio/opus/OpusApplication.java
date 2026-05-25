package com.github.auties00.cobalt.call.internal.audio.opus;

import com.github.auties00.cobalt.call.internal.audio.opus.bindings.Opus;

/**
 * Selects one of libopus's three application modes that trade latency
 * against quality.
 *
 * <p>The application mode is fixed at encoder construction and biases
 * every later coding decision: which internal layer (SILK, CELT, or the
 * hybrid of both) is preferred at a given bitrate, whether an aggressive
 * transmit-side high-pass filter is applied, and how much algorithmic
 * delay the encoder is allowed to introduce. It is passed once to
 * {@code opus_encoder_create} and cannot be changed afterward. The three
 * modes correspond exactly to the {@code OPUS_APPLICATION_*} constants
 * defined by RFC 6716. WhatsApp's call engine uses {@link #VOIP}: its
 * captured audio configuration is 16 kHz mono with 10 ms framing and a
 * voice signal type.
 */
public enum OpusApplication {
    /**
     * Favors voice intelligibility over fidelity.
     *
     * <p>This mode steers the encoder toward the SILK layer at voice
     * bitrates and applies an aggressive transmit-side high-pass filter
     * that removes DC offset and low-frequency rumble before coding. It
     * matches WhatsApp's call engine configuration.
     */
    VOIP,

    /**
     * Balances quality for mixed music and voice content.
     *
     * <p>This mode lets the encoder spend more complexity on broadband
     * fidelity and does not apply the aggressive high-pass filter that
     * {@link #VOIP} uses, at the cost of being less optimized for pure
     * speech.
     */
    AUDIO,

    /**
     * Restricts the encoder to the modes with the lowest algorithmic
     * delay.
     *
     * <p>This mode is intended for latency-sensitive applications that
     * cannot tolerate the extra lookahead the other modes introduce. It
     * is not used for WhatsApp call interoperability.
     */
    RESTRICTED_LOWDELAY;

    /**
     * Returns the libopus {@code OPUS_APPLICATION_*} integer code for
     * this mode.
     *
     * <p>The returned value is the native constant accepted by
     * {@code opus_encoder_create}; each enum constant maps to exactly one
     * {@code OPUS_APPLICATION_*} code resolved from the {@code Opus}
     * bindings.
     *
     * @return the libopus application code corresponding to this constant
     */
    int toNative() {
        return switch (this) {
            case VOIP -> Opus.OPUS_APPLICATION_VOIP();
            case AUDIO -> Opus.OPUS_APPLICATION_AUDIO();
            case RESTRICTED_LOWDELAY -> Opus.OPUS_APPLICATION_RESTRICTED_LOWDELAY();
        };
    }
}
