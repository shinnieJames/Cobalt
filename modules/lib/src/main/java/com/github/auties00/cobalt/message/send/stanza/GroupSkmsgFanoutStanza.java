package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.Objects;

/**
 * Builds the outer {@code <message>} stanza for group sender-key (SKMSG)
 * fanout.
 *
 * @apiNote
 * Used for every group send except per-device group-direct fanout (see
 * {@link ChatFanoutStanza}). In SKMSG mode the body is encrypted once with
 * the sender key and shipped as a single {@code <enc type="skmsg">}; new
 * group members and members whose sender key has not yet propagated
 * receive their copies via a pre-built {@code <participants>} child
 * carrying the per-device SKMSG distribution messages (built by
 * {@link ParticipantsStanza#buildSenderKeyDistribution}). Auxiliary
 * children ({@code <biz>}, {@code <meta>}, {@code <bot>},
 * {@code <reporting>}, {@code <sender_content_binding>}) are composed
 * exactly like {@link ChatFanoutStanza}.
 *
 * @implNote
 * This implementation drops the {@code <enc>} child entirely when
 * {@code skmsgCiphertext} is {@code null}; that branch is taken for
 * bot-feedback sends where the {@code <bot>} child carries the only
 * encrypted body and the parent stanza's outer {@code phash} is also
 * dropped.
 */
@WhatsAppWebModule(moduleName = "WAWebSendGroupSkmsgJob")
public final class GroupSkmsgFanoutStanza {
    /**
     * Prevents instantiation; this is a static composer.
     */
    private GroupSkmsgFanoutStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the outer {@code <message>} stanza for group SKMSG fanout.
     *
     * @apiNote
     * Mirrors the {@code wap("message", ...)} composition inside
     * {@code WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg}. The bot
     * feedback path drops both {@code phash} and the
     * {@code <enc type="skmsg">} sibling (delivery happens only via the
     * {@code <bot>} child).
     *
     * @implNote
     * This implementation defers to the caller to supply
     * {@code skDistributionNode}, {@code identityNode}, {@code metaNode},
     * {@code bizNode}, {@code botNode}, {@code reportingNode},
     * {@code senderContentBinding}; null children are elided by
     * {@link NodeBuilder#content(Node...)}.
     *
     * @param messageId            the stanza id
     * @param groupJid             the group {@link Jid}
     * @param type                 the stanza {@code type} attribute
     * @param phash                the participant hash (V2); {@code null}
     *                             for bot-feedback sends where the
     *                             attribute is dropped
     * @param skmsgCiphertext      the SKMSG-encrypted ciphertext;
     *                             {@code null} for bot-feedback sends
     *                             where the {@code <enc>} child is
     *                             omitted entirely
     * @param mediaType            the {@code mediatype} attribute on the
     *                             {@code <enc>}, or {@code null}
     * @param decryptFail          the {@code decrypt-fail} attribute on
     *                             the {@code <enc>}, or {@code null}
     * @param editAttribute        the {@code edit} attribute on the outer
     *                             {@code <message>}, or {@code null}
     * @param addressingMode       {@code "pn"} or {@code "lid"}
     * @param skDistributionNode   the {@code <participants>} carrying SK
     *                             distribution payloads for new members,
     *                             or {@code null}
     * @param identityNode         the {@code <device-identity>} child, or
     *                             {@code null}
     * @param metaNode             the {@code <meta>} child, or
     *                             {@code null}
     * @param bizNode              the {@code <biz>} child, or
     *                             {@code null}
     * @param botNode              the {@code <bot>} child, or
     *                             {@code null}
     * @param reportingNode        the {@code <reporting>} child, or
     *                             {@code null}
     * @param senderContentBinding the {@code <sender_content_binding>}
     *                             child, or {@code null}
     * @return the {@link NodeBuilder} for the outer {@code <message>}
     * @throws NullPointerException if {@code messageId}, {@code groupJid},
     *                              {@code type}, or
     *                              {@code addressingMode} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupSkmsgJob", exports = "encryptAndSendSenderKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
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

        var skmsgEncNode = skmsgCiphertext != null
                ? new NodeBuilder()
                        .description("enc")
                        .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                        .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                        .attribute("mediatype", mediaType)
                        .attribute("decrypt-fail", decryptFail)
                        .content(skmsgCiphertext)
                        .build()
                : null;

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
