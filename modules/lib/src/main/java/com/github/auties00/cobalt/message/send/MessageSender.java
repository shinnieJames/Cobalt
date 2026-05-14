package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.exception.WhatsAppCorruptedStoreException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.icdc.IcdcEnricher;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatKeepType;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.EmptyMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.group.GroupInviteMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveResponseMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateButtonReplyMessage;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.*;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterAdminInviteMessage;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterFollowerInviteMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.security.SecretEncMessage;
import com.github.auties00.cobalt.model.message.system.*;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryNotice;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.E2eMessageSendEventBuilder;
import com.github.auties00.cobalt.wam.type.AddressingMode;
import com.github.auties00.cobalt.wam.type.AgentEngagementEnumType;
import com.github.auties00.cobalt.wam.type.E2eCiphertextType;
import com.github.auties00.cobalt.wam.type.E2eDestination;
import com.github.auties00.cobalt.wam.type.EditType;
import com.github.auties00.cobalt.wam.type.EncryptionTypeCode;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.PlaceholderReasonType;

import java.io.IOException;
import java.util.*;

/**
 * Provides the shared cryptographic, stanza-attribute resolution, and encoding
 * utilities used by every chat-type-specific message sender. Subclasses focus
 * on orchestration logic and inherit per-device encryption, stanza type/edit/
 * decrypt-fail resolution, identity node construction, and the WAM emission
 * helpers.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCommonApi")
@WhatsAppWebModule(moduleName = "WAWebE2EProtoUtils")
@WhatsAppWebModule(moduleName = "WAWebAdvSignatureApi")
@WhatsAppWebModule(moduleName = "WAWebBackendJobsCommon")
abstract sealed class MessageSender<T extends MessageInfo> permits UserMessageSender, GroupMessageSender, StatusMessageSender, NewsletterMessageSender, PeerMessageSender {
    /**
     * Holds the logger used during per-device encryption diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageSender.class.getName());

    /**
     * Holds the client used to dispatch wire stanzas and report failures.
     */
    final WhatsAppClient client;

    /**
     * Holds the store carrying Signal sessions, device lists, identity records,
     * and the rest of the shared send-path state.
     */
    final WhatsAppStore store;

    /**
     * Holds the AB-props service consulted on the resend timeout path and by
     * subclasses for feature-gating decisions.
     */
    final ABPropsService abPropsService;

    /**
     * Holds the WAM telemetry service used to commit per-send events.
     */
    final WamService wamService;

    /**
     * Constructs a sender bound to the given client and WAM service.
     *
     * @param client         the WhatsApp client used to dispatch stanzas
     * @param abPropsService the AB-props service used for feature gating
     * @param wamService     the WAM telemetry service for send-path events
     */
    MessageSender(WhatsAppClient client, ABPropsService abPropsService, WamService wamService) {
        this.client = Objects.requireNonNull(client, "client");
        this.store = client.store();
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.wamService = Objects.requireNonNull(wamService, "wamService");
    }

    /**
     * Sends the given message to the specified chat.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message and its metadata
     * @return the server ack result
     */
    abstract AckResult send(Jid chatJid, T messageInfo);

    /**
     * Blocks until the offline-message queue has been drained.
     */
    @WhatsAppWebExport(moduleName = "WAWebEventsWaitForOfflineDeliveryEnd", exports = "waitForOfflineDeliveryEnd",
            adaptation = WhatsAppAdaptation.DIRECT)
    void waitForOfflineDelivery() {
        store.waitForOfflineDeliveryEnd();
    }

    /**
     * Returns the maximum age, in seconds, of an outgoing message that may
     * still be resent after a phash mismatch or other server-driven retry.
     * When the elapsed time exceeds this threshold the resend path is skipped.
     *
     * @return the resend timeout in seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "getResendTimeoutInSeconds",
            adaptation = WhatsAppAdaptation.DIRECT)
    long getResendTimeoutInSeconds() {
        var minutes = abPropsService.getInt(ABProp.WEB_E2E_BACKFILL_EXPIRE_TIME);
        if (minutes <= 0) {
            minutes = 5;
        }
        return minutes * 60L;
    }

    /**
     * Persists the store's encryption state to disk so session keys and
     * pre-keys survive an unexpected shutdown after the wire stanza is sent.
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalProtocolStore", exports = "flushBufferToDiskIfNotMemOnlyMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    void flushStore() {
        try {
            store.save();
        } catch (IOException ex) {
            client.handleFailure(new WhatsAppCorruptedStoreException(ex));
        }
    }

    /**
     * Returns the JID of the currently logged-in device.
     *
     * @return the self JID
     * @throws IllegalStateException if the client is not logged in
     */
    Jid requireSelfJid() {
        return store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
    }

    /**
     * Returns the LID of the currently logged-in device, falling back to the
     * PN JID when no LID is registered.
     *
     * @return the self LID, or the PN JID when LID is unavailable
     */
    Jid selfLidOrPn() {
        return store.lid().orElseGet(this::requireSelfJid);
    }

    /**
     * Encrypts the given container for each device in {@code devices},
     * populating ICDC metadata per device before serialisation. Companion
     * devices receive a {@code DeviceSentMessage} wrapper with only the
     * sender's ICDC metadata; recipient devices receive both sender and
     * recipient ICDC metadata.
     *
     * @param encryption     the encryption service to use
     * @param devices        the device JIDs to encrypt for
     * @param container      the source message container
     * @param destinationJid the chat recipient JID, written into the
     *                       {@code DeviceSentMessage} wrapper for companions
     * @param senderIcdc     the sender's ICDC metadata, or {@code null}
     * @param recipientIcdc  the recipient's ICDC metadata, or {@code null}
     * @return the encrypted payloads, possibly smaller than {@code devices}
     *         when some per-device encryptions fail
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebICDCMetaApi", exports = "populateICDCMeta",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebDeviceSentMessageProtoUtils", exports = "wrapDeviceSentMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    List<MessageEncryptedPayload> encryptForDevices(
            MessageEncryption encryption,
            Collection<Jid> devices,
            MessageContainer container,
            Jid destinationJid,
            IcdcResult senderIcdc,
            IcdcResult recipientIcdc
    ) {
        var selfPn = store.jid().map(Jid::toUserJid).orElse(null);
        var selfLid = store.lid().map(Jid::toUserJid).orElse(null);
        var results = new ArrayList<MessageEncryptedPayload>(devices.size());

        var companionContainer = IcdcEnricher.enrich(container, senderIcdc, null);
        var recipientContainer = IcdcEnricher.enrich(container, senderIcdc, recipientIcdc);

        for (var device : devices) {
            try {
                byte[] devicePlaintext;
                var deviceUserJid = device.toUserJid();
                var isSelfDevice = (selfPn != null && deviceUserJid.equals(selfPn))
                        || (selfLid != null && deviceUserJid.equals(selfLid));
                if (isSelfDevice) {
                    var innerContextInfo = companionContainer.messageContextInfo().orElse(null);
                    var innerContainer = innerContextInfo != null
                            ? companionContainer.withMessageContextInfo(null)
                            : companionContainer;
                    var deviceSentMessage = new DeviceSentMessageBuilder()
                            .destinationJid(destinationJid)
                            .messageContainer(innerContainer)
                            .build();
                    var wrapped = MessageContainer.of(deviceSentMessage);
                    if (innerContextInfo != null) {
                        wrapped = wrapped.withMessageContextInfo(innerContextInfo);
                    }
                    devicePlaintext = MessageContainerSpec.encode(wrapped);
                } else {
                    devicePlaintext = MessageContainerSpec.encode(recipientContainer);
                }
                var payload = encryption.encryptForDevice(device, devicePlaintext);
                results.add(payload);
                emitE2eMessageSendEvent(device, container, true, payload.type(), 0);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Encryption fail for {0}: {1}", device, e.getMessage());
                emitE2eMessageSendEvent(device, container, false, null, 0);
            }
        }

        store.updateIdentityRange(devices);

        return results;
    }

    /**
     * Resolves the stanza {@code type} attribute from the message content.
     *
     * @param container the message container
     * @return one of {@code "text"}, {@code "media"}, {@code "reaction"},
     *         {@code "poll"}, or {@code "event"}
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoUtils", exports = "typeAttributeFromProtobuf",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveStanzaType(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ReactionMessage _ -> "reaction";
            case EncReactionMessage _ -> "reaction";

            case EventMessage _ -> "event";
            case EncEventResponseMessage _ -> "event";
            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.EVENT_EDIT -> "event";

            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.MESSAGE_EDIT -> "text";

            case PollCreationMessage _ -> "poll";
            case PollUpdateMessage _ -> "poll";

            case PollResultSnapshotMessage _ -> "poll";

            case ExtendedTextMessage text when text.matchedText().isPresent() -> "media";

            case ExtendedTextMessage _ -> "text";
            case TemplateButtonReplyMessage _ -> "text";
            case ProtocolMessage _ -> "text";
            case InteractiveMessage _ -> "text";
            case InteractiveResponseMessage _ -> "text";
            case KeepInChatMessage _ -> "text";
            case PinInChatMessage _ -> "text";
            case EncCommentMessage _ -> "text";
            case NewsletterAdminInviteMessage _ -> "text";
            case NewsletterFollowerInviteMessage _ -> "text";
            case MessageHistoryNotice _ -> "text";
            case RequestPhoneNumberMessage _ -> "text";
            case EmptyMessage _ -> "text";

            default -> "media";
        };
    }

    /**
     * Resolves the stanza {@code edit} attribute from the message content,
     * defaulting to a non-admin revoke classification.
     *
     * @param container the message container
     * @return the edit attribute value, or {@code null} for normal messages
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "editAttribute",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveEditAttribute(MessageContainer container) {
        return resolveEditAttribute(container, false);
    }

    /**
     * Resolves the stanza {@code edit} attribute from the message content,
     * distinguishing admin revokes from sender revokes.
     *
     * @param container     the message container
     * @param isAdminRevoke {@code true} when a group admin is revoking another
     *                      participant's message
     * @return the edit attribute value, or {@code null} for normal messages
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "editAttribute",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveEditAttribute(MessageContainer container, boolean isAdminRevoke) {
        var message = container.content();
        return switch (message) {
            case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.REVOKE ->
                    isAdminRevoke ? "8" : "7";

            case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.MESSAGE_EDIT -> "1";

            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.EVENT_EDIT
                    || s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.MESSAGE_EDIT -> "1";

            case KeepInChatMessage keep when isUndoKeepForAll(keep) -> "7";

            case PinInChatMessage _ -> "2";

            case ReactionMessage r when r.text().orElse("").isEmpty() -> "7";

            default -> null;
        };
    }

    /**
     * Resolves the {@code decrypt-fail} attribute from the message content.
     *
     * @param container the message container
     * @return {@code "hide"} for messages whose decryption failures should be
     *         silenced, otherwise {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoUtils", exports = "decryptFailAttributeFromProtobuf",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveDecryptFail(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ReactionMessage _ -> "hide";
            case EncReactionMessage _ -> "hide";
            case PollUpdateMessage _ -> "hide";
            case KeepInChatMessage _ -> "hide";
            case PinInChatMessage _ -> "hide";
            case EncEventResponseMessage _ -> "hide";
            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.EVENT_EDIT -> "hide";
            case ProtocolMessage p when p.type().isPresent() -> switch (p.type().get()) {
                case REVOKE, MESSAGE_EDIT, EPHEMERAL_SYNC_RESPONSE, REQUEST_WELCOME_MESSAGE -> "hide";
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * Resolves the {@code mediatype} attribute written onto the {@code <enc>}
     * node for the given message container.
     *
     * @param container the message container
     * @return the media-type string, or {@code null} for non-media messages
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon",
            exports = {"mediaTypeFromProtobuf", "encodeMaybeMediaType"},
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveMediaType(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ImageMessage _ -> "image";
            case VideoMessage v -> v.gifPlayback() ? "gif" : "video";
            case AudioMessage a -> a.ptt() ? "ptt" : "audio";
            case DocumentMessage _ -> "document";
            case StickerMessage _ -> "sticker";
            case LocationMessage l -> l.isLive() ? "livelocation" : "location";
            case LiveLocationMessage _ -> "livelocation";
            case ContactMessage _ -> "vcard";
            case ContactsArrayMessage _ -> "contact_array";
            case GroupInviteMessage _ -> "url";
            case ExtendedTextMessage t when t.matchedText().isPresent() -> "url";
            case PollCreationMessage _ -> null;
            case PollUpdateMessage _ -> null;
            case ReactionMessage _ -> null;
            default -> null;
        };
    }

    /**
     * Resolves the {@code native_flow_name} attribute written onto the
     * {@code <enc>} node for interactive-response messages.
     *
     * @param container the message container
     * @return the native flow name, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "nativeFlowNameTypeFromProtobuf",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveNativeFlowName(MessageContainer container) {
        var message = container.content();
        if (!(message instanceof InteractiveResponseMessage irm)) {
            return null;
        }

        var content = irm.content();
        if (content.isEmpty() || !(content.get() instanceof InteractiveResponseMessage.NativeFlowResponseMessage nfr)) {
            return null;
        }

        return nfr.name()
                .orElse(null);
    }

    /**
     * Builds the {@code <device-identity>} node carrying the stored ADV signed
     * device identity, or {@code null} when no identity is available.
     *
     * @return the identity node, or {@code null} when unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi", exports = "getADVEncodedIdentity",
            adaptation = WhatsAppAdaptation.DIRECT)
    Node buildIdentityNode() {
        return store.signedDeviceIdentity()
                .map(identity -> new NodeBuilder()
                        .description("device-identity")
                        .content(ADVSignedDeviceIdentitySpec.encode(identity))
                        .build())
                .orElse(null);
    }

    /**
     * Returns whether the given {@link KeepInChatMessage} represents an
     * "undo keep for all" operation, which uses the sender-revoke edit value.
     *
     * @param keep the keep-in-chat message
     * @return {@code true} when the keep represents an undo by the original sender
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "editAttribute",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isUndoKeepForAll(KeepInChatMessage keep) {
        return keep.key().isPresent()
                && keep.key().get().fromMe()
                && keep.keepType().orElse(null) == ChatKeepType.UNDO_KEEP_FOR_ALL;
    }

    /**
     * Commits the {@code E2eMessageSendEvent} (id 476) for a per-device Signal
     * encryption attempt. Cobalt collapses the WA Web success and failure
     * helpers into a single emission point invoked from the per-device loop
     * in {@link #encryptForDevices}.
     *
     * @param device         the recipient device JID
     * @param container      the message container being encrypted, or
     *                       {@code null} when the encryption carries no
     *                       user-visible payload
     * @param success        {@code true} for a successful encryption,
     *                       {@code false} for a failure
     * @param ciphertextType the resolved Signal ciphertext type on success,
     *                       or {@code null} on failure
     * @param retryCount     the retry count passed through from the sender job;
     *                       {@code 0} for a fresh send
     */
    @WhatsAppWebExport(moduleName = "WAWebPostE2eMessageSendMetric",
            exports = {"postSuccessDirectE2eMessageSendMetric", "postFailureDirectE2eMessageSendMetric"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    void emitE2eMessageSendEvent(Jid device, MessageContainer container, boolean success,
                                 MessageEncryptionType ciphertextType,
                                 int retryCount) {
        var builder = new E2eMessageSendEventBuilder()
                .e2eCiphertextVersion(MessageEncryption.CIPHERTEXT_VERSION)
                .isLid(device.hasLidServer())
                .retryCount(retryCount)
                .editType(EditType.NOT_EDITED)
                .e2eDestination(E2eDestination.INDIVIDUAL)
                .e2eSuccessful(success);
        if (device.hasServer(JidServer.hosted())) {
            builder.encryptionType(EncryptionTypeCode.COEX);
        }
        if (ciphertextType != null) {
            builder.e2eCiphertextType(mapCiphertextType(ciphertextType));
        }
        if (container != null) {
            var mediaType = mapMediaType(container);
            if (mediaType != null) {
                builder.messageMediaType(mediaType);
            }
            if (device.isBot()) {
                builder.agentEngagementType(AgentEngagementEnumType.INVOKED);
            }
        }
        wamService.commit(builder.build());
    }

    /**
     * Commits the {@code E2eMessageSendEvent} (id 476) for a sender-key
     * (SKMSG) encryption attempt covering a group or status broadcast.
     *
     * @param groupOrStatusJid    the SKMSG target JID
     * @param container           the message container being encrypted
     * @param destination         the semantic destination
     *                            ({@link E2eDestination#GROUP} for groups,
     *                            {@link E2eDestination#STATUS} for status)
     * @param isLidAddressingMode whether the SKMSG fanout uses LID addressing
     * @param success             {@code true} for a successful encryption,
     *                            {@code false} for a failure
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptMsgProtobuf", exports = "encryptMsgSenderKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    void emitE2eMessageSendSenderKeyEvent(Jid groupOrStatusJid, MessageContainer container,
                                          E2eDestination destination, boolean isLidAddressingMode,
                                          boolean success) {
        var builder = new E2eMessageSendEventBuilder()
                .e2eSuccessful(success)
                .e2eCiphertextType(E2eCiphertextType.SENDER_KEY_MESSAGE)
                .e2eCiphertextVersion(MessageEncryption.CIPHERTEXT_VERSION)
                .e2eDestination(destination)
                .retryCount(0)
                .isLid(isLidAddressingMode)
                .editType(mapEditType(container))
                .localAddressingMode(isLidAddressingMode ? AddressingMode.LID : AddressingMode.PN);
        if (container != null) {
            var mediaType = mapMediaType(container);
            if (mediaType != null) {
                builder.messageMediaType(mediaType);
            }
        }
        wamService.commit(builder.build());
    }

    /**
     * Maps a {@link MessageEncryptionType} to the matching WAM
     * {@link E2eCiphertextType}.
     *
     * @param type the Signal ciphertext type
     * @return the matching WAM enum constant
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getMetricE2eCiphertextType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static E2eCiphertextType mapCiphertextType(MessageEncryptionType type) {
        return switch (type) {
            case MSG -> E2eCiphertextType.MESSAGE;
            case PKMSG -> E2eCiphertextType.PREKEY_MESSAGE;
            case SKMSG -> E2eCiphertextType.SENDER_KEY_MESSAGE;
            case MSMSG -> E2eCiphertextType.MESSAGE_SECRET_MESSAGE;
        };
    }

    /**
     * Maps the content of the given container to the matching WAM
     * {@link MediaType} classification.
     *
     * @param container the outbound message container
     * @return the WAM media type, or {@code null} when the message carries no
     *         classifiable media payload
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaType mapMediaType(MessageContainer container) {
        var message = container.content();
        return switch (message) {
            case ImageMessage _ -> MediaType.PHOTO;
            case VideoMessage v -> v.gifPlayback() ? MediaType.GIF : MediaType.VIDEO;
            case AudioMessage a -> a.ptt() ? MediaType.PTT : MediaType.AUDIO;
            case DocumentMessage _ -> MediaType.DOCUMENT;
            case StickerMessage _ -> MediaType.STICKER;
            case LocationMessage l -> l.isLive() ? MediaType.LIVE_LOCATION : MediaType.LOCATION;
            case LiveLocationMessage _ -> MediaType.LIVE_LOCATION;
            case ContactMessage _ -> MediaType.CONTACT;
            case ContactsArrayMessage _ -> MediaType.CONTACT_ARRAY;
            case GroupInviteMessage _ -> MediaType.URL;
            case ExtendedTextMessage t when t.matchedText().isPresent() -> MediaType.URL;
            case ExtendedTextMessage _ -> MediaType.TEXT;
            case ReactionMessage _ -> MediaType.REACTION;
            case EncReactionMessage _ -> MediaType.REACTION;
            case PollCreationMessage _ -> MediaType.POLL_CREATE;
            case PollUpdateMessage _ -> MediaType.POLL_VOTE;
            case PollResultSnapshotMessage _ -> MediaType.POLL_RESULT_SNAPSHOT;
            case EventMessage _ -> MediaType.EVENT_CREATE;
            case EncEventResponseMessage _ -> MediaType.EVENT_RESPOND;
            case KeepInChatMessage _ -> MediaType.KEEP;
            default -> null;
        };
    }

    /**
     * Encodes the protobuf decrypt-fail flag into the stanza
     * {@code decrypt-fail} attribute value. Returns {@code "hide"} when the
     * inner message wants the failure suppressed; otherwise returns
     * {@code null} so the caller drops the attribute.
     *
     * @param hide the protobuf decrypt-fail flag, or {@code null} when absent
     * @return {@code "hide"} when the failure should be hidden, otherwise {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "encodeMaybeDecryptFail",
            adaptation = WhatsAppAdaptation.DIRECT)
    static String encodeMaybeDecryptFail(Boolean hide) {
        if (hide == null) {
            return null;
        }
        return hide ? "hide" : null;
    }

    /**
     * Returns the optional native-flow-name string suitable for the stanza
     * {@code native_flow_name} attribute. Returns {@code null} when no value
     * is present so the caller can drop the attribute.
     *
     * @param nativeFlowName the resolved native flow name, or {@code null}
     * @return the same string when present, otherwise {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "encodeMaybeNativeFlowName",
            adaptation = WhatsAppAdaptation.DIRECT)
    static String encodeMaybeNativeFlowName(String nativeFlowName) {
        return nativeFlowName;
    }

    /**
     * Maps an {@code edit} stanza-attribute string to the WAM
     * {@link EditType} enum constant. {@code "7"} maps to
     * {@link EditType#SENDER_REVOKE}, {@code "8"} to
     * {@link EditType#ADMIN_REVOKE}, {@code "1"} to {@link EditType#EDITED},
     * and every other value (including {@code null}) falls through to
     * {@link EditType#NOT_EDITED}.
     *
     * @param editAttr the {@code edit} stanza attribute value, or {@code null}
     * @return the matching {@link EditType} constant
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getMetricEditType",
            adaptation = WhatsAppAdaptation.DIRECT)
    static EditType getMetricEditType(String editAttr) {
        if (editAttr == null) {
            return EditType.NOT_EDITED;
        }
        return switch (editAttr) {
            case "7" -> EditType.SENDER_REVOKE;
            case "8" -> EditType.ADMIN_REVOKE;
            case "1" -> EditType.EDITED;
            default -> EditType.NOT_EDITED;
        };
    }

    /**
     * Returns the {@link PlaceholderReasonType} the receiver should record for
     * the given inbound-decryption failure, or {@code null} when no placeholder
     * should be inserted.
     *
     * <p>{@code UnknownDevice} maps to
     * {@link PlaceholderReasonType#UNKNOWN_COMPANION_NO_PREKEY}; the
     * {@code Receive}-family Signal exceptions map to the matching
     * {@code SIGNAL_*} reason; anything outside the Signal-decryption family
     * returns {@code null}, matching WA Web's implicit {@code undefined}
     * fall-through which suppresses the placeholder.
     *
     * @param error the exception raised by the inbound decryption pipeline
     * @return the matching reason, or {@code null} when no placeholder applies
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getPlaceholderAddReason",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static PlaceholderReasonType getPlaceholderAddReason(Throwable error) {
        if (error instanceof WhatsAppMessageException.Receive.UnknownDevice) {
            return PlaceholderReasonType.UNKNOWN_COMPANION_NO_PREKEY;
        }
        if (error instanceof WhatsAppMessageException.Receive) {
            return switch (error) {
                case WhatsAppMessageException.Receive.NoSession _,
                     WhatsAppMessageException.Receive.NoSenderKey _ ->
                        PlaceholderReasonType.SIGNAL_NO_SESSION;
                case WhatsAppMessageException.Receive.InvalidMessage _ ->
                        PlaceholderReasonType.SIGNAL_INVALID_MESSAGE;
                case WhatsAppMessageException.Receive.InvalidKey _,
                     WhatsAppMessageException.Receive.InvalidOneTimeKey _,
                     WhatsAppMessageException.Receive.InvalidSignedPreKey _ ->
                        PlaceholderReasonType.SIGNAL_INVALID_KEY;
                case WhatsAppMessageException.Receive.FutureMessage _ ->
                        PlaceholderReasonType.SIGNAL_FUTURE_MESSAGE;
                case WhatsAppMessageException.Receive.BadMac _ ->
                        PlaceholderReasonType.SIGNAL_BAD_MAC;
                default -> PlaceholderReasonType.OTHER;
            };
        }
        return null;
    }

    /**
     * Returns the priority string assigned to non-critical inbound
     * notifications. WA Web uses {@code OFFLINE} while replaying queued
     * traffic and {@code LOW} during normal online operation.
     *
     * @param isOffline {@code true} when the runtime is draining the offline
     *                  backlog
     * @return {@code "OFFLINE"} when offline, otherwise {@code "LOW"}
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getNonCriticalNotificationPriority",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static String getNonCriticalNotificationPriority(boolean isOffline) {
        return isOffline ? "OFFLINE" : "LOW";
    }

    /**
     * Maps the content of the given container to the WAM {@link EditType}
     * classification used on outbound metric events.
     *
     * @param container the outbound message container, possibly {@code null}
     * @return the matching {@link EditType} ({@link EditType#NOT_EDITED} when
     *         the message is not an edit or revoke)
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getMetricEditTypeFromMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static EditType mapEditType(MessageContainer container) {
        if (container == null) {
            return EditType.NOT_EDITED;
        }
        var message = container.content();
        if (message instanceof ProtocolMessage p) {
            var type = p.type().orElse(null);
            if (type == ProtocolMessage.Type.REVOKE) {
                return EditType.SENDER_REVOKE;
            }
            if (type == ProtocolMessage.Type.MESSAGE_EDIT) {
                return EditType.EDITED;
            }
        }
        return EditType.NOT_EDITED;
    }
}
