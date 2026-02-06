package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashVersion;
import com.github.auties00.cobalt.device.key.DevicePreKeyHandler;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.addressing.AddressingModeMismatchHandler;
import com.github.auties00.cobalt.message.encryption.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.BizNode;
import com.github.auties00.cobalt.message.send.stanza.CtwaNode;
import com.github.auties00.cobalt.message.send.stanza.MetaNode;
import com.github.auties00.cobalt.message.send.stanza.StanzaBuilder;
import com.github.auties00.cobalt.message.send.token.ContentBindingToken;
import com.github.auties00.cobalt.message.send.token.ReportingToken;
import com.github.auties00.cobalt.message.send.token.TrustContactToken;
import com.github.auties00.cobalt.message.send.util.GroupSendQueue;
import com.github.auties00.cobalt.message.send.util.IcdcMetadata;
import com.github.auties00.cobalt.message.send.util.MessageDedup;
import com.github.auties00.cobalt.message.send.util.SagaDebugInfo;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.chat.ChatRole;
import com.github.auties00.cobalt.model.chat.ChatSettingPolicy;
import com.github.auties00.cobalt.model.chat.GroupSetting;
import com.github.auties00.cobalt.model.info.DeviceContextInfo;
import com.github.auties00.cobalt.model.info.DeviceContextInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.common.*;
import com.github.auties00.cobalt.model.message.server.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.server.ProtocolMessage;
import com.github.auties00.cobalt.model.sync.DeviceListMetadata;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.github.auties00.cobalt.model.message.server.ProtocolMessage.Type.EPHEMERAL_SYNC_RESPONSE;
import static com.github.auties00.cobalt.model.message.server.ProtocolMessage.Type.REQUEST_WELCOME_MESSAGE;

/**
 * Main service for sending encrypted messages.
 * <p>
 * Orchestrates the complete message sending flow including device list synchronization,
 * pre-key fetching, message encryption, sender key distribution, and stanza transmission.
 *
 * @apiNote WAWebSendMsgJob.encryptAndSendMsg: main entry point that routes to
 * WAWebSendUserMsgJob.encryptAndSendUserMsg for 1:1 or
 * WAWebSendGroupMsgJob.encryptAndSendGroupMsg for groups.
 */
public final class MessageSendingService {
    private static final System.Logger LOGGER = System.getLogger("MessageSendingService");

    /**
     * Default resend timeout in seconds.
     * Per WhatsApp Web: web_e2e_backfill_expire_time AB prop, default 5 minutes.
     *
     * @apiNote WAWebSendMsgCommonApi.getResendTimeoutInSeconds
     */
    private static final int RESEND_TIMEOUT_SECONDS = 5 * 60;

    /**
     * Maximum time to wait for offline delivery to complete (30 seconds).
     */
    private static final int OFFLINE_DELIVERY_WAIT_TIMEOUT_SECONDS = 30;

    private final WhatsAppClient client;
    private final WhatsAppStore store;

    /**
     * Lock and condition for waiting on offline delivery completion.
     */
    private final ReentrantLock offlineDeliveryLock = new ReentrantLock();
    private final Condition offlineDeliveryComplete = offlineDeliveryLock.newCondition();

    private final DeviceService deviceService;
    private final DevicePreKeyHandler preKeyService;
    private final DeviceFanoutCalculator fanoutCalculatorService;
    private final DevicePhashCalculator phashCalculatorService;
    private final MessageEncryption encryptionService;
    private final SenderKeyDistribution senderKeyDistributionService;
    private final IcdcMetadata icdcMetadataService;
    private final ContentBindingToken contentBindingService;
    private final ReportingToken reportingTokenService;
    private final BotMessage botMessageService;

    // New services for complete WhatsApp Web feature parity
    private final TrustContactToken trustContactTokenService;
    private final CtwaNode ctwaAttributionService;
    private final MetaNode metaNodeService;
    private final BizNode bizPrivacyModeService;
    private final SagaDebugInfo sagaDebugInfoService;
    private final AddressingModeMismatchHandler addressingModeMismatchHandler;
    private final GroupSendQueue groupSendQueue = new GroupSendQueue();

    /**
     * Message deduplication service.
     * <p>
     * Prevents duplicate sends when retries occur or same message sent multiple times.
     *
     * @apiNote WAWebMessageDedupUtils
     */
    private final MessageDedup dedupService = new MessageDedup();

    /**
     * Secure random for generating message secrets.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Message secret length in bytes.
     */
    private static final int MESSAGE_SECRET_LENGTH = 32;

    public MessageSendingService(
            WhatsAppClient client,
            DeviceService deviceService,
            ABPropsService abPropsService
    ) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.store = client.store();
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.preKeyService = Objects.requireNonNull(preKeyService, "preKeyService cannot be null");
        this.fanoutCalculatorService = Objects.requireNonNull(fanoutCalculatorService, "fanoutCalculatorService cannot be null");
        this.phashCalculatorService = Objects.requireNonNull(phashCalculatorService, "phashCalculatorService cannot be null");
        this.encryptionService = Objects.requireNonNull(encryptionService, "encryptionService cannot be null");
        this.senderKeyDistributionService = Objects.requireNonNull(senderKeyDistributionService, "senderKeyDistributionService cannot be null");
        this.icdcMetadataService = Objects.requireNonNull(icdcMetadataService, "icdcMetadataService cannot be null");
        this.contentBindingService = Objects.requireNonNull(contentBindingService, "contentBindingService cannot be null");
        this.reportingTokenService = Objects.requireNonNull(reportingTokenService, "reportingTokenService cannot be null");
        this.botMessageService = Objects.requireNonNull(botMessageService, "botMessageService cannot be null");

        // Initialize new services directly
        this.trustContactTokenService = new TrustContactTokenService(this.store, this.client);
        this.ctwaAttributionService = new CtwaAttributionService(this.store);
        this.metaNodeService = new MetaNodeService(this.store);
        this.bizPrivacyModeService = new BizPrivacyModeService(this.store);
        this.sagaDebugInfoService = new SagaDebugInfoService(this.store, abPropsService);
        this.addressingModeMismatchHandler = new com.github.auties00.cobalt.message.AddressingModeMismatchHandler(this.store);
    }

    /**
     * Sends an encrypted message to a user (1:1 chat).
     *
     * @param messageId     the message ID
     * @param chatJid       the recipient user JID
     * @param message       the message container to send
     * @param isResend      whether this is a resend after phash mismatch
     * @param editAttribute the edit attribute (sender_revoke, admin_revoke, etc.), or null
     * @return the send result
     * @throws WhatsAppMessageException.Send if sending fails
     *
     * @apiNote WAWebSendUserMsgJob.encryptAndSendUserMsg
     */
    public MessageSendResult sendToUser(
            String messageId,
            Jid chatJid,
            MessageContainer message,
            boolean isResend,
            String editAttribute
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        // WAWebMessageDedupUtils: check for duplicate send
        if (!isResend && !dedupService.tryMarkPending(messageId, chatJid, null)) {
            throw new WhatsAppMessageException.Send.Duplicate(messageId);
        }

        try {
            // WAWebSendMsgJob.encryptAndSendMsg: wait for offline delivery to complete
            if (!isResend) {
                waitForOfflineDeliveryEnd(messageId);
            }

            LOGGER.log(System.Logger.Level.DEBUG, "encryptAndSendUserMsg: sending {0}", messageId);

            // WAWebSendUserMsgJob: get device lists for recipient and self
            var selfJid = getSelfDeviceJid(chatJid);
            var userJids = List.of(chatJid.toUserJid(), selfJid.toUserJid());

            // WAWebSendUserMsgJob: determine if hosted devices should be included
            // bizHostedDevicesEnabled && chatJid.isUser() -> include hosted for 1:1
            Jid includeHostedFor = fanoutCalculatorService.isBizHostedDevicesEnabled() && chatJid.hasUserServer()
                    ? chatJid
                    : null;

            // WAWebDBDeviceListFanout.getFanOutList: get device lists and calculate fanout
            var deviceLists = deviceService.getDeviceLists(userJids, "message", null, false);
            var fanoutDevices = new ArrayList<>(fanoutCalculatorService.calculate(selfJid, deviceLists, includeHostedFor));

            if (fanoutDevices.isEmpty()) {
                // WAWebDBDeviceListFanout.getFanOutList: fallback to primary device
                LOGGER.log(System.Logger.Level.DEBUG, "No devices for user {0}, using primary", chatJid);
                fanoutDevices.add(chatJid.toUserJid());
            }

            // WAWebE2ESessionService: ensure LID mappings before session establishment
            ensurePhoneNumberToLidMapping(fanoutDevices);

            // WAWebManageE2ESessionsJob.ensureE2ESessions: fetch pre-keys for devices without sessions
            var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(fanoutDevices);
            if (!devicesNeedingSessions.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG, "Fetching prekeys for {0} devices", devicesNeedingSessions.size());
                preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions);
            }

            // WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf: determine decrypt-fail attribute
            var decryptFail = getDecryptFailAttribute(message);

            // WAWebSendMsgCommonApi.editAttribute: calculate edit attribute if not provided
            var effectiveEditAttribute = editAttribute != null ? editAttribute : getEditAttribute(message);

            // WAWebReportingTokenUtils: get or generate message secret for reporting token
            var messageSecret = getOrGenerateMessageSecret(message);

            // WAWebICDCMetaApi.populateICDCMeta: create ICDC metadata for the message
            var messageWithIcdc = populateIcdcMetadata(message, selfJid, chatJid, messageSecret);

            // Per WAWebSendMsgJob: add SAGA debug info for CAPI support accounts
            // This adds debug payload to messageContextInfo.supportPayload for troubleshooting
            var messageWithSaga = addSagaDebugInfoIfApplicable(messageWithIcdc, chatJid);

            // WAWebMsgRcatUtils.genContentBindingForMsg: generate content bindings for URL messages
            // Per WAWebMsgRcatUtils: only for messages sent by me (always true in send path)
            var participantJids = getUniqueUserJids(selfJid, fanoutDevices);
            var contentBindings = contentBindingService.generate(
                    messageId, messageWithSaga, messageSecret, selfJid, participantJids, true
            );

            // WAWebMsgRcatUtils: sender content binding for self
            var senderContentBinding = contentBindingService.generateForSender(
                    messageId, messageWithSaga, messageSecret, selfJid
            );

            // WAWebReportingTokenUtils.genReportingTokenBody: generate reporting token node
            var reportingTokenNode = reportingTokenService.generate(
                    messageId, messageWithSaga, messageSecret, selfJid, chatJid
            );

            // WAWebApiMessageInfoStore.createOrMergeReceiptRecords: track recipients for delivery tracking
            store.createOrMergeReceiptRecords(messageId, fanoutDevices);

            // WAWebSendMsgCreateFanoutStanza: encrypt for each device
            var encryptedNodes = encryptForDevices(fanoutDevices, chatJid, messageWithSaga, decryptFail);

            // WAWebSignalProtocolStore.flushBufferToDiskIfNotMemOnlyMode: persist signal state after encryption
            flushSignalStateIfNeeded();

            // WAWebSendMsgCreateFanoutStanza: check if identity node needed (any pkmsg)
            var needsIdentity = encryptedNodes.stream()
                    .anyMatch(node -> node.encryptionType().isPreKeyMessage());

            // WAWebAdvSignatureApi.getADVEncodedIdentity: get ADV identity if needed
            byte[] deviceIdentity = needsIdentity ? getEncodedDeviceIdentity() : null;

            // WAWebSendMsgMetaNode.genMetaNode: create meta node using MetaNodeService
            var metaNodeContext = getMetaNodeContext(chatJid, messageWithIcdc);
            var metaNode = metaNodeService.build(chatJid, messageWithIcdc, metaNodeContext);

            // WAWebSendMsgCreateFanoutStanza: get peer recipient attributes for LID chats
            var peerRecipientAttrs = getPeerRecipientAttributes(chatJid);

            // WAWebGetPrivacyModeWhenSent: get biz node for privacy mode
            var nativeFlowName = bizPrivacyModeService.getNativeFlowName(messageWithIcdc);
            var bizNode = bizPrivacyModeService.getBizNode(chatJid, messageWithIcdc, nativeFlowName);

            // WAWebSendMsgCreateFanoutStanza: get bot node if applicable
            var botNode = getBotNodeForUser(messageWithIcdc, chatJid);

            // WAWebTrustedContactsUtils: get TC token for chat
            var tcToken = trustContactTokenService.getTcTokenForChat(chatJid);

            // WAWebSendMsgCtwaAttributionNode: get CTWA attribution node
            var ctwaAttributionNode = ctwaAttributionService.getCtwaAttributionNode(chatJid);

            // Build full context for stanza builder
            var stanzaContext = new MessageSendStanzaBuilder.UserMessageStanzaContext(
                    getAddressingMode(chatJid),
                    effectiveEditAttribute,
                    isResend,
                    isBotFeedbackMessage(message),
                    peerRecipientAttrs.peerRecipientLid(),
                    peerRecipientAttrs.peerRecipientPn(),
                    peerRecipientAttrs.peerRecipientUsername(),
                    peerRecipientAttrs.recipientPn(),
                    bizNode,
                    metaNode,
                    botNode,
                    tcToken,
                    reportingTokenNode,
                    senderContentBinding,
                    contentBindings,
                    ctwaAttributionNode
            );

            // WAWebSendMsgCreateFanoutStanza: build message stanza
            var stanza = MessageSendStanzaBuilder.createUserMessageStanza(
                    messageId,
                    chatJid,
                    getMessageType(messageWithIcdc),
                    encryptedNodes,
                    deviceIdentity,
                    stanzaContext
            );

            // WADeprecatedSendIq.deprecatedSendStanzaAndReturnAck: send and wait for ack
            var response = client.sendNode(stanza);
            var ack = MessageSendAckParser.parse(response)
                    .orElseThrow(() -> new WhatsAppMessageException.Send.Unknown("Invalid ack from server"));

            // WAWebSendMsgCommonApi.updateIdentityRange: track identity ranges for resend filtering
            if (!isResend) {
                updateIdentityRangeAfterEncryption(ack.timestamp(), fanoutDevices);
            }

            // WAWebSendUserMsgJob: handle phash mismatch with resend
            if (ack.hasPhashMismatch() && !isResend) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "encryptAndSendUserMsg: phash mismatch, got server phash {0}", ack.phash());
                // WAWebResendUserMsg: schedule resend after syncing device list
                scheduleUserMessageResend(messageId, chatJid, messageWithIcdc, fanoutDevices, ack.timestamp(), editAttribute);
            }

            // WAWebSendUserMsgJob.maybeRefreshLid: handle refresh_lid by triggering contact sync
            if (ack.refreshLid()) {
                LOGGER.log(System.Logger.Level.DEBUG, "refreshLid requested for {0}", chatJid);
                handleRefreshLid(chatJid);
            }

            // WAWebSendMsgJob: send TC token for non-protocol messages
            // Per WhatsApp Web: t.data.type !== "protocol" && sendTcToken(u)
            if (!isProtocolMessage(message)) {
                trustContactTokenService.sendTcToken(chatJid);
            }

            return new MessageSendResult(
                    messageId,
                    chatJid,
                    ack.timestamp(),
                    ack.phash(),
                    ack.addressingMode(),
                    ack.count().orElse(-1)
            );
        } finally {
            // WAWebMessageDedupUtils: mark as completed
            if (!isResend) {
                dedupService.markCompleted(messageId);
            }
        }
    }

    /**
     * Internal implementation of sendToUser after deduplication check.
     */
    private MessageSendResult sendToUserInternal(
            String messageId,
            Jid chatJid,
            MessageContainer message,
            boolean isResend,
            String editAttribute
    ) {
        // WAWebSendMsgJob.encryptAndSendMsg: wait for offline delivery to complete
        if (!isResend) {
            waitForOfflineDeliveryEnd(messageId);
        }

        LOGGER.log(System.Logger.Level.DEBUG, "encryptAndSendUserMsg: sending {0}", messageId);

        // WAWebSendUserMsgJob: get device lists for recipient and self
        var selfJid = getSelfDeviceJid(chatJid);
        var userJids = List.of(chatJid.toUserJid(), selfJid.toUserJid());

        // WAWebSendUserMsgJob: determine if hosted devices should be included
        // bizHostedDevicesEnabled && chatJid.isUser() -> include hosted for 1:1
        Jid includeHostedFor = fanoutCalculatorService.isBizHostedDevicesEnabled() && chatJid.hasUserServer()
                ? chatJid
                : null;

        // WAWebDBDeviceListFanout.getFanOutList: get device lists and calculate fanout
        var deviceLists = deviceService.getDeviceLists(userJids, "message", null, false);
        var fanoutDevices = new ArrayList<>(fanoutCalculatorService.calculate(selfJid, deviceLists, includeHostedFor));

        if (fanoutDevices.isEmpty()) {
            // WAWebDBDeviceListFanout.getFanOutList: fallback to primary device
            LOGGER.log(System.Logger.Level.DEBUG, "No devices for user {0}, using primary", chatJid);
            fanoutDevices.add(chatJid.toUserJid());
        }

        // WAWebE2ESessionService: ensure LID mappings before session establishment
        ensurePhoneNumberToLidMapping(fanoutDevices);

        // WAWebManageE2ESessionsJob.ensureE2ESessions: fetch pre-keys for devices without sessions
        var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(fanoutDevices);
        if (!devicesNeedingSessions.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Fetching prekeys for {0} devices", devicesNeedingSessions.size());
            preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions);
        }

        // WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf: determine decrypt-fail attribute
        var decryptFail = getDecryptFailAttribute(message);

        // WAWebSendMsgCommonApi.editAttribute: calculate edit attribute if not provided
        var effectiveEditAttribute = editAttribute != null ? editAttribute : getEditAttribute(message);

        // WAWebReportingTokenUtils: get or generate message secret for reporting token
        var messageSecret = getOrGenerateMessageSecret(message);

        // WAWebICDCMetaApi.populateICDCMeta: create ICDC metadata for the message
        var messageWithIcdc = populateIcdcMetadata(message, selfJid, chatJid, messageSecret);

        // Per WAWebSendMsgJob: add SAGA debug info for CAPI support accounts
        // This adds debug payload to messageContextInfo.supportPayload for troubleshooting
        var messageWithSaga = addSagaDebugInfoIfApplicable(messageWithIcdc, chatJid);

        // WAWebMsgRcatUtils.genContentBindingForMsg: generate content bindings for URL messages
        // Per WAWebMsgRcatUtils: only for messages sent by me (always true in send path)
        var participantJids = getUniqueUserJids(selfJid, fanoutDevices);
        var contentBindings = contentBindingService.generateContentBindings(
                messageId, messageWithSaga, messageSecret, selfJid, participantJids, true
        );

        // WAWebMsgRcatUtils: sender content binding for self
        var senderContentBinding = contentBindingService.generateSenderContentBinding(
                messageId, messageWithSaga, messageSecret, selfJid
        );

        // WAWebReportingTokenUtils.genReportingTokenBody: generate reporting token node
        var reportingTokenNode = reportingTokenService.generateReportingTokenNode(
                messageId, messageWithSaga, messageSecret, selfJid, chatJid
        );

        // WAWebApiMessageInfoStore.createOrMergeReceiptRecords: track recipients for delivery tracking
        store.createOrMergeReceiptRecords(messageId, fanoutDevices);

        // WAWebSendMsgCreateFanoutStanza: encrypt for each device
        var encryptedNodes = encryptForDevices(fanoutDevices, chatJid, messageWithSaga, decryptFail);

        // WAWebSignalProtocolStore.flushBufferToDiskIfNotMemOnlyMode: persist signal state after encryption
        flushSignalStateIfNeeded();

        // WAWebSendMsgCreateFanoutStanza: check if identity node needed (any pkmsg)
        var needsIdentity = encryptedNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());

        // WAWebAdvSignatureApi.getADVEncodedIdentity: get ADV identity if needed
        byte[] deviceIdentity = needsIdentity ? getEncodedDeviceIdentity() : null;

        // WAWebSendMsgMetaNode.genMetaNode: create meta node using MetaNodeService
        var metaNodeContext = getMetaNodeContext(chatJid, messageWithIcdc);
        var metaNode = metaNodeService.generateMetaNode(chatJid, messageWithIcdc, metaNodeContext);

        // WAWebSendMsgCreateFanoutStanza: get peer recipient attributes for LID chats
        var peerRecipientAttrs = getPeerRecipientAttributes(chatJid);

        // WAWebGetPrivacyModeWhenSent: get biz node for privacy mode
        var nativeFlowName = bizPrivacyModeService.getNativeFlowName(messageWithIcdc);
        var bizNode = bizPrivacyModeService.getBizNode(chatJid, messageWithIcdc, nativeFlowName);

        // WAWebSendMsgCreateFanoutStanza: get bot node if applicable
        var botNode = getBotNodeForUser(messageWithIcdc, chatJid);

        // WAWebTrustedContactsUtils: get TC token for chat
        var tcToken = trustContactTokenService.getTcTokenForChat(chatJid);

        // WAWebSendMsgCtwaAttributionNode: get CTWA attribution node
        var ctwaAttributionNode = ctwaAttributionService.getCtwaAttributionNode(chatJid);

        // Build full context for stanza builder
        var stanzaContext = new MessageSendStanzaBuilder.UserMessageStanzaContext(
                getAddressingMode(chatJid),
                effectiveEditAttribute,
                isResend,
                isBotFeedbackMessage(message),
                peerRecipientAttrs.peerRecipientLid(),
                peerRecipientAttrs.peerRecipientPn(),
                peerRecipientAttrs.peerRecipientUsername(),
                peerRecipientAttrs.recipientPn(),
                bizNode,
                metaNode,
                botNode,
                tcToken,
                reportingTokenNode,
                senderContentBinding,
                contentBindings,
                ctwaAttributionNode
        );

        // WAWebSendMsgCreateFanoutStanza: build message stanza
        var stanza = MessageSendStanzaBuilder.createUserMessageStanza(
                messageId,
                chatJid,
                getMessageType(messageWithIcdc),
                encryptedNodes,
                deviceIdentity,
                stanzaContext
        );

        // WADeprecatedSendIq.deprecatedSendStanzaAndReturnAck: send and wait for ack
        var response = client.sendNode(stanza);
        var ack = MessageSendAckParser.parse(response)
                .orElseThrow(() -> new WhatsAppMessageException.Send.Unknown("Invalid ack from server"));

        // WAWebSendMsgCommonApi.updateIdentityRange: track identity ranges for resend filtering
        if (!isResend) {
            updateIdentityRangeAfterEncryption(ack.timestamp(), fanoutDevices);
        }

        // WAWebSendUserMsgJob: handle phash mismatch with resend
        if (ack.hasPhashMismatch() && !isResend) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "encryptAndSendUserMsg: phash mismatch, got server phash {0}", ack.phash());
            // WAWebResendUserMsg: schedule resend after syncing device list
            scheduleUserMessageResend(messageId, chatJid, messageWithIcdc, fanoutDevices, ack.timestamp(), editAttribute);
        }

        // WAWebSendUserMsgJob.maybeRefreshLid: handle refresh_lid by triggering contact sync
        if (ack.refreshLid()) {
            LOGGER.log(System.Logger.Level.DEBUG, "refreshLid requested for {0}", chatJid);
            handleRefreshLid(chatJid);
        }

        // WAWebSendMsgJob: send TC token for non-protocol messages
        // Per WhatsApp Web: t.data.type !== "protocol" && sendTcToken(u)
        if (!isProtocolMessage(message)) {
            trustContactTokenService.sendTcToken(chatJid);
        }

        return new MessageSendResult(
                messageId,
                chatJid,
                ack.timestamp(),
                ack.phash(),
                ack.addressingMode(),
                ack.count().orElse(-1)
        );
    }

    /**
     * Checks if a message is a protocol message.
     * <p>
     * Per WhatsApp Web: protocol messages don't trigger TC token sending.
     *
     * @param message the message to check
     * @return true if this is a protocol message
     */
    private boolean isProtocolMessage(MessageContainer message) {
        var unwrapped = message.unbox();
        return unwrapped.type() == Message.Type.PROTOCOL;
    }

    /**
     * Sends an encrypted message to a group using sender key encryption.
     *
     * @param messageId    the message ID
     * @param groupJid     the group JID
     * @param message      the message container to send
     * @param participants the group participant JIDs
     * @return the send result
     * @throws WhatsAppMessageException.Send if sending fails
     *
     * @apiNote WAWebSendGroupMsgJob.encryptAndSendGroupMsg ->
     * WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg
     */
    public MessageSendResult sendToGroup(
            String messageId,
            Jid groupJid,
            MessageContainer message,
            Collection<Jid> participants
    ) {
        return sendToGroup(messageId, groupJid, message, participants, false, null);
    }

    /**
     * Sends an encrypted message to a group using sender key encryption.
     *
     * @param messageId     the message ID
     * @param groupJid      the group JID
     * @param message       the message container to send
     * @param participants  the group participant JIDs
     * @param isResend      whether this is a resend after phash mismatch
     * @param editAttribute the edit attribute, or null
     * @return the send result
     * @throws WhatsAppMessageException.Send if sending fails
     *
     * @apiNote WAWebSendGroupMsgJob.encryptAndSendGroupMsg ->
     * WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg
     */
    public MessageSendResult sendToGroup(
            String messageId,
            Jid groupJid,
            MessageContainer message,
            Collection<Jid> participants,
            boolean isResend,
            String editAttribute
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");

        // WAWebMessageDedupUtils: check for duplicate send
        if (!isResend && !dedupService.tryMarkPending(messageId)) {
            throw new WhatsAppMessageException.Send.Duplicate(messageId);
        }

        try {
            // WAWebSendMsgQueueMap: serialize sends per group to prevent race conditions
            return groupSendQueue.execute(groupJid, () ->
                    sendToGroupInternal(messageId, groupJid, message, participants, isResend, editAttribute)
            );
        } finally {
            // WAWebMessageDedupUtils: mark as completed
            if (!isResend) {
                dedupService.markCompleted(messageId);
            }
        }
    }

    /**
     * Internal implementation of sendToGroup after deduplication check.
     */
    private MessageSendResult sendToGroupInternal(
            String messageId,
            Jid groupJid,
            MessageContainer message,
            Collection<Jid> participants,
            boolean isResend,
            String editAttribute
    ) {
        // WAWebSendMsgJob.encryptAndSendMsg: wait for offline delivery to complete
        if (!isResend) {
            waitForOfflineDeliveryEnd(messageId);
        }

        // WAWebGroupPermissionsApi: validate CAG send permissions
        // Skip for protocol messages (revokes, edits, etc.) which have their own logic
        if (!isProtocolMessage(message)) {
            validateCagSendPermission(groupJid, store.jid().orElse(null), "message");
        }

        LOGGER.log(System.Logger.Level.DEBUG, "encryptAndSendGroupMsg: sending {0} to {1}",
                messageId, groupJid);

        var selfJid = getSelfDeviceJid(groupJid);
        var isLidAddressingMode = groupJid.hasLidServer();
        var isBotFeedback = isBotFeedbackMessage(message);

        // WAWebDBDeviceListFanout.getFanOutList: get device lists for participants
        var deviceLists = deviceService.getDeviceLists(participants, "message", null, false);
        var allDevices = new ArrayList<>(fanoutCalculatorService.calculate(selfJid, deviceLists, null));

        // WAWebApiParticipantStore.getGroupSenderKeyListFromParticipantRecord
        var senderKeyLists = senderKeyDistributionService.categorizeParticipants(
                groupJid, selfJid, allDevices
        );

        // WAWebSendGroupSkmsgJob: handle key rotation if needed
        if (senderKeyLists.rotateKey()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Rotating sender key for {0}", groupJid);
            encryptionService.rotateSenderKey(groupJid, selfJid);
            senderKeyDistributionService.clearSenderKeyDistributionStatus(groupJid);
        }

        // WAWebE2ESessionService: ensure LID mappings before session establishment
        var distribDevices = senderKeyLists.needsDistribution();
        if (!distribDevices.isEmpty()) {
            ensurePhoneNumberToLidMapping(distribDevices);
        }

        // WAWebManageE2ESessionsJob.ensureE2ESessions: fetch pre-keys for distribution recipients
        if (!distribDevices.isEmpty()) {
            var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(distribDevices);
            if (!devicesNeedingSessions.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG, "Fetching prekeys for {0} devices",
                        devicesNeedingSessions.size());
                preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions);
            }
        }

        // WAWebPhashUtils.phashV2: calculate participant hash
        var allParticipants = new ArrayList<Jid>(allDevices);
        allParticipants.add(selfJid);
        String phash;
        try {
            phash = phashCalculatorService.calculate(allParticipants, DevicePhashVersion.V2, false);
        } catch (NoSuchAlgorithmException e) {
            throw new WhatsAppMessageException.Send.Unknown("Failed to calculate phash", e);
        }

        // WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf: determine decrypt-fail attribute
        var decryptFail = getDecryptFailAttribute(message);

        // WAWebSendMsgCommonApi.editAttribute: calculate edit attribute if not provided
        var effectiveEditAttribute = editAttribute != null ? editAttribute : getEditAttribute(message);

        // WAWebReportingTokenUtils: get or generate message secret
        var messageSecret = getOrGenerateMessageSecret(message);

        // WAWebICDCMetaApi.populateICDCMeta: create ICDC metadata for the message
        var messageWithIcdc = populateIcdcMetadataForGroup(message, selfJid, messageSecret);

        // WAWebMsgRcatUtils.genContentBindingForMsg: generate content bindings
        // Per WAWebMsgRcatUtils: only for messages sent by me (always true in send path)
        var participantJids = getUniqueUserJids(selfJid, allDevices);
        var contentBindings = contentBindingService.generateContentBindings(
                messageId, messageWithIcdc, messageSecret, selfJid, participantJids, true
        );

        // WAWebMsgRcatUtils: sender content binding for self
        var senderContentBinding = contentBindingService.generateSenderContentBinding(
                messageId, messageWithIcdc, messageSecret, selfJid
        );

        // WAWebReportingTokenUtils.genReportingTokenBody: generate reporting token node
        var reportingTokenNode = reportingTokenService.generateReportingTokenNode(
                messageId, messageWithIcdc, messageSecret, selfJid, groupJid
        );

        // WAWebApiMessageInfoStore.createOrMergeReceiptRecords: track recipients for delivery tracking
        store.createOrMergeReceiptRecords(messageId, allDevices);

        // WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: create distribution messages
        var distributionNodes = encryptSenderKeyDistribution(groupJid, selfJid, distribDevices, decryptFail);

        // WAWebEncryptMsgProtobuf.encryptMsgSenderKey: encrypt group message with sender key
        var plaintext = MessageContainerSpec.encode(messageWithIcdc);
        var senderKeyPayload = encryptionService.encryptForGroup(groupJid, selfJid, plaintext);
        var nativeFlowName = getNativeFlowName(messageWithIcdc);
        var encryptedGroupMessage = MessageSendStanzaBuilder.EncryptedGroupMessage.from(
                senderKeyPayload, getMediaType(messageWithIcdc), decryptFail, nativeFlowName
        );

        // WAWebSignalProtocolStore.flushBufferToDiskIfNotMemOnlyMode: persist signal state after encryption
        flushSignalStateIfNeeded();

        // WAWebSendGroupSkmsgJob: check if identity node needed (any pkmsg in distribution)
        var needsIdentity = distributionNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());

        // WAWebSendGroupSkmsgJob function L: encrypt for bots in the group
        Node botNode = null;
        boolean botNeedsIdentity = false;
        var invokedBotWid = getInvokedBotWid(message);
        var protocolMessageKeyParticipant = getProtocolMessageKeyParticipant(message);
        var botRespOrInvocationRevokeJid = getBotRespOrInvocationRevokeJid(message);
        var isRevokeForBot = isRevokeForBotMessage(message);

        // Per WhatsApp Web WAWebBotGroupGatingUtils: check if this is an open bot group
        // An open bot group has Meta AI bot as a participant
        var metaAiBotJid = botMessageService.getMetaAiBotFbidJid();
        var isOpenBotGroup = participants.stream()
                .anyMatch(p -> p.equals(metaAiBotJid) || botMessageService.isMetaAiBot(p));

        if (botMessageService.isBotsEnabled() && (invokedBotWid != null || isBotFeedback || isRevokeForBot || isOpenBotGroup)) {
            // Per WAWebSendGroupSkmsgJob (function L): use determineGroupBotJid for proper bot selection
            var botJidOpt = botMessageService.determineGroupBotJid(
                    invokedBotWid,
                    protocolMessageKeyParticipant,
                    botRespOrInvocationRevokeJid,
                    isBotFeedback,
                    isRevokeForBot,
                    isOpenBotGroup
            );

            if (botJidOpt.isPresent()) {
                var botJid = botJidOpt.get();

                // WAWebE2ESessionService: ensure LID mappings for bot
                ensurePhoneNumberToLidMapping(List.of(botJid));

                // Ensure we have a session with the bot
                var botSessionDevices = preKeyService.findDevicesNeedingSessions(List.of(botJid));
                if (!botSessionDevices.isEmpty()) {
                    preKeyService.fetchAndProcessPreKeyBundles(botSessionDevices);
                }

                // WAWebE2EProtoGenerator.updateBotInvokeMsgProtoCopyForCapi: prepare message for bot
                var botMessageCopy = botMessageService.updateForBotSending(messageWithIcdc, messageSecret);

                // WAWebE2EProtoGenerator.updateFbidBotInvokeProtobuf: convert PN to LID for FBID bots
                if (botMessageService.isFbidBot(botJid)) {
                    botMessageCopy = botMessageService.updateForFbidBotInvoke(botMessageCopy);
                }

                // WAWebE2EProtoGenerator.updateBotProtobuf: remove remoteJid from protocol messages
                if (botJid.hasBotServer()) {
                    if (botMessageService.isFbidBot(botJid)) {
                        botMessageCopy = botMessageService.updateForFbidBotProtocol(botMessageCopy);
                    } else {
                        botMessageCopy = botMessageService.updateForBotProtocol(botMessageCopy);
                    }
                }

                var botType = isBotFeedback
                        ? MessageStanzaNodeBuilders.BotMessageType.FEEDBACK
                        : botMessageService.getBotMessageType(messageWithIcdc, null, groupJid);

                var botEncryptionResult = botMessageService.createBotNodeWithEncryption(
                        botType, botJid, botMessageCopy != null ? botMessageCopy : messageWithIcdc
                );

                if (botEncryptionResult != null) {
                    botNode = botEncryptionResult.botNode();
                    botNeedsIdentity = botEncryptionResult.needsIdentity();
                }
            }
        }

        // Update needsIdentity to include bot encryption requirement
        needsIdentity = needsIdentity || botNeedsIdentity;
        byte[] deviceIdentity = needsIdentity ? getEncodedDeviceIdentity() : null;

        // WAWebSendMsgMetaNode.genMetaNode: create meta node using MetaNodeService
        var groupMetaNodeContext = getMetaNodeContext(groupJid, messageWithIcdc);
        var metaNode = metaNodeService.generateMetaNode(groupJid, messageWithIcdc, groupMetaNodeContext);

        // WAWebSendGroupSkmsgJob (function b): get biz node for native flow payment info
        var groupBizNode = bizPrivacyModeService.getGroupBizNode(messageWithIcdc, nativeFlowName);

        // Build full context for stanza builder
        var stanzaContext = new MessageSendStanzaBuilder.GroupMessageStanzaContext(
                isLidAddressingMode ? "lid" : "pn",
                effectiveEditAttribute,
                isBotFeedback,
                botNeedsIdentity,
                groupBizNode,
                metaNode,
                botNode,
                senderContentBinding,
                contentBindings,
                reportingTokenNode
        );

        // WAWebSendGroupSkmsgJob: build message stanza
        var stanza = MessageSendStanzaBuilder.createGroupSenderKeyStanza(
                messageId,
                groupJid,
                getMessageType(messageWithIcdc),
                phash,
                encryptedGroupMessage,
                distributionNodes,
                deviceIdentity,
                stanzaContext
        );

        // WADeprecatedSendIq.deprecatedSendStanzaAndReturnAck: send and wait for ack
        var response = client.sendNode(stanza);
        var ack = MessageSendAckParser.parse(response)
                .orElseThrow(() -> new WhatsAppMessageException.Send.Unknown("Invalid ack from server"));

        // WAWebSendMsgCommonApi.updateIdentityRange: track identity ranges for resend filtering
        updateIdentityRangeAfterEncryption(ack.timestamp(), allDevices);

        // WAWebApiParticipantStore.markHasSenderKey: mark distribution recipients
        if (!distribDevices.isEmpty()) {
            senderKeyDistributionService.markParticipantsHaveSenderKey(groupJid, distribDevices);
        }

        // WAWebGroupHandleAddressingModeMismatch: check for addressing mode mismatch
        // Per WhatsApp Web: re != null && re !== j && handleAddressingModeMismatch(...)
        var localAddressingMode = isLidAddressingMode ? "lid" : "pn";
        addressingModeMismatchHandler.checkAndHandleMismatch(
                groupJid,
                localAddressingMode,
                ack.addressingMode(),
                com.github.auties00.cobalt.message.AddressingModeMismatchHandler.MismatchOrigin.ACK_OUTGOING_MESSAGE
        );

        // WAWebSendGroupSkmsgJob: handle errors and phash mismatch
        if (ack.isStaleGroupAddressingModeError()) {
            LOGGER.log(System.Logger.Level.WARNING, "Stale group addressing mode for {0}", groupJid);
            throw new WhatsAppMessageException.Send.Unknown("Stale group addressing mode, error code 421");
        }

        if (ack.hasPhashMismatch() && !isResend) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "encryptAndSendSenderKeyMsg: phash mismatch for {0}, server phash {1}",
                    groupJid, ack.phash());
            // WAWebResendGroupMsg: schedule resend after syncing
            scheduleGroupMessageResend(messageId, groupJid, messageWithIcdc, participants, allDevices,
                    ack.timestamp(), editAttribute, isLidAddressingMode);
        }

        return new MessageSendResult(
                messageId,
                groupJid,
                ack.timestamp(),
                ack.phash(),
                ack.addressingMode(),
                ack.count().orElse(-1)
        );
    }

    /**
     * Encrypts a message for multiple devices.
     */
    private List<MessageSendStanzaBuilder.EncryptedDeviceNode> encryptForDevices(
            Collection<Jid> devices,
            Jid chatJid,
            MessageContainer message,
            String decryptFail
    ) {
        var result = new ArrayList<MessageSendStanzaBuilder.EncryptedDeviceNode>();
        var selfJid = store.jid();
        var mediaType = getMediaType(message);
        var nativeFlowName = getNativeFlowName(message);

        // WAWebSendMsgCreateFanoutStanza: encrypt for each device in parallel
        try (var scope = StructuredTaskScope.open()) {
            var subtasks = new ArrayList<StructuredTaskScope.Subtask<MessageSendStanzaBuilder.EncryptedDeviceNode>>();

            for (var device : devices) {
                subtasks.add(scope.fork(() -> {
                    // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage: wrap for self devices
                    var messageToEncrypt = isSelfDevice(device, selfJid.get())
                            ? wrapDeviceSentMessage(message, chatJid)
                            : message;

                    var messageBytes = MessageContainerSpec.encode(messageToEncrypt);
                    var payload = encryptionService.encryptForDevice(device, messageBytes);

                    return MessageSendStanzaBuilder.EncryptedDeviceNode.from(payload, mediaType, decryptFail, nativeFlowName);
                }));
            }

            scope.join();

            // WAWebSendMsgCreateFanoutStanza: collect results, log failures
            for (var subtask : subtasks) {
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    result.add(subtask.get());
                } else {
                    LOGGER.log(System.Logger.Level.WARNING, "Encryption failed for a device");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppMessageException.Send.Unknown("Interrupted while encrypting", e);
        }

        return result;
    }

    /**
     * Encrypts sender key distribution messages for devices that need the key.
     * <p>
     * Per WhatsApp Web WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
     * Self devices receive the distribution message wrapped in DeviceSentMessage
     * so they know the actual destination (the group).
     *
     * @param groupJid    the group JID
     * @param senderJid   the sender's device JID
     * @param devices     the devices that need the sender key
     * @param decryptFail the decrypt-fail attribute
     * @return list of encrypted device nodes for distribution
     *
     * @apiNote WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg
     */
    private List<MessageSendStanzaBuilder.EncryptedDeviceNode> encryptSenderKeyDistribution(
            Jid groupJid,
            Jid senderJid,
            Collection<Jid> devices,
            String decryptFail
    ) {
        if (devices.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<MessageSendStanzaBuilder.EncryptedDeviceNode>();
        var selfJid = store.jid().orElse(null);

        // WAWebGetGroupKeyDistributionMsg: create base distribution message container
        var baseDistributionMessage = senderKeyDistributionService.createDistributionMessageContainer(
                groupJid, senderJid
        );

        // WAWebGetGroupKeyDistributionMsg: encrypt for each device needing the key
        for (var device : devices) {
            try {
                // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
                // Self devices receive wrapped in DeviceSentMessage
                MessageContainer messageToEncrypt;
                if (selfJid != null && isSelfDevice(device, selfJid)) {
                    // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage for self devices
                    messageToEncrypt = wrapDeviceSentMessage(baseDistributionMessage, groupJid);
                } else {
                    messageToEncrypt = baseDistributionMessage;
                }

                var payload = encryptionService.encryptForDevice(device, messageToEncrypt);
                result.add(new MessageSendStanzaBuilder.EncryptedDeviceNode(
                        device,
                        payload.type(),
                        payload.ciphertext(),
                        null,  // mediaType
                        decryptFail,
                        null   // nativeFlowName (not applicable for sender key distribution)
                ));
            } catch (Exception e) {
                // WAWebGetGroupKeyDistributionMsg: log and continue, but fail if primary device
                LOGGER.log(System.Logger.Level.WARNING,
                        "getKeyDistributionMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());

                // WAWebSendMsgCommonApi.isPrimaryDevice: device.device === 0
                if (device.device() == 0) {
                    throw new WhatsAppMessageException.Send.Unknown(
                            "getKeyDistributionMsg: encryption fail for primary device", e
                    );
                }
            }
        }

        return result;
    }

    private Jid getSelfDeviceJid(Jid chatJid) {
        // WAWebSendUserMsgJob: use LID if chat is LID and we have a LID
        if (chatJid.hasLidServer()) {
            var lidJid = store.lid();
            if (lidJid.isPresent()) {
                return lidJid.get();
            }
        }
        return store.jid().get();
    }

    private boolean isSelfDevice(Jid deviceJid, Jid selfJid) {
        // WAWebUserPrefsMeUser.isMeAccount: compares user part
        return Objects.equals(deviceJid.toUserJid(), selfJid.toUserJid());
    }

    /**
     * Validates that the sender has permission to send messages to a CAG (Community Announcement Group).
     * <p>
     * Per WhatsApp Web: CAGs with SEND_MESSAGES policy set to ADMINS only allow admins to send
     * regular messages. Non-admin members can only send reactions and certain other message types.
     *
     * @param groupJid      the group JID
     * @param senderJid     the sender's JID
     * @param messageType   description of the message type for error reporting
     * @throws WhatsAppMessageException.Send.Unauthorized if sender lacks permission
     *
     * @apiNote WAWebGroupPermissionsApi.validateCagPermissions
     */
    private void validateCagSendPermission(Jid groupJid, Jid senderJid, String messageType) {
        var chatOpt = store.findChatByJid(groupJid);
        if (chatOpt.isEmpty()) {
            return; // Can't validate without chat metadata
        }

        var chat = chatOpt.get();
        var metadataOpt = chat.groupMetadata();
        if (metadataOpt.isEmpty()) {
            return; // Not a group or no metadata
        }

        var metadata = metadataOpt.get();

        // Check if this is a CAG (has parent community)
        if (metadata.parentCommunityJid().isEmpty()) {
            return; // Not a CAG, no special restrictions
        }

        // Check the SEND_MESSAGES policy
        var sendPolicy = metadata.getPolicy(GroupSetting.SEND_MESSAGES).orElse(ChatSettingPolicy.ANYONE);
        if (sendPolicy != ChatSettingPolicy.ADMINS) {
            return; // Anyone can send
        }

        // Check if sender is an admin
        var senderUserJid = senderJid.toUserJid();
        var isAdmin = metadata.participants().stream()
                .filter(p -> p.jid().toUserJid().equals(senderUserJid))
                .findFirst()
                .map(p -> p.role() == ChatRole.ADMIN || p.role() == ChatRole.FOUNDER)
                .orElse(false);

        if (!isAdmin) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Non-admin {0} attempted to send {1} to CAG {2}",
                    senderJid, messageType, groupJid);
            throw new WhatsAppMessageException.Send.Unauthorized(
                    "Cannot send " + messageType + " to announcement group: only admins can send messages"
            );
        }
    }

    /**
     * Gets the original message recipients for a revoke or edit operation.
     * <p>
     * Per WhatsApp Web: revokes and edits should only be sent to devices that
     * received the original message. This queries the message's receipt data
     * to filter the recipient list.
     *
     * @param groupJid  the group JID
     * @param messageId the original message ID
     * @param allDevices all devices that would normally receive the message
     * @return filtered collection containing only original recipients, or all devices if not found
     *
     * @apiNote WAWebSendGroupMsgJob.getGroupSendList: uses messageInfoTable to filter recipients
     */
    private Collection<Jid> getOriginalMessageRecipients(Jid groupJid, String messageId, Collection<Jid> allDevices) {
        // Find the original message
        var messageOpt = store.findMessageById(groupJid, messageId);
        if (messageOpt.isEmpty() || !(messageOpt.get() instanceof ChatMessageInfo chatMessageInfo)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Original message {0} not found for revoke/edit, sending to all devices",
                    messageId);
            return allDevices;
        }

        // Get delivered JIDs from receipt
        var receipt = chatMessageInfo.receipt();
        var deliveredJids = receipt.deliveredJids();

        if (deliveredJids.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "No delivery receipts for message {0}, sending to all devices",
                    messageId);
            return allDevices;
        }

        // Filter devices to only include those that received the original message
        var originalRecipientUsers = deliveredJids.stream()
                .map(Jid::toUserJid)
                .collect(Collectors.toSet());

        var filteredDevices = allDevices.stream()
                .filter(device -> originalRecipientUsers.contains(device.toUserJid()))
                .toList();

        LOGGER.log(System.Logger.Level.DEBUG,
                "Filtered revoke/edit recipients for {0}: {1} -> {2} devices",
                messageId, allDevices.size(), filteredDevices.size());

        return filteredDevices.isEmpty() ? allDevices : filteredDevices;
    }

    /**
     * Ensures phone number to LID mappings are registered for the given user JIDs.
     * <p>
     * Per WhatsApp Web: before establishing sessions with devices, we need to ensure
     * we have the correct LID mappings so sessions are established with the right
     * addressing mode. This populates the store's phone-to-LID mappings from contacts.
     *
     * @param userJids the user JIDs to ensure mappings for
     *
     * @apiNote WAWebE2ESessionService: calls ensurePhoneNumberToLidMapping before session establishment
     */
    private void ensurePhoneNumberToLidMapping(Collection<Jid> userJids) {
        for (var userJid : userJids) {
            if (userJid == null || !userJid.hasUserServer()) {
                continue;
            }

            // Check if we already have a mapping
            var existingLid = store.findLidByPhone(userJid);
            if (existingLid.isPresent()) {
                continue;
            }

            // Try to get LID from contact
            store.findContactByJid(userJid)
                    .flatMap(contact -> contact.lid())
                    .ifPresent(lid -> store.registerLidMapping(userJid, lid));
        }
    }

    /**
     * Gets or generates a message secret for reporting tokens and content bindings.
     * <p>
     * Per WhatsApp Web: message secret is stored in messageContextInfo.messageSecret
     * If not present, generate a new one.
     *
     * @param message the message container
     * @return the message secret bytes
     *
     * @apiNote WAWebReportingTokenUtils.genReportingTokenBody checks for existing messageSecret
     */
    private byte[] getOrGenerateMessageSecret(MessageContainer message) {
        // Check if message already has a secret
        var deviceInfo = message.deviceInfo().orElse(null);
        if (deviceInfo != null && deviceInfo.messageSecret() != null && deviceInfo.messageSecret().length > 0) {
            return deviceInfo.messageSecret();
        }

        // Generate new message secret
        var secret = new byte[MESSAGE_SECRET_LENGTH];
        secureRandom.nextBytes(secret);
        return secret;
    }

    /**
     * Populates ICDC metadata for a 1:1 message.
     * <p>
     * Per WhatsApp Web WAWebICDCMetaApi.populateICDCMeta: creates deviceListMetadata
     * containing sender and recipient ICDC information.
     *
     * @param message      the original message
     * @param senderJid    the sender JID
     * @param recipientJid the recipient JID
     * @param messageSecret the message secret
     * @return a new message container with ICDC metadata populated
     *
     * @apiNote WAWebICDCMetaApi.populateICDCMeta
     */
    private MessageContainer populateIcdcMetadata(
            MessageContainer message,
            Jid senderJid,
            Jid recipientJid,
            byte[] messageSecret
    ) {
        // WAWebIdentityIcdcApi.getICDCMeta for sender and recipient
        var deviceListMetadata = icdcMetadataService.createDeviceListMetadataForMessage(senderJid, recipientJid);

        return updateMessageWithDeviceInfo(message, deviceListMetadata, messageSecret);
    }

    /**
     * Populates ICDC metadata for a group message.
     * <p>
     * Per WhatsApp Web: group messages only include sender ICDC info, not per-recipient.
     *
     * @param message       the original message
     * @param senderJid     the sender JID
     * @param messageSecret the message secret
     * @return a new message container with ICDC metadata populated
     *
     * @apiNote WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs
     */
    private MessageContainer populateIcdcMetadataForGroup(
            MessageContainer message,
            Jid senderJid,
            byte[] messageSecret
    ) {
        // WAWebIdentityIcdcApi.getICDCMeta for sender only
        var senderMeta = icdcMetadataService.getIcdcMeta(senderJid);
        var deviceListMetadata = icdcMetadataService.createDeviceListMetadata(senderMeta, null);

        return updateMessageWithDeviceInfo(message, deviceListMetadata, messageSecret);
    }

    /**
     * Updates a message with device info containing ICDC metadata and message secret.
     * <p>
     * Per WhatsApp Web: comment messages should NOT include messageSecret in context info.
     *
     * @param message            the original message
     * @param deviceListMetadata the device list metadata
     * @param messageSecret      the message secret
     * @return a new message container with updated device info
     *
     * @apiNote WAWebE2EProtoUtils: messageSecret is removed from messageContextInfo for comment messages
     */
    private MessageContainer updateMessageWithDeviceInfo(
            MessageContainer message,
            DeviceListMetadata deviceListMetadata,
            byte[] messageSecret
    ) {
        var existingDeviceInfo = message.deviceInfo().orElse(null);

        // Per WhatsApp Web: comment messages should not have messageSecret
        var effectiveSecret = isCommentMessage(message) ? null : messageSecret;

        DeviceContextInfo newDeviceInfo;
        if (existingDeviceInfo != null) {
            // Update existing device info
            newDeviceInfo = new DeviceContextInfoBuilder()
                    .deviceListMetadata(deviceListMetadata != null ? deviceListMetadata : existingDeviceInfo.deviceListMetadata())
                    .deviceListMetadataVersion(deviceListMetadata != null ? 2 : existingDeviceInfo.deviceListMetadataVersion())
                    .messageSecret(effectiveSecret != null ? effectiveSecret : (isCommentMessage(message) ? null : existingDeviceInfo.messageSecret()))
                    .paddingBytes(existingDeviceInfo.paddingBytes())
                    .build();
        } else if (deviceListMetadata != null || effectiveSecret != null) {
            // Create new device info
            newDeviceInfo = new DeviceContextInfoBuilder()
                    .deviceListMetadata(deviceListMetadata)
                    .deviceListMetadataVersion(deviceListMetadata != null ? 2 : 0)
                    .messageSecret(effectiveSecret)
                    .build();
        } else {
            return message;
        }

        return message.withDeviceInfo(newDeviceInfo);
    }

    /**
     * Checks if a message is a comment message.
     *
     * @param message the message container
     * @return true if the message is a comment
     *
     * @apiNote WAWebE2EProtoUtils: checks message type for comment
     */
    private boolean isCommentMessage(MessageContainer message) {
        return message.unbox().type() == Message.Type.COMMENT;
    }

    /**
     * Adds SAGA debug info support payload for CAPI support accounts.
     * <p>
     * Per WhatsApp Web WAWebSendMsgJob: when sending to a CAPI support account with SAGA V1 enabled,
     * adds debug information to the messageContextInfo.supportPayload field.
     *
     * @param message      the message to potentially update
     * @param recipientJid the message recipient JID
     * @return the message with support payload added, or the original message if not applicable
     *
     * @apiNote WAWebSendMsgJob: isCAPISupportAccount && getIsSagaV1Enabled && getIsSagaV1ReengagementEnabled
     */
    private MessageContainer addSagaDebugInfoIfApplicable(MessageContainer message, Jid recipientJid) {
        var supportPayload = sagaDebugInfoService.getSupportPayloadIfApplicable(recipientJid);
        if (supportPayload == null) {
            return message;
        }

        // Get or create device info and set the support payload
        var existingDeviceInfo = message.deviceInfo().orElse(null);
        DeviceContextInfo newDeviceInfo;

        if (existingDeviceInfo != null) {
            // Clone existing device info and add support payload
            newDeviceInfo = new DeviceContextInfoBuilder()
                    .deviceListMetadata(existingDeviceInfo.deviceListMetadata())
                    .deviceListMetadataVersion(existingDeviceInfo.deviceListMetadataVersion())
                    .messageSecret(existingDeviceInfo.messageSecret())
                    .paddingBytes(existingDeviceInfo.paddingBytes())
                    .botMessageSecret(existingDeviceInfo.botMessageSecret())
                    .botMetadata(existingDeviceInfo.botMetadata())
                    .capiCreatedGroup(existingDeviceInfo.capiCreatedGroup())
                    .supportPayload(supportPayload)
                    .threadId(existingDeviceInfo.threadId())
                    .build();
        } else {
            // Create new device info with just support payload
            newDeviceInfo = new DeviceContextInfoBuilder()
                    .supportPayload(supportPayload)
                    .build();
        }

        return message.withDeviceInfo(newDeviceInfo);
    }

    /**
     * Gets unique user JIDs from a list of device JIDs.
     * <p>
     * Per WhatsApp Web WAWebMsgRcatUtils.genContentBindingForMsg: collects unique users
     * from the sender and recipient devices.
     *
     * @param senderJid the sender JID
     * @param devices   the device JIDs
     * @return list of unique user JIDs
     */
    private List<Jid> getUniqueUserJids(Jid senderJid, Collection<Jid> devices) {
        var uniqueUsers = new HashMap<String, Jid>();
        uniqueUsers.put(senderJid.user(), senderJid.toUserJid());
        for (var device : devices) {
            var userJid = device.toUserJid();
            uniqueUsers.putIfAbsent(device.user(), userJid);
        }
        return new ArrayList<>(uniqueUsers.values());
    }

    /**
     * Checks if a message is a bot feedback message.
     * <p>
     * Per WhatsApp Web WAWebMsgGetters.getIsBotFeedbackMessage.
     *
     * @param message the message to check
     * @return true if this is a bot feedback message
     */
    private boolean isBotFeedbackMessage(MessageContainer message) {
        if (!botMessageService.isBotsEnabled()) {
            return false;
        }

        // Check context info for bot-related fields
        var unwrapped = message.unbox();
        var contextualMessage = unwrapped.contentWithContext().orElse(null);
        if (contextualMessage != null) {
            var contextInfo = contextualMessage.contextInfo().orElse(null);
            if (contextInfo != null) {
                // Check if replying to a bot message
                var quotedParticipant = contextInfo.quotedMessageSenderJid().orElse(null);
                if (quotedParticipant != null && botMessageService.isBot(quotedParticipant)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the invoked bot JID from the message.
     * <p>
     * Per WhatsApp Web: message.invokedBotWid
     *
     * @param message the message to check
     * @return the invoked bot JID, or null
     */
    private Jid getInvokedBotWid(MessageContainer message) {
        var unwrapped = message.unbox();
        var contextualMessage = unwrapped.contentWithContext().orElse(null);
        if (contextualMessage != null) {
            var contextInfo = contextualMessage.contextInfo().orElse(null);
            if (contextInfo != null) {
                // Check for explicit bot invocation from mentions
                var invokedBot = contextInfo.mentions().stream()
                        .filter(botMessageService::isBot)
                        .findFirst()
                        .orElse(null);
                if (invokedBot != null) {
                    return invokedBot;
                }
            }
        }
        return null;
    }

    /**
     * Gets the bot recipient for feedback messages.
     * <p>
     * Per WhatsApp Web: when sending feedback, determines the target bot.
     *
     * @param message  the message to check
     * @param groupJid the group JID
     * @return the bot JID to send feedback to, or null
     */
    private Jid getBotRecipientForFeedback(MessageContainer message, Jid groupJid) {
        var unwrapped = message.unbox();
        var contextualMessage = unwrapped.contentWithContext().orElse(null);
        if (contextualMessage != null) {
            var contextInfo = contextualMessage.contextInfo().orElse(null);
            if (contextInfo != null) {
                // For feedback, the bot is the one we're responding to
                var quotedParticipant = contextInfo.quotedMessageSenderJid().orElse(null);
                if (quotedParticipant != null && botMessageService.isBot(quotedParticipant)) {
                    return quotedParticipant;
                }
            }
        }
        return null;
    }

    /**
     * Gets the protocol message key participant.
     * <p>
     * Per WhatsApp Web: used for bot feedback to identify the original bot.
     *
     * @param message the message to check
     * @return the protocol message key participant, or null
     *
     * @apiNote WAWebSendGroupSkmsgJob (function L): e.protocolMessageKey?.participant
     */
    private Jid getProtocolMessageKeyParticipant(MessageContainer message) {
        var unwrapped = message.unbox();
        var protocolMessage = unwrapped.protocolMessage().orElse(null);
        if (protocolMessage != null) {
            var key = protocolMessage.key().orElse(null);
            if (key != null) {
                return key.senderJid().orElse(null);
            }
        }
        return null;
    }

    /**
     * Gets the bot JID for revoke/response messages.
     * <p>
     * Per WhatsApp Web: message.botRespOrInvocationRevokeBotWid
     *
     * @param message the message to check
     * @return the bot JID for revoke, or null
     *
     * @apiNote WAWebSendGroupSkmsgJob (function L): e.botRespOrInvocationRevokeBotWid
     */
    private Jid getBotRespOrInvocationRevokeJid(MessageContainer message) {
        var unwrapped = message.unbox();
        var protocolMessage = unwrapped.protocolMessage().orElse(null);
        if (protocolMessage != null && protocolMessage.protocolType() == ProtocolMessage.Type.REVOKE) {
            var key = protocolMessage.key().orElse(null);
            if (key != null) {
                var participant = key.senderJid().orElse(null);
                if (participant != null && botMessageService.isBot(participant)) {
                    return participant;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the message is a revoke message targeting a bot's message.
     * <p>
     * Per WhatsApp Web WAWebMsgGetters.getIsRevokeForMsgFromOrDeliveredToBot
     *
     * @param message the message to check
     * @return true if this is a revoke for a bot message
     *
     * @apiNote WAWebMsgGetters.getIsRevokeForMsgFromOrDeliveredToBot
     */
    private boolean isRevokeForBotMessage(MessageContainer message) {
        var unwrapped = message.unbox();
        var protocolMessage = unwrapped.protocolMessage().orElse(null);
        if (protocolMessage != null && protocolMessage.protocolType() == ProtocolMessage.Type.REVOKE) {
            var key = protocolMessage.key().orElse(null);
            if (key != null) {
                var participant = key.senderJid().orElse(null);
                if (participant != null && botMessageService.isBot(participant)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the meta node context for a message.
     * <p>
     * Per WhatsApp Web WAWebSendMsgMetaNode.genMetaNode: extracts context information
     * including LID origin type and hashed AI thread ID.
     *
     * @param chatJid the chat JID
     * @param message the message container
     * @return the meta node context
     *
     * @apiNote WAWebSendMsgMetaNode.genMetaNode
     */
    private MetaNodeService.MetaNodeContext getMetaNodeContext(Jid chatJid, MessageContainer message) {
        // Per WhatsApp Web: get LID origin type from chat
        MetaNodeService.LidOriginType lidOriginType = null;
        if (chatJid.hasLidServer()) {
            var chat = store.findChatByJid(chatJid).orElse(null);
            if (chat != null) {
                var originType = chat.lidOriginType().orElse(null);
                if (originType != null) {
                    lidOriginType = MetaNodeService.LidOriginType.fromValue(originType);
                }
            }
        }

        // Per WhatsApp Web WAWebThreadMsgUtils.getMsgAiThread: get AI thread for hashed ID
        // The AI thread is stored in DeviceContextInfo (MessageContextInfo), not ContextInfo
        String hashedAiThreadId = null;
        var unwrapped = message.unbox();
        var deviceContextInfo = unwrapped.deviceContextInfo().orElse(null);
        if (deviceContextInfo != null) {
            var aiThread = findAiThread(deviceContextInfo);
            if (aiThread != null) {
                var threadKey = aiThread.threadKey().orElse(null);
                if (threadKey != null) {
                    hashedAiThreadId = metaNodeService.generateHashedAiThreadId(threadKey.id()).orElse(null);
                }
            }
        }

        // Per WhatsApp Web: check for hosted sender intent
        var appendHostedSenderIntent = store.isBizHostedDevicesEnabled() && store.isHostedMeAccount();

        return new MetaNodeService.MetaNodeContext(
                lidOriginType,
                hashedAiThreadId,
                appendHostedSenderIntent,
                false // isCategoryPeerMessage - typically false for regular sends
        );
    }

    /**
     * Gets the bot node for 1:1 user messages.
     * <p>
     * Per WhatsApp Web WAWebSendMsgCreateFanoutStanza: creates bot node when sending
     * to bots or sending bot-related messages.
     *
     * @param message the message container
     * @param chatJid the chat JID
     * @return the bot node, or null if not applicable
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza (bot node creation)
     */
    private Node getBotNodeForUser(MessageContainer message, Jid chatJid) {
        if (!botMessageService.isBotsEnabled()) {
            return null;
        }

        // Per WhatsApp Web: get bot message type
        var unwrapped = message.unbox();
        var subtype = unwrapped.protocolMessage()
                .map(pm -> pm.protocolType().name().toLowerCase())
                .orElse(null);
        var botType = botMessageService.getBotMessageType(message, subtype, chatJid);

        if (botType == null && !botMessageService.isBot(chatJid)) {
            return null;
        }

        // Per WhatsApp Web: get AI thread ID for bot node (client_thread_id attribute)
        // The AI thread is stored in DeviceContextInfo (MessageContextInfo)
        String clientThreadId = null;
        var deviceContextInfo = unwrapped.deviceContextInfo().orElse(null);
        if (deviceContextInfo != null) {
            var aiThread = findAiThread(deviceContextInfo);
            if (aiThread != null) {
                var threadKey = aiThread.threadKey().orElse(null);
                if (threadKey != null) {
                    clientThreadId = threadKey.id();
                }
            }
        }

        // Per WhatsApp Web: get biz bot type for local_automated_type attribute
        // The bizBotType would come from message metadata if this is a business bot message
        MessageStanzaNodeBuilders.BizBotType bizBotType = null;
        // Note: BizBotType is typically set by business integration, not extracted from messages

        // Per WhatsApp Web WAWebSimpleSignalPNToFBIDMigration.getFbidBotPersonaType:
        // Persona type is determined from the bot's FBID, NOT from message metadata.
        // - Meta AI bot (FBID 867051314767696) -> "default"
        // - Known first-party character bots -> "first_party_character"
        // - Unknown FBID bots (UGC) -> "ugc"
        String personaType = botMessageService.getFbidBotPersonaTypeValue(chatJid);

        // Create bot node only if we have a type, thread ID, or persona type
        if (botType != null || bizBotType != null || clientThreadId != null || personaType != null) {
            return MessageStanzaNodeBuilders.createBotNode(botType, bizBotType, clientThreadId, personaType);
        }

        return null;
    }

    /**
     * Gets peer recipient attributes for LID chats.
     * <p>
     * Per WhatsApp Web WAWebSendMsgCreateFanoutStanza: these attributes are used for
     * LID migration to provide peer recipient information.
     *
     * @param chatJid the chat JID
     * @return the peer recipient attributes
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza peer_recipient_* attributes
     */
    private PeerRecipientAttributes getPeerRecipientAttributes(Jid chatJid) {
        // WAWebSendMsgCreateFanoutStanza: logic for peer_recipient_* attributes
        // These are populated based on LID migration status and contact info

        Jid peerRecipientLid = null;
        Jid peerRecipientPn = null;
        String peerRecipientUsername = null;
        Jid recipientPn = null;

        if (chatJid.hasLidServer()) {
            // For LID chats, may need to provide phone number to peer
            var contact = store.findContactByJid(chatJid).orElse(null);
            if (contact != null) {
                // Per WhatsApp Web: if contact has phoneNumber and shareOwnPn is not true
                var phoneNumber = contact.phoneNumber().orElse(null);
                if (phoneNumber != null) {
                    recipientPn = Jid.ofPhoneNumber(phoneNumber);
                }
                // Username for username display
                var username = contact.username().orElse(null);
                if (username != null) {
                    peerRecipientUsername = username;
                }
            }
        } else if (chatJid.hasUserServer()) {
            // For PN chats where recipient has LID
            var chat = store.findChatByJid(chatJid).orElse(null);
            if (chat != null) {
                var accountLid = chat.accountLid().orElse(null);
                if (accountLid != null) {
                    peerRecipientLid = accountLid;
                }
            }
        }

        return new PeerRecipientAttributes(peerRecipientLid, peerRecipientPn, peerRecipientUsername, recipientPn);
    }

    /**
     * Handles the refresh_lid flag from server ack.
     * <p>
     * Per WhatsApp Web WAWebSendUserMsgJob.maybeRefreshLid: triggers a contact sync
     * when the server requests LID refresh.
     *
     * @param chatJid the chat JID to refresh
     *
     * @apiNote WAWebSendUserMsgJob.maybeRefreshLid
     */
    private void handleRefreshLid(Jid chatJid) {
        // Per WhatsApp Web: convert LID to PN and trigger contact sync
        Thread.ofVirtual().name("refresh-lid-" + chatJid).start(() -> {
            try {
                // Clear cached device list to force re-sync from server
                store.removeDeviceList(chatJid.toUserJid());
                // Trigger device list sync by fetching fresh data
                LOGGER.log(System.Logger.Level.DEBUG, "Triggering device list sync for LID refresh: {0}", chatJid);
                deviceService.getDeviceLists(List.of(chatJid.toUserJid()), "refresh_lid", null, false);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to sync device list for LID refresh {0}: {1}", chatJid, e.getMessage());
            }
        });
    }

    /**
     * Peer recipient attributes for LID migration.
     *
     * @param peerRecipientLid      peer's LID if chat is PN but peer has LID
     * @param peerRecipientPn       peer's PN for certain cases
     * @param peerRecipientUsername peer's username for username display
     * @param recipientPn           recipient's PN for LID chats
     */
    private record PeerRecipientAttributes(
            Jid peerRecipientLid,
            Jid peerRecipientPn,
            String peerRecipientUsername,
            Jid recipientPn
    ) {}

    /**
     * Wraps a message in DeviceSentMessage for delivery to self devices.
     * <p>
     * Per WhatsApp Web WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage:
     * Self devices receive messages wrapped in deviceSentMessage so they know
     * the actual destination of the message (the chat recipient).
     * <p>
     * If the original message has deviceContextInfo, it is lifted to the outer
     * container and removed from the inner message.
     *
     * @param message        the message to wrap
     * @param destinationJid the destination chat JID
     * @return a new MessageContainer containing the DeviceSentMessage
     *
     * @apiNote WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage
     */
    private MessageContainer wrapDeviceSentMessage(MessageContainer message, Jid destinationJid) {
        // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage:
        // Creates deviceSentMessage { destinationJid, message }
        //
        // Per WhatsApp Web: destinationJid uses legacy format (toString({legacy: true}))
        // In Cobalt, Jid handles this through its serialization format

        var deviceInfo = message.deviceInfo().orElse(null);
        MessageContainer innerMessage;

        // If the message has deviceInfo, remove it from the inner message
        // (it will be placed in the outer container)
        if (deviceInfo != null) {
            innerMessage = message.withDeviceInfo(null);
        } else {
            innerMessage = message;
        }

        // Create the DeviceSentMessage wrapper
        var deviceSentMessage = new DeviceSentMessageBuilder()
                .destinationJid(destinationJid.toUserJid())
                .message(innerMessage)
                .build();

        // Create the outer container with the DeviceSentMessage
        // If there was deviceInfo, place it in the outer container
        var builder = new MessageContainerBuilder()
                .deviceSentMessage(deviceSentMessage);

        if (deviceInfo != null) {
            builder.deviceInfo(deviceInfo);
        }

        return builder.build();
    }

    /**
     * Gets the message type attribute for the stanza.
     * <p>
     * Per WhatsApp Web WAWebE2EProtoUtils.typeAttributeFromProtobuf: recursively unwraps
     * wrapper messages (ephemeral, deviceSent, etc.) to determine the actual type.
     *
     * @param message the message container
     * @return the type attribute string
     *
     * @apiNote WAWebE2EProtoUtils.typeAttributeFromProtobuf
     */
    private String getMessageType(MessageContainer message) {
        // WAWebE2EProtoUtils.typeAttributeFromProtobuf: recursively unwrap wrapper messages
        var unwrapped = message.unbox();
        var type = unwrapped.type();

        return switch (type) {
            case REACTION, ENCRYPTED_REACTION -> "reaction";
            case POLL_CREATION, POLL_UPDATE -> "poll";
            // WAWebE2EProtoUtils: eventMessage or encEventResponseMessage -> "event"
            case EVENT, ENCRYPTED_EVENT_RESPONSE -> "event";
            default -> {
                // WAWebE2EProtoUtils: extendedTextMessage with matchedText -> "media"
                var content = unwrapped.content();
                if (content instanceof com.github.auties00.cobalt.model.message.standard.TextMessage textMsg) {
                    var matchedText = textMsg.matchedText().orElse(null);
                    if (matchedText != null && !matchedText.trim().isEmpty()) {
                        yield "media";
                    }
                }
                // WAWebE2EProtoUtils: protocolMessage, conversation, extendedTextMessage, etc. -> "text"
                yield "text";
            }
        };
    }

    /**
     * Gets the media type attribute for encryption nodes.
     * <p>
     * Per WhatsApp Web WAWebBackendJobsCommon.mediaTypeFromProtobuf: extracts the media type
     * from the message, recursively unwrapping wrapper messages.
     *
     * @param message the message container
     * @return the media type string, or null if not a media message
     *
     * @apiNote WAWebBackendJobsCommon.mediaTypeFromProtobuf
     */
    private String getMediaType(MessageContainer message) {
        // WAWebBackendJobsCommon.mediaTypeFromProtobuf: recursively unwrap
        var unwrapped = message.unbox();
        var content = unwrapped.content();

        // Handle specific message types with subtypes
        if (content instanceof com.github.auties00.cobalt.model.message.standard.AudioMessage audioMsg) {
            // WAWebBackendJobsCommon: ptt if voiceMessage/ptt is true
            return audioMsg.voiceMessage() ? "ptt" : "audio";
        }

        if (content instanceof com.github.auties00.cobalt.model.message.standard.VideoOrGifMessage videoMsg) {
            // WAWebBackendJobsCommon: gif if gifPlayback is true
            return videoMsg.gifPlayback() ? "gif" : "video";
        }

        if (content instanceof com.github.auties00.cobalt.model.message.standard.LocationMessage locMsg) {
            // WAWebBackendJobsCommon: livelocation if isLive is true
            // Note: LocationMessage doesn't have isLive in Cobalt - using type check
            return "location";
        }

        if (content instanceof com.github.auties00.cobalt.model.message.standard.TextMessage textMsg) {
            // WAWebBackendJobsCommon: url if matchedText is present and not empty
            var matchedText = textMsg.matchedText().orElse(null);
            if (matchedText != null && !matchedText.trim().isEmpty()) {
                return "url";
            }
            return null;
        }

        // Handle by message type
        return switch (unwrapped.type()) {
            case IMAGE -> "image";
            case VIDEO -> "video";
            case AUDIO -> "audio";
            case DOCUMENT -> "document";
            case STICKER -> "sticker";
            case CONTACT -> "vcard";  // WAWebBackendJobsCommon uses "vcard" not "contact"
            case CONTACT_ARRAY -> "vcard";  // WAWebBackendJobsCommon uses "vcard" for multi_vcard too
            case LOCATION -> "location";
            case LIVE_LOCATION -> "livelocation";
            case LIST -> "list";
            case LIST_RESPONSE -> "list_response";
            case BUTTONS_RESPONSE -> "buttons_response";
            case PAYMENT_ORDER -> "order";
            case PRODUCT -> "product";
            case NATIVE_FLOW_RESPONSE, INTERACTIVE_RESPONSE -> "native_flow_response";
            case GROUP_INVITE -> "url";  // WAWebBackendJobsCommon: groupInviteMessage -> Url
            default -> null;
        };
    }

    private String getAddressingMode(Jid chatJid) {
        return chatJid.hasLidServer() ? "lid" : null;
    }

    /**
     * Gets the native flow name from an interactive message or buttons message.
     * <p>
     * Per WhatsApp Web WAWebE2EProtoUtils.getBizNativeFlowName:
     * <ul>
     *   <li>Check interactiveMessage.nativeFlowMessage.buttons - return first button name</li>
     *   <li>Check buttonsMessage.buttons with nativeFlowInfo - return native flow name</li>
     *   <li>If interactive message has body/header/footer but no buttons and no shop, return "MIXED"</li>
     * </ul>
     *
     * @param message the message container to check
     * @return the native flow name, or null if not applicable
     *
     * @apiNote WAWebE2EProtoUtils.getBizNativeFlowName
     */
    private String getNativeFlowName(MessageContainer message) {
        var unwrapped = message.unbox();
        var content = unwrapped.content();

        // WAWebE2EProtoUtils: check interactiveMessage.nativeFlowMessage.buttons
        if (content instanceof com.github.auties00.cobalt.model.message.button.InteractiveMessage interactive) {
            var nativeFlow = interactive.contentNativeFlow().orElse(null);

            // If native flow with buttons, return first button name
            if (nativeFlow != null && nativeFlow.buttons() != null && !nativeFlow.buttons().isEmpty()) {
                var firstButton = nativeFlow.buttons().getFirst();
                if (firstButton.name() != null) {
                    return firstButton.name();
                }
            }

            // WAWebE2EProtoUtils: check for MIXED case
            // If no buttons but has body/header/footer content (and no shopStorefrontMessage)
            boolean hasButtons = nativeFlow != null && nativeFlow.buttons() != null && !nativeFlow.buttons().isEmpty();
            if (!hasButtons) {
                var hasBody = interactive.body().map(b -> b.content() != null && !b.content().isEmpty()).orElse(false);
                var hasHeader = interactive.header().isPresent();
                var hasFooter = interactive.footer().map(f -> f.content() != null && !f.content().isEmpty()).orElse(false);
                var hasContent = hasBody || hasHeader || hasFooter;
                var hasShop = interactive.contentShop().isPresent();

                if (hasContent && !hasShop) {
                    return "MIXED";
                }
            }
        }

        // WAWebE2EProtoUtils: check buttonsMessage.buttons with single button having nativeFlowInfo
        if (content instanceof com.github.auties00.cobalt.model.message.button.ButtonsMessage buttonsMsg) {
            var buttons = buttonsMsg.buttons();
            // Per WhatsApp Web: only check if exactly one button with nativeFlowInfo
            if (buttons != null && buttons.size() == 1) {
                var button = buttons.getFirst();
                // Button has bodyNativeFlow() which returns Optional<NativeFlowInfo>
                var nativeFlowInfo = button.bodyNativeFlow();
                if (nativeFlowInfo.isPresent()) {
                    return nativeFlowInfo.get().name();
                }
            }
        }

        return null;
    }

    /**
     * Gets the encoded ADV device identity.
     *
     * @apiNote WAWebAdvSignatureApi.getADVEncodedIdentity
     */
    private byte[] getEncodedDeviceIdentity() {
        return SignedDeviceIdentitySpec.encode(store.signedDeviceIdentity().orElse(null));
    }

    /**
     * Finds the first AI thread from a DeviceContextInfo.
     * <p>
     * Per WhatsApp Web WAWebThreadMsgUtils.getMsgAiThread: looks for a thread
     * with type AI_THREAD and returns it.
     *
     * @param deviceContextInfo the device context info containing thread IDs
     * @return the AI thread, or null if not found
     */
    private MessageThreadId findAiThread(DeviceContextInfo deviceContextInfo) {
        var threadIds = deviceContextInfo.threadId();
        if (threadIds == null || threadIds.isEmpty()) {
            return null;
        }
        return threadIds.stream()
                .filter(MessageThreadId::isAiThread)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the decrypt-fail attribute for the message.
     * <p>
     * Per WhatsApp Web WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf:
     * Returns "hide" for messages that should not show placeholder on decrypt failure
     * (reactions, poll votes, edits, keep-in-chat, pin-in-chat, etc.)
     *
     * @param message the message to check
     * @return "hide" if should hide decrypt failure, null otherwise
     *
     * @apiNote WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf
     */
    private String getDecryptFailAttribute(MessageContainer message) {
        // WAWebE2EProtoUtils.decryptFailAttributeFromProtobuf: check unwrapped message
        var unwrapped = message.unbox();
        var type = unwrapped.type();

        return switch (type) {
            // WAWebE2EProtoUtils: reactionMessage or encReactionMessage -> Hide
            case REACTION, ENCRYPTED_REACTION -> "hide";
            // WAWebE2EProtoUtils: keepInChatMessage -> Hide
            case KEEP_IN_CHAT -> "hide";
            // WAWebE2EProtoUtils: editedMessage -> Hide
            case EDITED -> "hide";
            // WAWebE2EProtoUtils: pinInChatMessage -> Hide
            case PIN_IN_CHAT -> "hide";
            // WAWebE2EProtoUtils: encEventResponseMessage -> Hide
            case ENCRYPTED_EVENT_RESPONSE -> "hide";
            // WAWebE2EProtoUtils: secretEncryptedMessage with EVENT_EDIT -> Hide
            case SECRET_ENCRYPTED -> {
                var content = unwrapped.content();
                if (content instanceof com.github.auties00.cobalt.model.message.standard.SecretEncryptedMessage secret) {
                    var secretType = secret.secretEncType();
                    yield (secretType == com.github.auties00.cobalt.model.message.standard.SecretEncryptedMessage.SecretEncType.EVENT_EDIT)
                            ? "hide" : null;
                }
                yield null;
            }
            case POLL_UPDATE -> {
                // WAWebE2EProtoUtils: pollUpdateMessage.vote -> Hide (only if has vote)
                var pollUpdate = unwrapped.pollUpdateMessage().orElse(null);
                yield (pollUpdate != null && pollUpdate.votes() != null && !pollUpdate.votes().isEmpty())
                        ? "hide" : null;
            }
            case PROTOCOL -> {
                // Check for specific protocol message types that should hide
                var proto = unwrapped.protocolMessage().orElse(null);
                if (proto != null) {
                    var protoType = proto.protocolType();
                    if (protoType != null) {
                        yield switch (protoType) {
                            // WAWebE2EProtoUtils: EPHEMERAL_SYNC_RESPONSE -> Hide
                            case EPHEMERAL_SYNC_RESPONSE -> "hide";
                            // WAWebE2EProtoUtils: REQUEST_WELCOME_MESSAGE -> Hide
                            // (also applies to botInvokeMessage.message.protocolMessage with this type)
                            case REQUEST_WELCOME_MESSAGE -> "hide";
                            default -> {
                                // WAWebE2EProtoUtils: editedMessage inside protocolMessage -> Hide
                                yield proto.editedMessage().isPresent() ? "hide" : null;
                            }
                        };
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    /**
     * Gets the edit attribute for the message.
     * <p>
     * Per WhatsApp Web WAWebSendMsgCommonApi.editAttribute:
     * Returns the appropriate edit attribute based on message type.
     * <p>
     * Edit attribute values (from WAWebAck.EDIT_ATTR):
     * <ul>
     *   <li>1 = MESSAGE_EDIT</li>
     *   <li>5 = PIN_IN_CHAT</li>
     *   <li>7 = SENDER_REVOKE (also ADMIN_REVOKE, distinguished by subtype)</li>
     * </ul>
     *
     * @param message the message to check
     * @return the edit attribute string, or null
     *
     * @apiNote WAWebSendMsgCommonApi.editAttribute
     */
    private String getEditAttribute(MessageContainer message) {
        // WAWebSendMsgCommonApi.editAttribute: check unwrapped message
        var unwrapped = message.unbox();
        var type = unwrapped.type();

        // Check for protocol message types
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.PROTOCOL) {
            var proto = unwrapped.protocolMessage().orElse(null);
            if (proto != null && proto.protocolType() != null) {
                return switch (proto.protocolType()) {
                    case REVOKE -> "7";  // SENDER_REVOKE (or ADMIN_REVOKE based on subtype)
                    case MESSAGE_EDIT -> "1";  // MESSAGE_EDIT
                    default -> null;
                };
            }
        }

        // Check for reaction with empty text (revoked reaction)
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.REACTION) {
            var reaction = unwrapped.reactionMessage().orElse(null);
            if (reaction != null && (reaction.text() == null || reaction.text().isEmpty())) {
                return "7";  // SENDER_REVOKE for revoked reactions
            }
        }

        // WAWebSendMsgCommonApi.editAttribute: keepInChatMessage with UNDO_KEEP_FOR_ALL -> SENDER_REVOKE
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.KEEP_IN_CHAT) {
            var keepInChat = unwrapped.content();
            if (keepInChat instanceof com.github.auties00.cobalt.model.message.standard.KeepInChatMessage keepMsg) {
                // Per WAWebE2EProtoUtils: key.fromMe === true && keepType === UNDO_KEEP_FOR_ALL
                if (keepMsg.key() != null && keepMsg.key().fromMe()
                        && keepMsg.keepType() == com.github.auties00.cobalt.model.message.common.KeepInChat.Type.UNDO_KEEP_FOR_ALL) {
                    return "7";  // SENDER_REVOKE
                }
            }
        }

        // WAWebSendMsgCommonApi.editAttribute: pinInChatMessage -> PIN_IN_CHAT
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.PIN_IN_CHAT) {
            return "5";  // PIN_IN_CHAT
        }

        // WAWebSendMsgCommonApi.editAttribute: secretEncryptedMessage with EVENT_EDIT -> MESSAGE_EDIT
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.SECRET_ENCRYPTED) {
            var content = unwrapped.content();
            if (content instanceof com.github.auties00.cobalt.model.message.standard.SecretEncryptedMessage secret) {
                var secretType = secret.secretEncType();
                if (secretType == com.github.auties00.cobalt.model.message.standard.SecretEncryptedMessage.SecretEncType.EVENT_EDIT) {
                    return "1";  // MESSAGE_EDIT
                }
            }
        }

        return null;
    }

    /**
     * Waits for offline delivery to complete before sending messages.
     * <p>
     * Per WhatsApp Web WAWebEventsWaitForOfflineDeliveryEnd: messages should not
     * be sent until the offline delivery phase is complete to avoid race conditions.
     *
     * @param messageId the message ID (for logging)
     *
     * @apiNote WAWebEventsWaitForOfflineDeliveryEnd.waitForOfflineDeliveryEnd
     */
    private void waitForOfflineDeliveryEnd(String messageId) {
        if (store.isResumeFromRestartComplete()) {
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "[messaging] waiting for offline delivery end before sending {0} message", messageId);

        offlineDeliveryLock.lock();
        try {
            // Wait with timeout to avoid blocking forever
            var deadline = System.currentTimeMillis() + (OFFLINE_DELIVERY_WAIT_TIMEOUT_SECONDS * 1000L);
            while (!store.isResumeFromRestartComplete()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Timeout waiting for offline delivery end, proceeding anyway");
                    break;
                }
                offlineDeliveryComplete.await(remaining, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "Interrupted while waiting for offline delivery end");
        } finally {
            offlineDeliveryLock.unlock();
        }
    }

    /**
     * Signals that offline delivery is complete.
     * <p>
     * Called by the stream handler when offline delivery ends.
     * Wakes up any threads waiting to send messages.
     */
    public void signalOfflineDeliveryComplete() {
        offlineDeliveryLock.lock();
        try {
            offlineDeliveryComplete.signalAll();
        } finally {
            offlineDeliveryLock.unlock();
        }
    }

    /**
     * Schedules a user message resend after phash mismatch.
     * <p>
     * Per WhatsApp Web WAWebResendUserMsg.resendUserMsg:
     * After a phash mismatch, sync device lists and resend ONLY to new devices.
     * This is important: we don't re-encrypt for devices that already received the message.
     *
     * @apiNote WAWebResendUserMsg.resendUserMsg
     */
    private void scheduleUserMessageResend(
            String messageId,
            Jid chatJid,
            MessageContainer message,
            List<Jid> originalDevices,
            long ackTime,
            String editAttribute
    ) {
        // WAWebResendUserMsg: check timeout
        var now = System.currentTimeMillis() / 1000;
        if (now - ackTime > RESEND_TIMEOUT_SECONDS) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "resendUserMsg: {0}: skip resending due to {1} min timeout",
                    messageId, RESEND_TIMEOUT_SECONDS / 60);
            return;
        }

        // WAWebResendUserMsg: sync device list and resend asynchronously
        Thread.ofVirtual().name("resend-user-" + messageId).start(() -> {
            try {
                // WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob: clear cache to force fresh fetch
                var selfJid = getSelfDeviceJid(chatJid);
                var userJids = List.of(chatJid.toUserJid(), selfJid.toUserJid());

                // WAWebFetchResendMissingKeyJob.fetchResendMissingKeys: proactively fetch pre-keys
                // for all devices of the involved users before resending
                // This must be called BEFORE device list sync per WhatsApp Web flow
                if (!chatJid.hasServer(Jid.Server.LID)) {
                    fetchResendMissingKeys(userJids);
                }

                // Clear cached device lists to force re-sync from server
                for (var jid : userJids) {
                    store.removeDeviceList(jid);
                }

                // Get fresh device list from server
                var deviceLists = deviceService.getDeviceLists(userJids, "message", null, false);
                var newDevices = new ArrayList<>(fanoutCalculatorService.calculate(selfJid, deviceLists, null));

                // WAWebResendUserMsg: find devices that weren't in the original list
                // differenceBy(newDevices, originalDevices, String)
                var originalSet = new java.util.HashSet<>(originalDevices.stream()
                        .map(Jid::toString).toList());
                var devicesToResend = newDevices.stream()
                        .filter(d -> !originalSet.contains(d.toString()))
                        .toList();

                // WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity: exclude devices with changed identity
                var filteredDevices = filterDevicesWithChangedIdentity(ackTime, devicesToResend);

                if (filteredDevices.isEmpty()) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "resendUserMsg: {0}: skip resending to empty list", messageId);
                    return;
                }

                // WAWebResendUserMsg: check if message was overwritten by a revoke
                // If so, skip resending as the revoke should take precedence
                if (isMessageOverwrittenByRevoke(messageId)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "resendUserMsg: {0}: skip resending because message was overwritten by a revoke",
                            messageId);
                    return;
                }

                LOGGER.log(System.Logger.Level.DEBUG,
                        "resendUserMsg: {0}: resending to {1} devices: {2}",
                        messageId, filteredDevices.size(),
                        filteredDevices.stream().map(Jid::toString).toList());

                // WAWebResendUserMsg: resend ONLY to new devices, not all devices
                // This calls sendMsgToDeviceList with { isResendingMsg: true }
                sendToSpecificDevices(messageId, chatJid, message, filteredDevices, editAttribute, true);

            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "resendUserMsg: failed to resend {0}: {1}", messageId, e.getMessage());
            }
        });
    }

    /**
     * Schedules a group message resend after phash mismatch.
     * <p>
     * Per WhatsApp Web WAWebResendGroupMsg.resendGroupMsg:
     * After a phash mismatch, query group, sync device lists, and resend to new devices.
     *
     * @apiNote WAWebResendGroupMsg.resendGroupMsg
     */
    private void scheduleGroupMessageResend(
            String messageId,
            Jid groupJid,
            MessageContainer message,
            Collection<Jid> participants,
            List<Jid> originalDevices,
            long ackTime,
            String editAttribute,
            boolean isLidAddressingMode
    ) {
        // WAWebResendGroupMsg: check timeout
        var now = System.currentTimeMillis() / 1000;
        if (now - ackTime > RESEND_TIMEOUT_SECONDS) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "resendGroupMsg: {0}: skip resending due to {1} min timeout",
                    messageId, RESEND_TIMEOUT_SECONDS / 60);
            return;
        }

        // WAWebResendGroupMsg: sync and resend asynchronously
        Thread.ofVirtual().name("resend-group-" + messageId).start(() -> {
            try {
                // WAWebFetchResendMissingKeyJob.fetchResendMissingKeys: proactively fetch pre-keys
                // for all devices of the involved users before resending
                var uniqueUserJids = participants.stream()
                        .map(Jid::toUserJid)
                        .distinct()
                        .toList();
                fetchResendMissingKeys(uniqueUserJids);

                // WAWebGroupQueryBridge.sendQueryGroup: query group for updated participant list
                // Clear cached device lists to force re-sync from server
                for (var jid : participants) {
                    store.removeDeviceList(jid);
                }

                // Get fresh device list from server
                var selfJid = getSelfDeviceJid(groupJid);
                var deviceLists = deviceService.getDeviceLists(participants, "message", null, false);
                var newDevices = new ArrayList<>(fanoutCalculatorService.calculate(selfJid, deviceLists, null));

                // Find devices that weren't in the original list
                var originalSet = new java.util.HashSet<>(originalDevices.stream()
                        .map(Jid::toString).toList());
                var devicesToResend = newDevices.stream()
                        .filter(d -> !originalSet.contains(d.toString()))
                        .toList();

                // WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity: exclude devices with changed identity
                var filteredDevices = filterDevicesWithChangedIdentity(ackTime, devicesToResend);

                if (filteredDevices.isEmpty()) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "resendGroupMsg: {0}: skip resending to empty list", messageId);
                    return;
                }

                // WAWebResendGroupMsg: check if message was overwritten by a revoke
                if (isMessageOverwrittenByRevoke(messageId)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "resendGroupMsg: {0}: skip resending because message was overwritten by a revoke",
                            messageId);
                    return;
                }

                LOGGER.log(System.Logger.Level.DEBUG,
                        "resendGroupMsg: {0}: resending to {1} devices via GROUP_DIRECT",
                        messageId, filteredDevices.size());

                // WAWebSendDirectMsgToDeviceList: resend using GROUP_DIRECT fanout (per-device encryption)
                sendGroupDirect(messageId, groupJid, message, filteredDevices, editAttribute);

            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "resendGroupMsg: failed to resend {0}: {1}", messageId, e.getMessage());
            }
        });
    }

    /**
     * Sends a message directly to specific group devices (GROUP_DIRECT fanout).
     * <p>
     * Used for group resends after phash mismatch - encrypts per-device instead of
     * using sender key encryption.
     *
     * @param messageId     the message ID
     * @param groupJid      the group JID
     * @param message       the message container
     * @param targetDevices the specific devices to send to
     * @param editAttribute the edit attribute, or null
     * @return the send result
     *
     * @apiNote WAWebSendDirectMsgToDeviceList.sendDirectMsgToDeviceList
     */
    public MessageSendResult sendGroupDirect(
            String messageId,
            Jid groupJid,
            MessageContainer message,
            Collection<Jid> targetDevices,
            String editAttribute
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(targetDevices, "targetDevices cannot be null");

        if (targetDevices.isEmpty()) {
            throw new WhatsAppMessageException.Send.Unknown("No target devices for GROUP_DIRECT send");
        }

        LOGGER.log(System.Logger.Level.DEBUG, "sendGroupDirect: sending {0} to {1} devices",
                messageId, targetDevices.size());

        // WAWebE2ESessionService: ensure LID mappings before session establishment
        ensurePhoneNumberToLidMapping(targetDevices);

        // Fetch pre-keys for devices without sessions (with identity reason for resend)
        var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(targetDevices);
        if (!devicesNeedingSessions.isEmpty()) {
            preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions, true);
        }

        var decryptFail = getDecryptFailAttribute(message);
        var effectiveEditAttribute = editAttribute != null ? editAttribute : getEditAttribute(message);

        // Per-device encryption (NOT sender key)
        var encryptedNodes = encryptForDevices(new ArrayList<>(targetDevices), groupJid, message, decryptFail);

        var needsIdentity = encryptedNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());
        byte[] deviceIdentity = needsIdentity ? getEncodedDeviceIdentity() : null;

        // Build GROUP_DIRECT stanza
        var stanza = MessageSendStanzaBuilder.createGroupDirectStanza(
                messageId,
                groupJid,
                getMessageType(message),
                encryptedNodes,
                deviceIdentity,
                groupJid.hasLidServer() ? "lid" : "pn"
        );

        var response = client.sendNode(stanza);
        var ack = MessageSendAckParser.parse(response)
                .orElseThrow(() -> new WhatsAppMessageException.Send.Unknown("Invalid ack from server"));

        return new MessageSendResult(
                messageId,
                groupJid,
                ack.timestamp(),
                ack.phash(),
                ack.addressingMode(),
                ack.count().orElse(-1)
        );
    }

    /**
     * Sends a message to specific devices only (for resend operations).
     * <p>
     * This is used when resending after a phash mismatch - we only encrypt and send
     * to devices that didn't receive the original message.
     *
     * @param messageId       the message ID
     * @param chatJid         the chat JID
     * @param message         the message container
     * @param targetDevices   the specific devices to send to
     * @param editAttribute   the edit attribute, or null
     * @param isResend        whether this is a resend operation
     * @return the send result
     *
     * @apiNote WAWebSendMsgToDeviceList.sendMsgToDeviceList with isResendingMsg=true
     */
    private MessageSendResult sendToSpecificDevices(
            String messageId,
            Jid chatJid,
            MessageContainer message,
            Collection<Jid> targetDevices,
            String editAttribute,
            boolean isResend
    ) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(targetDevices, "targetDevices cannot be null");

        if (targetDevices.isEmpty()) {
            throw new WhatsAppMessageException.Send.Unknown("No target devices for resend");
        }

        LOGGER.log(System.Logger.Level.DEBUG, "sendToSpecificDevices: sending {0} to {1} devices",
                messageId, targetDevices.size());

        // WAWebE2ESessionService: ensure LID mappings before session establishment
        ensurePhoneNumberToLidMapping(targetDevices);

        // Fetch pre-keys for devices without sessions
        var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(targetDevices);
        if (!devicesNeedingSessions.isEmpty()) {
            preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions, true);
        }

        var decryptFail = getDecryptFailAttribute(message);
        var effectiveEditAttribute = editAttribute != null ? editAttribute : getEditAttribute(message);

        // Encrypt for specific devices only
        var encryptedNodes = encryptForDevices(new ArrayList<>(targetDevices), chatJid, message, decryptFail);

        var needsIdentity = encryptedNodes.stream()
                .anyMatch(node -> node.encryptionType().isPreKeyMessage());
        byte[] deviceIdentity = needsIdentity ? getEncodedDeviceIdentity() : null;

        // Build stanza with device_fanout="false" for resend
        var stanza = StanzaBuilder.createUserMessageStanza(
                messageId,
                chatJid,
                getMessageType(message),
                encryptedNodes,
                deviceIdentity,
                getAddressingMode(chatJid),
                effectiveEditAttribute,
                decryptFail,
                isResend  // device_fanout = "false"
        );

        var response = client.sendNode(stanza);
        var ack = MessageSendAckParser.parse(response)
                .orElseThrow(() -> new WhatsAppMessageException.Send.Unknown("Invalid ack from server"));

        // WAWebResendUserMsg: if we get another phash mismatch during resend, sync device list
        // but don't recurse into another resend
        if (ack.hasPhashMismatch()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "sendToSpecificDevices: got phash during resend for {0}", messageId);
            // Per WhatsApp Web: sync device list but don't resend again
            var selfJid = getSelfDeviceJid(chatJid);
            var userJids = List.of(chatJid.toUserJid(), selfJid.toUserJid());
            deviceService.getDeviceLists(userJids, "message", ack.phash(), false);
        }

        return new MessageSendResult(
                messageId,
                chatJid,
                ack.timestamp(),
                ack.phash(),
                ack.addressingMode(),
                ack.count().orElse(-1)
        );
    }

    /**
     * Flushes the signal protocol state to persistent storage if needed.
     * <p>
     * Per WhatsApp Web WAWebSignalProtocolStore.flushBufferToDiskIfNotMemOnlyMode:
     * Ensures signal session state is persisted before sending messages, preventing
     * data loss if the application crashes after sending but before state is saved.
     * <p>
     * This is critical because:
     * <ul>
     *   <li>Signal ratcheting state must be synced between sender and receiver</li>
     *   <li>Losing state after sending would cause decryption failures</li>
     *   <li>Pre-key state changes must be persisted before acknowledging use</li>
     * </ul>
     *
     * @apiNote WAWebSignalProtocolStore.flushBufferToDiskIfNotMemOnlyMode
     */
    private void flushSignalStateIfNeeded() {
        // Only serialize if the store is configured for persistence
        if (store.serializable()) {
            try {
                store.serialize();
                LOGGER.log(System.Logger.Level.TRACE, "Flushed signal state to storage");
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to flush signal state: {0}", e.getMessage());
                // Don't fail the send - state will be saved later
            }
        }
    }

    /**
     * Updates the identity range for devices after encryption.
     * <p>
     * Per WhatsApp Web WAWebSendMsgCommonApi.updateIdentityRange: records which devices
     * were encrypted for at a given message timestamp. This allows filtering devices
     * with changed identity during resend operations.
     *
     * @param messageTimestamp the timestamp of the message being sent
     * @param devices          the devices that were encrypted for
     *
     * @apiNote WAWebSendMsgCommonApi.updateIdentityRange
     */
    private void updateIdentityRangeAfterEncryption(long messageTimestamp, Collection<Jid> devices) {
        for (var device : devices) {
            var address = device.toSignalAddress().toString();
            store.updateDeviceIdentityRange(address, messageTimestamp);
        }
    }

    /**
     * Filters devices with changed identity since the original send.
     * <p>
     * Per WhatsApp Web WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity: when resending
     * a message, excludes devices whose identity changed after the original encryption.
     *
     * @param originalTimestamp the timestamp of the original message
     * @param devices           the devices to filter
     * @return list of devices that have NOT had identity changes since the original send
     *
     * @apiNote WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity
     */
    private List<Jid> filterDevicesWithChangedIdentity(long originalTimestamp, Collection<Jid> devices) {
        var result = new ArrayList<Jid>();
        for (var device : devices) {
            var address = device.toSignalAddress().toString();
            if (store.shouldIncludeDeviceInResend(address, originalTimestamp)) {
                result.add(device);
            }
        }
        return result;
    }

    /**
     * Proactively fetches missing pre-keys for all devices of the given user JIDs.
     * <p>
     * Per WhatsApp Web WAWebFetchResendMissingKeyJob.fetchResendMissingKeys: before resending
     * a message, ensures pre-keys are available for all devices of the involved users.
     * This is called BEFORE device list sync to ensure we have keys ready for any devices
     * we might discover.
     *
     * @param userJids the user JIDs to fetch pre-keys for
     *
     * @apiNote WAWebFetchResendMissingKeyJob.fetchResendMissingKeys
     */
    private void fetchResendMissingKeys(Collection<Jid> userJids) {
        try {
            // WAWebEventsWaitForOfflineDeliveryEnd.waitForOfflineDeliveryEnd: wait for offline delivery
            // to complete before fetching keys (important to avoid race conditions)
            waitForOfflineDeliveryEnd("fetchResendMissingKeys");

            LOGGER.log(System.Logger.Level.DEBUG,
                    "fetchResendMissingKeys: fetching keys for {0} users", userJids.size());

            // WAWebFetchResendMissingKeyJob: get all device JIDs for the users
            var deviceLists = deviceService.getDeviceLists(userJids, "resend", null, false);
            var allDevices = new ArrayList<Jid>();

            for (var userJid : userJids) {
                var deviceList = deviceLists.get(userJid);
                if (deviceList != null) {
                    // Add all non-primary devices from the device list
                    for (var device : deviceList.devices()) {
                        if (device.deviceId() != 0) {
                            var deviceJid = Jid.device(userJid, device.deviceId());
                            allDevices.add(deviceJid);
                        }
                    }
                }
                // Also add the primary device (device 0)
                allDevices.add(userJid.toUserJid());
            }

            if (allDevices.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG, "fetchResendMissingKeys: no devices to fetch for");
                return;
            }

            // WAWebE2ESessionService: ensure LID mappings before session establishment
            ensurePhoneNumberToLidMapping(allDevices);

            // WAWebFetchResendMissingKeyJob: fetch pre-keys for devices that need sessions
            var devicesNeedingSessions = preKeyService.findDevicesNeedingSessions(allDevices);
            if (!devicesNeedingSessions.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "fetchResendMissingKeys: fetching pre-keys for {0} devices",
                        devicesNeedingSessions.size());
                // Use identity reason true for resend key fetches
                preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingSessions, true);
            }

            LOGGER.log(System.Logger.Level.DEBUG,
                    "fetchResendMissingKeys: completed for {0} users", userJids.size());

        } catch (Exception e) {
            // WAWebFetchResendMissingKeyJob: log and continue on error (don't block resend)
            LOGGER.log(System.Logger.Level.WARNING,
                    "fetchResendMissingKeys: failed: {0}", e.getMessage());
        }
    }

    /**
     * Checks if a message has been overwritten by a revoke.
     * <p>
     * Per WhatsApp Web WAWebResendUserMsg/WAWebResendGroupMsg: if the message has been
     * overwritten by a revoke (isOverwrittenByRevoke === true), skip resending as the
     * revoke should take precedence.
     *
     * @param messageId the message ID to check
     * @return true if the message was overwritten by a revoke
     *
     * @apiNote WAWebResendUserMsg, WAWebResendGroupMsg: checks a.data.isOverwrittenByRevoke
     */
    private boolean isMessageOverwrittenByRevoke(String messageId) {
        // Check the store for revoke status
        // Per WhatsApp Web: the message record tracks isOverwrittenByRevoke flag
        return store.isMessageOverwrittenByRevoke(messageId);
    }

    /**
     * Result of sending a message.
     *
     * @param messageId      the message ID
     * @param chatJid        the chat JID
     * @param timestamp      the server timestamp
     * @param serverPhash    the server phash if mismatched, or null
     * @param addressingMode the addressing mode from server
     * @param count          the message count
     */
    public record MessageSendResult(
            String messageId,
            Jid chatJid,
            long timestamp,
            String serverPhash,
            String addressingMode,
            int count
    ) {
        /**
         * Returns whether a phash mismatch occurred requiring resend.
         */
        public boolean hasPhashMismatch() {
            return serverPhash != null && !serverPhash.isEmpty();
        }
    }

    // ==================== CAG and Group Send Type Handling ====================

    /**
     * Group send type determines whether to use sender key (SKMSG) or direct (per-device) encryption.
     * <p>
     * Per WhatsApp Web WAWebSendGroupMsgJob.GROUP_MSG_TYPE.
     *
     * @apiNote WAWebSendGroupMsgJob.GROUP_MSG_TYPE
     */
    public enum GroupSendType {
        /** Use sender key encryption (default for most messages). */
        SKMSG,
        /** Use per-device direct encryption (for revokes, edits, keep-in-chat, history bundles). */
        DIRECT
    }

    /**
     * Determines whether a group is a Community Admin Group (CAG).
     * <p>
     * Per WhatsApp Web: a CAG is a group with GroupType === LINKED_ANNOUNCEMENT_GROUP,
     * which means it's a group linked to a community (has a parent community JID).
     *
     * @param metadata the group metadata from queryGroupOrCommunityMetadata
     * @return true if this is a CAG
     *
     * @apiNote WAWebGroupType.GroupType.LINKED_ANNOUNCEMENT_GROUP
     */
    public boolean isCag(com.github.auties00.cobalt.model.chat.GroupOrCommunityMetadata metadata) {
        return metadata != null && metadata.parentCommunityJid().isPresent();
    }

    /**
     * Determines whether a message should be treated as a CAG addon.
     * <p>
     * Per WhatsApp Web WAWebGroupMsgSendUtils.isCagAddon: in Community Admin Groups (CAG),
     * certain message types (reactions, comments, event responses, poll votes) use LID addressing.
     * <p>
     * The check is: isCag AND (isReaction OR isUnifiedInfraEnabledForType)
     *
     * @param message  the message container
     * @param metadata the group metadata
     * @return true if this is a CAG addon message
     *
     * @apiNote WAWebGroupMsgSendUtils.isCagAddon, WAWebAddonGatingUtils.isUnifiedInfraEnabledForType
     */
    public boolean isCagAddon(MessageContainer message, com.github.auties00.cobalt.model.chat.GroupOrCommunityMetadata metadata) {
        if (!isCag(metadata)) {
            return false;
        }

        var unwrapped = message.unbox();
        var type = unwrapped.type();

        // Per WAWebMsgGetters.getIsReaction: REACTION or REACTION_ENC
        if (type == com.github.auties00.cobalt.model.message.common.Message.Type.REACTION
                || type == com.github.auties00.cobalt.model.message.common.Message.Type.ENCRYPTED_REACTION) {
            return true;
        }

        // Per WAWebAddonGatingUtils.isUnifiedInfraEnabledForType + WAWebSendGroupMsgJob function C:
        // These types are addon types that use LID addressing in CAG
        return switch (type) {
            case COMMENT -> true;
            case EVENT_RESPONSE -> true;  // event_response in WhatsApp Web
            case POLL_UPDATE -> {
                // Per WhatsApp Web: only poll_vote subtype is an addon
                var pollUpdate = unwrapped.pollUpdateMessage().orElse(null);
                yield pollUpdate != null && pollUpdate.vote().isPresent();
            }
            default -> false;
        };
    }

    /**
     * Determines the group send type for a message.
     * <p>
     * Per WhatsApp Web WAWebSendGroupMsgJob: determines whether to use sender key (SKMSG)
     * or direct (per-device) encryption based on the message type.
     * <p>
     * Direct mode is used for:
     * <ul>
     *   <li>Revokes (sender_revoke) - to ensure delivery to original recipients</li>
     *   <li>Edits (MESSAGE_EDIT) - to ensure delivery to recipients who got the original</li>
     *   <li>Keep-in-chat messages - similar delivery requirements</li>
     *   <li>Message history bundles - to specific history receivers</li>
     * </ul>
     *
     * @param message  the message container
     * @param metadata the group metadata (from queryGroupOrCommunityMetadata)
     * @return the determined group send type
     *
     * @apiNote WAWebSendGroupMsgJob.encryptAndSendGroupMsg, getCagMessageSendList (function h),
     *          getGroupSendList (function b)
     */
    public GroupSendType determineGroupSendType(
            MessageContainer message,
            com.github.auties00.cobalt.model.chat.GroupOrCommunityMetadata metadata
    ) {
        var unwrapped = message.unbox();

        // 1. Revoke messages -> DIRECT
        // Per WAWebSendGroupMsgJob function x: checks protocolMessage.type === REVOKE
        if (getRevokeTargetKey(message) != null) {
            return GroupSendType.DIRECT;
        }

        // 2. Edit messages -> DIRECT
        // Per WAWebSendGroupMsgJob function P: checks protocolMessage.type === MESSAGE_EDIT
        if (getEditTargetKey(message) != null) {
            return GroupSendType.DIRECT;
        }

        // 3. Keep-in-chat messages -> DIRECT
        // Per WAWebSendGroupMsgJob function $: checks keepInChatMessage.key
        if (getKeepInChatTargetKey(message) != null) {
            return GroupSendType.DIRECT;
        }

        // 4. Message history bundle -> DIRECT
        // Per WAWebSendGroupMsgJob: messageHistoryBundle goes to specific historyReceivers
        if (unwrapped.type() == com.github.auties00.cobalt.model.message.common.Message.Type.MESSAGE_HISTORY_BUNDLE) {
            return GroupSendType.DIRECT;
        }

        // Default: SKMSG (sender key encryption)
        return GroupSendType.SKMSG;
    }

    /**
     * Extracts the revoke target message key from a protocol message.
     * <p>
     * Per WhatsApp Web WAWebSendGroupMsgJob function x: extracts the target key
     * from protocolMessage when type is REVOKE.
     *
     * @param message the message container
     * @return the revoke target key string, or null if not a revoke
     *
     * @apiNote WAWebSendGroupMsgJob (function x)
     */
    public String getRevokeTargetKey(MessageContainer message) {
        var proto = message.unbox().protocolMessage().orElse(null);
        if (proto == null || proto.protocolType() != ProtocolMessage.Type.REVOKE) {
            return null;
        }
        var key = proto.key().orElse(null);
        if (key == null) {
            return null;
        }
        var remote = key.chatJid().orElse(null);
        var id = key.id();
        var participant = key.senderJid().orElse(null);
        if (remote == null || id == null || id.isEmpty() || participant == null) {
            return null;
        }
        return remote + ":" + id + ":" + participant;
    }

    /**
     * Extracts the edit target message key from a protocol message.
     * <p>
     * Per WhatsApp Web WAWebSendGroupMsgJob function P: extracts the target key
     * from protocolMessage when type is MESSAGE_EDIT.
     *
     * @param message the message container
     * @return the edit target key string, or null if not an edit
     *
     * @apiNote WAWebSendGroupMsgJob (function P)
     */
    public String getEditTargetKey(MessageContainer message) {
        var proto = message.unbox().protocolMessage().orElse(null);
        if (proto == null || proto.protocolType() != ProtocolMessage.Type.MESSAGE_EDIT) {
            return null;
        }
        var key = proto.key().orElse(null);
        if (key == null) {
            return null;
        }
        var remote = key.chatJid().orElse(null);
        var id = key.id();
        var participant = key.senderJid().orElse(null);
        if (remote == null || id == null || id.isEmpty() || participant == null) {
            return null;
        }
        return remote + ":" + id + ":" + participant;
    }

    /**
     * Extracts the keep-in-chat target message key.
     * <p>
     * Per WhatsApp Web WAWebSendGroupMsgJob function $: extracts the target key
     * from keepInChatMessage.key.
     *
     * @param message the message container
     * @return the keep-in-chat target key string, or null if not applicable
     *
     * @apiNote WAWebSendGroupMsgJob (function $)
     */
    public String getKeepInChatTargetKey(MessageContainer message) {
        var content = message.unbox().content();
        if (!(content instanceof com.github.auties00.cobalt.model.message.standard.KeepInChatMessage keepMsg)) {
            return null;
        }
        var key = keepMsg.key();
        if (key == null) {
            return null;
        }
        var remote = key.chatJid().orElse(null);
        var id = key.id();
        var participant = key.senderJid().orElse(null);
        if (remote == null || id == null || id.isEmpty() || participant == null) {
            return null;
        }
        return remote + ":" + id + ":" + participant;
    }

    /**
     * Checks if the current user is an admin in the group.
     * <p>
     * Per WhatsApp Web WAWebGroupUtils.amIGroupAdmin: checks if the current user
     * is in the admins list of the group.
     *
     * @param metadata the group metadata
     * @return true if the current user is an admin or superadmin
     *
     * @apiNote WAWebGroupUtils.amIGroupAdmin
     */
    public boolean amIGroupAdmin(com.github.auties00.cobalt.model.chat.GroupOrCommunityMetadata metadata) {
        if (metadata == null) {
            return false;
        }
        var selfJid = store.jid();
        return metadata.participants().stream()
                .filter(p -> p.jid().toUserJid().equals(selfJid.toUserJid()))
                .anyMatch(p -> p.role() == com.github.auties00.cobalt.model.chat.ChatRole.ADMIN
                        || p.role() == com.github.auties00.cobalt.model.chat.ChatRole.SUPERADMIN);
    }

    /**
     * Determines the sender JID to use for group messages based on CAG addon status.
     * <p>
     * Per WhatsApp Web WAWebSendGroupSkmsgJob: CAG addon messages and LID addressing mode
     * groups use the LID device JID, otherwise use the PN device JID.
     *
     * @param message  the message container
     * @param metadata the group metadata
     * @return the appropriate sender device JID
     *
     * @apiNote WAWebSendGroupSkmsgJob: F = isCagAddon(D,i) || isLidAddressingMode ? w : A
     */
    public Jid getSenderJidForGroup(
            MessageContainer message,
            com.github.auties00.cobalt.model.chat.GroupOrCommunityMetadata metadata
    ) {
        var useLid = isCagAddon(message, metadata)
                || (metadata != null && metadata.isLidAddressingMode());

        return useLid
                ? store.jid().toDeviceLid()
                : store.jid().toDevicePn();
    }
}
