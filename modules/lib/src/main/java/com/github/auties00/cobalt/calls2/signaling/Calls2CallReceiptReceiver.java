package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;

/**
 * Acknowledges an inbound VoIP {@code <receipt>} stanza back to the server.
 *
 * <p>The server delivers a {@code <receipt>} carrying an {@code <offer>}, {@code <accept>}, or
 * {@code <reject>} child to confirm one of the caller's outbound signaling legs reached its
 * destination. This handler parses the sender and stanza metadata and ships an {@code <ack>} back so the
 * server stops retransmitting: the acknowledgement echoes the inbound {@code id}, targets the original
 * sender, stamps the local user's device-stripped phone-number JID as its {@code from} attribute, sets
 * {@code class} to {@code "receipt"}, and preserves the inbound {@code type} when present.
 *
 * <p>This is the {@code <receipt>}-stream counterpart to {@link Calls2CallReceiver}, which acknowledges
 * the offer, accept, reject, and rekey legs that arrive inside a {@code <call>} envelope. The two paths
 * share the same {@code <receipt>} acknowledgement shape but are reached from different stream tags: the
 * receipt-stream dispatcher routes the server's signaling-receipt confirmations here, while the call
 * stream routes inbound call actions to {@link Calls2CallReceiver}.
 *
 * @implNote This implementation ports {@code handle_call_receipt} from {@code WAWebHandleVoipCallReceipt}
 * and preserves the acknowledgement stanza shape exactly while omitting the WhatsApp Web steps that
 * forward the receipt into the VoIP stack for incoming-signaling processing and that fetch a
 * transport-control token before sending the acknowledgement; the calls2 engine drives those legs
 * elsewhere, so this handler only emits the wire acknowledgement the server expects.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCallReceipt")
public final class Calls2CallReceiptReceiver extends SocketStreamHandler.Concurrent {
    /**
     * Logs parse errors for unrecognized or malformed call receipt stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2CallReceiptReceiver.class.getName());

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
    public Calls2CallReceiptReceiver(LinkedWhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the sender JID from the {@code from} attribute, the stanza identifier from the
     * {@code id} attribute, and the optional receipt type from the {@code type} attribute, then sends an
     * {@code <ack>} carrying those values plus the local user's device-stripped phone-number JID as its
     * {@code from} attribute. The stanza is ignored, with a warning logged, when it carries no recognized
     * {@code <offer>}, {@code <accept>}, or {@code <reject>} child, when the {@code from} attribute is
     * missing, or when the {@code id} attribute is missing. The acknowledgement is also dropped without
     * logging when the local JID is not yet available, which occurs before the connection is ready.
     *
     * @param stanza the inbound {@code <receipt>} stanza expected to carry an {@code <offer>},
     *             {@code <accept>}, or {@code <reject>} child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Stanza stanza) {
        if (!stanza.hasChild("offer", "accept", "reject")) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: Unrecognized call stanza: {0}", stanza);
            return;
        }

        var from = stanza.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing from attribute in call receipt: {0}", stanza);
            return;
        }

        var stanzaId = stanza.getAttributeAsString("id", null);
        if (stanzaId == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing id attribute in call receipt: {0}", stanza);
            return;
        }

        var meDevicePn = whatsapp.store().accountStore().jid().orElse(null);
        if (meDevicePn == null) {
            return;
        }

        ackSender.ack(AckClass.RECEIPT, stanza)
                .from(meDevicePn.toUserJid())
                .send();
    }
}
