package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the outer {@code <message>} stanza for 1:1 chat sends and
 * group-direct (per-device) fanout, wrapping per-device
 * {@link MessageEncryptedPayload} entries and every auxiliary child node
 * the server expects.
 *
 * @apiNote
 * This is the top-level composer for the chat fanout family. Outbound
 * dispatchers ({@code ChatMessageSender}, the resend pipeline,
 * peer-to-peer fanout) call {@link #build} with the per-device payloads and
 * the optional child nodes ({@code <biz>}, {@code <bot>}, {@code <meta>},
 * {@code <reporting>}, {@code <tctoken>}, {@code <cstoken>},
 * {@code <ctwa_attribution>}, {@code <device-identity>},
 * {@code <sender_content_binding>}, group-direct {@code <enc type="skmsg">})
 * already prepared by their respective stanza builders. The returned
 * {@link NodeBuilder} is finalised and sent to the relay.
 *
 * @implNote
 * This implementation places the single {@code <enc>} directly under
 * {@code <message>} only when there is exactly one payload, that payload
 * targets a primary device ({@code device == 0}), and the message is not
 * bot-related; otherwise every {@code <enc>} is wrapped under
 * {@code <participants>}. The {@code <tctoken>} child takes precedence
 * over {@code <cstoken>}: the latter is emitted only when the former is
 * absent.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
@WhatsAppWebModule(moduleName = "WAWebSendDirectMsgToDeviceList")
public final class ChatFanoutStanza {
    /**
     * Prevents instantiation; this is a static composer.
     */
    private ChatFanoutStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the outer {@code <message>} stanza for chat or group-direct
     * fanout.
     *
     * @apiNote
     * The 27 parameters mirror the {@code createFanoutMsgStanza} output
     * shape one-for-one. The boolean {@code isBotRelated} forces the
     * multi-{@code <enc>} branch even for a single payload (so the bot
     * sibling {@code <enc>} can coexist), and {@code deviceFanout="false"}
     * is set by the caller for resends and bot-feedback sends to suppress
     * server-side fanout. {@code peer_recipient_lid}, {@code peer_recipient_pn},
     * {@code recipient_pn}, and {@code peer_recipient_username} carry the
     * peer-id metadata that the server uses for LID 1:1 migration. The
     * trailing child-node arguments are inlined verbatim in WA Web's wire
     * order and {@code null} children are simply elided by
     * {@link NodeBuilder#content(Node...)}.
     *
     * @implNote
     * This implementation falls back to {@code cstokenNode} when
     * {@code tctokenNode} is {@code null}; WA Web ships the same precedence
     * inline at the call site. Calls to
     * {@link ParticipantsStanza#requiresIdentityNode(List)} suppress the
     * caller-supplied {@code identityNode} when no payload is a PreKey
     * message (matching WA Web's {@code shouldHaveIdentity} flag).
     *
     * @param messageId             the stanza id (typically the 22-char
     *                              upper-hex id)
     * @param chatJid               the recipient chat {@link Jid}
     * @param type                  the stanza {@code type} attribute (one
     *                              of {@code "text"}, {@code "media"},
     *                              {@code "reaction"}, {@code "poll"},
     *                              {@code "event"})
     * @param payloads              the per-device encrypted payloads
     * @param editAttribute         the {@code edit} attribute (e.g.
     *                              {@code "1"} for edits, {@code "7"} for
     *                              revokes), or {@code null}
     * @param addressingMode        {@code "pn"} or {@code "lid"}, or
     *                              {@code null}
     * @param deviceFanout          {@code "false"} for resends and
     *                              bot-feedback sends, or {@code null}
     * @param mediaType             the per-{@code <enc>} {@code mediatype},
     *                              or {@code null}
     * @param decryptFail           the per-{@code <enc>} {@code decrypt-fail}
     *                              attribute, or {@code null}
     * @param nativeFlowName        the per-{@code <enc>}
     *                              {@code native_flow_name} attribute, or
     *                              {@code null}
     * @param contentBindings       per-recipient RCAT tags keyed by user
     *                              {@link Jid}, or {@code null}
     * @param isBotRelated          whether the message targets a bot
     *                              (forces the multi-{@code <enc>} branch)
     * @param peerRecipientLid      the peer LID for 1:1 LID migration, or
     *                              {@code null}
     * @param peerRecipientPn       the peer PN for 1:1 LID migration, or
     *                              {@code null}
     * @param recipientPn           the recipient PN attribute, or
     *                              {@code null}
     * @param peerRecipientUsername the peer username for usernames feature,
     *                              or {@code null}
     * @param identityNode          the {@code <device-identity>} child, or
     *                              {@code null}
     * @param metaNode              the {@code <meta>} child, or
     *                              {@code null}
     * @param bizNode               the {@code <biz>} child, or
     *                              {@code null}
     * @param botNode               the encrypted-bot {@code <bot>} child,
     *                              or {@code null}
     * @param reportingNode         the {@code <reporting>} child, or
     *                              {@code null}
     * @param senderContentBinding  the {@code <sender_content_binding>}
     *                              child, or {@code null}
     * @param botMetadataNode       the metadata-only {@code <bot>} child,
     *                              or {@code null}
     * @param tctokenNode           the {@code <tctoken>} child, or
     *                              {@code null}
     * @param cstokenNode           the {@code <cstoken>} child used only
     *                              when {@code tctokenNode} is
     *                              {@code null}
     * @param ctwaNode              the {@code <ctwa_attribution>} child,
     *                              or {@code null}
     * @param groupDirectSkmsgNode  an empty {@code <enc type="skmsg">}
     *                              sibling emitted on group-direct fanout,
     *                              or {@code null}
     * @return the {@link NodeBuilder} for the outer {@code <message>}
     * @throws NullPointerException if {@code messageId}, {@code chatJid},
     *                              {@code type}, or {@code payloads} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendDirectMsgToDeviceList", exports = "sendDirectMsgToDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder build(
            String messageId,
            Jid chatJid,
            String type,
            List<MessageEncryptedPayload> payloads,
            String editAttribute,
            String addressingMode,
            String deviceFanout,
            String mediaType,
            String decryptFail,
            String nativeFlowName,
            Map<Jid, byte[]> contentBindings,
            boolean isBotRelated,
            Jid peerRecipientLid,
            Jid peerRecipientPn,
            Jid recipientPn,
            String peerRecipientUsername,
            Node identityNode,
            Node metaNode,
            Node bizNode,
            Node botNode,
            Node reportingNode,
            Node senderContentBinding,
            Node botMetadataNode,
            Node tctokenNode,
            Node cstokenNode,
            Node ctwaNode,
            Node groupDirectSkmsgNode
    ) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payloads, "payloads");

        var singlePrimary = payloads.size() == 1
                && payloads.getFirst().recipientJid() != null
                && payloads.getFirst().recipientJid().device() == 0
                && !isBotRelated;

        var needsIdentity = ParticipantsStanza.requiresIdentityNode(payloads);

        var builder = new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", chatJid)
                .attribute("type", type)
                .attribute("edit", editAttribute)
                .attribute("device_fanout", deviceFanout)
                .attribute("addressing_mode", addressingMode)
                .attribute("peer_recipient_lid", peerRecipientLid)
                .attribute("peer_recipient_pn", peerRecipientPn)
                .attribute("recipient_pn", recipientPn)
                .attribute("peer_recipient_username", peerRecipientUsername);

        if (singlePrimary) {
            builder.content(buildEncNode(payloads.getFirst(), mediaType, decryptFail, nativeFlowName));
        } else {
            builder.content(buildParticipantsNode(payloads, mediaType, decryptFail, nativeFlowName, contentBindings));
        }

        var tokenNode = tctokenNode != null ? tctokenNode : cstokenNode;
        builder.content(
                botNode,
                groupDirectSkmsgNode,
                needsIdentity ? identityNode : null,
                bizNode,
                metaNode,
                senderContentBinding,
                botMetadataNode,
                reportingNode,
                tokenNode,
                ctwaNode
        );

        return builder;
    }

    /**
     * Builds the outer {@code <message>} stanza without a {@code <cstoken>}
     * fallback.
     *
     * @apiNote
     * Convenience overload for callers that do not need the NCT token
     * fallback path; delegates to the 27-arg form with {@code cstokenNode}
     * set to {@code null}.
     *
     * @param messageId             the stanza id
     * @param chatJid               the recipient chat {@link Jid}
     * @param type                  the stanza {@code type} attribute
     * @param payloads              the per-device encrypted payloads
     * @param editAttribute         the {@code edit} attribute, or
     *                              {@code null}
     * @param addressingMode        the {@code addressing_mode}, or
     *                              {@code null}
     * @param deviceFanout          the {@code device_fanout} flag, or
     *                              {@code null}
     * @param mediaType             the per-{@code <enc>} {@code mediatype},
     *                              or {@code null}
     * @param decryptFail           the per-{@code <enc>} {@code decrypt-fail},
     *                              or {@code null}
     * @param nativeFlowName        the per-{@code <enc>}
     *                              {@code native_flow_name}, or
     *                              {@code null}
     * @param contentBindings       per-recipient RCAT tags, or
     *                              {@code null}
     * @param isBotRelated          whether the message targets a bot
     * @param peerRecipientLid      the peer LID, or {@code null}
     * @param peerRecipientPn       the peer PN, or {@code null}
     * @param recipientPn           the recipient PN, or {@code null}
     * @param peerRecipientUsername the peer username, or {@code null}
     * @param identityNode          the {@code <device-identity>}, or
     *                              {@code null}
     * @param metaNode              the {@code <meta>}, or {@code null}
     * @param bizNode               the {@code <biz>}, or {@code null}
     * @param botNode               the encrypted-bot {@code <bot>}, or
     *                              {@code null}
     * @param reportingNode         the {@code <reporting>}, or
     *                              {@code null}
     * @param senderContentBinding  the {@code <sender_content_binding>},
     *                              or {@code null}
     * @param botMetadataNode       the metadata-only {@code <bot>}, or
     *                              {@code null}
     * @param tctokenNode           the {@code <tctoken>}, or {@code null}
     * @param ctwaNode              the {@code <ctwa_attribution>}, or
     *                              {@code null}
     * @param groupDirectSkmsgNode  the group-direct {@code <enc type="skmsg">},
     *                              or {@code null}
     * @return the {@link NodeBuilder} for the outer {@code <message>}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static NodeBuilder build(
            String messageId,
            Jid chatJid,
            String type,
            List<MessageEncryptedPayload> payloads,
            String editAttribute,
            String addressingMode,
            String deviceFanout,
            String mediaType,
            String decryptFail,
            String nativeFlowName,
            Map<Jid, byte[]> contentBindings,
            boolean isBotRelated,
            Jid peerRecipientLid,
            Jid peerRecipientPn,
            Jid recipientPn,
            String peerRecipientUsername,
            Node identityNode,
            Node metaNode,
            Node bizNode,
            Node botNode,
            Node reportingNode,
            Node senderContentBinding,
            Node botMetadataNode,
            Node tctokenNode,
            Node ctwaNode,
            Node groupDirectSkmsgNode
    ) {
        return build(
                messageId, chatJid, type, payloads,
                editAttribute, addressingMode, deviceFanout,
                mediaType, decryptFail, nativeFlowName,
                contentBindings, isBotRelated,
                peerRecipientLid, peerRecipientPn, recipientPn,
                peerRecipientUsername,
                identityNode, metaNode, bizNode, botNode,
                reportingNode, senderContentBinding, botMetadataNode,
                tctokenNode, null, ctwaNode, groupDirectSkmsgNode
        );
    }

    /**
     * Builds a single {@code <enc>} node for one device payload.
     *
     * @apiNote
     * Used both for the single-primary top-level {@code <enc>} and as the
     * inner {@code <enc>} of every {@code <participants><to>} child. The
     * {@code v} attribute always carries
     * {@link MessageEncryption#CIPHERTEXT_VERSION}.
     *
     * @param payload        the {@link MessageEncryptedPayload}
     * @param mediaType      the {@code mediatype} attribute, or
     *                       {@code null}
     * @param decryptFail    the {@code decrypt-fail} attribute, or
     *                       {@code null}
     * @param nativeFlowName the {@code native_flow_name} attribute, or
     *                       {@code null}
     * @return the {@code <enc>} {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Node buildEncNode(
            MessageEncryptedPayload payload,
            String mediaType,
            String decryptFail,
            String nativeFlowName
    ) {
        return new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", payload.type().protocolValue())
                .attribute("mediatype", mediaType)
                .attribute("decrypt-fail", decryptFail)
                .attribute("native_flow_name", nativeFlowName)
                .content(payload.ciphertext())
                .build();
    }

    /**
     * Builds the {@code <participants>} container for multi-device chat
     * fanout, attaching per-device content bindings when present.
     *
     * @apiNote
     * Each device payload becomes a
     * {@code <to jid="..."><enc .../><content_binding .../></to>} child.
     * Payloads with a {@code null}
     * {@link MessageEncryptedPayload#recipientJid()} are skipped (those
     * represent the sender-key cipher in WA Web).
     *
     * @implNote
     * This implementation resolves each content binding by hashing on
     * {@link Jid#toUserJid()} rather than the device JID; the RCAT map is
     * keyed by user JID per WA Web's
     * {@code WAWebMsgRcatUtils.genContentBindingForMsg}.
     *
     * @param payloads        the per-device encrypted payloads
     * @param mediaType       the {@code mediatype} attribute, or
     *                        {@code null}
     * @param decryptFail     the {@code decrypt-fail} attribute, or
     *                        {@code null}
     * @param nativeFlowName  the {@code native_flow_name} attribute, or
     *                        {@code null}
     * @param contentBindings per-recipient RCAT tags keyed by user
     *                        {@link Jid}, or {@code null}
     * @return the {@code <participants>} {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Node buildParticipantsNode(
            List<MessageEncryptedPayload> payloads,
            String mediaType,
            String decryptFail,
            String nativeFlowName,
            Map<Jid, byte[]> contentBindings
    ) {
        var children = new ArrayList<Node>(payloads.size());
        for (var payload : payloads) {
            if (payload.recipientJid() == null) {
                continue;
            }

            var encNode = buildEncNode(payload, mediaType, decryptFail, nativeFlowName);

            Node contentBindingNode = null;
            if (contentBindings != null) {
                var binding = contentBindings.get(payload.recipientJid().toUserJid());
                if (binding != null) {
                    contentBindingNode = new NodeBuilder()
                            .description("content_binding")
                            .content(binding)
                            .build();
                }
            }

            var toNode = new NodeBuilder()
                    .description("to")
                    .attribute("jid", payload.recipientJid())
                    .content(encNode, contentBindingNode)
                    .build();
            children.add(toNode);
        }
        return new NodeBuilder()
                .description("participants")
                .content(children)
                .build();
    }
}
