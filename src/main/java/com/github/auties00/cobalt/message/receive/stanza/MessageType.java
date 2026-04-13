package com.github.auties00.cobalt.message.receive.stanza;

/**
 * Classifies the addressing type of an incoming message based on the
 * {@code from} JID and the presence/absence of a {@code participant}
 * attribute.
 *
 * <p>This enum determines how the message is processed downstream:
 * DSM unwrapping rules, receipt type, sender key distribution, and
 * placeholder generation all depend on the message type.
 *
 * <p>The first six values ({@code CHAT}, {@code GROUP},
 * {@code PEER_BROADCAST}, {@code OTHER_BROADCAST},
 * {@code DIRECT_PEER_STATUS}, {@code OTHER_STATUS}) correspond exactly
 * to the WA Web {@code MESSAGE_TYPE} enum.  The additional
 * {@link #PEER_CHAT} value is a Cobalt-specific adaptation that
 * combines the {@code CHAT} type with the
 * {@code MSG_CATEGORY.peer} check.
 *
 * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE: defines
 * {@code CHAT}, {@code GROUP}, {@code PEER_BROADCAST},
 * {@code OTHER_BROADCAST}, {@code DIRECT_PEER_STATUS}, and
 * {@code OTHER_STATUS}.
 * WAWebHandleMsgCommon.MSG_CATEGORY: defines {@code peer} as the
 * only message category, used downstream to identify peer messages.
 */
public enum MessageType {
    /**
     * 1:1 chat message -- {@code from} is a user, LID, or bot JID,
     * and the {@code category} attribute is not {@code "peer"}.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.CHAT
     */
    CHAT,

    /**
     * Group message -- {@code from} is a group JID with a
     * {@code participant} attribute identifying the sender.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.GROUP
     */
    GROUP,

    /**
     * Peer broadcast message -- broadcast from our own device to
     * multiple recipients, with a {@code <participants>} child
     * listing the BCL.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.PEER_BROADCAST
     */
    PEER_BROADCAST,

    /**
     * Broadcast message from another user -- broadcast JID with
     * participant identifying the sender.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.OTHER_BROADCAST
     */
    OTHER_BROADCAST,

    /**
     * Direct peer status -- status from our own device with direct
     * (non-skmsg) encryption, optionally with a participant list.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.DIRECT_PEER_STATUS
     */
    DIRECT_PEER_STATUS,

    /**
     * Status from another user -- status broadcast JID with
     * participant.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.OTHER_STATUS
     */
    OTHER_STATUS,

    /**
     * Peer message from the companion device -- a 1:1 chat message
     * where the {@code category} attribute is {@code "peer"}.
     *
     * <p>In WA Web this is represented as
     * {@code MESSAGE_TYPE.CHAT} combined with
     * {@code MSG_CATEGORY.peer}.  Cobalt merges these two checks
     * into a single enum value for convenience.
     *
     * @implNote ADAPTED: WAWebHandleMsgTypes.flow.MESSAGE_TYPE.CHAT
     * combined with WAWebHandleMsgCommon.MSG_CATEGORY.peer
     */
    PEER_CHAT,
}
