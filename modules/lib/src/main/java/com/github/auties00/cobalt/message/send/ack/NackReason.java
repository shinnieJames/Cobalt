package com.github.auties00.cobalt.message.send.ack;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Well-known server nack (negative acknowledgement) error codes.
 *
 * <p>These codes appear on two sides of the protocol: the server writes
 * them into the {@code error} attribute of the {@code <ack>} node it
 * sends after rejecting a client stanza ({@link AckResult#error()}), and
 * the client writes them back out when it constructs its own nack stanza
 * for a stanza it could not process.
 * Each constant mirrors a value from the WA Web {@code NackReason}
 * internal enum.
 *
 * @implNote WAWebCreateNackFromStanza.NackReason: defines the known
 * error codes the server may include in the ack's {@code error}
 * attribute AND the codes the client echoes back on outgoing nack
 * stanzas. WA Web exposes the enum as
 * {@code $InternalEnum({StaleGroupAddressingMode:421, ...})} so keys
 * round-trip as names via {@code NackReason.getName(n)}.
 * @see AckResult#error()
 */
@WhatsAppWebModule(moduleName = "WAWebCreateNackFromStanza")
public final class NackReason {
    /**
     * The group's addressing mode has changed since the client's last sync.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.StaleGroupAddressingMode
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int STALE_GROUP_ADDRESSING_MODE = 421;

    /**
     * The chat has reached its new-message cap.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.NewChatMessagesCapped
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int NEW_CHAT_MESSAGES_CAPPED = 475;

    /**
     * The stanza could not be parsed by the server.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.ParsingError
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PARSING_ERROR = 487;

    /**
     * The stanza type is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanza
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNRECOGNIZED_STANZA = 488;

    /**
     * The stanza class is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanzaClass
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNRECOGNIZED_STANZA_CLASS = 489;

    /**
     * The stanza type attribute is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanzaType
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNRECOGNIZED_STANZA_TYPE = 490;

    /**
     * The protobuf payload is invalid.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.InvalidProtobuf
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int INVALID_PROTOBUF = 491;

    /**
     * The hosted companion stanza is invalid.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.InvalidHostedCompanionStanza
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int INVALID_HOSTED_COMPANION_STANZA = 493;

    /**
     * The message secret is missing.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.MissingMessageSecret
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int MISSING_MESSAGE_SECRET = 495;

    /**
     * The Signal counter is older than expected.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.SignalErrorOldCounter
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int SIGNAL_ERROR_OLD_COUNTER = 496;

    /**
     * The message was deleted on the peer's device.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.MessageDeletedOnPeer
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int MESSAGE_DELETED_ON_PEER = 499;

    /**
     * An unhandled server-side error.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnhandledError
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNHANDLED_ERROR = 500;

    /**
     * Admin revoke is not supported for this message.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnsupportedAdminRevoke
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNSUPPORTED_ADMIN_REVOKE = 550;

    /**
     * LID groups are not supported by this client.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnsupportedLIDGroup
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int UNSUPPORTED_LID_GROUP = 551;

    /**
     * A database operation failed on the server.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.DBOperationFailed
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza", exports = "NackReason",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int DB_OPERATION_FAILED = 552;

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason: enum in WA Web,
     * constants class in Cobalt.
     */
    private NackReason() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
