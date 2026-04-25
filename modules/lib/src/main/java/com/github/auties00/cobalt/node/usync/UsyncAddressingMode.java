package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the {@code addressing_mode} values accepted by the
 * {@code <contact>} child of a USync query.
 *
 * <p>WhatsApp Web has been migrating user identifiers from phone-number JIDs
 * (PN) to long IDs (LID) for several releases. The contact protocol uses this
 * attribute to disambiguate which identifier space the request applies to.
 *
 * <p>WhatsApp Web exports the constants as
 * {@code USYNC_ADDRESSING_MODE = {PN: "pn", LID: "lid"}} from
 * {@code WAWebUsync}; callers branch on the literal strings. Cobalt mirrors
 * the same wire values via {@link #wireValue()} but exposes them as a Java
 * enum so call sites cannot typo the value silently.
 *
 * @implNote WAWebUsync.USYNC_ADDRESSING_MODE: frozen object with
 *     {@code PN: "pn"} and {@code LID: "lid"}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public enum UsyncAddressingMode {
    /**
     * Phone-number addressing. The contact query targets the legacy
     * {@code @s.whatsapp.net} JID space.
     *
     * @implNote WAWebUsync.USYNC_ADDRESSING_MODE.PN: literal value
     *     {@code "pn"}. The contact protocol omits the
     *     {@code addressing_mode} attribute entirely on the wire when this
     *     mode is selected (mirrors {@code DROP_ATTR} behaviour).
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE", adaptation = WhatsAppAdaptation.DIRECT)
    PN("pn"),

    /**
     * LID addressing. The contact query targets the {@code @lid} identifier
     * space and the {@code addressing_mode="lid"} attribute is emitted on
     * the {@code <contact>} query element.
     *
     * @implNote WAWebUsync.USYNC_ADDRESSING_MODE.LID: literal value
     *     {@code "lid"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE", adaptation = WhatsAppAdaptation.DIRECT)
    LID("lid");

    /**
     * The literal value emitted on the wire.
     */
    private final String wireValue;

    /**
     * Creates a new addressing mode bound to the given wire string.
     *
     * @param wireValue the literal value the relay expects
     */
    UsyncAddressingMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal string emitted on the {@code addressing_mode}
     * attribute.
     *
     * @return the wire value
     */
    public String wireValue() {
        return wireValue;
    }
}
