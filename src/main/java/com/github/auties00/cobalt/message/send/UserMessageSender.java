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
 * @apiNote WAWebSendUserMsgJob.encryptAndSendUserMsg: orchestrates the
 * full 1:1 send flow including device list resolution, stanza creation,
 * and post-send phash/refreshLid handling.
 */
final class UserMessageSender extends MessageSender<ChatMessageInfo> {
    private static final System.Logger LOGGER = System.getLogger(UserMessageSender.class.getName());

    private final MessageEncryption encryption;
    private final DeviceService deviceService;
    private final ABPropsService abPropsService;
    private final BotStanza botStanza;
    private final BizStanza bizStanza;
    private final MetaStanza metaStanza;
    private final ReportingStanza reportingStanza;
    private final CtwaAttributionStanza ctwaStanza;
    private final TcTokenStanza tcTokenStanza;

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

    @Override
    AckResult send(Jid chatJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        var fanoutDevices = deviceService.getUserFanout(chatJid, null);

        // WAWebSendMsgCreateFanoutStanza: create receipt records for all devices
        store.createOrMergeReceiptRecords(messageInfo.key().id().orElseThrow(), fanoutDevices);

        var ack = encryptBuildAndSend(chatJid, messageInfo, fanoutDevices, false);

        if (ack.refreshLid()) {
            // WAWebSendUserMsgJob.maybeRefreshLid: trigger contact list sync
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Server requested LID refresh for {0}", chatJid);
            store.removeDeviceList(chatJid);
            deviceService.getDeviceLists(List.of(chatJid), "message", null, false);
        }

        ack.phash().ifPresent(serverPhash ->
                handlePhashMismatch(chatJid, messageInfo, fanoutDevices, serverPhash));

        return ack;
    }

    /**
     * Encrypts, builds the stanza, sends it, and parses the ack.
     *
     * @apiNote WAWebSendMsgToDeviceList.sendMsgToDeviceList
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
        return AckParser.parse(ackNode);
    }

    /**
     * Builds the complete chat fanout stanza with all fields resolved.
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza
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
     * @apiNote WAWebSendMsgCreateFanoutStanza: when {@code to.isUser()}
     * and {@code chat.accountLid} exists, includes it.
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
     * @apiNote WAWebSendMsgCreateFanoutStanza: when {@code to.isLid()}
     * and {@code isLidMigrated}, includes {@code getPhoneNumber(to)}.
     */
    private Jid resolvePeerRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves {@code recipient_pn} for LID-addressed chats.
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: when {@code to.isLid()},
     * {@code shareOwnPn !== true}, and {@code contact.phoneNumber != null}.
     */
    private Jid resolveRecipientPn(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        return store.findPhoneByLid(chatJid.toUserJid()).orElse(null);
    }

    /**
     * Resolves {@code peer_recipient_username} for LID-addressed chats.
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza: when {@code to.isLid()},
     * {@code usernameDisplayedEnabled()}, and {@code contact.username != null}.
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
     * @apiNote WAWebSendMsgCreateFanoutStanza: resolves {@code ne}
     * (type), {@code re} (local_automated_type), {@code x}
     * (client_thread_id) and builds {@code oe} when any is present.
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
     * @apiNote WAWebBotCommandFormatMutator: matches text against the
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
     * @apiNote WAWebSendUserMsgJob: syncDeviceListJob then resendUserMsg.
     * WAWebResendUserMsg: computes delta, resends with device_fanout="false".
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
