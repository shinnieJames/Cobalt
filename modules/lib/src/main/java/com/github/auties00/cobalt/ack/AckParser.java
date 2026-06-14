package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Objects;

/**
 * Decodes a server {@code <ack>} {@link Node} into a structured {@link AckResult}.
 *
 * <p>This utility class exposes a single static {@link #parse(Node)} entry point that flattens
 * the common envelope attributes ({@code id}, {@code t}, {@code class}, {@code type},
 * {@code from}, {@code participant}, {@code recipient}, {@code error}) and dispatches on the
 * {@code class} attribute to populate the matching {@link AckResult} subtype:
 *
 * <ul>
 *   <li>{@code message} → {@link MessageAck} (carries {@code sync}, {@code phash},
 *       {@code refresh_lid}, {@code addressing_mode}, {@code count})</li>
 *   <li>{@code receipt} → {@link ReceiptAck}</li>
 *   <li>{@code notification} → {@link NotificationAck}</li>
 *   <li>{@code call} → {@link CallAck} (carries the {@code <relay>} child on offer ACKs)</li>
 * </ul>
 *
 * <p>An unrecognised {@code class} attribute is parsed as {@link MessageAck} since the
 * message-fanout slots are the safest superset on the wire.
 *
 * @see AckResult
 * @see NackReason
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCommonApi")
@WhatsAppWebModule(moduleName = "WAAckParser")
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
     * Parses the given {@code <ack>} node into the matching {@link AckResult} subtype.
     *
     * <p>The input must already be the {@code <ack>} element, not its enclosing {@code <iq>}.
     * The {@code t} attribute is interpreted as epoch seconds and converted to an
     * {@link Instant}, {@code refresh_lid} defaults to {@code false} when absent, and the
     * dispatch on {@code class} populates the message-fanout slots only for
     * {@link MessageAck}, the {@code <relay>} child only for {@link CallAck}.
     *
     * @param ack the {@code <ack>} node returned by the server
     * @return the parsed {@link AckResult} subtype
     * @throws NullPointerException     if {@code ack} is {@code null}
     * @throws IllegalArgumentException if the node tag is not {@code "ack"}
     */
    @WhatsAppWebExport(moduleName = "WAAckParser", exports = "AckParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static AckResult parse(Node ack) {
        Objects.requireNonNull(ack, "ack");
        if (!ack.hasDescription("ack")) {
            throw new IllegalArgumentException(
                    "Expected <ack> node, got <" + ack.description() + ">");
        }

        var id = ack.getAttributeAsString("id", null);
        var timestampSeconds = ack.getAttributeAsLong("t", null);
        var timestamp = timestampSeconds != null
                ? Instant.ofEpochSecond(timestampSeconds)
                : null;
        var ackClassToken = ack.getAttributeAsString("class", null);
        var ackClass = AckClass.fromWireToken(ackClassToken);
        var type = ack.getAttributeAsString("type", null);
        var from = ack.getAttributeAsJid("from", null);
        var participant = ack.getAttributeAsJid("participant", null);
        var recipient = ack.getAttributeAsJid("recipient", null);
        var error = ack.getAttributeAsInt("error", null);

        return switch (ackClass) {
            case RECEIPT -> new ReceiptAck(id, timestamp, type, from, participant, recipient,
                    error);
            case NOTIFICATION -> new NotificationAck(id, timestamp, type, from, participant,
                    recipient, error);
            case CALL -> {
                var relay = ack.getChild("relay").flatMap(CallRelay::parse).orElse(null);
                yield new CallAck(id, timestamp, type, from, participant, recipient, error, relay);
            }
            case null, default -> {
                var sync = ack.getAttributeAsString("sync", null);
                var phash = ack.getAttributeAsString("phash", null);
                var refreshLid = ack.getAttributeAsBool("refresh_lid", false);
                var addressingMode = ack.getAttributeAsString("addressing_mode", null);
                var count = ack.getAttributeAsInt("count", null);
                yield new MessageAck(id, timestamp, type, from, participant, recipient, error,
                        sync, phash, refreshLid, addressingMode, count);
            }
        };
    }
}
