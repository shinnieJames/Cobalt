package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;

/**
 * Generates the call identifiers carried by the {@code call-id} attribute of every outbound call payload.
 *
 * <p>A generated identifier is a {@value #LENGTH}-character uppercase hexadecimal string whose first
 * character pair is always the fixed {@link #PREFIX}, with the remaining thirty characters drawn from
 * a cryptographically random sample. The fixed prefix means the first byte of every identifier is
 * {@code 0x00}; an example value is {@code "00B8E865663D28CFFF9469A77156A381"}. Some peer clients and
 * the relay reject identifiers whose length differs from {@value #LENGTH}, so callers placing a call
 * must obtain the identifier from here rather than emitting a shorter raw random value.
 *
 * @implNote This implementation draws sixteen random bytes (thirty-two hex characters), discards the
 * leading two characters, and re-prepends {@link #PREFIX}, reproducing the production identifier shape
 * exactly while keeping the total length at {@value #LENGTH}.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStartCall")
public final class CallIdGenerator {
    /**
     * The fixed two-hexadecimal-character prefix that every generated call identifier begins with.
     */
    public static final String PREFIX = "00";

    /**
     * The total length, in characters, of a generated call identifier.
     */
    public static final int LENGTH = 32;

    /**
     * Prevents instantiation of this static helper.
     *
     * @throws AssertionError always, since this class is not instantiable
     */
    private CallIdGenerator() {
        throw new AssertionError("CallIdGenerator is not instantiable");
    }

    /**
     * Generates a fresh call identifier.
     *
     * <p>The returned value is a {@value #LENGTH}-character uppercase hexadecimal string that starts
     * with {@link #PREFIX} and carries thirty cryptographically random hexadecimal characters after it.
     * Each invocation produces an independent value.
     *
     * @return a fresh {@value #LENGTH}-character uppercase hexadecimal call identifier beginning with
     *         {@link #PREFIX}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipStartCall", exports = "generateCallId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String generate() {
        var raw = DataUtils.randomHex(16);
        return PREFIX + raw.substring(2);
    }
}
