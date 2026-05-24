package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming VoIP call receipt stanzas by acknowledging them back to the
 * server.
 *
 * <p>When a {@code <receipt>} stanza arrives that contains an {@code <offer>},
 * {@code <accept>}, or {@code <reject>} child, this handler parses the sender
 * and stanza metadata, then sends an {@code <ack>} node back to the server with
 * the same {@code id}, the sender as {@code to}, the local user's
 * device-stripped phone-number JID as {@code from}, {@code class} set to
 * {@code "receipt"}, and the original {@code type} attribute when present.
 *
 * <p>The WhatsApp Web implementation also forwards the receipt to the VoIP
 * stack interface for incoming signaling processing and fetches a TC token
 * via {@code frontendSendAndReceive("getTcToken", ...)} before sending the ack,
 * but Cobalt does not implement a VoIP media runtime, so both steps are
 * intentionally omitted. The ack stanza shape is preserved exactly.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCallReceipt")
public final class CallReceiptReceiver implements SocketStream.Handler {

    /**
     * Logger for this handler.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiptReceiver.class.getName());

    /**
     * The WhatsApp client used to access the local user's device JID
     * from the store.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to
     * ship the outbound {@code <ack class="receipt">} stanza with the
     * local user PN echoed back as the {@code from} attribute.
     */
    private final AckSender ackSender;

    /**
     * Constructs a new {@code CallReceiptReceiver} with the specified
     * WhatsApp client.
     *
     * @param whatsapp  the WhatsApp client instance used for accessing
     *                  the store
     * @param ackSender the {@link AckSender}
     *                  used to emit the outbound
     *                  {@code <ack class="receipt">} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiptReceiver(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Handles an incoming VoIP call receipt stanza by parsing it and sending
     * an acknowledgement node back to the server.
     *
     * <p>The handler extracts the sender JID from the {@code from} attribute,
     * the stanza identifier from the {@code id} attribute, and the optional
     * receipt type from the {@code type} attribute. It then constructs an
     * {@code <ack>} node containing these values plus the local user's
     * device-stripped phone-number JID as the {@code from} attribute, and sends
     * it to the server.
     *
     * <p>If the stanza cannot be parsed (missing {@code from} attribute, missing
     * {@code id} attribute, or no recognized child element), the handler logs a
     * warning and returns without sending an acknowledgement.
     *
     * @param node the incoming {@code <receipt>} stanza containing an
     *             {@code <offer>}, {@code <accept>}, or {@code <reject>} child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Node node) {
        // The WA Web parser scans all children for offer/accept/reject; the first match wins.
        if (!node.hasChild("offer", "accept", "reject")) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: Unrecognized call stanza: {0}", node);
            return;
        }

        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing from attribute in call receipt: {0}", node);
            return;
        }

        var stanzaId = node.getAttributeAsString("id", null);
        if (stanzaId == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing id attribute in call receipt: {0}", node);
            return;
        }

        // WA Web uses getMePnUserOrThrow_DO_NOT_USE unconditionally here, even though the sibling signaling sender branches on LID-vs-PN.
        var meDevicePn = whatsapp.store().jid().orElse(null);
        if (meDevicePn == null) {
            // Drop the ack defensively when the connection state is not yet ready instead of throwing.
            return;
        }

        ackSender.ack(AckClass.RECEIPT, node)
                .from(meDevicePn.toUserJid())
                .send();
    }
}
