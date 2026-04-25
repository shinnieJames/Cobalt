package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Classifies the addressing shape of an incoming message based on its
 * {@code from} JID, its {@code participant} attribute, and the logged-in
 * user's identity.
 *
 * <p>The classification is done once during stanza parsing and then
 * drives every subsequent decision in the receive pipeline:
 * {@code DeviceSentMessage} unwrapping rules, receipt type selection,
 * sender key distribution processing, and debug placeholder generation
 * all depend on the classified type.
 *
 * <p>The first six values ({@link #CHAT}, {@link #GROUP},
 * {@link #PEER_BROADCAST}, {@link #OTHER_BROADCAST},
 * {@link #DIRECT_PEER_STATUS}, {@link #OTHER_STATUS}) correspond exactly
 * to the WA Web {@code MESSAGE_TYPE} enum. The additional
 * {@link #PEER_CHAT} value is a Cobalt-specific adaptation that merges
 * the {@code CHAT} type with the {@code MSG_CATEGORY.peer} check that
 * WA Web performs separately.
 *
 * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE: defines
 * {@code CHAT}, {@code GROUP}, {@code PEER_BROADCAST},
 * {@code OTHER_BROADCAST}, {@code DIRECT_PEER_STATUS}, and
 * {@code OTHER_STATUS}.
 * WAWebHandleMsgCommon.MSG_CATEGORY: defines {@code peer} as the
 * only message category, used downstream to identify peer messages.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgTypes.flow")
@WhatsAppWebModule(moduleName = "WAWebHandleMsgCommon")
public enum MessageType {
    /**
     * A 1:1 chat message.
     *
     * <p>The {@code from} attribute is a user, LID, or bot JID, and the
     * stanza's {@code category} attribute is not {@code "peer"}.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.CHAT (literal value {@code "chat"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT,

    /**
     * A group message.
     *
     * <p>The {@code from} attribute is a group or community JID and the
     * sender's device is identified by the {@code participant} attribute.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.GROUP (literal value {@code "group"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP,

    /**
     * A broadcast message sent from one of the user's own devices.
     *
     * <p>The {@code from} attribute is a broadcast list JID, the
     * participant identifies our own companion device, and a
     * {@code <participants>} child lists the broadcast contact list.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.PEER_BROADCAST (literal value {@code "peer_broadcast"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PEER_BROADCAST,

    /**
     * A broadcast message from another user addressed to us.
     *
     * <p>The {@code from} attribute is a broadcast list JID with a
     * participant identifying the remote sender.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.OTHER_BROADCAST (literal value {@code "other_broadcast"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    OTHER_BROADCAST,

    /**
     * A status update from one of the user's own devices, using direct
     * (non-SKMSG) encryption.
     *
     * <p>Status updates posted from the same account but a different
     * device are delivered as direct-peer status so the current device
     * can mirror them locally.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.DIRECT_PEER_STATUS (literal value {@code "direct_peer_status"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DIRECT_PEER_STATUS,

    /**
     * A status update from another user.
     *
     * <p>Uses the status broadcast JID with a participant attribute
     * identifying the remote poster.
     *
     * @implNote WAWebHandleMsgTypes.flow.MESSAGE_TYPE.OTHER_STATUS (literal value {@code "other_status"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    OTHER_STATUS,

    /**
     * A peer protocol message from one of the user's own companion
     * devices.
     *
     * <p>This is a 1:1 chat message whose {@code category} attribute is
     * {@code "peer"}. In WA Web this is represented as
     * {@code MESSAGE_TYPE.CHAT} combined with
     * {@code MSG_CATEGORY.peer}; Cobalt merges these two checks into a
     * single enum value for convenience.
     *
     * @implNote Adapted from WAWebHandleMsgTypes.flow.MESSAGE_TYPE.CHAT
     * combined with WAWebHandleMsgCommon.MSG_CATEGORY.peer.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "MESSAGE_TYPE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgCommon", exports = "MSG_CATEGORY",
            adaptation = WhatsAppAdaptation.ADAPTED)
    PEER_CHAT,
}
