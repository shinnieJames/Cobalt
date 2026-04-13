package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;


import java.util.Objects;

/**
 * Builds the outgoing {@code <message>} stanza for group sender-key
 * (SKMSG) fanout.
 *
 * <p>In this mode the SKMSG ciphertext is sent once in a single
 * {@code <enc type="skmsg">} node.  Sender-key distribution messages
 * for new members are provided as a pre-built {@code <participants>}
 * node alongside the SKMSG.
 *
 * @apiNote WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg: builds
 * the message stanza with phash, SKMSG enc node, optional participants,
 * identity, biz, meta, bot, and reporting nodes.
 * @see ChatFanoutStanza
 * @see ParticipantsStanza
 */
public final class GroupSkmsgFanoutStanza {
    private GroupSkmsgFanoutStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the {@code <message>} stanza for group SKMSG fanout.
     *
     * @param messageId            the message stanza ID
     * @param groupJid             the group JID
     * @param type                 the stanza type attribute
     * @param phash                the participant hash (V2), or {@code null}
     *                             for bot feedback messages where phash is dropped
     * @param skmsgCiphertext      the SKMSG-encrypted ciphertext, or {@code null}
     *                             for bot feedback messages where the enc node
     *                             is omitted (delivery only via {@code <bot>})
     * @param mediaType            the enc mediatype, or {@code null}
     * @param decryptFail          the decrypt-fail attribute, or {@code null}
     * @param editAttribute        the edit attribute, or {@code null}
     * @param addressingMode       {@code "pn"} or {@code "lid"}
     * @param skDistributionNode   optional {@code <participants>} with SK distribution
     * @param identityNode         optional {@code <device-identity>}
     * @param metaNode             optional {@code <meta>}
     * @param bizNode              optional {@code <biz>}
     * @param botNode              optional {@code <bot>}
     * @param reportingNode        optional {@code <reporting>}
     * @param senderContentBinding optional {@code <sender_content_binding>}
     * @return a {@link NodeBuilder} for the {@code <message>} stanza,
     *         ready to be passed to {@code WhatsAppClient.sendNode}
     *
     * @apiNote WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg
     */
    public static NodeBuilder build(
            String messageId,
            Jid groupJid,
            String type,
            String phash,
            byte[] skmsgCiphertext,
            String mediaType,
            String decryptFail,
            String editAttribute,
            String addressingMode,
            Node skDistributionNode,
            Node identityNode,
            Node metaNode,
            Node bizNode,
            Node botNode,
            Node reportingNode,
            Node senderContentBinding
    ) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(addressingMode, "addressingMode");

        // WAWebSendGroupSkmsgJob: SKMSG <enc> node
        // When skmsgCiphertext is null (bot feedback messages), the enc
        // node is omitted and the message is delivered only via <bot>
        Node skmsgEncNode = skmsgCiphertext != null
                ? new NodeBuilder()
                        .description("enc")
                        .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                        .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                        .attribute("mediatype", mediaType)
                        .attribute("decrypt-fail", decryptFail)
                        .content(skmsgCiphertext)
                        .build()
                : null;

        // WAWebSendGroupSkmsgJob: build the <message> stanza
        // NodeBuilder.content(Node...) silently skips nulls
        // WAWebSendGroupSkmsgJob: child order matches WA Web stanza:
        // V (participants), H (skmsg enc), U (identity), b(t,e) (biz),
        // meta, q (bot), z (sender_content_binding), K (reporting)
        return new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", groupJid)
                .attribute("type", type)
                .attribute("phash", phash)
                .attribute("edit", editAttribute)
                .attribute("addressing_mode", addressingMode)
                .content(
                        skDistributionNode,
                        skmsgEncNode,
                        identityNode,
                        bizNode,
                        metaNode,
                        botNode,
                        senderContentBinding,
                        reportingNode
                )
                ;
    }
}
