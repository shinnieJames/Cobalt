package com.github.auties00.cobalt.message.receive.stanza;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed reporting token information from the {@code <reporting>} child
 * node of an incoming message stanza.
 *
 * <p>Reporting tokens are used by WhatsApp's content moderation system.
 * When present, they must be validated and stored alongside the message
 * for potential future reporting.
 *
 * @implNote WAWebHandleMsgParser function k(): parses the reporting node
 * to extract reporting_token (bytes + version), reporting_tag (bytes),
 * and stanzaTs (timestamp from the parent stanza's {@code t} attribute).
 */
public final class MessageReceiveReportingInfo {
    /**
     * The stanza timestamp, included in the reporting info for correlation.
     *
     * @implNote WAWebHandleMsgParser function k(): {@code stanzaTs: e.attrTime("t")}
     */
    private final Instant stanzaTs;

    /**
     * The reporting token bytes from the {@code <reporting_token>} child.
     *
     * @implNote WAWebHandleMsgParser function k(): {@code reportingToken: r.contentBytes()}
     */
    private final byte[] reportingToken;

    /**
     * The reporting token version from the {@code v} attribute.
     *
     * @implNote WAWebHandleMsgParser function k(): {@code version: r.attrInt("v")}
     */
    private final int version;

    /**
     * The reporting tag bytes from the {@code <reporting_tag>} child.
     *
     * @implNote WAWebHandleMsgParser function k(): {@code reportingTag: a.contentBytes()}
     */
    private final byte[] reportingTag;

    /**
     * Constructs a new reporting info with the given parameters.
     *
     * @param stanzaTs       the stanza timestamp
     * @param reportingToken the reporting token bytes (nullable)
     * @param version        the reporting token version
     * @param reportingTag   the reporting tag bytes (nullable)
     * @implNote WAWebHandleMsgParser function k()
     */
    public MessageReceiveReportingInfo(
            Instant stanzaTs,
            byte[] reportingToken,
            int version,
            byte[] reportingTag
    ) {
        this.stanzaTs = Objects.requireNonNull(stanzaTs, "stanzaTs cannot be null");
        this.reportingToken = reportingToken;
        this.version = version;
        this.reportingTag = reportingTag;
    }

    /**
     * Returns the stanza timestamp included for reporting correlation.
     *
     * @return the stanza timestamp, never {@code null}
     * @implNote WAWebHandleMsgParser function k(): {@code stanzaTs: e.attrTime("t")}
     */
    public Instant stanzaTs() {
        return stanzaTs;
    }

    /**
     * Returns the reporting token bytes, if present.
     *
     * @return an {@link Optional} containing the reporting token bytes
     * @implNote WAWebHandleMsgParser function k(): {@code reportingToken: r.contentBytes()}
     */
    public Optional<byte[]> reportingToken() {
        return Optional.ofNullable(reportingToken);
    }

    /**
     * Returns the reporting token version.
     *
     * @return the version number
     * @implNote WAWebHandleMsgParser function k(): {@code version: r.attrInt("v")}
     */
    public int version() {
        return version;
    }

    /**
     * Returns the reporting tag bytes, if present.
     *
     * @return an {@link Optional} containing the reporting tag bytes
     * @implNote WAWebHandleMsgParser function k(): {@code reportingTag: a.contentBytes()}
     */
    public Optional<byte[]> reportingTag() {
        return Optional.ofNullable(reportingTag);
    }
}
