package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;

/**
 * Generates the 32-character call identifiers used in the
 * {@code call-id} attribute of every outbound {@code <offer>}.
 *
 * <p>Mirrors WA Web's {@code WAWebVoipStartCall.me()}:
 *
 * <pre>{@code
 *   var e = "00" + WARandomHex.randomHex(16).substr(2);
 * }</pre>
 *
 * <p>WA Web's choice produces 32 hex characters where the first
 * character pair is always {@code "00"}, with the remaining 30
 * characters drawn from a 15-byte CSPRNG sample. Live captures
 * confirm the format (e.g. {@code "00B8E865663D28CFFF9469A77156A381"}).
 *
 * <p>Cobalt's previous implementation (raw {@link DataUtils#randomHex(int)})
 * produced 16-char identifiers; that is rejected by some peer clients
 * because the relay cross-checks the length. This generator is the
 * authoritative path.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStartCall")
public final class CallIdGenerator {
    /**
     * The fixed two-hex-character prefix every call identifier carries.
     */
    public static final String PREFIX = "00";

    /**
     * The total length of a generated call identifier in characters.
     */
    public static final int LENGTH = 32;

    /**
     * Hidden constructor.
     */
    private CallIdGenerator() {
        throw new AssertionError("CallIdGenerator is not instantiable");
    }

    /**
     * Generates a fresh call identifier in the WA-Web-compatible
     * 32-character {@code "00" + 30 hex chars} format.
     *
     * @return a fresh, uppercase, 32-character hex string starting with
     *         {@code "00"}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipStartCall", exports = "generateCallId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String generate() {
        // WAWebVoipStartCall.me: var e = "00" + WARandomHex.randomHex(16).substr(2);
        // randomHex(16) yields 32 hex chars; substr(2) drops the first two; prefix "00"
        // gives a 32-char id whose first byte is always 0x00.
        var raw = DataUtils.randomHex(16);
        return PREFIX + raw.substring(2);
    }
}
