package com.github.auties00.cobalt.message.send.ack;

import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Objects;

/**
 * Parses the server acknowledgement node returned after a message stanza
 * is sent.
 *
 * <p>The server responds to each outgoing {@code <message>} stanza with an
 * {@code <ack>} node containing delivery metadata, an optional participant
 * hash for group-message verification, and an optional error code.
 *
 * @implNote WAWebSendMsgCommonApi.sendMsgAckSyncParser: parses the ack
 * stanza asserting tag {@code "ack"}, then extracts {@code t},
 * {@code sync}, {@code phash}, {@code refresh_lid}, {@code addressing_mode},
 * {@code count}, and {@code error} attributes.
 * @see AckResult
 * @see NackReason
 */
public final class AckParser {
    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote WAWebSendMsgCommonApi.sendMsgAckSyncParser: static parser,
     * no instances needed.
     */
    private AckParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses a server ack node into a structured result.
     *
     * @implNote WAWebSendMsgCommonApi.sendMsgAckSyncParser.parse:
     * asserts tag "ack" then extracts each attribute.
     * @param ack the ack node returned by the server
     * @return the parsed result
     * @throws NullPointerException     if {@code ack} is {@code null}
     * @throws IllegalArgumentException if the node tag is not {@code "ack"}
     */
    public static AckResult parse(Node ack) {
        Objects.requireNonNull(ack, "ack");
        if (!ack.hasDescription("ack")) {
            throw new IllegalArgumentException(
                    "Expected <ack> node, got <" + ack.description() + ">");
        }

        // WAWebSendMsgCommonApi: e.attrTime("t")
        var timestampSeconds = ack.getAttributeAsLong("t", null);
        var timestamp = timestampSeconds != null
                ? Instant.ofEpochSecond(timestampSeconds)
                : null;

        // WAWebSendMsgCommonApi: e.maybeAttrString("sync")
        var sync = ack.getAttributeAsString("sync", null);

        // WAWebSendMsgCommonApi: e.maybeAttrString("phash")
        var phash = ack.getAttributeAsString("phash", null);

        // WAWebSendMsgCommonApi: e.hasAttr("refresh_lid") ? e.attrString("refresh_lid")==="true" : false
        var refreshLid = ack.getAttributeAsBool("refresh_lid", false);

        // WAWebSendMsgCommonApi: e.maybeAttrString("addressing_mode")
        var addressingMode = ack.getAttributeAsString("addressing_mode", null);

        // WAWebSendMsgCommonApi: e.maybeAttrInt("count")
        var count = ack.getAttributeAsInt("count", null);

        // WAWebSendMsgCommonApi: e.maybeAttrInt("error")
        var error = ack.getAttributeAsInt("error", null);

        return new AckResult(
                timestamp,
                sync,
                phash,
                refreshLid,
                addressingMode,
                count,
                error
        );
    }
}
