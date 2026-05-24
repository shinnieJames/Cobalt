package com.github.auties00.cobalt.node.iq.debug;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import java.util.Optional;

/**
 * Enumerates the GDPR data export scopes that the {@code <gdpr>} stanza family addresses.
 *
 * @apiNote
 * Pick a value when issuing or cancelling a GDPR data-export request via the
 * {@link IqDebugGdprRequest} family. {@link #ACCOUNT} covers the full account export
 * (transcripts, settings, profile); {@link #NEWSLETTERS} narrows the export to the user's
 * channel follow graph and channel-post history. The wire encoding rule is to omit the
 * {@code report_type} attribute entirely for {@link #ACCOUNT} and emit
 * {@code report_type="newsletters"} for {@link #NEWSLETTERS}.
 *
 * @implNote
 * This implementation mirrors WA Web's mirrored-enum constants verbatim. The
 * {@code null} wire string for {@link #ACCOUNT} encodes WA Web's "no
 * {@code report_type} attribute" branch in {@link IqDebugGdprRequest#toNode()}.
 */
@WhatsAppWebModule(moduleName = "WAWebGdprConstants")
public enum IqDebugGdprReportType {
    /**
     * Indicates a full-account GDPR data export.
     *
     * @apiNote
     * Covers transcripts, settings and profile data; selected by default when the
     * user invokes "Request account info" from the WA Web settings drawer.
     *
     * @implNote
     * This implementation maps to no {@code report_type} attribute on the wire,
     * matching WA Web's switch arm that returns {@code null} for
     * {@code ReportType.Account}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGdprConstants",
            exports = "ReportType.Account",
            adaptation = WhatsAppAdaptation.DIRECT)
    ACCOUNT(null),

    /**
     * Indicates a channel-only GDPR data export.
     *
     * @apiNote
     * Covers the user's channel follow graph and the user's own channel posts;
     * selected from the Channels-specific export entry point.
     *
     * @implNote
     * This implementation maps to the literal {@code report_type="newsletters"}
     * attribute on the wire, matching WA Web's switch arm.
     */
    @WhatsAppWebExport(moduleName = "WAWebGdprConstants",
            exports = "ReportType.Newsletters",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEWSLETTERS("newsletters");

    /**
     * Holds the literal {@code report_type} attribute value, or {@code null} when the
     * attribute is to be omitted from the outbound {@code <gdpr>} stanza.
     */
    private final String wire;

    /**
     * Constructs a report-type constant bound to the given wire string.
     *
     * @apiNote
     * Not part of the public surface; the constants are constructed by the enum
     * declarations above. Library callers select an existing constant rather than
     * constructing one.
     *
     * @param wire the literal attribute value, or {@code null} to omit the
     *             {@code report_type} attribute entirely
     */
    IqDebugGdprReportType(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the optional wire string emitted on the {@code report_type} attribute.
     *
     * @apiNote
     * An empty {@link Optional} signals the "omit attribute" branch (see
     * {@link IqDebugGdprRequest#toNode()}); a present value is routed verbatim into
     * the outbound stanza.
     *
     * @return an {@link Optional} carrying the literal wire string, or empty when the
     *         attribute is to be omitted
     */
    public Optional<String> wire() {
        return Optional.ofNullable(wire);
    }
}
