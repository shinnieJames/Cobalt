package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.stanza.*;
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
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sends messages to 1:1 user chats (both PN and LID addressed).
 *
 * <p>The send flow:
 * <ol>
 *   <li>Resolve the device fanout list for the recipient and self</li>
 *   <li>Encrypt the protobuf individually for each device</li>
 *   <li>Build the chat fanout stanza via {@link ChatFanoutStanza}</li>
 *   <li>Send the stanza and parse the server ack</li>
 *   <li>Handle phash mismatch by resyncing devices and resending
 *       to the delta device list</li>
 *   <li>Handle {@code refresh_lid} by triggering a contact list sync</li>
 * </ol>
 *
 * @implNote WAWebSendUserMsgJob.encryptAndSendUserMsg: orchestrates the
 * full 1:1 send flow including device list resolution, stanza creation,
 * and post-send phash/refreshLid handling.
 * WAWebSendMsgToDeviceList.sendMsgToDeviceList: sends the stanza and
 * parses the server ack.
 */
final class UserMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Logger for diagnostics.
     *
     * @implNote ADAPTED: WAWebSendUserMsgJob uses {@code WALogger.LOG/WARN/ERROR};
     * Cobalt uses {@link System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(UserMessageSender.class.getName());

    /**
     * LID origin type constant for phone-number-hiding click-to-WhatsApp chats.
     *
     * @implNote WAWebUsernameTypes.LidOriginType.PNH_CTWA: the string value
     * {@code "ctwa"} used when comparing {@code chat.lidOriginType}.
     */
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * The encryption service for per-device message encryption.
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf is a module-level import;
     * Cobalt uses constructor-based DI instead.
     */
    private final MessageEncryption encryption;

    /**
     * The device service for fanout list resolution and session management.
     *
     * @implNote ADAPTED: WAWebDBDeviceListFanout, WAWebManageE2ESessionsJob,
     * WAWebSyncDeviceAdvDeviceListJob are module-level imports;
     * Cobalt uses constructor-based DI instead.
     */
    private final DeviceService deviceService;

    /**
     * The AB props service for feature flag lookups.
     *
     * @implNote ADAPTED: WAWebABProps is a module-level import;
     * Cobalt uses constructor-based DI instead.
     */
    private final ABPropsService abPropsService;

    /**
     * Stanza builder for bot-specific nodes.
     *
     * @implNote ADAPTED: WAWebSendMsgCreateFanoutStanza builds bot nodes
     * inline; Cobalt delegates to {@link BotStanza}.
     */
    private final BotStanza botStanza;

    /**
     * Stanza builder for business-specific nodes.
     *
     * @implNote ADAPTED: WAWebSendMsgCreateFanoutStanza builds biz nodes
     * inline; Cobalt delegates to {@link BizStanza}.
     */
    private final BizStanza bizStanza;

    /**
     * Stanza builder for meta nodes.
     *
     * @implNote ADAPTED: WAWebSendMsgMetaNode is a module-level import;
     * Cobalt delegates to {@link MetaStanza}.
     */
    private final MetaStanza metaStanza;

    /**
     * Stanza builder for reporting nodes.
     *
     * @implNote ADAPTED: WAWebReportingTokenUtils is a module-level import;
     * Cobalt delegates to {@link ReportingStanza}.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Stanza builder for CTWA attribution nodes.
     *
     * @implNote ADAPTED: WAWebSendMsgCtwaAttributionNode is a module-level
     * import; Cobalt delegates to {@link CtwaAttributionStanza}.
     */
    private final CtwaAttributionStanza ctwaStanza;

    /**
     * Stanza builder for trusted contact token nodes.
     *
     * @implNote ADAPTED: WAWebSendMsgCreateFanoutStanza resolves tctoken
     * inline; Cobalt delegates to {@link TcTokenStanza}.
     */
    private final TcTokenStanza tcTokenStanza;

    /**
     * Constructs a new user message sender with the given dependencies.
     *
     * @param client         the WhatsApp client
     * @param encryption     the encryption service
     * @param deviceService  the device service
     * @param abPropsService the AB props service
     * @param botStanza      the bot stanza builder
     * @param bizStanza      the business stanza builder
     * @param metaStanza     the meta stanza builder
     * @param reportingStanza the reporting stanza builder
     * @param ctwaStanza     the CTWA attribution stanza builder
     * @param tcTokenStanza  the trusted contact token stanza builder
     *
     * @implNote ADAPTED: WAWebSendUserMsgJob uses module-level imports;
     * Cobalt uses constructor-based DI instead.
     */
    UserMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            BotStanza botStanza,
            BizStanza bizStanza,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza,
            CtwaAttributionStanza ctwaStanza,
            TcTokenStanza tcTokenStanza
    ) {
        super(client);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.botStanza = Objects.requireNonNull(botStanza, "botStanza");
        this.bizStanza = Objects.requireNonNull(bizStanza, "bizStanza");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
        this.ctwaStanza = Objects.requireNonNull(ctwaStanza, "ctwaStanza");
        this.tcTokenStanza = Objects.requireNonNull(tcTokenStanza, "tcTokenStanza");
    }

    /**
     * Sends a message to a 1:1 user chat.
     *
     * <p>Resolves the device fanout list, encrypts and sends the message,
     * then handles {@code refresh_lid} and phash mismatch in the server ack.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message with its key, container,
     *                    and metadata
     * @return the server ack result
     *
     * @implNote WAWebSendUserMsgJob.encryptAndSendUserMsg: resolves fanout
     * via {@code getFanOutList}, calls {@code sendMsgToDeviceList}, then
     * handles {@code maybeRefreshLid} and phash mismatch resend.
     */
    @Override
    AckResult send(Jid chatJid, ChatMessageInfo messageInfo) {
        // WAWebSendUserMsgJob.encryptAndSendUserMsg: waits for offline delivery
        waitForOfflineDelivery();

        // WAWebSendUserMsgJob.encryptAndSendUserMsg: R = {wids: [v, S]}
        // then yield getFanOutList(R)
        var fanoutDevices = deviceService.getUserFanout(chatJid, null);

        // WAWebSendMsgCreateFanoutStanza: create receipt records for all devices
        store.createOrMergeReceiptRecords(messageInfo.key().id().orElseThrow(), fanoutDevices);

        // WAWebSendUserMsgJob.encryptAndSendUserMsg: yield sendMsgToDeviceList(...)
        var ack = encryptBuildAndSend(chatJid, messageInfo, fanoutDevices, false);

        // WAWebSendUserMsgJob.encryptAndSendUserMsg: f(v, I) — maybeRefreshLid
        maybeRefreshLid(chatJid, ack);

        // WAWebSendUserMsgJob.encryptAndSendUserMsg: if (T != null) — phash mismatch
        ack.phash().ifPresent(serverPhash ->
                handlePhashMismatch(chatJid, messageInfo, fanoutDevices, serverPhash));

        return ack;
    }

    /**
     * Triggers a contact list sync when the server requests a LID refresh.
     *
     * <p>Converts the chat JID from LID to PN, then fires a device list
     * sync for the PN JID. This is the Cobalt adaptation of the WA Web
     * contact sync job, since Cobalt does not have a dedicated contact
     * sync API.
     *
     * @param chatJid the target chat JID
     * @param ack     the server ack result
     *
     * @implNote WAWebSendUserMsgJob.maybeRefreshLid: if
     * {@code ack.refreshLid}, converts {@code chatJid} to PN via
     * {@code WAWebLidMigrationUtils.toPn(chatJid)} then calls
     * {@code syncContactListJob({contactIds: [pn], shouldSyncDevice: false, mode: "query"})}.
     * ADAPTED: Cobalt uses a device list sync as a stand-in for the
     * contact sync job, since both trigger a USync request that updates
     * LID-to-PN mappings.
     */
    private void maybeRefreshLid(Jid chatJid, AckResult ack) {
        if (!ack.refreshLid()) {
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Server requested LID refresh for {0}", chatJid);

        // WAWebSendUserMsgJob.maybeRefreshLid: var r = toPn(e)
        // Convert LID to PN; if not LID, the JID itself is already PN
        var pnJid = chatJid.hasLidServer()
                ? store.findPhoneByLid(chatJid.toUserJid()).orElse(null)
                : null;
        if (pnJid == null) {
            return; // WAWebSendUserMsgJob.maybeRefreshLid: r && ... (early return if null)
        }

        // ADAPTED: WAWebSendUserMsgJob.maybeRefreshLid: calls
        // syncContactListJob({contactIds: [r], shouldSyncDevice: false, mode: "query"})
        // Cobalt uses a device list sync as a stand-in
        deviceService.getDeviceLists(List.of(pnJid), "message", null, false);
    }

    /**
     * Encrypts, builds the stanza, sends it, and parses the ack.
     *
     * <p>Corresponds to the full {@code sendMsgToDeviceList} flow:
     * calls {@code createFanoutMsgStanza} to encrypt and build the stanza,
     * sends it via {@code deprecatedSendStanzaAndReturnAck}, and parses
     * the server ack.  Throws if the ack contains an error.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @param devices     the device JIDs to encrypt for
     * @param isResend    {@code true} if this is a phash mismatch resend
     * @return the parsed ack result
     * @throws WhatsAppMessageException.Send.Unknown if the ack contains an error
     *
     * @implNote WAWebSendMsgToDeviceList.sendMsgToDeviceList: calls
     * {@code createFanoutMsgStanza} then
     * {@code deprecatedSendStanzaAndReturnAck}, parses via
     * {@code sendMsgAckSyncParser.parse}, and throws if
     * {@code C.error} is present.
     */
    private AckResult encryptBuildAndSend(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        // WAWebSendMsgCreateFanoutStanza: ensureE2ESessions before encrypting
        deviceService.ensureSessions(devices);
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

        // WAWebSendMsgToDeviceList.sendMsgToDeviceList: if (C.error) throw err(...)
        if (ack.error().isPresent()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Invalid ack from server (error " + ack.error().getAsInt() + ") for " + chatJid, null);
        }

        return ack;
    }

    /**
     * Builds the complete chat fanout stanza with all fields resolved.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @param payloads    the per-device encrypted payloads
     * @param devices     the device JIDs (used for identity node resolution)
     * @param isResend    {@code true} if this is a phash mismatch resend
     * @return a {@link NodeBuilder} for the {@code <message>} stanza
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza:
     * builds the message stanza with all child nodes, attributes, and
     * per-device encrypted payloads.
     */
    private NodeBuilder buildStanza(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            List<MessageEncryptedPayload> payloads,
            Collection<Jid> devices,
            boolean isResend
    ) {
        var container = messageInfo.message();
        var identityNode = ParticipantsStanza.requiresIdentityNode(payloads)
                ? buildIdentityNode() : null;

        var metaNode = metaStanza.buildChat(chatJid, container, null);
        var bizNode = bizStanza.build(chatJid);
        var botNode = botStanza.build(messageInfo, chatJid);
        var reportingNode = reportingStanza.build(messageInfo, requireSelfJid(), chatJid);
        var senderContentBinding = SenderContentBindingStanza.buildForUser(messageInfo, requireSelfJid());
        var ctwaNode = ctwaStanza.build(chatJid);

        // WAWebSendMsgCreateFanoutStanza: metadata-only <bot> node with
        // type (prompt/command/request_welcome), local_automated_type,
        // client_thread_id - resolved from message context and AI thread
        var botMetadataNode = resolveBotMetadataNode(chatJid, messageInfo);

        return ChatFanoutStanza.build(
                messageInfo.key().id().orElseThrow(),
                chatJid,
                resolveStanzaType(container),
                payloads,
                resolveEditAttribute(container),
                null,
                isResend ? "false" : null,
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
     * Resolves {@code peer_recipient_lid} for PN-addressed chats.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient LID, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: when {@code L.isUser()}
     * and {@code K.accountLid} exists and {@code J.isLid()}, includes
     * {@code J} as the {@code peer_recipient_lid} attribute.
     */
    private Jid resolvePeerRecipientLid(Jid chatJid) {
        if (!chatJid.hasUserServer()) {
            return null;
        }

        return store.findChatByJid(chatJid)
                .flatMap(Chat::accountLid)
                .orElse(null);
    }

    /**
     * Resolves {@code peer_recipient_pn} for LID-addressed chats.
     *
     * <p>Only resolves the PN when the chat is LID-addressed,
     * LID migration is complete (always true in Cobalt), and the
     * {@code lidOriginType} is not {@code PNH_CTWA}.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient PN, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: {@code L.isLid() ?
     * te && (K?.lidOriginType) !== LidOriginType.PNH_CTWA &&
     * (Z = getPhoneNumber(L))}. {@code te} is {@code isLidMigrated()},
     * which is always true in Cobalt.
     */
    private Jid resolvePeerRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza: te && (K?.lidOriginType) !== PNH_CTWA
        // isLidMigrated() is always true in Cobalt
        var lidOriginType = store.findChatByJid(chatJid)
                .flatMap(Chat::lidOriginType)
                .orElse(null);
        if (LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves {@code recipient_pn} for LID-addressed chats.
     *
     * <p>Only includes the recipient's phone number when the chat is
     * LID-addressed, the {@code lidOriginType} is {@code null} or
     * {@code PNH_CTWA}, the contact has not opted in to share their PN,
     * and a phone number is available.
     *
     * @param chatJid the target chat JID
     * @return the recipient PN, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: {@code l.isLid() &&
     * ((K?.lidOriginType) == null || (K?.lidOriginType) === PNH_CTWA)
     * && (j?.shareOwnPn) !== true && (j?.phoneNumber) != null
     * && (Y = j?.phoneNumber)}.
     */
    private Jid resolveRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza: (K?.lidOriginType) == null || === PNH_CTWA
        var chat = store.findChatByJid(chatJid).orElse(null);
        var lidOriginType = chat != null ? chat.lidOriginType().orElse(null) : null;
        if (lidOriginType != null && !LID_ORIGIN_TYPE_PNH_CTWA.equals(lidOriginType)) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza: (j?.shareOwnPn) !== true
        var contact = store.findContactByJid(chatJid).orElse(null);
        if (contact != null && contact.isPhoneNumberShared()) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza: (j?.phoneNumber) != null && (Y = j.phoneNumber)
        // ADAPTED: Cobalt uses the LID-to-PN mapping from the store rather than
        // contact.phoneNumber, since in Cobalt the phone number is stored in the
        // LID mapping table rather than on the Contact model.
        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves {@code peer_recipient_username} for LID-addressed chats.
     *
     * @param chatJid the target chat JID
     * @return the peer recipient username, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: {@code l.isLid() &&
     * usernameDisplayedEnabled() && (j?.username) != null
     * && (ee = j.username)}.
     */
    private String resolvePeerRecipientUsername(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        var usernameEnabled = abPropsService.getBool(ABProp.USERNAME_CONTACT_DISPLAY);
        if (!usernameEnabled) {
            return null;
        }

        return store.findContactByJid(chatJid)
                .flatMap(Contact::username)
                .orElse(null);
    }

    /**
     * Resolves the metadata-only {@code <bot>} node for the stanza.
     *
     * <p>Determines the bot message body type (prompt/command/request_welcome)
     * from the message content and chat context, and extracts the AI
     * thread ID from the device context info.
     *
     * @param chatJid     the target chat JID
     * @param messageInfo the outgoing message
     * @return the bot metadata node, or {@code null} if no metadata applies
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: resolves {@code ae}
     * (type), {@code ie} (local_automated_type), {@code N.key.id}
     * (client_thread_id) and builds {@code me} when any is present.
     */
    private Node resolveBotMetadataNode(Jid chatJid, ChatMessageInfo messageInfo) {
        var container = messageInfo.message();
        var content = container.content();

        // WAWebSendMsgCreateFanoutStanza: resolve bot message body type
        String botMsgBodyType = null;
        if (content instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.REQUEST_WELCOME_MESSAGE) {
            botMsgBodyType = "request_welcome";
        } else if (chatJid.hasBotServer() || container.futureProofContentType() == FutureProofMessageType.BOT_INVOKE) {
            botMsgBodyType = resolveBotMsgBodyType(chatJid, content);
        }

        // WAWebSendMsgCreateFanoutStanza: resolve AI thread ID
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

        // WAWebSendMsgCreateFanoutStanza: resolve bizBotType from the
        // business profile's automated_type field
        // WAWebCommonParsersParseBusinessProfile: automated_type is
        // "1p_partial" (BIZ_1P) or "3p_full" (BIZ_3P)
        var bizBotType = client.queryBusinessProfile(chatJid)
                .flatMap(BusinessProfile::automatedType)
                .map(BusinessAutomatedType::value)
                .orElse(null);

        return BotStanza.buildMetadata(botMsgBodyType, bizBotType, clientThreadId);
    }

    /**
     * Resolves the bot message body type by checking whether the message
     * text matches a registered command on the bot's profile.
     *
     * @param botJid  the bot JID
     * @param content the inner message content
     * @return {@code "command"} if the text starts with a registered
     *         bot command, otherwise {@code "prompt"}
     *
     * @implNote WAWebBotCommandFormatMutator: matches text against the
     * bot's registered command names.
     * WAWebSendTextMsgChatAction: sets botMsgBodyType from caller options.
     */
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
     * Handles phash mismatch by resyncing device lists and resending
     * to newly discovered devices only.
     *
     * @param chatJid         the target chat JID
     * @param messageInfo     the outgoing message
     * @param originalDevices the devices from the original send
     * @param serverPhash     the phash returned by the server
     *
     * @implNote WAWebSendUserMsgJob.encryptAndSendUserMsg: when phash is
     * non-null, calls {@code syncDeviceListJob([v, S], "message", T)} then
     * delegates to {@code resendUserMsg}.
     * WAWebResendUserMsg.resendUserMsg: computes delta via
     * {@code differenceBy(b, a, String)}, resends with
     * {@code isResendingMsg: true} (device_fanout="false").
     */
    private void handlePhashMismatch(
            Jid chatJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            String serverPhash
    ) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Phash mismatch for {0}, server phash: {1}", chatJid, serverPhash);

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
}
