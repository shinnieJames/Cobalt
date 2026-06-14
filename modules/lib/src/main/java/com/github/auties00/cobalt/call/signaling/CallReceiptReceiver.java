package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

/**
 * Handles inbound VoIP call receipt stanzas by acknowledging them back to the server.
 *
 * <p>When a {@code <receipt>} stanza carrying an {@code <offer>}, {@code <accept>}, or {@code <reject>}
 * child arrives, this handler parses the sender and stanza metadata and sends an {@code <ack>} back to
 * the server through the {@link AckSender}. The acknowledgement echoes the inbound {@code id}, targets
 * the original sender, and sets the local user's device-stripped phone-number JID as its {@code from}
 * attribute, with {@code class} set to {@code "receipt"} and the original {@code type} preserved when
 * present.
 *
 * @implNote This implementation omits the WhatsApp Web steps that forward the receipt to the VoIP
 * stack for incoming-signaling processing and that fetch a transport-control token before sending the
 * acknowledgement, because Cobalt implements no VoIP media runtime; the acknowledgement stanza shape is
 * preserved exactly.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCallReceipt")
public final class CallReceiptReceiver extends SocketStreamHandler.Concurrent {

    /**
     * Logs parse errors for unrecognized or malformed call receipt stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiptReceiver.class.getName());

    /**
     * Holds the WhatsApp client used to read the local user's device JID from the store.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the {@link AckSender} used to emit the outbound {@code <ack class="receipt">} stanza, with
     * the local user phone number echoed back as the {@code from} attribute.
     */
    private final AckSender ackSender;

    /**
     * Constructs a call receipt receiver bound to its client and acknowledgement sender.
     *
     * @param whatsapp  the WhatsApp client used to access the store
     * @param ackSender the {@link AckSender} used to emit the outbound {@code <ack class="receipt">}
     *                  stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiptReceiver(LinkedWhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the sender JID from the {@code from} attribute, the stanza identifier from the
     * {@code id} attribute, and the optional receipt type from the {@code type} attribute, then sends
     * an {@code <ack>} carrying those values plus the local user's device-stripped phone-number JID as
     * its {@code from} attribute. The stanza is ignored, with a warning logged, when it carries no
     * recognized {@code <offer>}, {@code <accept>}, or {@code <reject>} child, when the {@code from}
     * attribute is missing, or when the {@code id} attribute is missing. The acknowledgement is also
     * dropped without logging when the local JID is not yet available, which occurs before the
     * connection is ready.
     *
     * @param node the inbound {@code <receipt>} stanza expected to carry an {@code <offer>},
     *             {@code <accept>}, or {@code <reject>} child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Node node) {
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

        var meDevicePn = whatsapp.store().accountStore().jid().orElse(null);
        if (meDevicePn == null) {
            return;
        }

        ackSender.ack(AckClass.RECEIPT, node)
                .from(meDevicePn.toUserJid())
                .send();
    }
}
