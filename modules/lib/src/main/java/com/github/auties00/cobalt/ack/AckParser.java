package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Objects;

/**
 * Decodes a server {@code <ack>} {@link Node} into a structured
 * {@link AckResult}.
 *
 * @apiNote
 * Invoked by every send path in
 * {@link com.github.auties00.cobalt.message.send.MessageSendingService} after
 * the wire stanza has been dispatched and the synchronous response has been
 * received. The parser is structurally identical to WA Web's
 * {@code sendMsgAckSyncParser} wap parser; embedders that need to interpret a
 * raw ack node (for example to feed it back into a custom retry loop) can use
 * the public {@link #parse(Node)} entry point directly.
 *
 * @see AckResult
 * @see NackReason
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCommonApi")
public final class AckParser {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private AckParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses the given {@code <ack>} node into an {@link AckResult}.
     *
     * @apiNote
     * Pulls the {@code t}, {@code sync}, {@code phash}, {@code refresh_lid},
     * {@code addressing_mode}, {@code count}, and {@code error} attributes off
     * the supplied node and returns a flattened result; the input must already
     * be the {@code <ack>} element (not its enclosing {@code <iq>}).
     *
     * @param ack the {@code <ack>} node returned by the server
     * @return the parsed {@link AckResult}
     * @throws NullPointerException     if {@code ack} is {@code null}
     * @throws IllegalArgumentException if the node tag is not {@code "ack"}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static AckResult parse(Node ack) {
        Objects.requireNonNull(ack, "ack");
        if (!ack.hasDescription("ack")) {
            throw new IllegalArgumentException(
                    "Expected <ack> node, got <" + ack.description() + ">");
        }

        var timestampSeconds = ack.getAttributeAsLong("t", null);
        var timestamp = timestampSeconds != null
                ? Instant.ofEpochSecond(timestampSeconds)
                : null;
        var sync = ack.getAttributeAsString("sync", null);
        var phash = ack.getAttributeAsString("phash", null);
        var refreshLid = ack.getAttributeAsBool("refresh_lid", false);
        var addressingMode = ack.getAttributeAsString("addressing_mode", null);
        var count = ack.getAttributeAsInt("count", null);
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