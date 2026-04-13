package com.github.auties00.cobalt.stream.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming VoIP call receipt stanzas by acknowledging them back to the
 * server.
 *
 * <p>When a {@code <receipt>} stanza arrives whose first child is
 * {@code <offer>}, {@code <accept>}, or {@code <reject>}, this handler parses
 * the sender and stanza metadata, then sends an {@code <ack>} node back to the
 * server with the same {@code id}, the sender as {@code to}, the local user as
 * {@code from}, {@code class} set to {@code "receipt"}, and the original
 * {@code type} attribute if present.
 *
 * <p>The WhatsApp Web implementation also forwards the receipt to the VoIP
 * stack interface for incoming signaling processing, but Cobalt does not
 * implement a VoIP media runtime, so that step is intentionally omitted.
 *
 * @implNote WAWebHandleVoipCallReceipt
 */
public final class CallReceiptStreamHandler implements SocketStream.Handler {

    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandleVoipCallReceipt (WALogger.ERROR for parse failures)
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiptStreamHandler.class.getName());

    /**
     * The WhatsApp client used to send acknowledgement nodes and access the
     * local user's JID from the store.
     *
     * @implNote WAWebHandleVoipCallReceipt (WAWebUserPrefsMeUser, WAWap, WAWebCommsWapMd)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@code CallReceiptStreamHandler} with the specified
     * WhatsApp client.
     *
     * @param whatsapp the WhatsApp client instance used for sending ack nodes
     *                 and accessing the store
     * @implNote WAWebHandleVoipCallReceipt
     */
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
     * {@code <ack>} node containing these values plus the local user's JID as
     * the {@code from} attribute, and sends it to the server.
     *
     * <p>If the stanza cannot be parsed (missing {@code from} attribute or no
     * recognized child element), the handler logs a warning and returns without
     * sending an acknowledgement.
     *
     * @param node the incoming {@code <receipt>} stanza containing an
     *             {@code <offer>}, {@code <accept>}, or {@code <reject>} child
     * @implNote WAWebHandleVoipCallReceipt.handleCallReceipt
     */
    @Override
    public void handle(Node node) {
        // WAWebHandleVoipCallReceipt.handleCallReceipt: parser extracts from, stanzaId, type
        var from = node.getAttributeAsJid("from", null); // WAWebHandleVoipCallReceipt: a.from via jidWithTypeToWid(attrJidWithType("from"))
        if (from == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: missing from attribute in call receipt: {0}", node); // WAWebHandleVoipCallReceipt: WALogger.ERROR for parse errors
            return;
        }

        var child = node.getChild().orElse(null); // WAWebHandleVoipCallReceipt: parser checks maybeChild("offer") || maybeChild("accept") || maybeChild("reject")
        if (child == null || !isCallChild(child)) {
            LOGGER.log(System.Logger.Level.WARNING, "Parsing Error: unrecognized call stanza: {0}", node); // WAWebHandleVoipCallReceipt: createParseError("Unrecognized call stanza")
            return;
        }

        // WAWebHandleVoipCallReceipt: VoIP stack signaling and TC token fetching omitted (no VoIP media runtime in Cobalt)

        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleVoipCallReceipt: a.stanzaId = attrString("id")
        var type = node.getAttributeAsString("type", null); // WAWebHandleVoipCallReceipt: a.type = maybeAttrString("type")
        var meJid = whatsapp.store().jid().orElse(null); // ADAPTED: WAWebHandleVoipCallReceipt: getMePnUserOrThrow_DO_NOT_USE()
        if (stanzaId == null || meJid == null) {
            return;
        }

        var ack = new NodeBuilder() // WAWebHandleVoipCallReceipt: WAWap.wap("ack", {...})
                .description("ack") // WAWebHandleVoipCallReceipt: "ack"
                .attribute("id", stanzaId) // WAWebHandleVoipCallReceipt: id: CUSTOM_STRING(l)
                .attribute("to", from) // WAWebHandleVoipCallReceipt: to: JID(i)
                .attribute("from", meJid) // WAWebHandleVoipCallReceipt: from: JID(getMePnUserOrThrow_DO_NOT_USE())
                .attribute("class", "receipt") // WAWebHandleVoipCallReceipt: class: "receipt"
                .attribute("type", type) // WAWebHandleVoipCallReceipt: type: MAYBE_CUSTOM_STRING(c)
                .build();
        whatsapp.sendNodeWithNoResponse(ack); // ADAPTED: WAWebHandleVoipCallReceipt returns the ack stanza as response
    }

    /**
     * Checks whether the given child node represents a recognized call receipt
     * child element.
     *
     * <p>The recognized child tags are {@code "offer"}, {@code "accept"}, and
     * {@code "reject"}, corresponding to the three call receipt types that the
     * WhatsApp Web parser accepts.
     *
     * @param child the child node to check
     * @return {@code true} if the child tag is a recognized call receipt type
     * @implNote WAWebHandleVoipCallReceipt: parser checks maybeChild("offer") || maybeChild("accept") || maybeChild("reject")
     */
    private boolean isCallChild(Node child) {
        return switch (child.description()) {
            case "offer", "accept", "reject" -> true; // WAWebHandleVoipCallReceipt: maybeChild("offer") || maybeChild("accept") || maybeChild("reject")
            default -> false;
        };
    }
}
