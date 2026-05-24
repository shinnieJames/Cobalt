package com.github.auties00.cobalt.message.receipt;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the receipt {@code type} values that the client serialises into the
 * {@code <receipt>} stanza sent in response to an incoming message or to broadcast an
 * outbound state change.
 *
 * @apiNote
 * Each constant returns its on-the-wire string via {@link #protocolValue()}; the
 * {@link #DELIVERY} constant carries a {@code null} value because a successful active
 * delivery omits the {@code type} attribute, which is how the server recognises the
 * default delivery semantics.
 *
 * @implNote
 * This implementation collapses WhatsApp Web's {@code RECEIPT_TYPE} frozen map and the
 * literal {@code "retry"} string used by {@link WhatsAppWebModule}
 * {@code WAWebSendRetryReceiptJob} into a single Java enum so callers cannot pass an
 * unknown value.
 */
@WhatsAppWebModule(moduleName = "WAWebSendReceiptJobCommon")
public enum MessageReceiptType {
    /**
     * Default delivery receipt that confirms successful decryption and storage of an
     * incoming message.
     *
     * @apiNote
     * Use this when the message was decrypted, the protobuf was decoded, and there is
     * no active inactive-chat flag; the wire stanza omits the {@code type} attribute
     * entirely.
     *
     * @implNote
     * This implementation uses a {@code null} protocol value; the {@link com.github.auties00.cobalt.node.NodeBuilder}
     * drops any attribute whose value is {@code null} so the {@code type} attribute is
     * never serialised for this constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DELIVERY(null),

    /**
     * Receipt acknowledging that one of the logged-in user's companion devices has
     * received a self-sent message.
     *
     * @apiNote
     * Sent when the {@code from} or {@code participant} JID matches the local PN or LID
     * account; the server uses this to fan the delivery confirmation back to the
     * primary device.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SENDER("sender"),

    /**
     * Receipt for a peer-protocol message such as an app-state sync stanza echoed
     * between the user's own devices.
     *
     * @apiNote
     * WhatsApp Web uses the wire literal {@code "peer_msg"}; this is selected when
     * {@link com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza#isPeer()}
     * returns {@code true} during delivery-receipt construction.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PEER("peer_msg"),

    /**
     * Receipt that flags the recipient chat as inactive so the server can suppress push
     * notifications.
     *
     * @apiNote
     * Selected when the decrypted message produced a {@code hasInactiveMsg} hint and
     * the sender is not the local account; mirrors WhatsApp Web's
     * {@code RECEIPT_TYPE.INACTIVE} branch in
     * {@code WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INACTIVE("inactive"),

    /**
     * Retry receipt that requests the sender to re-encrypt and re-send a message which
     * could not be decrypted.
     *
     * @apiNote
     * Sent by {@link MessageReceiptHandler#sendRetryReceipt} after a Signal-protocol
     * decryption failure; from the second attempt onward the receipt also carries a
     * fresh prekey bundle so the sender can re-establish the Signal session.
     *
     * @implNote
     * This implementation owns the wire literal {@code "retry"} that WhatsApp Web
     * hardcodes inside {@code WAWebSendRetryReceiptJob.sendRetryReceipt} rather than
     * pulling from {@code WAWebSendReceiptJobCommon.RECEIPT_TYPE}, which does not
     * expose a retry entry.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    RETRY("retry"),

    /**
     * Read receipt indicating that the recipient has opened the chat and viewed the
     * message.
     *
     * @apiNote
     * Sent only when the recipient's privacy settings permit read receipts; the server
     * echoes the receipt back to the sender so the message renders with the blue
     * double-check.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    READ("read"),

    /**
     * Read receipt mirrored across the logged-in user's companion devices so the
     * unread badge clears everywhere.
     *
     * @apiNote
     * Sent in addition to a normal read receipt when the user has more than one
     * device; the companion devices use this to drop the chat from their unread list
     * without echoing back to the original sender.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    READ_SELF("read-self"),

    /**
     * Played receipt indicating that voice-note or view-once media has been played by
     * the recipient.
     *
     * @apiNote
     * Sent after the recipient has fully played a voice note or revealed a view-once
     * media message; mirrors the read receipt semantics for non-text payloads.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PLAYED("played"),

    /**
     * Played receipt mirrored across the logged-in user's companion devices.
     *
     * @apiNote
     * Companion-only counterpart of {@link #PLAYED}; emitted alongside the main played
     * receipt so other devices stop highlighting the voice note or view-once media.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PLAYED_SELF("played-self"),

    /**
     * Receipt announcing that the history-sync backfill for a chat has finished
     * processing on the client.
     *
     * @apiNote
     * Sent at the end of the on-demand history-sync flow so the server stops pushing
     * additional notifications for the request; mirrors WhatsApp Web's
     * {@code RECEIPT_TYPE.HISTORY_SYNC_COMPLETION}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HISTORY_SYNC_COMPLETION("hist_sync"),

    /**
     * Receipt sent when the server reports a delivery error for the message.
     *
     * @apiNote
     * Used to acknowledge a server-side failure path so the server stops re-attempting
     * delivery for the same id.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SERVER_ERROR("server-error");

    /**
     * The protocol literal placed in the {@code type} attribute of the receipt stanza,
     * or {@code null} when the attribute must be omitted (the default delivery case).
     */
    private final String protocolValue;

    /**
     * Constructs a receipt-type constant bound to its on-the-wire literal.
     *
     * @apiNote
     * Invoked by the enum constants above; not callable from outside the enum.
     *
     * @param protocolValue the literal carried by the {@code type} attribute, or
     *                      {@code null} for {@link #DELIVERY} so the attribute is
     *                      dropped from the serialised stanza
     */
    MessageReceiptType(String protocolValue) {
        this.protocolValue = protocolValue;
    }

    /**
     * Returns the protocol literal carried by the {@code type} attribute of the
     * receipt stanza, or {@code null} for the default delivery receipt.
     *
     * @apiNote
     * Pass the returned value into the {@code "type"} attribute slot of a
     * {@link com.github.auties00.cobalt.node.NodeBuilder}; passing {@code null} causes
     * the builder to omit the attribute, which is the correct serialisation for
     * {@link #DELIVERY}.
     *
     * @return the on-the-wire {@code type} literal, or {@code null} for
     *         {@link #DELIVERY}
     */
    public String protocolValue() {
        return protocolValue;
    }
}
