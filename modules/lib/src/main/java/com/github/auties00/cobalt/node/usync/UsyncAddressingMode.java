package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the {@code addressing_mode} values the USync {@code <contact>}
 * child accepts.
 *
 * @apiNote
 * Pass {@link #LID} when the local contact database has been migrated to long
 * identifiers and the relay should resolve and verify peers in the LID space;
 * pass {@link #PN} when the request still carries phone-number JIDs. Most
 * call sites obtain the right value from
 * {@code WAWebUsernameGatingUtils.usernameContactUsyncLidBased()}.
 *
 * @implNote
 * This implementation is the typed Cobalt counterpart of the frozen
 * {@code USYNC_ADDRESSING_MODE} object in {@code WAWebUsync}; the JS dictionary
 * exposes the same {@code "pn"} and {@code "lid"} strings and is consumed by
 * branching on the raw values.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public enum UsyncAddressingMode {
    /**
     * Phone-number addressing.
     *
     * @apiNote
     * The {@code addressing_mode} attribute is dropped from the wire when this
     * value is selected, matching the JS {@code DROP_ATTR} default.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE", adaptation = WhatsAppAdaptation.DIRECT)
    PN("pn"),

    /**
     * LID addressing.
     *
     * @apiNote
     * Selects the {@code @lid} identifier space and forces {@code addressing_mode="lid"}
     * onto the {@code <contact>} query element.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE", adaptation = WhatsAppAdaptation.DIRECT)
    LID("lid");

    /**
     * The literal value emitted on the {@code addressing_mode} attribute.
     */
    private final String wireValue;

    /**
     * Binds a new constant to its wire literal.
     *
     * @param wireValue the literal the relay expects on the wire
     */
    UsyncAddressingMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal emitted on the {@code addressing_mode} attribute of
     * the {@code <contact>} query element.
     *
     * @apiNote
     * Call sites that build raw {@link com.github.auties00.cobalt.node.Node}
     * stanzas without going through {@link UsyncQuery} use this to mirror the
     * exact JS attribute value.
     *
     * @return the wire literal
     */
    public String wireValue() {
        return wireValue;
    }
}
