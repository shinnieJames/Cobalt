package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.icdc.IcdcEnricher;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.info.MessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.button.InteractiveMessage;
import com.github.auties00.cobalt.model.message.button.InteractiveResponseMessage;
import com.github.auties00.cobalt.model.message.KeepInChat;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.server.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.server.ProtocolMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Base class for all message senders, providing shared cryptographic,
 * stanza-attribute resolution, and encoding utilities.
 *
 * <p>Subclasses implement the chat-type-specific orchestration logic
 * while inheriting common operations such as per-device encryption,
 * stanza type/edit/decrypt-fail resolution, and identity node construction.
 *
 * @apiNote WAWebSendMsgCommonApi: shared utilities used across all send paths.
 * WAWebE2EProtoUtils: type, edit, and decrypt-fail attribute resolution.
 * WAWebAdvSignatureApi: ADV identity node encoding.
 * WAWebBackendJobsCommon: ciphertext version and media type extraction.
 */
abstract sealed class MessageSender<T extends MessageInfo> permits UserMessageSender, GroupMessageSender, StatusMessageSender, NewsletterMessageSender, PeerMessageSender {
    private static final System.Logger LOGGER = System.getLogger("MessageSender");

    final WhatsAppClient client;
    final WhatsAppStore store;

    MessageSender(WhatsAppClient client) {
        this.client = Objects.requireNonNull(client, "client");
        this.store = client.store();
    }

    /**
     * Sends a message to the specified chat.
     *
     * @param chatJid     the target JID
     * @param messageInfo the outgoing message with its key, container,
     *                    and metadata
     * @return the server ack result
     */
    abstract AckResult send(Jid chatJid, T messageInfo);

    /**
     * Blocks until the offline message queue has been drained.
     *
     * @apiNote WAWebEventsWaitForOfflineDeliveryEnd: waits for the
     * {@code offline_delivery_end} event before sending.
     */
    void waitForOfflineDelivery() {
        store.waitForOfflineDeliveryEnd();
    }

    /**
     * Persists the store's encryption state before sending the wire
     * stanza, ensuring session keys and prekeys survive a crash.
     *
     * @apiNote WAWebEncryptAndSendStatusMsg, WAWebSendMsgJob,
     * WAWebSendGroupSkmsgJob: all call
     * {@code getSignalProtocolStore().flushBufferToDiskIfNotMemOnlyMode()}
     * before the stanza is sent on the wire.
     */
    void flushStore() {
        store.save();
    }

    /**
     * Returns the JID of the currently logged-in device.
     *
     * @return the self JID
     * @throws IllegalStateException if not logged in
     */
    Jid requireSelfJid() {
        return store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
    }

    /**
     * Returns the LID of the currently logged-in device, falling back
     * to the PN JID if no LID is registered.
     *
     * @return the self LID or PN JID
     */
    Jid selfLidOrPn() {
        return store.lid().orElseGet(this::requireSelfJid);
    }

    /**
     * Encrypts the message for each device in the list, populating ICDC
     * metadata per device before serialisation.
     *
     * <p>Companion devices (same account as the sender) receive a
     * {@code DeviceSentMessage} wrapper with only the sender's ICDC
     * metadata.  Recipient devices receive both sender and recipient
     * ICDC metadata.
     *
     * @param encryption     the encryption service
     * @param devices        the device JIDs to encrypt for
     * @param container      the message container
     * @param destinationJid the chat recipient JID (used for DeviceSentMessage
     *                       wrapping of companion device payloads)
     * @param senderIcdc     the sender's ICDC metadata, or {@code null}
     * @param recipientIcdc  the recipient's ICDC metadata, or {@code null}
     * @return the encrypted payloads (may be smaller than {@code devices}
     *         if some encryptions failed)
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: encrypts via
     * encryptMsgProtobuf per device, calling populateICDCMeta for each.
     * WAWebICDCMetaApi.populateICDCMeta: sets deviceListMetadata and
     * deviceListMetadataVersion on the message's messageContextInfo.
     */
    List<MessageEncryptedPayload> encryptForDevices(
            MessageEncryption encryption,
            Collection<Jid> devices,
            MessageContainer container,
            Jid destinationJid,
            IcdcResult senderIcdc,
            IcdcResult recipientIcdc
    ) {
        var selfJid = store.jid().orElse(null);
        var results = new ArrayList<MessageEncryptedPayload>(devices.size());

        // WAWebICDCMetaApi.populateICDCMeta: for self devices, recipientIcdc is null
        var companionContainer = IcdcEnricher.enrich(container, senderIcdc, null);
        var recipientContainer = IcdcEnricher.enrich(container, senderIcdc, recipientIcdc);

        for (var device : devices) {
            try {
                byte[] devicePlaintext;
                if (selfJid != null && device.toUserJid().equals(selfJid.toUserJid())) {
                    // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage:
                    // wraps the message in a DeviceSentMessage with the
                    // destination JID so companion devices know who the
                    // message was sent to
                    var deviceSentMessage = new DeviceSentMessageBuilder()
                            .destinationJid(destinationJid)
                            .message(companionContainer)
                            .build();
                    var wrapped = MessageContainer.of(deviceSentMessage);
                    devicePlaintext = MessageContainerSpec.encode(wrapped);
                } else {
                    devicePlaintext = MessageContainerSpec.encode(recipientContainer);
                }
                results.add(encryption.encryptForDevice(device, devicePlaintext));
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Encryption fail for {0}: {1}", device, e.getMessage());
            }
        }

        // WAWebSendMsgCommonApi.updateIdentityRange: track which identity
        // keys were used for this encryption, enabling retry detection
        store.updateIdentityRange(devices);

        return results;
    }

    /**
     * Resolves the stanza {@code type} attribute from the message content.
     *
     * @param container the message container
     * @return one of {@code "text"}, {@code "media"}, {@code "reaction"},
     *         {@code "poll"}, or {@code "event"}
     *
     * @apiNote WAWebE2EProtoUtils.typeAttributeFromProtobuf: unwraps
     * wrapper messages (ephemeral, deviceSent, viewOnce, botInvoke),
     * then pattern-matches on the inner message type.
     * WAWebHandleMsgCommon.STANZA_MSG_TYPES: the possible values.
     */
    String resolveStanzaType(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            // WAWebE2EProtoUtils: reactionMessage, encReactionMessage → "reaction"
            case ReactionMessage _ -> "reaction";
            case EncryptedReactionMessage _ -> "reaction";

            // WAWebE2EProtoUtils: eventMessage, encEventResponseMessage,
            // secretEncryptedMessage(EVENT_EDIT) → "event"
            case EventMessage _ -> "event";
            case EncryptedEventResponseMessage _ -> "event";
            case SecretEncryptedMessage s
                    when s.secretEncType() == SecretEncryptedMessage.SecretEncType.EVENT_EDIT -> "event";

            // WAWebE2EProtoUtils: pollCreation*, pollUpdate, pollResultSnapshot → "poll"
            case PollCreationMessage _ -> "poll";
            case PollUpdateMessage _ -> "poll";
            case PollResultSnapshotMessage _ -> "poll";

            // WAWebE2EProtoUtils: extendedTextMessage with non-empty matchedText → "media"
            case TextMessage text when text.matchedText().isPresent() -> "media";

            // WAWebE2EProtoUtils: conversation, extendedText, protocolMessage,
            // interactiveMessage, keepInChat, pinInChat, newsletterAdminInvite,
            // requestPhoneNumber, editedMessage → "text"
            case TextMessage _ -> "text";
            case ProtocolMessage _ -> "text";
            case InteractiveMessage _ -> "text";
            case InteractiveResponseMessage _ -> "text";
            case KeepInChatMessage _ -> "text";
            case NewsletterAdminInviteMessage _ -> "text";
            case RequestPhoneNumberMessage _ -> "text";
            case EmptyMessage _ -> "text";

            // WAWebE2EProtoUtils: everything else → "media"
            // (image, video, audio, document, sticker, location, contact, etc.)
            default -> "media";
        };
    }

    /**
     * Resolves the stanza {@code edit} attribute from the message content.
     *
     * <p>Equivalent to calling
     * {@code resolveEditAttribute(container, false)}.
     *
     * @param container the message container
     * @return the edit attribute value, or {@code null} for normal messages
     *
     * @apiNote WAWebSendMsgCommonApi.editAttribute: checks protobuf
     * message type to determine if this is a revoke (7), admin revoke (8),
     * edit (1), or pin (2).
     * WAWebAck.EDIT_ATTR: the constant values.
     */
    String resolveEditAttribute(MessageContainer container) {
        return resolveEditAttribute(container, false);
    }

    /**
     * Resolves the stanza {@code edit} attribute from the message content,
     * distinguishing admin revokes from sender revokes.
     *
     * @param container      the message container
     * @param isAdminRevoke  {@code true} when the revoking user is a group
     *                       admin revoking another participant's message
     * @return the edit attribute value, or {@code null} for normal messages
     *
     * @apiNote WAWebSendMsgCommonApi.editAttribute: uses
     * {@code subtype === "admin_revoke"} to return 8 instead of 7.
     */
    String resolveEditAttribute(MessageContainer container, boolean isAdminRevoke) {
        var message = container.content();
        return switch (message) {
            // WAWebSendMsgCommonApi: protocolMessage REVOKE →
            // 7 (sender_revoke) or 8 (admin_revoke)
            case ProtocolMessage p when p.protocolType() == ProtocolMessage.Type.REVOKE ->
                    isAdminRevoke ? "8" : "7";

            // WAWebSendMsgCommonApi: protocolMessage MESSAGE_EDIT → 1
            case ProtocolMessage p when p.protocolType() == ProtocolMessage.Type.MESSAGE_EDIT -> "1";

            // WAWebSendMsgCommonApi: keepInChatMessage UNDO_KEEP_FOR_ALL → 7
            case KeepInChatMessage keep when isUndoKeepForAll(keep) -> "7";

            // WAWebSendMsgCommonApi: pinInChatMessage → 2
            case PinInChatMessage _ -> "2";

            // WAWebSendMsgCommonApi: reactionMessage with revoked text → 7
            case ReactionMessage r when r.content() == null || r.content().isEmpty() -> "7";

            default -> null;
        };
    }

    /**
     * Resolves the {@code decrypt-fail} attribute from the message content.
     *
     * @param container the message container
     * @return {@code "hide"} or {@code null}
     *
     * @apiNote WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf: returns
     * Hide for reactions, poll votes, event responses, edits, revokes,
     * keepInChat, pinInChat, ephemeral sync, welcome messages.
     */
    String resolveDecryptFail(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ReactionMessage _ -> "hide";
            case EncryptedReactionMessage _ -> "hide";
            case PollUpdateMessage _ -> "hide";
            case KeepInChatMessage _ -> "hide";
            case PinInChatMessage _ -> "hide";
            case EncryptedEventResponseMessage _ -> "hide";
            case SecretEncryptedMessage s
                    when s.secretEncType() == SecretEncryptedMessage.SecretEncType.EVENT_EDIT -> "hide";
            case ProtocolMessage p -> switch (p.protocolType()) {
                case REVOKE, MESSAGE_EDIT, EPHEMERAL_SYNC_RESPONSE, REQUEST_WELCOME_MESSAGE -> "hide";
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * Resolves the {@code mediatype} attribute for the {@code <enc>} node.
     *
     * @param container the message container
     * @return the media type string, or {@code null} for non-media messages
     *
     * @apiNote WAWebBackendJobsCommon.mediaTypeFromProtobuf: extracts the
     * media type from the protobuf, unwrapping deviceSent/ephemeral/viewOnce.
     * WAWebBackendJobsCommon.encodeMaybeMediaType: encodes to stanza string.
     */
    String resolveMediaType(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ImageMessage _ -> "image";
            case VideoOrGifMessage v -> v.gifPlayback() ? "gif" : "video";
            case AudioMessage a -> a.voiceMessage() ? "ptt" : "audio";
            case DocumentMessage _ -> "document";
            case StickerMessage _ -> "sticker";
            case LocationMessage l -> l.live() ? "livelocation" : "location";
            case LiveLocationMessage _ -> "livelocation";
            case ContactMessage _ -> "vcard";
            case ContactsMessage _ -> "contact_array";
            case GroupInviteMessage _ -> "url";
            case TextMessage t when t.matchedText().isPresent() -> "url";
            case PollCreationMessage _ -> null;
            case PollUpdateMessage _ -> null;
            case ReactionMessage _ -> null;
            default -> null;
        };
    }

    /**
     * Resolves the {@code native_flow_name} attribute for the {@code <enc>} node.
     *
     * @param container the message container
     * @return the native flow name, or {@code null}
     *
     * @apiNote WAWebBackendJobsCommon.nativeFlowNameTypeFromProtobuf: extracts
     * the native flow name from interactiveResponseMessage.
     */
    String resolveNativeFlowName(MessageContainer container) {
        var message = container.content();
        if (message instanceof InteractiveResponseMessage irm && irm.nativeFlowResponseMessage() != null) {
            return irm.nativeFlowResponseMessage().name();
        }
        return null;
    }

    /**
     * Builds the {@code <device-identity>} node from the stored ADV
     * signed device identity.
     *
     * @return the identity node, or {@code null} if unavailable
     *
     * @apiNote WAWebAdvSignatureApi.getADVEncodedIdentity: serialises
     * the ADV signed device identity to protobuf bytes.
     */
    Node buildIdentityNode() {
        return store.signedDeviceIdentity()
                .map(identity -> new NodeBuilder()
                        .description("device-identity")
                        .content(SignedDeviceIdentitySpec.encode(identity))
                        .build())
                .orElse(null);
    }

    /**
     * Checks whether a KeepInChatMessage represents an "undo keep for all"
     * operation, which uses the sender_revoke edit attribute.
     *
     * @apiNote WAWebSendMsgCommonApi: keepInChatMessage with
     * keepType=UNDO_KEEP_FOR_ALL and fromMe=true → SENDER_REVOKE (7).
     */
    private boolean isUndoKeepForAll(KeepInChatMessage keep) {
        // WAWebSendMsgCommonApi: keepInChatMessage.key.fromMe === true
        // && keepType === UNDO_KEEP_FOR_ALL
        return keep.keepType() == KeepInChat.Type.UNDO_KEEP_FOR_ALL;
    }
}
