package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException.Receive.InvalidDeviceSentMessage.DsmErrorType;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryptionHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveBotInfo;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Processes incoming E2E-encrypted chat messages through the full
 * decryption pipeline.
 *
 * <p>This receiver handles all non-newsletter messages: 1:1 chats,
 * groups, broadcasts, status updates, and peer protocol messages.
 * The processing pipeline consists of two phases:
 *
 * <p><b>Phase 1 — Decryption</b> (mirrors {@code WAWebMsgProcessingDecryptApi}):
 * <ol>
 *   <li>Parse the stanza via {@link MessageReceiveStanzaParser}</li>
 *   <li>Validate enc ordering (SKMSG should not precede PKMSG/MSG)</li>
 *   <li>Validate ADV identity for companion devices</li>
 *   <li>Iterate encrypted payloads via {@link MessageDecryptionHandler},
 *       dispatching SKMSG/PKMSG/MSG/MSMSG to the appropriate cipher</li>
 *   <li>Flush Signal protocol store to disk</li>
 * </ol>
 *
 * <p><b>Phase 2 — Protobuf processing</b> (mirrors {@code WAWebHandleMsgProcess}):
 * <ol>
 *   <li>Decode the protobuf {@link MessageContainer}</li>
 *   <li>Validate HSM consistency</li>
 *   <li>Process sender key distribution messages</li>
 *   <li>Unwrap DeviceSentMessage envelopes for self-device messages</li>
 *   <li>Extract messageSecret from deviceContextInfo</li>
 *   <li>Construct the final {@link ChatMessageInfo}</li>
 * </ol>
 *
 * @apiNote WAWebHandleMsg: the main E2E message handler.
 * WAWebMsgProcessingDecryptApi.decryptE2EPayload: phase 1.
 * WAWebHandleMsgProcess.processDecryptedMessageProto: phase 2.
 */
final class ChatMessageReceiver extends MessageReceiver<ChatMessageInfo> {
    /**
     * Logger for diagnostic messages during message processing.
     *
     * @implNote ADAPTED: WAWebHandleMsg uses WALogger with tagged template
     * literals; Cobalt uses {@code System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(ChatMessageReceiver.class.getName());

    /**
     * The decryption service for Signal protocol (PKMSG/MSG/SKMSG) and
     * bot message (MSMSG) decryption.
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptEnc.decryptEnc accesses
     * WAWebSignal.Cipher via module-level imports; Cobalt uses constructor
     * DI via this field.
     */
    private final MessageDecryption decryption;

    /**
     * Constructs a new chat message receiver with the required dependencies.
     *
     * @param store      the central session data store
     * @param decryption the decryption service for Signal and bot messages
     *
     * @implNote ADAPTED: WAWebHandleMsg.default uses module-level imports
     * for store and decryption; Cobalt uses constructor-based DI.
     */
    ChatMessageReceiver(WhatsAppStore store, MessageDecryption decryption) {
        super(store);
        this.decryption = Objects.requireNonNull(decryption, "decryption");
    }

    /**
     * Processes an incoming E2E-encrypted message node.
     *
     * @param node    the raw {@code <message>} node
     * @param fromJid the sender/chat JID (from the {@code from} attribute)
     * @return the decrypted and processed chat message info, or
     *         {@code null} for unavailable messages
     * @throws WhatsAppMessageException.Receive if decryption or
     *         validation fails
     *
     * @implNote WAWebHandleMsg.default and
     * WAWebMsgProcessingDecryptApi.decryptE2EPayload: parses stanza,
     * validates enc ordering, validates ADV identity for companion
     * devices, decrypts all enc payloads, processes protobuf,
     * returns result for receipt handling.
     */
    @Override
    ChatMessageInfo receive(Node node, Jid fromJid) {
        var selfJid = store.jid().orElse(null);
        var stanza = MessageReceiveStanzaParser.parse(node, selfJid);

        if (stanza.isUnavailable()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping unavailable (fanout) message {0}", stanza.id());
            return null;
        }

        if (stanza.encs().isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Message {0} has no encrypted payloads", stanza.id());
            return null;
        }

        validateRecipient(stanza);
        validateNotHostedCompanion(stanza);

        // Phase 1: Decryption
        validateEncOrdering(stanza);
        validateAdvIdentity(stanza);
        byte[] plaintext;
        try {
            plaintext = decryptPayloads(stanza);
        } catch (WhatsAppMessageException.Receive e) {
            // ADAPTED: WAWebMsgProcessingDecryptionHandler function k(): the
            // expired status check in WA Web only suppresses metric reporting,
            // not the result itself.  Cobalt extends this to suppress the
            // exception entirely for expired status messages, avoiding
            // unnecessary retry receipts for content older than 24 hours.
            if (isExpiredStatus(stanza)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Skipping decryption error for expired status {0}", stanza.id());
                return null;
            }
            throw e;
        }
        flushSignalStore();

        // Phase 2: Protobuf processing
        var container = decodeProtobuf(stanza.id(), plaintext);
        if (container == null) {
            throw new WhatsAppMessageException.Receive.InvalidProtobuf(
                    "Failed to decode protobuf for: " + stanza.id(), null);
        }

        validateHsmConsistency(stanza, container);
        processSenderKeyDistribution(container, stanza);

        var chatJid = resolveChatJid(stanza);
        var effectiveContainer = container;

        if (container.content() instanceof DeviceSentMessage dsm) {
            effectiveContainer = unwrapDeviceSentMessage(container, dsm, stanza);
            // WAWebMsgProcessingApiUtils.parseSelfMessage: requires
            // destinationJid, throws INVALID_DSM if null
            chatJid = dsm.destinationJid().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                            DsmErrorType.INVALID_DSM));
        } else if (shouldHaveDeviceSentMessage(stanza)) {
            throw new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                    DsmErrorType.MISSING_DSM);
        }

        return buildChatMessageInfo(stanza, chatJid, effectiveContainer);
    }

    /**
     * Validates that the {@code recipient_pn}/{@code recipient_lid}
     * attributes are only present on messages from self (peer devices).
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.InvalidMessage if a
     *         recipient attribute is set on a non-peer message
     *
     * @implNote WAWebHandleMsgParser: validates
     * {@code recipient != null && !isMeAccount(sender) → throw}.
     */
    private void validateRecipient(MessageReceiveStanza stanza) {
        var hasRecipient = stanza.recipientPn().isPresent()
                || stanza.recipientLid().isPresent();
        if (hasRecipient && !isFromMe(stanza)) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Recipient attribute from non-peer device: " + stanza.senderJid(), null);
        }
    }

    /**
     * Validates that hosted companion devices do not send group, broadcast,
     * or status messages.
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.InvalidMessage if a hosted
     *         device sends to a group/broadcast/status chat
     *
     * @implNote WAWebHandleMsgParser: validates
     * {@code participant.isHosted() && (from.isGroup()||from.isBroadcast()||from.isStatus())}
     * → throws InvalidHostedCompanionStanza.
     */
    private void validateNotHostedCompanion(MessageReceiveStanza stanza) {
        var participant = stanza.participant().orElse(null);
        if (participant == null) {
            return;
        }

        if (!participant.hasHostedServer() && !participant.hasHostedLidServer()) {
            return;
        }

        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Hosted companion device " + participant
                            + " cannot send to " + chatJid, null);
        }
    }

    /**
     * Resolves the effective chat JID, applying bot-specific routing.
     *
     * <p>When the {@code from} JID has a bot server and the stanza's
     * {@code target_chat_jid} (or {@code target_chat_jid_lid}) is set,
     * the message is routed to that chat instead of the bot's own JID.
     *
     * @param stanza the parsed stanza
     * @return the effective chat JID for message storage
     *
     * @implNote WAWebHandleMsgParser: when {@code from.isPnBot() &&
     * targetChatJid != null}, uses {@code targetChatJidLid ?? targetChatJid}
     * as the chat destination.
     */
    private Jid resolveChatJid(MessageReceiveStanza stanza) {
        if (stanza.chatJid().hasBotServer()) {
            var targetChatJid = stanza.targetChatJidLid()
                    .or(stanza::targetChatJid)
                    .orElse(null);
            if (targetChatJid != null) {
                return targetChatJid;
            }
        }
        return stanza.chatJid();
    }

    /**
     * Checks whether the message is an expired status (older than 24 hours).
     *
     * <p>Expired status messages that fail decryption are silently dropped
     * rather than triggering retry receipts or error handling.
     *
     * @param stanza the parsed stanza
     * @return {@code true} if this is a status message older than 24 hours
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptionHandler function R():
     * {@code from.isStatus() && unixTimeWithoutClockSkewCorrection() -
     * (ts + DAY_SECONDS) > 0}.  In WA Web this only suppresses metric
     * reporting in function k(); Cobalt extends this to suppress the
     * exception entirely, avoiding unnecessary retries.
     */
    private boolean isExpiredStatus(MessageReceiveStanza stanza) {
        if (!stanza.chatJid().isStatusBroadcastAccount()) {
            return false;
        }
        var age = ChronoUnit.HOURS.between(stanza.timestamp(), Instant.now());
        return age > 24;
    }

    /**
     * Validates that SKMSG is not the first of two encrypted payloads.
     *
     * @param stanza the parsed stanza
     *
     * @implNote WAWebMsgProcessingDecryptApi function p(): logs an error
     * when SKMSG is out of order ({@code t.length === 2 &&
     * t[0].e2eType === CiphertextType.Skmsg}).
     */
    private void validateEncOrdering(MessageReceiveStanza stanza) {
        var encs = stanza.encs();
        if (encs.size() == 2
                && encs.getFirst().e2eType() == MessageEncryptionType.SKMSG) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Message {0}: SKMSG is out of order (should not be first of two encs)",
                    stanza.id());
        }
    }

    /**
     * Validates the ADV identity when the sender is a companion device
     * and a PKMSG payload is present.
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.AdvFailure if validation fails
     *
     * @implNote WAWebMsgProcessingDecryptApi.decryptE2EPayload: checks
     * {@code m.author.device != null && m.author.device !== 0}, then
     * calls WAWebAdvSignatureApi.validateADVwithEncs which finds the
     * PKMSG enc, extracts the identity key, and validates the ADV
     * signed device identity against the stored identity for the
     * primary device. Returns false on failure, mapped to RETRY result
     * with RetryReason.AdvFailure.
     */
    private void validateAdvIdentity(MessageReceiveStanza stanza) {
        if (!stanza.isCompanionDevice()) {
            return;
        }

        var pkmsgPayload = stanza.encs().stream()
                .filter(enc -> enc.e2eType() == MessageEncryptionType.PKMSG)
                .findFirst()
                .orElse(null);
        if (pkmsgPayload == null) {
            return;
        }

        var deviceIdentityBytes = stanza.deviceIdentity().orElse(null);
        if (deviceIdentityBytes == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Companion device {0} sent PKMSG without device-identity",
                    stanza.senderJid());
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "Missing device-identity for companion device: "
                            + stanza.senderJid());
        }

        var identityKey = decryption.extractIdentityKeyFromPkmsg(
                pkmsgPayload.ciphertext()).orElse(null);
        if (identityKey == null) {
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "Cannot extract identity key from PKMSG for: "
                            + stanza.senderJid());
        }

        try {
            var signedIdentity = ADVSignedDeviceIdentitySpec.decode(deviceIdentityBytes);

            var primaryJid = stanza.senderJid().toUserJid();
            var storedKey = store.findIdentityByAddress(
                    primaryJid.toSignalAddress());

            if (storedKey.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "No stored identity for primary {0}, accepting ADV",
                        primaryJid);
            }
        } catch (WhatsAppMessageException.Receive e) {
            throw e;
        } catch (Exception e) {
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "ADV identity parsing failed for: "
                            + stanza.senderJid(), e);
        }
    }

    /**
     * Iterates over all encrypted payloads using the
     * {@link MessageDecryptionHandler} state machine, attempting
     * decryption of each payload and tracking errors per slot.
     *
     * <p>Unlike a short-circuit approach, all encs are attempted even
     * after the first success. This ensures that Signal session state
     * is updated for all encryption types and that the
     * {@link MessageDecryptionHandler} correctly tracks composite
     * results across both SKMSG and PKMSG/MSG slots.
     *
     * @param stanza the parsed stanza
     * @return the first successfully decrypted plaintext bytes
     * @throws WhatsAppMessageException.Receive if all payloads fail
     *
     * @implNote WAWebMsgProcessingDecryptApi.decryptE2EPayload: creates
     * DecryptionHandler, iterates ALL encs without breaking on first
     * success, calls decryptEnc and processDecryptedMessageProto per
     * successfully decrypted payload.
     */
    private byte[] decryptPayloads(MessageReceiveStanza stanza) {
        var handler = new MessageDecryptionHandler();
        byte[] plaintext = null;

        // WAWebMsgProcessingDecryptApi.decryptE2EPayload: iterates ALL encs
        // without breaking on first success — subsequent encs are still
        // attempted so that Signal session state is updated for all types
        // and the DecryptionHandler correctly tracks composite results.
        for (var enc : stanza.encs()) {
            if (!handler.canDecryptNext(enc)) {
                continue;
            }

            try {
                var decrypted = decryptSinglePayload(enc, stanza);
                if (plaintext == null) {
                    plaintext = decrypted; // WAWebMsgProcessingDecryptApi: first success is used
                }
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Decrypted message {0} via {1}",
                        stanza.id(), enc.e2eType());
            } catch (WhatsAppMessageException.Receive e) {
                handler.handleError(enc, e);
            }
        }

        if (plaintext != null) {
            return plaintext;
        }

        var error = handler.failedError().orElse(null);
        if (error != null) {
            throw error;
        }

        throw new WhatsAppMessageException.Receive.Unknown(
                "No encrypted payloads could be decrypted for: "
                        + stanza.id(), null);
    }

    /**
     * Decrypts a single encrypted payload based on its encryption type.
     *
     * @param enc    the encrypted payload
     * @param stanza the parent stanza for addressing context
     * @return the decrypted plaintext bytes (padding already removed)
     * @throws WhatsAppMessageException.Receive if decryption fails
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc: dispatches to
     * decryptGroupSignalProto for SKMSG, decryptSignalProto for
     * PKMSG/MSG, decryptMsmsgBotMessage for MSMSG.
     */
    private byte[] decryptSinglePayload(
            MessageReceiveEncryptedPayload enc,
            MessageReceiveStanza stanza
    ) {
        return switch (enc.e2eType()) {
            case SKMSG -> {
                var groupJid = stanza.chatJid();
                if (!groupJid.hasGroupOrCommunityServer()
                        && !groupJid.hasBroadcastServer()) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "SKMSG for non-group JID: " + groupJid, null);
                }
                var participant = stanza.participant().orElseThrow(() ->
                        new WhatsAppMessageException.Receive.InvalidMessage(
                                "SKMSG without participant for: " + groupJid, null));
                yield decryption.decryptFromGroup(
                        enc.ciphertext(), groupJid, participant);
            }
            case PKMSG, MSG -> {
                var sender = resolveSignalSender(stanza);
                yield decryption.decryptFromDevice(
                        enc.ciphertext(), sender, enc.e2eType());
            }
            case MSMSG -> {
                var messageSecret = resolveBotMessageSecret(stanza);
                var messageId = stanza.botInfo()
                        .flatMap(MessageReceiveBotInfo::editTargetId)
                        .orElse(stanza.id());
                var targetSenderJid = stanza.targetSenderJid()
                        .map(Jid::toUserJid)
                        .orElseGet(() -> requireSelfJid().toUserJid());
                var botSenderJid = stanza.senderJid().toUserJid();
                yield decryption.decryptBotMessage(
                        enc.ciphertext(), messageSecret, messageId,
                        targetSenderJid, botSenderJid);
            }
        };
    }

    /**
     * Resolves the Signal sender JID for per-device decryption.
     *
     * <p>For group and broadcast messages the sender is the participant.
     * For 1:1 chat messages the sender is the from JID.
     *
     * @param stanza the parsed stanza
     * @return the sender's device JID for Signal session lookup
     *
     * @implNote WAWebMsgProcessingDecryptEnc: for non-group/broadcast
     * messages uses from directly, otherwise uses participant.
     */
    private Jid resolveSignalSender(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer() || chatJid.hasBroadcastServer()) {
            return stanza.participant().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "PKMSG/MSG without participant for group: "
                                    + chatJid, null));
        }
        return chatJid;
    }

    /**
     * Resolves the {@code messageSecret} for a bot message by looking
     * up the target message from the store.
     *
     * @param stanza the parsed stanza containing the target_id and
     *               target_chat_jid metadata
     * @return the 32-byte message secret
     * @throws WhatsAppMessageException.Receive.InvalidMessage if the
     *         target message or its secret cannot be found
     *
     * @implNote WAWebBotMessageSecret function b(): looks up the target
     * message by key, extracts messageSecret.
     */
    private byte[] resolveBotMessageSecret(MessageReceiveStanza stanza) {
        var targetId = stanza.targetId().orElseThrow(() ->
                new WhatsAppMessageException.Receive.InvalidMessage(
                        "MSMSG missing target_id", null));
        var targetChatJid = stanza.targetChatJid().orElse(stanza.chatJid());
        var targetMessage = store.findMessageById(targetChatJid, targetId)
                .orElse(null);
        if (targetMessage instanceof ChatMessageInfo chatInfo) {
            var secret = chatInfo.messageSecret().orElse(null);
            if (secret != null && secret.length > 0) {
                return secret;
            }
        }
        throw new WhatsAppMessageException.Receive.InvalidMessage(
                "Cannot find messageSecret for target message: " + targetId
                        + " in chat: " + targetChatJid, null);
    }

    /**
     * Flushes the Signal protocol store to disk to persist session
     * state changes from the decryption.
     *
     * @implNote WAWebMsgProcessingDecryptApi: calls
     * getSignalProtocolStore().flushBufferToDiskIfNotMemOnlyMode()
     * after decryption completes.
     */
    private void flushSignalStore() {
        try {
            store.save();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to flush signal store: {0}", e.getMessage());
        }
    }

    /**
     * Validates that the HSM flag on the stanza is consistent with the
     * decoded protobuf content.
     *
     * @param stanza    the parsed stanza
     * @param container the decoded message container
     * @throws WhatsAppMessageException.Receive.HsmMismatch if the
     *         stanza is not HSM but the protobuf has a
     *         highlyStructuredMessage
     *
     * @implNote WAWebHandleMsgProcessUtils.preProcessMsg: checks
     * !isHsm && proto.highlyStructuredMessage → HsmMismatchError.
     */
    private void validateHsmConsistency(
            MessageReceiveStanza stanza,
            MessageContainer container
    ) {
        if (!stanza.isHsm() && container.content() instanceof HighlyStructuredMessage) {
            throw new WhatsAppMessageException.Receive.HsmMismatch(
                    "HSM mismatch for: " + stanza.id());
        }
    }

    /**
     * Processes the sender key distribution message embedded in the
     * decoded protobuf, if present.
     *
     * @param container the decoded message container
     * @param stanza    the incoming stanza for group/sender context
     *
     * @implNote WAWebMsgProcessingApiUtils: extracts groupId and
     * axolotlSenderKeyDistributionMessage, validates groupId matches
     * stanza chat JID, calls Signal.Session.createGroupSignalSession.
     */
    private void processSenderKeyDistribution(
            MessageContainer container,
            MessageReceiveStanza stanza
    ) {
        var skdm = container.senderKeyDistributionMessage().orElse(null);
        if (skdm == null) {
            return;
        }

        var skdmGroupJid = skdm.groupJid()
                .orElse(null);
        var distributionData = skdm.axolotlSenderKeyDistributionMessage()
                .orElse(null);
        if (distributionData == null || distributionData.length == 0) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Sender key distribution missing data for {0}",
                    stanza.id());
            return;
        }

        var groupJid = stanza.chatJid();
        if (!Objects.equals(groupJid, skdmGroupJid)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Sender key distribution group ID mismatch: stanza={0}, proto={1}",
                    groupJid, skdmGroupJid);
            return;
        }

        try {
            decryption.processSenderKeyDistribution(
                    groupJid, stanza.senderJid(), distributionData);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Processed sender key distribution from {0} for {1}",
                    stanza.senderJid(), groupJid);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to process sender key distribution for {0}: {1}",
                    stanza.id(), e.getMessage());
        }
    }

    /**
     * Determines whether a DeviceSentMessage wrapper is expected based
     * on the message type and sender.
     *
     * @param stanza the parsed stanza
     * @return {@code true} if a DSM wrapper should be present
     *
     * @implNote WAWebMsgProcessingApiUtils.parseMessage: dispatches
     * to parseSelfMessage (expect DSM) or parseOtherMessage (reject DSM)
     * based on MESSAGE_TYPE and isMeAccount(author).
     */
    private boolean shouldHaveDeviceSentMessage(MessageReceiveStanza stanza) {
        if (!isFromMe(stanza)) {
            return false;
        }
        return switch (stanza.messageType()) {
            case CHAT -> true;
            case OTHER_BROADCAST, OTHER_STATUS, PEER_CHAT -> false;
            case GROUP, DIRECT_PEER_STATUS -> stanza.isDirect();
            case PEER_BROADCAST -> stanza.encs().stream()
                    .noneMatch(enc -> enc.e2eType().isSenderKeyMessage());
        };
    }

    /**
     * Unwraps a {@code DeviceSentMessage} envelope, extracting the
     * inner message container and merging {@code messageContextInfo}
     * fields from the outer envelope into the inner message.
     *
     * @param outerContainer the outer message container containing the DSM
     * @param dsm    the device sent message wrapper
     * @param stanza the parsed stanza for error context
     * @return the inner message container with merged context info
     * @throws WhatsAppMessageException.Receive.InvalidDeviceSentMessage
     *         if the inner message is null
     *
     * @implNote WAWebDeviceSentMessageProtoUtils.unwrapDeviceSentMessage:
     * merges messageContextInfo fields (messageSecret, messageAssociation,
     * limitSharingV2, threadId, botMetadata) from outer into inner.
     * Inner values take priority for messageSecret, messageAssociation,
     * threadId, and botMetadata; limitSharingV2 always comes from outer.
     */
    private MessageContainer unwrapDeviceSentMessage(
            MessageContainer outerContainer,
            DeviceSentMessage dsm,
            MessageReceiveStanza stanza
    ) {
        var inner = dsm.message();
        if (inner.isEmpty()) {
            throw new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                    DsmErrorType.INVALID_DSM);
        }

        var innerContainer = inner.get();

        // WAWebDeviceSentMessageProtoUtils.unwrapDeviceSentMessage: merge
        // messageContextInfo from outer into inner, with inner taking priority
        // for messageSecret, messageAssociation, threadId, botMetadata
        var outerCtx = outerContainer.messageContextInfo().orElse(null);
        var innerCtx = innerContainer.messageContextInfo().orElse(null);

        // WAWebDeviceSentMessageProtoUtils: babelHelpers.extends({}, g.messageContextInfo, {...})
        var mergedCtx = new ChatMessageContextInfoBuilder();

        // Copy all inner fields as the base
        if (innerCtx != null) {
            innerCtx.deviceListMetadata().ifPresent(mergedCtx::deviceListMetadata);
            innerCtx.deviceListMetadataVersion().ifPresent(mergedCtx::deviceListMetadataVersion);
            innerCtx.paddingBytes().ifPresent(mergedCtx::paddingBytes);
            innerCtx.messageAddOnDurationInSecs().ifPresent(mergedCtx::messageAddOnDurationInSecs);
            innerCtx.botMessageSecret().ifPresent(mergedCtx::botMessageSecret);
            innerCtx.reportingTokenVersion().ifPresent(mergedCtx::reportingTokenVersion);
            innerCtx.messageAddOnExpiryType().ifPresent(mergedCtx::messageAddOnExpiryType);
            if (innerCtx.capiCreatedGroup()) {
                mergedCtx.capiCreatedGroup(true);
            }
            innerCtx.supportPayload().ifPresent(mergedCtx::supportPayload);
            innerCtx.limitSharing().ifPresent(mergedCtx::limitSharing);
            innerCtx.weblinkRenderConfig().ifPresent(mergedCtx::weblinkRenderConfig);
        }

        // WAWebDeviceSentMessageProtoUtils: messageSecret — inner ?? outer
        var messageSecret = innerCtx != null ? innerCtx.messageSecret().orElse(null) : null;
        if (messageSecret == null && outerCtx != null) {
            messageSecret = outerCtx.messageSecret().orElse(null);
        }
        if (messageSecret != null) {
            mergedCtx.messageSecret(messageSecret);
        }

        // WAWebDeviceSentMessageProtoUtils: messageAssociation — inner ?? outer
        var messageAssociation = innerCtx != null ? innerCtx.messageAssociation().orElse(null) : null;
        if (messageAssociation == null && outerCtx != null) {
            messageAssociation = outerCtx.messageAssociation().orElse(null);
        }
        if (messageAssociation != null) {
            mergedCtx.messageAssociation(messageAssociation);
        }

        // WAWebDeviceSentMessageProtoUtils: limitSharingV2 — always from outer
        if (outerCtx != null) {
            outerCtx.limitSharingV2().ifPresent(mergedCtx::limitSharingV2);
        }

        // WAWebDeviceSentMessageProtoUtils: threadId — inner ?? outer ?? []
        java.util.List<com.github.auties00.cobalt.model.message.MessageThreadId> threadId;
        if (innerCtx != null && !innerCtx.threadId().isEmpty()) {
            threadId = innerCtx.threadId();
        } else if (outerCtx != null) {
            threadId = outerCtx.threadId();
        } else {
            threadId = java.util.List.of();
        }
        mergedCtx.threadId(threadId);

        // WAWebDeviceSentMessageProtoUtils: botMetadata — inner ?? outer
        var botMetadata = innerCtx != null ? innerCtx.botMetadata().orElse(null) : null;
        if (botMetadata == null && outerCtx != null) {
            botMetadata = outerCtx.botMetadata().orElse(null);
        }
        if (botMetadata != null) {
            mergedCtx.botMetadata(botMetadata);
        }

        return innerContainer.withMessageContextInfo(mergedCtx.build());
    }

    /**
     * Builds the final {@link ChatMessageInfo} from the stanza metadata
     * and the decoded (possibly unwrapped) message container.
     *
     * @param stanza    the parsed stanza
     * @param chatJid   the effective chat JID (may be overridden by DSM)
     * @param container the decoded message container
     * @return the fully-populated message info
     *
     * @implNote WAWebMsgProcessingApiUtils.generateBaseMsg: constructs
     * the base message with id, from, to, type, ack, author, notifyName,
     * invis, count, clientReceivedTsMillis.
     */
    private ChatMessageInfo buildChatMessageInfo(
            MessageReceiveStanza stanza,
            Jid chatJid,
            MessageContainer container
    ) {
        var fromMe = isFromMe(stanza);
        var senderJid = stanza.senderJid().toUserJid();

        var key = new MessageKeyBuilder()
                .id(stanza.id())
                .parentJid(chatJid)
                .fromMe(fromMe)
                .senderJid(senderJid)
                .build();

        var builder = new ChatMessageInfoBuilder()
                .key(key)
                .message(container)
                .timestamp(stanza.timestamp())
                .status(MessageStatus.DELIVERED)
                .senderJid(senderJid)
                .broadcast(stanza.chatJid().hasBroadcastServer())
                .pushName(stanza.pushName().orElse(null))
                .urlText(stanza.urlText())
                .urlNumber(stanza.urlNumber());

        container.messageContextInfo()
                .flatMap(ChatMessageContextInfo::messageSecret)
                .ifPresent(builder::messageSecret);

        return builder.build();
    }
}
