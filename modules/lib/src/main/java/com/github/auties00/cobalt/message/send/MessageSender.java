package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.exception.WhatsAppCorruptedStoreException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.ack.AckResult;
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
import com.github.auties00.cobalt.model.props.ABProp;
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
 * Sealed base of the per-chat-kind senders.
 *
 * <p>Concrete subclasses ({@link UserMessageSender}, {@link GroupMessageSender},
 * {@link StatusMessageSender}, {@link NewsletterMessageSender},
 * {@link PeerMessageSender}) implement the chat-kind-specific orchestration
 * and reuse this class for the cross-cutting helpers: per-device Signal
 * encryption with ICDC enrichment, the {@code type}/{@code edit}/
 * {@code decrypt-fail}/{@code mediatype}/{@code native_flow_name} attribute
 * resolvers, the {@code <device-identity>} child builder, and the WAM
 * emission helpers that record per-send Signal cipher results.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCommonApi")
@WhatsAppWebModule(moduleName = "WAWebE2EProtoUtils")
@WhatsAppWebModule(moduleName = "WAWebAdvSignatureApi")
@WhatsAppWebModule(moduleName = "WAWebBackendJobsCommon")
abstract sealed class MessageSender<T extends MessageInfo> permits UserMessageSender, GroupMessageSender, StatusMessageSender, BroadcastMessageSender, NewsletterMessageSender, PeerMessageSender {
    /**
     * The {@link System.Logger} used to surface per-device encryption
     * failures from {@link #encryptForDevices}.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageSender.class.getName());

    /**
     * The {@link WhatsAppClient} used to dispatch wire stanzas and to surface
     * fatal store-persistence failures to the embedder's error handler.
     */
    final WhatsAppClient client;

    /**
     * The {@link WhatsAppStore} carrying Signal sessions, device-list state,
     * identity records, sender-key distribution flags, chat metadata, and
     * receipt records consulted by every subclass.
     */
    final WhatsAppStore store;

    /**
     * The {@link ABPropsService} consulted by the base class for the resend
     * timeout AB prop and by subclasses for feature-gating decisions.
     */
    final ABPropsService abPropsService;

    /**
     * The {@link WamService} used to commit per-send WAM events.
     */
    final WamService wamService;

    /**
     * Constructs a {@link MessageSender} bound to the supplied dependencies.
     *
     * @apiNote
     * Invoked only by the sealed subclasses; the package-private visibility
     * is intentional.
     *
     * @param client         the {@link WhatsAppClient} used to dispatch
     *                       stanzas and surface failures
     * @param abPropsService the {@link ABPropsService} consulted for
     *                       feature-gating decisions
     * @param wamService     the {@link WamService} that records per-send
     *                       events
     * @throws NullPointerException if any argument is {@code null}
     */
    MessageSender(WhatsAppClient client, ABPropsService abPropsService, WamService wamService) {
        this.client = Objects.requireNonNull(client, "client");
        this.store = client.store();
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.wamService = Objects.requireNonNull(wamService, "wamService");
    }

    /**
     * Dispatches the supplied {@link MessageInfo} to the given chat or
     * audience JID.
     *
     * @implSpec
     * Each subclass implements the chat-kind-specific stanza shape and
     * encryption flow; the dispatch from
     * {@link MessageSendingService#send(MessageInfo)} routes by JID server.
     *
     * @param chatJid     the target chat, group, status, newsletter, or
     *                    peer-device JID
     * @param messageInfo the prepared message info
     * @return the parsed server {@link AckResult}
     */
    abstract AckResult send(Jid chatJid, T messageInfo);

    /**
     * Blocks the current virtual thread until the offline-message backlog has
     * been fully replayed.
     *
     * @apiNote
     * Every subclass calls this before composing the outbound stanza so that
     * sends issued mid-reconnect are emitted only after the local store has
     * caught up with the queue the server held while the client was offline.
     */
    @WhatsAppWebExport(moduleName = "WAWebEventsWaitForOfflineDeliveryEnd", exports = "waitForOfflineDeliveryEnd",
            adaptation = WhatsAppAdaptation.DIRECT)
    void waitForOfflineDelivery() {
        store.waitForOfflineDeliveryEnd();
    }

    /**
     * Returns the maximum age, in seconds, a previously-sent message may
     * still be resent at after a server-driven retry signal.
     *
     * @apiNote
     * Backed by the {@code WEB_E2E_BACKFILL_EXPIRE_TIME} AB prop (a minute
     * value); the resend pipeline skips messages older than this threshold
     * to avoid replaying stale backfill traffic.
     *
     * @implNote
     * This implementation falls back to {@code 5} minutes when the AB prop
     * is missing or non-positive, mirroring WA Web's default literal in
     * {@code getResendTimeoutInSeconds}.
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
     * Flushes the {@link WhatsAppStore} to its persistent backing so the
     * Signal session ratchets and pre-key updates produced by the encryption
     * step survive a process crash that follows immediately after the wire
     * write.
     *
     * @apiNote
     * Called by every subclass after building the stanza and before invoking
     * {@link WhatsAppClient#sendNode(Node)} or
     * {@link WhatsAppClient#sendNode(NodeBuilder)}; a persistence failure is
     * routed through the client's
     * {@link WhatsAppClient#handleFailure(Throwable) error handler} as a
     * {@link WhatsAppCorruptedStoreException}.
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
     * Returns the {@link Jid} of the currently-paired device or fails fast.
     *
     * @apiNote
     * Use whenever a send path needs the local PN JID and treats a missing
     * pairing as a programming error rather than a recoverable miss.
     *
     * @return the self PN {@link Jid}
     * @throws IllegalStateException if the client is not logged in
     */
    Jid requireSelfJid() {
        return store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
    }

    /**
     * Returns the local LID JID, or the PN JID when no LID is paired.
     *
     * @apiNote
     * Used by the group, status, and CAG paths to pick the addressing-mode
     * sender JID; CAG and LID-addressed groups need the LID, but legacy PN
     * groups still address the sender by PN.
     *
     * @return the self LID JID, or the PN JID when no LID is paired
     */
    Jid selfLidOrPn() {
        return store.lid().orElseGet(this::requireSelfJid);
    }

    /**
     * Encrypts {@code container} for each device in {@code devices} and
     * returns the per-device payloads, populating ICDC metadata before
     * serialisation.
     *
     * @apiNote
     * Companion devices receive a {@code DeviceSentMessage}-wrapped copy
     * carrying only the sender ICDC; non-self recipient devices receive both
     * the sender and recipient ICDC. Devices whose encryption raises any
     * exception are logged and dropped from the result; the receipts
     * recorded against them via {@link WhatsAppStore#updateIdentityRange}
     * still cover the full input list so identity ranges stay aligned with
     * the dispatched fanout.
     *
     * @param encryption     the {@link MessageEncryption} service to use
     * @param devices        the device {@link Jid}s to encrypt for
     * @param container      the source {@link MessageContainer}
     * @param destinationJid the chat recipient JID, written into the
     *                       {@code DeviceSentMessage} wrapper sent to self
     *                       devices
     * @param senderIcdc     the sender's ICDC, or {@code null}
     * @param recipientIcdc  the recipient's ICDC, or {@code null}
     * @return the encrypted payloads, possibly shorter than {@code devices}
     *         when per-device encryption failed
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
     * Returns the wire-level {@code type} attribute value for the given
     * {@link MessageContainer}.
     *
     * @apiNote
     * The result is one of {@code "text"}, {@code "media"},
     * {@code "reaction"}, {@code "poll"}, or {@code "event"} and is stamped
     * onto the outer {@code <message type="...">} attribute by every chat
     * fanout stanza builder. The classification mirrors WA Web's
     * {@code typeAttributeFromProtobuf} so receivers parse the wire shape
     * identically.
     *
     * @param container the outbound {@link MessageContainer}
     * @return the {@code type} attribute value; never {@code null}
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
     * Returns the wire {@code edit} attribute value for {@code container},
     * defaulting to the non-admin revoke classification.
     *
     * @apiNote
     * Shortcut for {@link #resolveEditAttribute(MessageContainer, boolean)}
     * called with {@code isAdminRevoke=false}; used by every send path other
     * than the group admin-revoke branch.
     *
     * @param container the outbound {@link MessageContainer}
     * @return the {@code edit} value, or {@code null} when no attribute
     *         should be written
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "editAttribute",
            adaptation = WhatsAppAdaptation.DIRECT)
    String resolveEditAttribute(MessageContainer container) {
        return resolveEditAttribute(container, false);
    }

    /**
     * Returns the wire {@code edit} attribute value for {@code container},
     * distinguishing the admin-revoke variant.
     *
     * @apiNote
     * Mirrors WA Web's {@code editAttribute} routing: {@code "7"} for sender
     * revoke (and undo-keep-for-all and reaction-clear), {@code "8"} for
     * admin revoke, {@code "1"} for protobuf and secret message-edit, and
     * {@code "2"} for pin-in-chat. Anything else returns {@code null} and the
     * caller drops the attribute.
     *
     * @param container     the outbound {@link MessageContainer}
     * @param isAdminRevoke {@code true} when a group admin is revoking a
     *                      participant's message
     * @return the {@code edit} value, or {@code null} when no attribute
     *         should be written
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
     * Returns the wire {@code decrypt-fail} attribute value for
     * {@code container}.
     *
     * @apiNote
     * {@code "hide"} silences the receiver-side fallback that surfaces a
     * decryption failure as a placeholder bubble; used for reactions,
     * encrypted reactions, poll updates, keep/pin-in-chat addons, encrypted
     * event responses, secret message edits, and the silent protocol
     * subtypes (revoke, message edit, ephemeral sync response, welcome
     * request). Anything else returns {@code null} so the caller drops the
     * attribute.
     *
     * @param container the outbound {@link MessageContainer}
     * @return {@code "hide"} or {@code null}
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
     * Returns the wire {@code mediatype} attribute value written onto the
     * inner {@code <enc>} child for the given {@link MessageContainer}.
     *
     * @apiNote
     * Used by every chat-fanout and group-skmsg stanza builder. The result
     * mirrors WA Web's {@code mediaTypeFromProtobuf} / {@code encodeMaybeMediaType}
     * pair; {@code null} means the attribute is dropped (non-media payload).
     *
     * @param container the outbound {@link MessageContainer}
     * @return the {@code mediatype} value, or {@code null} for non-media
     *         payloads
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
     * Returns the wire {@code native_flow_name} attribute value written onto
     * the inner {@code <enc>} child for interactive-response payloads.
     *
     * @apiNote
     * The native flow name routes the response on the bot backend; only
     * {@link InteractiveResponseMessage} payloads with an inner
     * {@link InteractiveResponseMessage.NativeFlowResponseMessage} carry it.
     *
     * @param container the outbound {@link MessageContainer}
     * @return the native flow name, or {@code null} when the payload is not
     *         a native-flow response
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
     * Builds the {@code <device-identity>} child carrying this device's
     * stored ADV-signed identity, or {@code null} when no identity is
     * available.
     *
     * @apiNote
     * Recipients need the ADV-signed identity to verify a PKMSG envelope on
     * first contact; subclasses emit the child only when at least one
     * per-device payload is PKMSG.
     *
     * @return the {@code <device-identity>} {@link Node}, or {@code null}
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
     * undo-keep-for-all by the original sender.
     *
     * @apiNote
     * Drives the {@link #resolveEditAttribute(MessageContainer, boolean)}
     * branch that maps undo-keep-for-all to the sender-revoke value
     * ({@code "7"}); the operation is allowed only on messages the caller
     * originally sent.
     *
     * @param keep the keep-in-chat payload
     * @return {@code true} when {@code keep} is a fromMe undo-keep-for-all
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "editAttribute",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isUndoKeepForAll(KeepInChatMessage keep) {
        return keep.key().isPresent()
                && keep.key().get().fromMe()
                && keep.keepType().orElse(null) == ChatKeepType.UNDO_KEEP_FOR_ALL;
    }

    /**
     * Commits the {@code E2eMessageSendEvent} (event id 476) for a single
     * per-device Signal encryption result.
     *
     * @apiNote
     * Called from inside the per-device loop in
     * {@link #encryptForDevices(MessageEncryption, Collection, MessageContainer, Jid, IcdcResult, IcdcResult)}
     * and from {@link PeerMessageSender#send(Jid, com.github.auties00.cobalt.model.chat.ChatMessageInfo)}.
     *
     * @implNote
     * This implementation collapses WA Web's
     * {@code postSuccessDirectE2eMessageSendMetric} /
     * {@code postFailureDirectE2eMessageSendMetric} helpers into one
     * emission point parameterised by {@code success}, with the per-branch
     * field population (hosted COEX flag, agent-engagement flag) handled
     * inline.
     *
     * @param device         the recipient device {@link Jid}
     * @param container      the encrypted {@link MessageContainer}, or
     *                       {@code null} when the encryption carried no
     *                       user-visible payload
     * @param success        {@code true} for a successful encryption
     * @param ciphertextType the resolved Signal ciphertext type on success,
     *                       or {@code null} on failure
     * @param retryCount     the retry count passed through from the sender;
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
     * Commits the {@code E2eMessageSendEvent} (event id 476) for a sender-key
     * (SKMSG) encryption result covering an entire group or status fanout.
     *
     * @apiNote
     * Called once per group send and once per status broadcast; the
     * destination ({@link E2eDestination#GROUP} or
     * {@link E2eDestination#STATUS}) and the addressing mode are reflected
     * on the emitted event.
     *
     * @param groupOrStatusJid    the SKMSG target {@link Jid}
     * @param container           the {@link MessageContainer} being encrypted
     * @param destination         the semantic destination
     *                            ({@link E2eDestination#GROUP} or
     *                            {@link E2eDestination#STATUS})
     * @param isLidAddressingMode whether the fanout uses LID addressing
     * @param success             {@code true} for a successful encryption
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
     * @apiNote
     * Used by {@link #emitE2eMessageSendEvent} to populate the
     * {@code e2eCiphertextType} slot on per-device events.
     *
     * @param type the Signal ciphertext type
     * @return the matching {@link E2eCiphertextType}
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
     * Maps the content of the given {@link MessageContainer} to the matching
     * WAM {@link MediaType}.
     *
     * @apiNote
     * Used to populate the {@code messageMediaType} slot on the WAM
     * {@code E2eMessageSendEvent}; non-classifiable payloads return
     * {@code null} so the field is omitted.
     *
     * @param container the outbound {@link MessageContainer}
     * @return the matching {@link MediaType}, or {@code null}
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
     * Encodes the protobuf decrypt-fail flag into the wire
     * {@code decrypt-fail} attribute value.
     *
     * @apiNote
     * Returns {@code "hide"} when the message wants the failure suppressed
     * on the receiver and {@code null} otherwise (the caller drops the
     * attribute). A {@code null} input is treated as the no-attribute case.
     *
     * @param hide the protobuf decrypt-fail flag, or {@code null}
     * @return {@code "hide"} or {@code null}
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
     * Echoes the supplied native-flow-name string for the wire
     * {@code native_flow_name} attribute.
     *
     * @apiNote
     * A pass-through helper preserved for parity with WA Web's
     * {@code encodeMaybeNativeFlowName}; the caller drops the attribute when
     * the value is {@code null}.
     *
     * @param nativeFlowName the resolved native flow name, or {@code null}
     * @return the same value
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "encodeMaybeNativeFlowName",
            adaptation = WhatsAppAdaptation.DIRECT)
    static String encodeMaybeNativeFlowName(String nativeFlowName) {
        return nativeFlowName;
    }

    /**
     * Maps the wire {@code edit} attribute value to the WAM {@link EditType}
     * classification.
     *
     * @apiNote
     * The mapping mirrors WA Web's {@code getMetricEditType}: {@code "7"} is
     * {@link EditType#SENDER_REVOKE}, {@code "8"} is
     * {@link EditType#ADMIN_REVOKE}, {@code "1"} is {@link EditType#EDITED},
     * everything else (including {@code null}) is
     * {@link EditType#NOT_EDITED}.
     *
     * @param editAttr the {@code edit} attribute value, or {@code null}
     * @return the matching {@link EditType}; never {@code null}
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
     * Returns the placeholder reason the receiver should record for the
     * given decryption error.
     *
     * @apiNote
     * Called by the inbound pipeline when an opaque ciphertext fails to
     * decrypt; the returned {@link PlaceholderReasonType} drives the
     * downstream retry-or-give-up classification. A {@code null} return
     * preserves WA Web's implicit {@code undefined} fall-through which
     * skips placeholder insertion entirely.
     *
     * @implNote
     * This implementation extends the WA Web table with a dedicated branch
     * for {@code UnknownDevice}; everything inside the Signal
     * {@code Receive}-family hierarchy maps to the matching {@code SIGNAL_*}
     * constant, and unrelated errors fall through to {@code null}.
     *
     * @param error the exception raised by inbound decryption
     * @return the matching {@link PlaceholderReasonType}, or {@code null}
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
     * Returns the {@code push_priority} value assigned to non-critical
     * inbound notifications.
     *
     * @apiNote
     * WA Web's {@code getNonCriticalNotificationPriority} returns
     * {@code "OFFLINE"} while the runtime is replaying queued traffic on
     * reconnect and {@code "LOW"} during normal online operation; the same
     * mapping is needed for retransmission scheduling.
     *
     * @param isOffline {@code true} when the runtime is draining the offline
     *                  backlog
     * @return {@code "OFFLINE"} or {@code "LOW"}
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "getNonCriticalNotificationPriority",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static String getNonCriticalNotificationPriority(boolean isOffline) {
        return isOffline ? "OFFLINE" : "LOW";
    }

    /**
     * Maps the given {@link MessageContainer} to the WAM {@link EditType}
     * classification used on outbound metric events.
     *
     * @apiNote
     * Used by {@link #emitE2eMessageSendSenderKeyEvent}; populates the
     * {@code editType} slot on SKMSG events without requiring callers to
     * round-trip through the wire {@code edit} string.
     *
     * @param container the outbound {@link MessageContainer}, possibly
     *                  {@code null}
     * @return the matching {@link EditType}; never {@code null}
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
