package com.github.auties00.cobalt.message.send.ack;

/**
 * Well-known server nack (negative acknowledgement) error codes.
 *
 * <p>These codes may appear in the {@code error} attribute of the
 * {@code <ack>} node returned by the server after a message stanza
 * is sent.  Each constant mirrors a value from the WA Web
 * {@code NackReason} internal enum.
 *
 * @implNote WAWebCreateNackFromStanza.NackReason: defines the known
 * error codes the server may include in the ack's {@code error} attribute.
 * @see AckResult#error()
 */
public final class NackReason {
    /**
     * The group's addressing mode has changed since the client's last sync.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.StaleGroupAddressingMode
     */
    public static final int STALE_GROUP_ADDRESSING_MODE = 421;

    /**
     * The chat has reached its new-message cap.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.NewChatMessagesCapped
     */
    public static final int NEW_CHAT_MESSAGES_CAPPED = 475;

    /**
     * The stanza could not be parsed by the server.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.ParsingError
     */
    public static final int PARSING_ERROR = 487;

    /**
     * The stanza type is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanza
     */
    public static final int UNRECOGNIZED_STANZA = 488;

    /**
     * The stanza class is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanzaClass
     */
    public static final int UNRECOGNIZED_STANZA_CLASS = 489;

    /**
     * The stanza type attribute is not recognised.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnrecognizedStanzaType
     */
    public static final int UNRECOGNIZED_STANZA_TYPE = 490;

    /**
     * The protobuf payload is invalid.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.InvalidProtobuf
     */
    public static final int INVALID_PROTOBUF = 491;

    /**
     * The hosted companion stanza is invalid.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.InvalidHostedCompanionStanza
     */
    public static final int INVALID_HOSTED_COMPANION_STANZA = 493;

    /**
     * The message secret is missing.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.MissingMessageSecret
     */
    public static final int MISSING_MESSAGE_SECRET = 495;

    /**
     * The Signal counter is older than expected.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.SignalErrorOldCounter
     */
    public static final int SIGNAL_ERROR_OLD_COUNTER = 496;

    /**
     * The message was deleted on the peer's device.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.MessageDeletedOnPeer
     */
    public static final int MESSAGE_DELETED_ON_PEER = 499;

    /**
     * An unhandled server-side error.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnhandledError
     */
    public static final int UNHANDLED_ERROR = 500;

    /**
     * Admin revoke is not supported for this message.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnsupportedAdminRevoke
     */
    public static final int UNSUPPORTED_ADMIN_REVOKE = 550;

    /**
     * LID groups are not supported by this client.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.UnsupportedLIDGroup
     */
    public static final int UNSUPPORTED_LID_GROUP = 551;

    /**
     * A database operation failed on the server.
     *
     * @implNote WAWebCreateNackFromStanza.NackReason.DBOperationFailed
     */
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
