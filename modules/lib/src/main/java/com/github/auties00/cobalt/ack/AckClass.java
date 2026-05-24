package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

/**
 * Enumerates the stanza classes Cobalt writes into the {@code class}
 * attribute of an outbound {@code <ack>}.
 *
 * <p>The WhatsApp wire protocol stamps every {@code <ack>} with one of
 * four class values that name the inbound stanza being acknowledged:
 * {@code message}, {@code receipt}, {@code notification} and
 * {@code call}. Each constant binds the typed value to its on-wire
 * token and carries the {@code WhatsAppWebExport} annotation pinning
 * it to the WA Web ack-builder that produces that class.
 *
 * @apiNote
 * Every {@link AckSender} entry point accepts an {@code AckClass}.
 * Per-class default behaviour for the {@code type} and
 * {@code participant} attributes is applied by
 * {@link AckBuilder#send()}; see its javadoc for the resolution rules.
 *
 * @implNote
 * This implementation lists only the four classes Cobalt currently
 * ships outbound acks for. WA Web's {@code AckParser.parse} also
 * recognises a fifth {@code "smax"} class on inbound acks, but the
 * client never echoes it back so there is no outbound counterpart on
 * the Cobalt side.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendAck")
@WhatsAppWebModule(moduleName = "WAWebReceiptAck")
@WhatsAppWebModule(moduleName = "WAAckParser")
public enum AckClass {
    /**
     * Acknowledges an inbound {@code <message>} stanza.
     *
     * @apiNote
     * Emitted from
     * {@link com.github.auties00.cobalt.stream.message.MessageStreamHandler}
     * for both the plain {@code <ack class="message">} confirmation
     * and the {@code <ack class="message" error=N>} NACK variant.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE("message"),

    /**
     * Acknowledges an inbound {@code <receipt>} stanza, including the
     * {@code type="retry"} variant that closes the retry-receipt
     * handshake.
     *
     * @apiNote
     * Emitted from
     * {@link com.github.auties00.cobalt.stream.receipt.MessageReceiptStreamHandler}
     * and from
     * {@link com.github.auties00.cobalt.call.internal.signaling.CallReceiptReceiver}
     * for VoIP-receipt confirmations.
     */
    @WhatsAppWebExport(moduleName = "WAWebReceiptAck", exports = "buildReceiptAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    RECEIPT("receipt"),

    /**
     * Acknowledges an inbound {@code <notification>} stanza of any
     * server-side category.
     *
     * @apiNote
     * Emitted from every {@code *NotificationStreamHandler} under
     * {@link com.github.auties00.cobalt.stream.notification} once the
     * notification's local-store effect has been applied.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    NOTIFICATION("notification"),

    /**
     * Acknowledges an inbound call-signalling stanza.
     *
     * @apiNote
     * Emitted from
     * {@link com.github.auties00.cobalt.call.internal.signaling.CallReceiver}
     * with {@link AckBuilder#type(String)} set to the parsed VoIP
     * payload tag rather than the inbound {@code <call>} element's
     * own {@code type}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    CALL("call");

    /**
     * The on-wire string written verbatim into the {@code class}
     * attribute.
     */
    private final String wireToken;

    /**
     * Constructs an {@code AckClass} bound to its on-wire token.
     *
     * @param wireToken the on-wire string for the {@code class}
     *                  attribute
     */
    AckClass(String wireToken) {
        this.wireToken = wireToken;
    }

    /**
     * Returns the on-wire string written into the {@code class}
     * attribute of an outbound {@code <ack>} stanza.
     *
     * @apiNote
     * Read by {@link AckBuilder#send()} when assembling the wire
     * stanza. The four tokens are wire-stable across WhatsApp Web
     * snapshots.
     *
     * @return the literal value of the {@code class} attribute for
     *         this constant
     */
    public String wireToken() {
        return wireToken;
    }

    /**
     * Maps a {@code class} attribute value parsed off an inbound
     * {@code <ack>} back to its enum constant.
     *
     * @apiNote
     * Returns {@code null} for an absent or unrecognised token
     * (including the inbound-only {@code "smax"} variant) so the
     * caller can drop the stanza or surface a warning. WA Web's
     * {@code AckParser.parse} keeps the raw string and matches it
     * lexically; this typed counterpart serves any future Cobalt code
     * that needs to dispatch on inbound class.
     *
     * @param token the {@code class} attribute value off an inbound
     *              {@link Node}, or {@code null} when absent
     * @return the matching {@code AckClass}, or {@code null} when
     *         {@code token} is not one of the four supported values
     */
    public static AckClass fromWireToken(String token) {
        if (token == null) {
            return null;
        }
        return switch (token) {
            case "message" -> MESSAGE;
            case "receipt" -> RECEIPT;
            case "notification" -> NOTIFICATION;
            case "call" -> CALL;
            default -> null;
        };
    }
}
