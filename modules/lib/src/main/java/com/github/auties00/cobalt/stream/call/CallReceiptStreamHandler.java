package com.github.auties00.cobalt.stream.call;

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
 *
 * @implNote WAWebHandleVoipCallReceipt
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCallReceipt")
public final class CallReceiptStreamHandler implements SocketStream.Handler {

    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandleVoipCallReceipt logs parse failures via
     *           {@code WALogger.ERROR("Parsing Error: ...")}.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiptStreamHandler.class.getName());

    /**
     * The WhatsApp client used to send acknowledgement nodes and access the
     * local user's device JID from the store.
     *
     * @implNote WAWebHandleVoipCallReceipt resolves
     *           {@link com.github.auties00.cobalt.store.WhatsAppStore#jid()}
     *           through {@code WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE},
     *           and the ack envelope through {@code WAWap.wap("ack", ...)} +
     *           {@code WAWebCommsWapMd.JID}.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@code CallReceiptStreamHandler} with the specified
     * WhatsApp client.
     *
     * @param whatsapp the WhatsApp client instance used for sending ack nodes
     *                 and accessing the store
     * @implNote WAWebHandleVoipCallReceipt resolves its dependencies through
     *           module imports; Cobalt replaces the module-level dependencies
     *           with constructor injection.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiptStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
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
     * @implNote WAWebHandleVoipCallReceipt.handleCallReceipt:
     *           <ul>
     *             <li>The VoIP stack signaling handoff
     *                 ({@code handleIncomingSignalingReceipt}) and the TC token
     *                 fetch ({@code frontendSendAndReceive("getTcToken", ...)})
     *                 are both intentionally omitted because Cobalt does not
     *                 implement a VoIP media runtime.</li>
     *             <li>WA Web returns the constructed {@code <ack>} wap node
     *                 from the case-{@code "receipt"} branch of
     *                 {@code WAWebCommsHandleWorkerCompatibleStanza.handleWorkerCompatibleStanza};
     *                 the dispatcher then sends it. Cobalt instead sends the
     *                 ack directly via
     *                 {@link WhatsAppClient#sendNodeWithNoResponse(Node)}.</li>
     *           </ul>
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCallReceipt", exports = "handleCallReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Node node) {
        // WAWebHandleVoipCallReceipt: callReceiptParser does e.assertTag("receipt") then
        //     var t = e.maybeChild("offer") || e.maybeChild("accept") || e.maybeChild("reject");
        //     if (!t) throw e.createParseError("Unrecognized call stanza")
        // The maybeChild calls scan all children, so an offer/accept/reject anywhere wins.
        if (!node.hasChild("offer", "accept", "reject")) {
            // WAWebHandleVoipCallReceipt: WALogger.ERROR("Parsing Error: ", error.toString())
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: Unrecognized call stanza: {0}", node);
            return;
        }

        // WAWebHandleVoipCallReceipt: a.from = WAWebJidToWid.jidWithTypeToWid(e.attrJidWithType("from"))
        // attrJidWithType throws if "from" is missing; that becomes a parse error logged via WALogger.ERROR.
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing from attribute in call receipt: {0}", node);
            return;
        }

        // WAWebHandleVoipCallReceipt: a.stanzaId = e.attrString("id")
        // attrString throws on a missing id, which surfaces as a parse error.
        var stanzaId = node.getAttributeAsString("id", null);
        if (stanzaId == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing id attribute in call receipt: {0}", node);
            return;
        }

        // WAWebHandleVoipCallReceipt: a.type = e.maybeAttrString("type")
        var type = node.getAttributeAsString("type", null);

        // WAWebHandleVoipCallReceipt: from = WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE()
        // which is asUserWidOrThrow(getMeDevicePnOrThrow_DO_NOT_USE()) — the device-stripped PN user JID.
        // This is unconditional in WAWebHandleVoipCallReceipt; the LID-vs-PN branching that exists
        // in the sibling module WAWebHandleVoipCall (signaling sender) is NOT used here.
        var meDevicePn = whatsapp.store().jid().orElse(null);
        if (meDevicePn == null) {
            // WA Web throws via getMePnUserOrThrow_DO_NOT_USE; Cobalt drops the ack defensively
            // because the connection state is not yet ready.
            return;
        }
        var meUserPn = meDevicePn.toUserJid(); // WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE = asUserWidOrThrow

        // WAWebHandleVoipCallReceipt: WAWap.wap("ack", { id, to, from, class:"receipt", type })
        var ack = new NodeBuilder()
                .description("ack") // WAWebHandleVoipCallReceipt: "ack"
                .attribute("id", stanzaId) // WAWebHandleVoipCallReceipt: id: WAWap.CUSTOM_STRING(l)
                .attribute("to", from) // WAWebHandleVoipCallReceipt: to: WAWebCommsWapMd.JID(i)
                .attribute("from", meUserPn) // WAWebHandleVoipCallReceipt: from: WAWebCommsWapMd.JID(getMePnUserOrThrow_DO_NOT_USE())
                .attribute("class", "receipt") // WAWebHandleVoipCallReceipt: class: "receipt"
                .attribute("type", type) // WAWebHandleVoipCallReceipt: type: WAWap.MAYBE_CUSTOM_STRING(c) — drops attr when undefined
                .build();
        // ADAPTED: WAWebHandleVoipCallReceipt returns the ack from the case-"receipt" branch in
        // WAWebCommsHandleWorkerCompatibleStanza.handleWorkerCompatibleStanza; the dispatcher sends it.
        whatsapp.sendNodeWithNoResponse(ack);
    }
}
