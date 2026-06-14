package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.ack.AckParser;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.ack.MessageAck;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.business.profile.BusinessAutomatedType;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageThreadId;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdDeviceSyncAckEventBuilder;
import com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder;
import com.github.auties00.cobalt.wam.type.MessageChatType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.PrekeysFetchContext;
import com.github.auties00.cobalt.wam.type.SizeBucket;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sends messages to 1:1 user chats addressed by either PN or LID.
 *
 * <p>Each send resolves the recipient's device fanout, optionally rewrites the
 * wid to the account LID (PN-less stanza migration), encrypts the protobuf for
 * every device, builds the {@link ChatFanoutStanza}, dispatches it, and reacts
 * to the server ack by triggering a LID refresh or a phash-mismatch resend to
 * the delta devices when needed.
 */
@WhatsAppWebModule(moduleName = "WAWebSendUserMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgToDeviceList")
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
final class UserMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Surfaces user-send diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(UserMessageSender.class.getName());

    /**
     * Flags a phone-number-hidden click-to-WhatsApp chat as originating from the
     * CTWA ad funnel.
     *
     * <p>{@link #resolvePeerRecipientPn(Jid)} and {@link #resolveRecipientPn(Jid)}
     * use this value to gate the inclusion of the recipient's phone number on
     * outbound stanzas; PNH-CTWA chats intentionally omit it.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * Performs per-device Signal encryption.
     */
    private final MessageEncryption encryption;

    /**
     * Resolves the device fanout and manages Signal sessions.
     */
    private final DeviceService deviceService;

    /**
     * Decides whether the outgoing chat JID should be rewritten to its account
     * LID, consulted by {@link #maybeReplaceWidWithAccountLid(Jid)}.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Builds the {@code <bot>} child.
     */
    private final BotStanza botStanza;

    /**
     * Builds the {@code <biz>} child.
     */
    private final BizStanza bizStanza;

    /**
     * Builds the {@code <meta>} child.
     */
    private final MetaStanza metaStanza;

    /**
     * Builds the {@code <reporting>} child.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Builds the click-to-WhatsApp attribution child.
     */
    private final CtwaAttributionStanza ctwaStanza;

    /**
     * Builds the trusted-contact token child.
     */
    private final TcTokenStanza tcTokenStanza;

    /**
     * Constructs a {@link UserMessageSender} bound to the supplied dependencies.
     *
     * <p>Constructed once by {@link MessageSendingService}; embedders should not
     * instantiate directly.
     *
     * @param client              the {@link LinkedWhatsAppClient} used to dispatch
     *                            stanzas
     * @param encryption          the {@link MessageEncryption} service
     * @param deviceService       the {@link DeviceService}
     * @param lidMigrationService the {@link LidMigrationService} consulted by
     *                            {@link #maybeReplaceWidWithAccountLid(Jid)}
     * @param abPropsService      the {@link ABPropsService} consulted by the base
     *                            sender and the recipient-username branch
     * @param botStanza           the {@link BotStanza} builder
     * @param bizStanza           the {@link BizStanza} builder
     * @param metaStanza          the {@link MetaStanza} builder
     * @param reportingStanza     the {@link ReportingStanza} builder
     * @param ctwaStanza          the {@link CtwaAttributionStanza} builder
     * @param tcTokenStanza       the {@link TcTokenStanza} builder
     * @param wamService          the {@link WamService} shared with the base
     *                            sender
     * @throws NullPointerException if any non-base argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "encryptAndSendUserMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    UserMessageSender(
            LinkedWhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            BotStanza botStanza,
            BizStanza bizStanza,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza,
            CtwaAttributionStanza ctwaStanza,
            TcTokenStanza tcTokenStanza,
            WamService wamService
    ) {
        super(client, abPropsService, wamService);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService");
        this.botStanza = Objects.requireNonNull(botStanza, "botStanza");
        this.bizStanza = Objects.requireNonNull(bizStanza, "bizStanza");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
        this.ctwaStanza = Objects.requireNonNull(ctwaStanza, "ctwaStanza");
        this.tcTokenStanza = Objects.requireNonNull(tcTokenStanza, "tcTokenStanza");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the device fanout, dispatches the encrypted stanza, then
     * reacts to the server ack by triggering a LID refresh (when the server sets
     * {@code refresh_lid="true"}) and/or a phash-mismatch resend to the delta
     * devices.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "encryptAndSendUserMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult doSend(Jid chatJid, ChatMessageInfo messageInfo) {
        var routedChatJid = maybeReplaceWidWithAccountLid(chatJid);

        var fanoutDevices = deviceService.getUserFanout(routedChatJid, null);

        store.createOrMergeReceiptRecords(messageInfo.key().id().orElseThrow(), fanoutDevices);

        var ack = encryptBuildAndSend(routedChatJid, messageInfo, fanoutDevices, false);

        maybeRefreshLid(routedChatJid, ack);

        if (ack instanceof MessageAck messageAck) {
            messageAck.phash().ifPresent(serverPhash ->
                    handlePhashMismatch(routedChatJid, messageInfo, fanoutDevices, serverPhash));
        }

        return ack;
    }

    /**
     * Rewrites a bare regular-user PN to its associated LID for the wire stanza
     * when the PN-less stanza migration is active for this account.
     *
     * <p>For self-send the LID is taken from the local store; for other regular
     * users it is read from the chat record's {@code accountLid} field. When no
     * LID is known the original PN is returned and the downstream pipeline keeps
     * routing by PN.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code shouldConvertToLid} gate in
     * {@code WAWebPnlessStanzaMigration}: conversion happens only when
     * {@link LidMigrationService#isLidMigrated()} is {@code true}, the
     * {@link ABProp#WEB_PNLESS_STANZAS} AB prop is enabled, the JID represents a
     * regular user PN, and the JID has no device suffix.
     *
     * @param chatJid the chat {@link Jid} supplied by the caller
     * @return the rewritten LID, or the original PN when no rewrite applies
     */
    @WhatsAppWebExport(moduleName = "WAWebPnlessStanzaMigration",
            exports = "maybeReplaceWidWithAccountLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid maybeReplaceWidWithAccountLid(Jid chatJid) {
        if (!shouldConvertToLid(chatJid)) {
            return chatJid;
        }

        var selfPn = store.accountStore().jid().map(Jid::toUserJid).orElse(null);
        if (selfPn != null && chatJid.toUserJid().equals(selfPn)) {
            return store.accountStore().lid().map(Jid::toUserJid).orElse(chatJid);
        }

        return store.chatStore().findChatByJid(chatJid)
                .flatMap(Chat::accountLid)
                .orElse(chatJid);
    }

    /**
     * Returns whether the supplied chat {@link Jid} is eligible for the
     * PN-to-LID rewrite performed by {@link #maybeReplaceWidWithAccountLid(Jid)}.
     *
     * <p>All four conditions of WA Web's {@code shouldConvertToLid} must hold:
     * the account-wide 1:1 LID migration must have completed, the
     * {@link ABProp#WEB_PNLESS_STANZAS} AB prop must be enabled, the JID must be
     * a regular-user PN (not bot, not LID-server, not the PSA announcements
     * account), and the JID must carry no device suffix (device {@code 0}).
     *
     * @param chatJid the chat {@link Jid} to evaluate, may be {@code null}
     * @return {@code true} when the rewrite should proceed
     */
    @WhatsAppWebExport(moduleName = "WAWebPnlessStanzaMigration",
            exports = "maybeReplaceWidWithAccountLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean shouldConvertToLid(Jid chatJid) {
        if (chatJid == null) {
            return false;
        }
        if (!lidMigrationService.isLidMigrated()) {
            return false;
        }
        if (!abPropsService.getBool(ABProp.WEB_PNLESS_STANZAS)) {
            return false;
        }
        var isRegularUserPn = chatJid.hasUserServer()
                && !chatJid.equals(Jid.announcementsAccount())
                && !chatJid.hasBotServer()
                && !chatJid.hasLidServer();
        if (!isRegularUserPn) {
            return false;
        }
        return chatJid.device() == 0;
    }

    /**
     * Triggers a device-list sync when the server requested a LID refresh on the
     * ack.
     *
     * <p>The server sets {@code refresh_lid="true"} when its LID-to-PN mapping
     * for the recipient diverges from the client's.
     *
     * @implNote
     * This implementation reuses the regular device-list-sync entry point
     * {@link DeviceService#getDeviceLists(Collection, String, String, boolean)}
     * rather than calling WA Web's standalone {@code syncContactListJob}; the two
     * converge on the same wire query.
     *
     * @param chatJid the target chat {@link Jid}
     * @param ack     the server {@link AckResult}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendUserMsgJob", exports = "maybeRefreshLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void maybeRefreshLid(Jid chatJid, AckResult ack) {
        if (!(ack instanceof MessageAck messageAck) || !messageAck.refreshLid()) {
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Server requested LID refresh for {0}", chatJid);

        var pnJid = chatJid.hasLidServer()
                ? store.contactStore().findPhoneByLid(chatJid.toUserJid()).orElse(null)
                : null;
        if (pnJid == null) {
            return;
        }

        deviceService.getDeviceLists(List.of(pnJid), "message", null, false);
    }

    /**
     * Encrypts the message for every supplied device, builds and dispatches the
     * chat fanout stanza, and parses the server ack.
     *
     * <p>Used both for the initial send and the phash-mismatch resend; the
     * {@code isResend} flag controls the {@code device_fanout="false"} attribute
     * on the outer message.
     *
     * @param chatJid     the target chat {@link Jid}
     * @param messageInfo the outgoing {@link ChatMessageInfo}
     * @param devices     the device {@link Jid}s to encrypt for
     * @param isResend    {@code true} when this is a phash-mismatch resend
     * @return the parsed {@link AckResult}
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails for every
     *                                               device or the ack carries an
     *                                               error
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgToDeviceList", exports = "sendMsgToDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    private AckResult encryptBuildAndSend(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        var depletedPrekeyCount = deviceService.ensureSessions(devices);
        emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.INDIVIDUAL, null);
        var senderIcdc = deviceService.computeIcdc(requireSelfJid())
                .orElse(null);
        var recipientIcdc = deviceService.computeIcdc(chatJid)
                .orElse(null);
        var payloads = encryptForDevices(encryption, devices, container, chatJid, senderIcdc, recipientIcdc);
        if (payloads.isEmpty()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Encryption failed for all devices targeting " + chatJid, null);
        }

        var stanza = buildStanza(chatJid, messageInfo, payloads, devices, isResend);
        flushStore();
        var ackNode = client.sendNode(stanza);
        var ack = AckParser.parse(ackNode);

        if (ack.error().isPresent()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Invalid ack from server (error " + ack.error().getAsInt() + ") for " + chatJid, null);
        }

        return ack;
    }

    /**
     * Builds the complete chat fanout stanza.
     *
     * <p>Resolves every attribute and child node ({@code type}, {@code edit},
     * {@code mediatype}, {@code decrypt-fail}, {@code native_flow_name},
     * peer-recipient hints, identity, meta, biz, bot, reporting,
     * sender-content-binding, bot-metadata, trusted-contact token, CTWA
     * attribution) into one composite {@link NodeBuilder}.
     *
     * @param chatJid     the target chat {@link Jid}
     * @param messageInfo the outgoing {@link ChatMessageInfo}
     * @param payloads    the per-device encrypted payloads
     * @param devices     the device {@link Jid}s used for identity-node
     *                    resolution
     * @param isResend    {@code true} when this is a phash-mismatch resend
     * @return the {@code <message>} {@link NodeBuilder}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildStanza(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            List<MessageEncryptedPayload> payloads,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        var anyPkmsg = ParticipantsStanza.requiresIdentityNode(payloads);
        var identityNode = anyPkmsg ? buildIdentityNode() : null;

        var metaNode = metaStanza.buildChat(chatJid, container, null);
        var bizNode = bizStanza.build(chatJid);
        var botNode = botStanza.build(messageInfo, chatJid);
        var reportingNode = reportingStanza.build(messageInfo, requireSelfJid(), chatJid);
        var senderContentBinding = SenderContentBindingStanza.buildForUser(messageInfo, requireSelfJid());
        var ctwaNode = ctwaStanza.build(chatJid);

        var botMetadataNode = resolveBotMetadataNode(chatJid, messageInfo);

        return ChatFanoutStanza.build(
                messageInfo.key().id().orElseThrow(),
                chatJid,
                resolveStanzaType(container),
                payloads,
                resolveEditAttribute(container),
                null,
                (isResend || anyPkmsg) ? "false" : null,
                resolveMediaType(container),
                resolveDecryptFail(container),
                resolveNativeFlowName(container),
                null,
                false,
                resolvePeerRecipientLid(chatJid),
                resolvePeerRecipientPn(chatJid),
                resolveRecipientPn(chatJid),
                resolvePeerRecipientUsername(chatJid),
                identityNode,
                metaNode,
                bizNode,
                botNode,
                reportingNode,
                senderContentBinding,
                botMetadataNode,
                tcTokenStanza.build(chatJid),
                ctwaNode,
                null
        );
    }

    /**
     * Returns the {@code peer_recipient_lid} attribute value for a PN-addressed
     * chat.
     *
     * <p>Echoes the recipient's LID alongside the PN-addressed stanza so the
     * server can route on either identifier; only applicable to user-PN chats
     * whose {@link Chat#accountLid()} is known.
     *
     * @param chatJid the target chat {@link Jid}
     * @return the peer recipient LID, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolvePeerRecipientLid(Jid chatJid) {
        if (!chatJid.hasUserServer()) {
            return null;
        }

        return store.chatStore().findChatByJid(chatJid)
                .flatMap(Chat::accountLid)
                .orElse(null);
    }

    /**
     * Returns the {@code peer_recipient_pn} attribute value for a LID-addressed
     * chat.
     *
     * <p>Mirrors the LID-to-PN mapping back into the stanza so the server can
     * route on the legacy PN. Omitted when the 1:1 LID migration has not
     * completed yet, for non-LID chats, and for LID chats whose
     * {@link Chat#lidOriginType()} matches {@link #LID_ORIGIN_TYPE_PNH_CTWA},
     * since those chats intentionally hide the recipient's PN.
     *
     * @param chatJid the target chat {@link Jid}
     * @return the peer recipient PN, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolvePeerRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        if (!lidMigrationService.isLidMigrated()) {
            return null;
        }

        var lidOriginType = store.chatStore().findChatByJid(chatJid)
                .flatMap(Chat::lidOriginType)
                .orElse(null);
        if (LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        return store.contactStore().findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Returns the {@code recipient_pn} attribute value for a LID-addressed chat.
     *
     * <p>Only included when the chat's {@link Chat#lidOriginType()} is absent or
     * matches {@link #LID_ORIGIN_TYPE_PNH_CTWA}, the {@link Contact} has not
     * opted in to share their phone number, and a LID-to-PN mapping is
     * available; otherwise the field is omitted to honour the PN-hiding
     * contract.
     *
     * @param chatJid the target chat {@link Jid}
     * @return the recipient PN, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid resolveRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var chat = store.chatStore().findChatByJid(chatJid).orElse(null);
        var lidOriginType = chat != null ? chat.lidOriginType().orElse(null) : null;
        if (lidOriginType != null && !LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        var contact = store.contactStore().findContactByJid(chatJid).orElse(null);
        if (contact != null && contact.isPhoneNumberShared()) {
            return null;
        }

        return store.contactStore().findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Returns the {@code peer_recipient_username} attribute value for a
     * LID-addressed chat.
     *
     * <p>Only emitted when the {@link ABProp#USERNAME_CONTACT_DISPLAY} AB prop is
     * enabled and a {@link Contact#username()} is available; the attribute lets
     * the recipient's client display the sender by username even when the sender
     * keeps the PN hidden.
     *
     * @param chatJid the target chat {@link Jid}
     * @return the peer recipient username, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private String resolvePeerRecipientUsername(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var usernameEnabled = abPropsService.getBool(ABProp.USERNAME_CONTACT_DISPLAY);
        if (!usernameEnabled) {
            return null;
        }

        return store.contactStore().findContactByJid(chatJid)
                .flatMap(Contact::username)
                .orElse(null);
    }

    /**
     * Resolves the metadata-only {@code <bot>} child carried on the stanza.
     *
     * <p>Drives the bot routing on the server side; combines three sources: the
     * protobuf body kind (request-welcome vs prompt vs command), the AI-thread id
     * from {@link ChatMessageContextInfo#threadId()}, and the business profile's
     * {@link BusinessProfile#automatedType()}. Returns {@code null} when no
     * combination applies.
     *
     * @param chatJid     the target chat {@link Jid}
     * @param messageInfo the outgoing {@link ChatMessageInfo}
     * @return the bot metadata {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node resolveBotMetadataNode(Jid chatJid, ChatMessageInfo messageInfo) {
        var container = messageInfo.message();
        var content = container.content();

        String botMsgBodyType = null;
        if (content instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.REQUEST_WELCOME_MESSAGE) {
            botMsgBodyType = "request_welcome";
        } else if (chatJid.hasBotServer() || container.futureProofContentType() == FutureProofMessageType.BOT_INVOKE) {
            botMsgBodyType = resolveBotMsgBodyType(chatJid, content);
        }

        var clientThreadId = container.messageContextInfo()
                .map(ChatMessageContextInfo::threadId)
                .stream()
                .flatMap(Collection::stream)
                .filter(thread -> thread.threadType().orElse(null) == MessageThreadId.ThreadType.AI_THREAD)
                .findFirst()
                .flatMap(MessageThreadId::threadKey)
                .flatMap(MessageKey::id)
                .filter(id -> !id.isEmpty())
                .orElse(null);

        var bizBotType = client.queryBusinessProfile(chatJid)
                .flatMap(BusinessProfile::automatedType)
                .map(BusinessAutomatedType::value)
                .orElse(null);

        return BotStanza.buildMetadata(botMsgBodyType, bizBotType, clientThreadId);
    }

    /**
     * Returns the bot message-body type for the supplied chat and content.
     *
     * <p>Returns {@code "command"} when the message body matches a registered
     * command on the bot's profile, otherwise {@code "prompt"}. The
     * classification drives the bot backend's routing between prompt-style
     * generations and registered command handlers.
     *
     * @param botJid  the bot {@link Jid}
     * @param content the inner {@link Message} content
     * @return the bot message body type
     */
    @WhatsAppWebExport(moduleName = "WAWebBotCommandFormatMutator", exports = "formatBotCommand",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private String resolveBotMsgBodyType(Jid botJid, Message content) {
        if (!(content instanceof ExtendedTextMessage textMessage) || textMessage.text().isEmpty()) {
            return "prompt";
        } else {
            var isCommand = client.queryBotProfile(botJid)
                    .map(profile -> profile.isCommand(textMessage.text().get()))
                    .orElse(false);
            return isCommand ? "command" : "prompt";
        }
    }

    /**
     * Handles a server-reported phash mismatch by re-resolving the device fanout
     * and resending to the delta devices.
     *
     * <p>Emits the {@code MdDeviceSyncAck} WAM event, refreshes the device fanout
     * via {@link DeviceService#getUserFanout}, and dispatches the resend via
     * {@link #encryptBuildAndSend} with the resend flag set so the wire stanza
     * carries {@code device_fanout="false"}.
     *
     * @param chatJid         the target chat {@link Jid}
     * @param messageInfo     the outgoing {@link ChatMessageInfo}
     * @param originalDevices the device list used for the original send
     * @param serverPhash     the phash returned by the server
     */
    @WhatsAppWebExport(moduleName = "WAWebResendUserMsg", exports = "resendUserMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebPostMdDeviceSyncAckMetric",
            exports = "postMdDeviceSyncAckMetric", adaptation = WhatsAppAdaptation.DIRECT)
    private void handlePhashMismatch(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            String serverPhash
    ) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Phash mismatch for {0}, server phash: {1}", chatJid, serverPhash);

        wamService.commit(new MdDeviceSyncAckEventBuilder()
                .revoke(isRevokeMessage(messageInfo))
                .chatType(chatTypeFromJid(chatJid))
                .isLid(chatJid.hasLidServer())
                .build());

        var refreshedDevices = deviceService.getUserFanout(chatJid, serverPhash);

        var originalJids = originalDevices.stream()
                .map(Jid::toString)
                .collect(Collectors.toUnmodifiableSet());
        var deltaDevices = refreshedDevices.stream()
                .filter(device -> !originalJids.contains(device.toString()))
                .toList();

        if (deltaDevices.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "No new devices after phash resync for {0}", chatJid);
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Resending to {0} new devices for {1}",
                deltaDevices.size(), chatJid);

        encryptBuildAndSend(chatJid, messageInfo, deltaDevices, true);
    }

    /**
     * Returns the WAM {@link MessageChatType} for the given chat {@link Jid}.
     *
     * <p>Used by {@link GroupMessageSender#resendAsGroupDirect} and by
     * {@link #handlePhashMismatch} to populate the chat-type slot on the emitted
     * {@code MdDeviceSyncAck} event. Mirrors WA Web's
     * {@code getMessageChatTypeFromWid} predicate cascade.
     *
     * @implNote
     * This implementation maps a {@code null} input to
     * {@link MessageChatType#OTHER} as a defensive guard; WA Web's predicate
     * cascade would throw on a missing wid.
     *
     * @param jid the chat {@link Jid}, or {@code null}
     * @return the matching {@link MessageChatType}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMessageChatTypeFromWid",
            exports = "getMessageChatTypeFromWid",
            adaptation = WhatsAppAdaptation.DIRECT)
    static MessageChatType chatTypeFromJid(Jid jid) {
        if (jid == null) {
            return MessageChatType.OTHER;
        }
        if (jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer()) {
            return MessageChatType.INDIVIDUAL;
        }
        if (jid.hasGroupOrCommunityServer()) {
            return MessageChatType.GROUP;
        }
        if (jid.hasBroadcastServer()) {
            return MessageChatType.BROADCAST;
        }
        if (jid.isStatusBroadcastAccount()) {
            return MessageChatType.STATUS;
        }
        if (jid.hasNewsletterServer()) {
            return MessageChatType.CHANNEL;
        }
        return MessageChatType.OTHER;
    }

    /**
     * Returns whether the supplied message wraps a
     * {@link ProtocolMessage.Type#REVOKE} payload.
     *
     * <p>Shared classifier used by both this sender and
     * {@link GroupMessageSender} when populating the {@code revoke} slot on the
     * {@code MdDeviceSyncAck} WAM event.
     *
     * @param messageInfo the outgoing {@link ChatMessageInfo}, possibly
     *                    {@code null}
     * @return {@code true} when the payload is a revoke protocol message
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "isRevokeMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    static boolean isRevokeMessage(ChatMessageInfo messageInfo) {
        if (messageInfo == null) {
            return false;
        }
        return messageInfo.message().content() instanceof ProtocolMessage protocol
                && protocol.type().orElse(null) == ProtocolMessage.Type.REVOKE;
    }

    /**
     * Commits one
     * {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEvent} per
     * depleted one-time pre-key reported by the last
     * {@link DeviceService#ensureSessions(java.util.Collection)} call.
     *
     * <p>No-op when {@code depletedPrekeyCount} is not positive. Mirrors WA Web's
     * {@code maybePostPrekeysDepletionMetric}.
     *
     * @param depletedPrekeyCount the number of depleted one-time pre-keys
     * @param messageType         the WAM {@link MessageType} for this send
     * @param deviceCount         the fanout device count for the
     *                            {@code deviceSizeBucket} classification, or
     *                            {@code null} to omit the bucket
     */
    @WhatsAppWebExport(moduleName = "WAWebPostPrekeysDepletionMetric",
            exports = "maybePostPrekeysDepletionMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitPrekeysDepletionEvents(int depletedPrekeyCount, MessageType messageType, Integer deviceCount) {
        if (depletedPrekeyCount <= 0) {
            return;
        }
        var bucket = deviceCount == null ? null : numberToSizeBucket(deviceCount);
        for (var i = 0; i < depletedPrekeyCount; i++) {
            wamService.commit(new PrekeysDepletionEventBuilder()
                    .prekeysFetchReason(PrekeysFetchContext.SEND_MESSAGE)
                    .messageType(messageType)
                    .deviceSizeBucket(bucket)
                    .build());
        }
    }

    /**
     * Maps a fanout device count to the matching {@link SizeBucket} carried by
     * the {@code deviceSizeBucket} WAM property.
     *
     * <p>Buckets are exclusive upper bounds: {@code count=31} returns
     * {@link SizeBucket#LT32}, {@code count=1024} returns
     * {@link SizeBucket#LT1500}, and any {@code count >= 5000} returns
     * {@link SizeBucket#LARGEST_BUCKET}.
     *
     * @param count the device count to classify
     * @return the matching {@link SizeBucket}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamNumberToSizeBucket",
            exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static SizeBucket numberToSizeBucket(int count) {
        if (count < 32) return SizeBucket.LT32;
        if (count < 64) return SizeBucket.LT64;
        if (count < 128) return SizeBucket.LT128;
        if (count < 256) return SizeBucket.LT256;
        if (count < 512) return SizeBucket.LT512;
        if (count < 1024) return SizeBucket.LT1024;
        if (count < 1500) return SizeBucket.LT1500;
        if (count < 2000) return SizeBucket.LT2000;
        if (count < 2500) return SizeBucket.LT2500;
        if (count < 3000) return SizeBucket.LT3000;
        if (count < 3500) return SizeBucket.LT3500;
        if (count < 4000) return SizeBucket.LT4000;
        if (count < 4500) return SizeBucket.LT4500;
        if (count < 5000) return SizeBucket.LT5000;
        return SizeBucket.LARGEST_BUCKET;
    }
}
