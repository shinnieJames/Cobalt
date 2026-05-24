package com.github.auties00.cobalt.call.internal.audio.opus;

import com.github.auties00.cobalt.call.internal.audio.opus.bindings.Opus;

/**
 * The Opus encoder's application mode — selects between three
 * latency/quality tradeoff profiles defined by RFC 6716 §2.1.4.
 *
 * <p>WhatsApp's wasm engine uses {@link #VOIP} for voice calls (the
 * captured {@code AudioDriverConfig} confirms 16 kHz mono with the
 * VoIP signal type and 10 ms framing).
 */
public enum OpusApplication {
    /**
     * Optimised for voice signals — emphasises intelligibility over
     * fidelity, applies an aggressive transmit-side high-pass filter
     * to remove DC and low-frequency rumble. Matches WhatsApp's wasm
     * configuration.
     */
    VOIP,

    /**
     * Balanced for general audio — music + voice. Higher complexity
     * encoder than VOIP, no aggressive HPF.
     */
    AUDIO,

    /**
     * Restricted to fewer modes for lower algorithmic delay — used
     * by latency-sensitive applications. Not relevant for WhatsApp
     * call interop.
     */
    RESTRICTED_LOWDELAY;

    /**
     * Maps the enum constant to libopus's {@code OPUS_APPLICATION_*}
     * integer code passed to {@code opus_encoder_create}.
     *
     * @return the libopus application code
     */
    int toNative() {
        return switch (this) {
            case VOIP -> Opus.OPUS_APPLICATION_VOIP();
            case AUDIO -> Opus.OPUS_APPLICATION_AUDIO();
            case RESTRICTED_LOWDELAY -> Opus.OPUS_APPLICATION_RESTRICTED_LOWDELAY();
        };
    }
}
