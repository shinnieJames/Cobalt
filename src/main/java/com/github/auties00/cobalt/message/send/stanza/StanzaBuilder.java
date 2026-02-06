package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.encryption.MessageEncryption;
import com.github.auties00.cobalt.message.encryption.MessageSignalEncryptionType;
import com.github.auties00.cobalt.message.send.stanza.StanzaContext.EncryptedDeviceNode;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Builder for constructing message stanzas for sending to the WhatsApp server.
 * <p>
 * Builds the XML-like structure required for sending encrypted messages
 * to one or more devices.
 *
 * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza: creates the message
 * stanza structure for 1:1 and group messages.
 */
public final class StanzaBuilder {
    private static final System.Logger LOGGER = System.getLogger("StanzaBuilder");

    private StanzaBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a message stanza for sending to a single user (1:1 chat).
     * <p>
     * Structure:
     * <pre>{@code
     * <message id="{msgId}" to="{chatJid}" type="{msgType}" edit="{editAttr}"
     *          peer_recipient_lid="{lid}" peer_recipient_pn="{pn}" recipient_pn="{pn}">
     *   <participants>
     *     <to jid="{deviceJid}">
     *       <enc v="2" type="pkmsg|msg" decrypt-fail="hide">{ciphertext}</enc>
     *       <content_binding>{binding}</content_binding>
     *     </to>
     *     ...
     *   </participants>
     *   <device-identity>{advIdentity}</device-identity>
     *   <biz host_storage="{}" actual_actors="{}" privacy_mode_ts="{}"/>
     *   <meta .../>
     *   <bot type="{}" local_automated_type="{}" client_thread_id="{}"/>
     *   <tctoken>{token}</tctoken>
     *   <reporting_token>{token}</reporting_token>
     *   <sender_content_binding>{binding}</sender_content_binding>
     * </message>
     * }</pre>
     *
     * @param messageId       the message ID
     * @param chatJid         the chat JID (recipient user)
     * @param messageType     the message type attribute (text, media, etc.)
     * @param encryptedNodes  the encrypted payload nodes for each device
     * @param deviceIdentity  the ADV device identity bytes, or null if not needed
     * @param context         the stanza context with additional attributes and nodes
     * @return the constructed message stanza
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza: builds message stanza
     * for FANOUT_TYPE.CHAT
     */
    public static NodeBuilder createUserMessageStanza(
            String messageId,
            Jid chatJid,
            String messageType,
            Collection<EncryptedDeviceNode> encryptedNodes,
            byte[] deviceIdentity,
            StanzaContext.UserStanzaContext context
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(messageType, "messageType cannot be null");
        Objects.requireNonNull(encryptedNodes, "encryptedNodes cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        // WAWebSendMsgCreateFanoutStanza: build participant nodes with content binding
        var participantNodes = buildParticipantNodes(encryptedNodes, context.contentBindings());

        // WAWebSendMsgCreateFanoutStanza: check if any encryption was pkmsg
        var shouldHaveIdentity = encryptedNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());

        // WAWebSendMsgCreateFanoutStanza: build participants wrapper
        var participantsNode = new NodeBuilder()
                .description("participants")
                .content(participantNodes)
                .build();

        // Build all child nodes
        var childNodes = new ArrayList<Node>();
        childNodes.add(participantsNode);

        // WAWebSendMsgCreateFanoutStanza: build device-identity if needed
        if (shouldHaveIdentity && deviceIdentity != null) {
            var deviceIdentityNode = new NodeBuilder()
                    .description("device-identity")
                    .content(deviceIdentity)
                    .build();
            childNodes.add(deviceIdentityNode);
        }

        // WAWebSendMsgCreateFanoutStanza: build biz node for privacy mode
        if (context.bizNode() != null) {
            childNodes.add(context.bizNode());
        }

        // WAWebSendMsgMetaNode.genMetaNode: add meta node
        if (context.metaNode() != null) {
            childNodes.add(context.metaNode());
        }

        // WAWebSendMsgCreateFanoutStanza: add bot node
        if (context.botNode() != null) {
            childNodes.add(context.botNode());
        }

        // WAWebSendMsgCreateFanoutStanza: add tctoken
        if (context.tcToken() != null && context.tcToken().length > 0) {
            var tcTokenNode = new NodeBuilder()
                    .description("tctoken")
                    .content(context.tcToken())
                    .build();
            childNodes.add(tcTokenNode);
        }

        // WAWebReportingTokenUtils.genReportingTokenBody: add reporting token
        if (context.reportingTokenNode() != null) {
            childNodes.add(context.reportingTokenNode());
        }

        // WAWebMsgRcatUtils: add sender content binding
        if (context.senderContentBinding() != null && context.senderContentBinding().length > 0) {
            var senderBindingNode = new NodeBuilder()
                    .description("sender_content_binding")
                    .content(context.senderContentBinding())
                    .build();
            childNodes.add(senderBindingNode);
        }

        // WAWebSendMsgCtwaAttributionNode: add CTWA attribution
        if (context.ctwaAttributionNode() != null) {
            childNodes.add(context.ctwaAttributionNode());
        }

        // WAWebSendMsgCreateFanoutStanza: build final message stanza with all attributes
        return new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", chatJid)
                .attribute("type", messageType)
                .attribute("addressing_mode", context.addressingMode())
                .attribute("edit", context.editAttribute())
                .attribute("device_fanout", context.isResend() || context.isBotFeedback() ? "false" : null)
                // Peer recipient attributes for LID migration
                .attribute("peer_recipient_lid", context.peerRecipientLid())
                .attribute("peer_recipient_pn", context.peerRecipientPn())
                .attribute("peer_recipient_username", context.peerRecipientUsername())
                .attribute("recipient_pn", context.recipientPn())
                .content(childNodes);
    }

    /**
     * Creates a message stanza for sending to a group using sender key encryption.
     * <p>
     * Structure:
     * <pre>{@code
     * <message id="{msgId}" to="{groupJid}" type="{msgType}" phash="{participantHash}" edit="{editAttr}">
     *   <participants>
     *     <to jid="{deviceJid}">
     *       <enc v="2" type="pkmsg|msg" decrypt-fail="hide">{senderKeyDistribution}</enc>
     *       <content_binding>{binding}</content_binding>
     *     </to>
     *     ...
     *   </participants>
     *   <enc v="2" type="skmsg" decrypt-fail="hide">{ciphertext}</enc>
     *   <device-identity>{advIdentity}</device-identity>
     *   <biz .../>
     *   <meta .../>
     *   <bot .../>
     *   <sender_content_binding>{binding}</sender_content_binding>
     *   <reporting_token>{token}</reporting_token>
     * </message>
     * }</pre>
     *
     * @param messageId         the message ID
     * @param groupJid          the group JID
     * @param messageType       the message type attribute
     * @param phash             the participant hash for verification
     * @param senderKeyMessage  the sender key encrypted message
     * @param distributionNodes the sender key distribution nodes for new recipients
     * @param deviceIdentity    the ADV device identity bytes
     * @param context           the stanza context with additional attributes and nodes
     * @return the constructed message stanza
     *
     * @apiNote WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg: builds stanza for
     * group sender key messages
     */
    public static NodeBuilder createGroupSenderKeyStanza(
            String messageId,
            Jid groupJid,
            String messageType,
            String phash,
            StanzaContext.EncryptedGroupMessage senderKeyMessage,
            Collection<EncryptedDeviceNode> distributionNodes,
            byte[] deviceIdentity,
            StanzaContext.GroupStanzaContext context
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(messageType, "messageType cannot be null");
        Objects.requireNonNull(senderKeyMessage, "senderKeyMessage cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        var children = new ArrayList<Node>();

        // WAWebSendGroupSkmsgJob: build participant nodes for sender key distribution
        if (distributionNodes != null && !distributionNodes.isEmpty()) {
            var participantNodes = buildParticipantNodes(distributionNodes, context.contentBindings());
            var participantsNode = new NodeBuilder()
                    .description("participants")
                    .content(participantNodes)
                    .build();
            children.add(participantsNode);
        } else if (context.contentBindings() != null && !context.contentBindings().isEmpty()) {
            // WAWebSendGroupSkmsgJob: even without distribution, include content bindings
            var participantNodes = new ArrayList<Node>();
            for (var entry : context.contentBindings().entrySet()) {
                if (entry.getValue() != null && entry.getValue().length > 0) {
                    var bindingNode = new NodeBuilder()
                            .description("content_binding")
                            .content(entry.getValue())
                            .build();
                    var toNode = new NodeBuilder()
                            .description("to")
                            .attribute("jid", entry.getKey())
                            .content(bindingNode)
                            .build();
                    participantNodes.add(toNode);
                }
            }
            if (!participantNodes.isEmpty()) {
                var participantsNode = new NodeBuilder()
                        .description("participants")
                        .content(participantNodes)
                        .build();
                children.add(participantsNode);
            }
        }

        // WAWebSendGroupSkmsgJob: build enc node for sender key message
        var encNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageSignalEncryptionType.SKMSG.protocolValue())
                .attribute("mediatype", senderKeyMessage.mediaType())
                .attribute("decrypt-fail", senderKeyMessage.decryptFail())
                .attribute("native_flow_name", senderKeyMessage.nativeFlowName())
                .content(senderKeyMessage.ciphertext())
                .build();
        children.add(encNode);

        // WAWebSendGroupSkmsgJob: check if any distribution was pkmsg
        var shouldHaveIdentity = (distributionNodes != null && distributionNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage()))
                || context.botNeedsIdentity();

        // WAWebSendGroupSkmsgJob: build device-identity if needed
        if (shouldHaveIdentity && deviceIdentity != null) {
            var deviceIdentityNode = new NodeBuilder()
                    .description("device-identity")
                    .content(deviceIdentity)
                    .build();
            children.add(deviceIdentityNode);
        }

        // WAWebSendGroupSkmsgJob: add biz node for native flow payment info
        if (context.bizNode() != null) {
            children.add(context.bizNode());
        }

        // WAWebSendMsgMetaNode: add meta node
        if (context.metaNode() != null) {
            children.add(context.metaNode());
        }

        // WAWebSendGroupSkmsgJob: add bot node
        if (context.botNode() != null) {
            children.add(context.botNode());
        }

        // Add sender content binding
        if (context.senderContentBinding() != null && context.senderContentBinding().length > 0) {
            var senderBindingNode = new NodeBuilder()
                    .description("sender_content_binding")
                    .content(context.senderContentBinding())
                    .build();
            children.add(senderBindingNode);
        }

        // Add reporting token
        if (context.reportingTokenNode() != null) {
            children.add(context.reportingTokenNode());
        }

        // WAWebSendGroupSkmsgJob: build final message stanza
        // Note: phash is dropped for bot feedback messages
        return new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", groupJid)
                .attribute("type", messageType)
                .attribute("phash", context.isBotFeedback() ? null : phash)
                .attribute("addressing_mode", context.addressingMode())
                .attribute("edit", context.editAttribute())
                .content(children);
    }

    /**
     * Creates a message stanza for sending directly to group members (group direct mode).
     * <p>
     * Used for messages that need to be encrypted per-device rather than using sender keys.
     *
     * @param messageId       the message ID
     * @param groupJid        the group JID
     * @param messageType     the message type attribute
     * @param encryptedNodes  the encrypted payload nodes for each device
     * @param deviceIdentity  the ADV device identity bytes
     * @param addressingMode  the addressing mode
     * @return the constructed message stanza
     *
     * @apiNote WAWebSendGroupDirectJob.encryptAndSendGroupDirectMsg: used for
     * FANOUT_TYPE.GROUP_DIRECT messages
     */
    public static NodeBuilder createGroupDirectStanza(
            String messageId,
            Jid groupJid,
            String messageType,
            Collection<EncryptedDeviceNode> encryptedNodes,
            byte[] deviceIdentity,
            String addressingMode
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(messageType, "messageType cannot be null");
        Objects.requireNonNull(encryptedNodes, "encryptedNodes cannot be null");

        // WAWebSendMsgCreateFanoutStanza: build participant nodes
        var participantNodes = buildParticipantNodes(encryptedNodes, null);

        var shouldHaveIdentity = encryptedNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());

        var participantsNode = new NodeBuilder()
                .description("participants")
                .content(participantNodes)
                .build();

        // WAWebSendMsgCreateFanoutStanza: enc node for group direct (empty, type skmsg)
        var groupEncNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageSignalEncryptionType.SKMSG.protocolValue())
                .build();

        Node deviceIdentityNode = null;
        if (shouldHaveIdentity && deviceIdentity != null) {
            deviceIdentityNode = new NodeBuilder()
                    .description("device-identity")
                    .content(deviceIdentity)
                    .build();
        }

        return new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", groupJid)
                .attribute("type", messageType)
                .attribute("addressing_mode", addressingMode)
                .content(participantsNode, groupEncNode, deviceIdentityNode);
    }

    /**
     * Builds participant nodes from encrypted device payloads.
     *
     * @param encryptedNodes  the encrypted nodes for each device
     * @param contentBindings optional content bindings map (JID -> binding bytes)
     * @return list of participant nodes
     */
    private static List<Node> buildParticipantNodes(
            Collection<EncryptedDeviceNode> encryptedNodes,
            java.util.Map<Jid, byte[]> contentBindings
    ) {
        var result = new ArrayList<Node>();

        for (var encrypted : encryptedNodes) {
            // WAWebSendMsgCreateFanoutStanza: build enc node for each device
            var encNode = new NodeBuilder()
                    .description("enc")
                    .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                    .attribute("type", encrypted.encryptionType().protocolValue())
                    .attribute("mediatype", encrypted.mediaType())
                    .attribute("decrypt-fail", encrypted.decryptFail())
                    .attribute("native_flow_name", encrypted.nativeFlowName())
                    .content(encrypted.ciphertext())
                    .build();

            // WAWebMsgRcatUtils: check for content binding for this device's user
            Node contentBindingNode = null;
            if (contentBindings != null) {
                var userJid = encrypted.deviceJid().toUserJid();
                var binding = contentBindings.get(userJid);
                if (binding != null && binding.length > 0) {
                    contentBindingNode = new NodeBuilder()
                            .description("content_binding")
                            .content(binding)
                            .build();
                }
            }

            // WAWebSendMsgCreateFanoutStanza: wrap in to node with device JID
            var toNode = new NodeBuilder()
                    .description("to")
                    .attribute("jid", encrypted.deviceJid())
                    .content(encNode, contentBindingNode)
                    .build();

            result.add(toNode);
        }

        return result;
    }
}
