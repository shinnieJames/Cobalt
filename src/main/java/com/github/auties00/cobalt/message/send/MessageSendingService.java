package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashVersion;
import com.github.auties00.cobalt.message.addressing.LidMessageAddressingMode;
import com.github.auties00.cobalt.message.addressing.MessageAddressingMode;
import com.github.auties00.cobalt.message.addressing.MixedMessageAddressingMode;
import com.github.auties00.cobalt.message.addressing.PhoneNumberMessageAddressingMode;
import com.github.auties00.cobalt.message.send.encryption.MessageDeviceEncryption;
import com.github.auties00.cobalt.message.send.encryption.MessageEncryptionService;
import com.github.auties00.cobalt.message.send.error.MessageRetryHandler;
import com.github.auties00.cobalt.message.send.keys.MessagePreKeyBundleService;
import com.github.auties00.cobalt.message.send.keys.MessageSenderKeyDistributionService;
import com.github.auties00.cobalt.message.send.stanza.MessageStanzaBuilder;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentity;
import com.github.auties00.cobalt.model.chat.ChatParticipant;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.info.ChatMessageInfo;
import com.github.auties00.cobalt.model.info.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.info.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.model.ChatMessageKey;
import com.github.auties00.cobalt.model.message.model.MessageContainer;
import com.github.auties00.cobalt.model.message.model.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.model.MessageStatus;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.Clock;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Main service for sending messages in WhatsApp.
 * Orchestrates the entire message sending flow.
 * </ul>
 */
public final class MessageSendingService {
    private static final System.Logger LOGGER = System.getLogger("MessageSendingService");

    private final WhatsAppClient client;
    private final WhatsAppStore store;
    private final DeviceService deviceService;
    private final LidMigrationService lidMigrationService;
    private final MessageEncryptionService encryptionService;
    private final MessagePreKeyBundleService preKeyService;
    private final MessageSenderKeyDistributionService senderKeyService;
    private final MessageRetryHandler retryHandler;

    public MessageSendingService(
            WhatsAppClient client,
            WhatsAppStore store,
            LidMigrationService lidMigrationService,
            DeviceService deviceService,
            SignalSessionCipher sessionCipher,
            SignalGroupCipher groupCipher
    ) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.encryptionService = new MessageEncryptionService(store, sessionCipher, groupCipher);
        this.preKeyService = new MessagePreKeyBundleService(client, sessionCipher);
        this.senderKeyService = new MessageSenderKeyDistributionService(store, groupCipher);
        this.retryHandler = new MessageRetryHandler(deviceService, preKeyService);
    }

    /**
     * Sends a message using the provided options.
     * Dispatches to the appropriate handler based on the options type:
     * <ul>
     *   <li>{@link MessageSendInput.Newsletter} - sends an unencrypted newsletter message</li>
     *   <li>{@link MessageSendInput.Chat} - routes to the appropriate encrypted chat handler</li>
     * </ul>
     *
     * @param options the send options (newsletter or chat)
     * @return the send result
     */
    public MessageSendResult send(MessageSendInput options) {
        Objects.requireNonNull(options, "options cannot be null");
        return switch (options) {
            case MessageSendInput.Newsletter newsletter -> sendToNewsletter(newsletter.info());
            case MessageSendInput.Chat chat -> sendToChat(chat, chat.info().chatJid(), 0);
        };
    }

    /**
     * Routes the message to the appropriate handler based on recipient type.
     */
    private MessageSendResult sendToChat(
            MessageSendInput.Chat options,
            Jid recipient,
            int retryCount
    ) {
        try {
            return switch (recipient.server().type()) {
                case USER, LEGACY_USER, LID, HOSTED_LID, BOT, INTEROP, MSGR, HOSTED -> sendToPrivateChat(options);
                case GROUP_OR_COMMUNITY -> options instanceof MessageSendInput.Chat.CommunityAnnouncementGroup cag
                        ? sendToCommunityAnnouncementGroup(cag)
                        : sendToGroup(options);
                case BROADCAST -> recipient.equals(Jid.statusBroadcastAccount())
                        ? sendStatusUpdate(options)
                        : sendToBroadcast(options);
                case CALL -> new MessageSendResult.ProtocolError("INVALID_RECIPIENT",
                        "Call is not a valid recipient");
                case NEWSLETTER -> new MessageSendResult.ProtocolError("INVALID_RECIPIENT",
                        "Use NewsletterMessageSendOptions for newsletter messages");
                case UNKNOWN ->  new MessageSendResult.ProtocolError("INVALID_RECIPIENT",
                        "Unknown recipient");
            };
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error sending message: {0}", e.getMessage());
            return handleSendError(options, e, retryCount);
        }
    }

    /**
     * Sends a message to a private (1:1) chat.
     */
    private MessageSendResult sendToPrivateChat(MessageSendInput.Chat options) {
        var info = options.info();
        var recipient = info.chatJid();
        var message = info.message();
        var myDeviceJid = getMyDeviceJid();

        // Determine addressing mode (PN vs LID vs mixed)
        var addressingMode = determineAddressingMode(recipient);
        var effectiveRecipient = addressingMode.primaryJid();

        // Get device lists for recipient and self
        var recipientUsers = List.of(effectiveRecipient.toUserJid());
        var selfUsers = List.of(myDeviceJid.toUserJid());

        var recipientDevices = deviceService.getDeviceLists(recipientUsers);
        var selfDevices = deviceService.getDeviceLists(selfUsers);

        var allDeviceLists = new ArrayList<>(recipientDevices);
        allDeviceLists.addAll(selfDevices);

        // Calculate fanout (exclude own device)
        var allFanoutDevices = new HashSet<Jid>();
        if (options instanceof MessageSendInput.Chat.Bot) {
            if (!recipientDevices.isEmpty()) {
                var recipientDeviceList = recipientDevices.getFirst();
                if(!recipientDeviceList.devices().isEmpty()) {
                    var recipientDevice = recipientDeviceList.devices().getFirst();
                    var userJid = recipientDeviceList.userJid();
                    allFanoutDevices.add(recipientDevice.toDeviceJid(userJid.user(), userJid.server()));
                }
            }
        } else {
            var deviceJids = DeviceFanoutCalculator.calculate(myDeviceJid, allDeviceLists);
            allFanoutDevices.addAll(deviceJids);
        }

        if (allFanoutDevices.isEmpty()) {
            return new MessageSendResult.ProtocolError("NO_DEVICES",
                    "No devices found for recipient");
        }

        // Ensure Signal sessions exist for all devices
        preKeyService.ensureSessions(allFanoutDevices);

        // Calculate phash (only for multi-device)
        String phash = null;
        if (allFanoutDevices.size() > 1 && !options.skipPhashValidation()) {
            try {
                // Meta AI bot not included for private chats
                phash = DevicePhashCalculator.calculate(allFanoutDevices, DevicePhashVersion.V2, false);
            } catch (NoSuchAlgorithmException e) {
                throw new InternalError("SHA-256 not available", e);
            }
        }

        // Prepare message with optional message secret
        var messageToSend = prepareMessage(message, options);

        // Serialize and encrypt message for each device
        // Feature 8: Wrap in deviceSentMessage for own devices
        var deviceEncryptions = new ArrayList<MessageDeviceEncryption>();
        var myUserJid = myDeviceJid.toUserJid();
        for (var deviceJid : allFanoutDevices) {
            var isOwnDevice = deviceJid.toUserJid().equals(myUserJid);
            var plaintextToSend = isOwnDevice
                    ? wrapInDeviceSentMessage(messageToSend, effectiveRecipient, phash)
                    : MessageContainerSpec.encode(messageToSend);
            var encrypted = encryptionService.encryptForDevice(plaintextToSend, deviceJid);
            deviceEncryptions.add(new MessageDeviceEncryption(deviceJid, encrypted));
        }

        // Get device identity if any prekey messages
        var deviceIdentity = getDeviceIdentityIfNeeded(deviceEncryptions);

        // 10. Build stanza
        var stanza = MessageStanzaBuilder.buildPrivateMessageStanza(
                info.id(),
                effectiveRecipient,
                deviceEncryptions,
                phash,
                addressingMode,
                deviceIdentity,
                messageToSend,
                options
        );

        // 11. Send and process response
        return sendStanzaAndProcessResponse(options, stanza, allFanoutDevices);
    }

    /**
     * Sends a message to a group chat using Sender Key encryption.
     */
    private MessageSendResult sendToGroup(MessageSendInput.Chat options) {
        var info = options.info();
        var groupJid = info.chatJid();
        var message = info.message();
        var myDeviceJid = getMyDeviceJid();

        // Get group fanout (all participant devices)
        var fanoutResult = deviceService.getGroupFanout(groupJid, myDeviceJid);
        var fanoutDevices = fanoutResult.devices();
        var phash = fanoutResult.phash();

        if (fanoutDevices.isEmpty()) {
            return new MessageSendResult.ProtocolError("NO_PARTICIPANTS",
                    "Group has no participants");
        }

        // Get devices needing sender key distribution
        var devicesNeedingSK = senderKeyService.getDevicesNeedingSenderKey(groupJid, fanoutDevices);

        // Ensure Signal sessions exist for sender key distribution
        if (!devicesNeedingSK.isEmpty()) {
            var devicesNeedingPrekeys = preKeyService.findDevicesNeedingSessions(devicesNeedingSK);
            if (!devicesNeedingPrekeys.isEmpty()) {
                preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingPrekeys);
            }
        }

        // Prepare message
        var messageToSend = prepareMessage(message, options);

        // Serialize and encrypt with sender key for group
        var plaintext = MessageContainerSpec.encode(messageToSend);
        var senderKeyEncryption = encryptionService.encryptForGroup(plaintext, groupJid, myDeviceJid);

        // Encrypt sender key distribution for new devices
        List<MessageDeviceEncryption> senderKeyDistributions = null;
        if (!devicesNeedingSK.isEmpty()) {
            senderKeyDistributions = new ArrayList<>();
            var distributionContainer = senderKeyService.createDistributionMessageContainer(groupJid, myDeviceJid);
            var distributionPlaintext = MessageContainerSpec.encode(distributionContainer);

            for (var deviceJid : devicesNeedingSK) {
                var encrypted = encryptionService.encryptForDevice(distributionPlaintext, deviceJid);
                senderKeyDistributions.add(new MessageDeviceEncryption(deviceJid, encrypted));
            }
        }

        // Get device identity if needed
        var deviceIdentity = getDeviceIdentityIfNeeded(senderKeyDistributions);

        // Build stanza
        var stanza = MessageStanzaBuilder.buildGroupMessageStanza(
                info.id(),
                groupJid,
                senderKeyEncryption,
                senderKeyDistributions,
                phash,
                deviceIdentity,
                messageToSend,
                options
        );

        // Send and process response
        var result = sendStanzaAndProcessResponse(options, stanza, fanoutDevices);

        // 10. Mark sender key as distributed on success
        if (result.isSuccess() && !devicesNeedingSK.isEmpty()) {
            senderKeyService.markSenderKeyDistributed(groupJid, devicesNeedingSK);
        }

        return result;
    }

    /**
     * Sends a message to a Community Announcement Group (CAG).
     * CAG messages don't require sender key distribution to linked groups.
     */
    private MessageSendResult sendToCommunityAnnouncementGroup(MessageSendInput.Chat.CommunityAnnouncementGroup options) {
        var info = options.info();
        var cagJid = info.chatJid();
        var message = info.message();
        var myDeviceJid = getMyDeviceJid();

        // Get CAG fanout (only direct members, not linked group members)
        var fanoutResult = deviceService.getGroupFanout(cagJid, myDeviceJid);
        var fanoutDevices = fanoutResult.devices();
        var phash = fanoutResult.phash();

        if (fanoutDevices.isEmpty()) {
            return new MessageSendResult.ProtocolError("NO_PARTICIPANTS",
                    "CAG has no participants");
        }

        // Prepare message
        var messageToSend = prepareMessage(message, options);

        // Encrypt with sender key
        var plaintext = MessageContainerSpec.encode(messageToSend);
        var senderKeyEncryption = encryptionService.encryptForGroup(plaintext, cagJid, myDeviceJid);

        // Build stanza
        var stanza = MessageStanzaBuilder.buildCagMessageStanza(
                info.id(),
                cagJid,
                senderKeyEncryption,
                phash,
                messageToSend,
                options
        );

        // Send and process response
        return sendStanzaAndProcessResponse(options, stanza, fanoutDevices);
    }

    /**
     * Sends a broadcast list message by expanding to individual 1:1 messages.
     */
    private MessageSendResult sendToBroadcast(MessageSendInput.Chat options) {
        var info = options.info();
        var broadcastJid = info.chatJid();

        // Get broadcast list recipients from metadata
        var metadata = client.queryGroupOrCommunityMetadata(broadcastJid);
        var recipients = metadata.participants().stream()
                .map(ChatParticipant::jid)
                .toList();

        if (recipients.isEmpty()) {
            return new MessageSendResult.ProtocolError("400", "Broadcast list has no recipients");
        }

        // Send individual messages to each recipient
        var successCount = 0;
        var failureCount = 0;
        MessageSendResult lastError = null;

        for (var recipient : recipients) {
            // Create a new key and info for this recipient
            var newKey = new ChatMessageKey(
                    recipient,
                    info.key().fromMe(),
                    ChatMessageKey.randomId(store.clientType()),
                    info.key().senderJid().orElse(null)
            );
            var individualInfo = new ChatMessageInfoBuilder()
                    .key(newKey)
                    .message(info.message())
                    .timestampSeconds(info.timestampSeconds().orElse(0L))
                    .status(MessageStatus.PENDING)
                    .senderJid(info.senderJid())
                    .broadcast(true)
                    .build();

            var result = sendToPrivateChat(options.withInfo(individualInfo));

            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
                lastError = result;
            }
        }

        // Return aggregate result
        if (failureCount == 0) {
            return new MessageSendResult.Success(info);
        } else if (successCount == 0) {
            return lastError;
        } else {
            // Partial success
            LOGGER.log(System.Logger.Level.WARNING,
                    "Broadcast partially delivered: {0} success, {1} failed",
                    successCount, failureCount);
            return new MessageSendResult.Success(info);
        }
    }

    /**
     * Sends a status update to all status recipients.
     */
    private MessageSendResult sendStatusUpdate(MessageSendInput.Chat options) {
        var info = options.info();
        var message = info.message();
        var myDeviceJid = getMyDeviceJid();

        // Get contacts who can see our status
        var statusRecipients = store.contacts().stream()
                .map(Contact::jid)
                .filter(jid -> jid.hasServer(JidServer.user()) || jid.hasServer(JidServer.legacyUser()))
                .toList();

        if (statusRecipients.isEmpty()) {
            // No recipients - success (status posted but nobody will see it)
            return new MessageSendResult.Success(info);
        }

        // Get device lists for all recipients
        var deviceLists = deviceService.getDeviceLists(statusRecipients);

        // Calculate fanout
        var allFanoutDevices = DeviceFanoutCalculator.calculate(myDeviceJid, deviceLists);

        if (allFanoutDevices.isEmpty()) {
            return new MessageSendResult.Success(info);
        }

        // Ensure sessions
        var devicesNeedingPrekeys = preKeyService.findDevicesNeedingSessions(allFanoutDevices);
        if (!devicesNeedingPrekeys.isEmpty()) {
            preKeyService.fetchAndProcessPreKeyBundles(devicesNeedingPrekeys);
        }

        // Calculate phash
        String phash;
        try {
            // Meta AI bot not included for status broadcasts
            phash = DevicePhashCalculator.calculate(allFanoutDevices, DevicePhashVersion.V2, false);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-256 not available", e);
        }

        // Prepare message
        var messageToSend = prepareMessage(message, options);

        // Encrypt for each device
        var plaintext = MessageContainerSpec.encode(messageToSend);
        var deviceEncryptions = new ArrayList<MessageDeviceEncryption>();
        for (var deviceJid : allFanoutDevices) {
            var encrypted = encryptionService.encryptForDevice(plaintext, deviceJid);
            deviceEncryptions.add(new MessageDeviceEncryption(deviceJid, encrypted));
        }

        // Build and send stanza
        var deviceIdentity = getDeviceIdentityIfNeeded(deviceEncryptions);
        var stanza = MessageStanzaBuilder.buildStatusBroadcastStanza(
                info.id(),
                deviceEncryptions,
                phash,
                deviceIdentity,
                messageToSend,
                options
        );

        return sendStanzaAndProcessResponse(options, stanza, allFanoutDevices);
    }

    /**
     * Sends a message to a newsletter (unencrypted).
     */
    private MessageSendResult sendToNewsletter(NewsletterMessageInfo info) {
        var newsletterJid = info.newsletter().jid();
        var message = info.message();

        // Serialize message (no encryption for newsletters)
        var plaintext = MessageContainerSpec.encode(message);

        // Build stanza
        var stanza = MessageStanzaBuilder.buildNewsletterMessageStanza(
                info.id(),
                newsletterJid,
                plaintext,
                message
        );

        // Send and process response
        var response = client.sendNode(stanza);

        // Check for errors
        var error = response.getChild("error");
        if (error.isPresent()) {
            var code = error.get().getAttributeAsString("code").orElse("unknown");
            var text = error.get().getAttributeAsString("text").orElse("Unknown error");
            return new MessageSendResult.ProtocolError(code, text);
        }

        // Extract server ID if available
        var serverIdAttr = response.getAttributeAsLong("server_id");
        var serverId = (int) serverIdAttr.orElse(info.serverId());

        info.setServerId(serverId);

        return new MessageSendResult.Success(info);
    }

    /**
     * Sends a stanza and processes the response.
     */
    private MessageSendResult sendStanzaAndProcessResponse(
            MessageSendInput.Chat options,
            NodeBuilder stanza,
            Set<Jid> fanoutDevices
    ) {
        var info = options.info();
        var response = client.sendNode(stanza);

        // Check for phash mismatch
        var ackPhash = response.getAttributeAsString("phash").orElse(null);
        if (ackPhash != null) {
            var errorAttr = response.getAttributeAsString("error").orElse(null);
            if ("phash_mismatch".equals(errorAttr)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Phash mismatch, will retry");
                return handlePhashMismatch(options, ackPhash, fanoutDevices);
            }
        }

        // Check for error node
        var error = response.getChild("error");
        if (error.isPresent()) {
            return parseErrorResponse(error.get(), info);
        }

        // Success - update message status
        var timestamp = response.getAttributeAsLong("t")
                .orElse(Clock.nowSeconds());
        info.setStatus(MessageStatus.SERVER_ACK);
        info.setTimestampSeconds(timestamp);

        // Clean up retry state
        retryHandler.clearRetryState(info.id());

        return new MessageSendResult.Success(info);
    }

    /**
     * Parses an error response from the server.
     */
    private MessageSendResult parseErrorResponse(Node errorNode, ChatMessageInfo info) {
        var code = errorNode.getAttributeAsString("code").orElse("unknown");
        var text = errorNode.getAttributeAsString("text").orElse("Unknown error");

        // Handle specific error codes
        return switch (code) {
            case "401" -> new MessageSendResult.ProtocolError(code, "Unauthorized: " + text);
            case "403" -> new MessageSendResult.ProtocolError(code, "Forbidden: " + text);
            case "404" -> new MessageSendResult.ProtocolError(code, "Not found: " + text);
            case "406" -> new MessageSendResult.IdentityChanged(List.of(info.chatJid()));
            case "408" -> new MessageSendResult.NetworkError(new Exception("Request timeout"));
            case "410" -> new MessageSendResult.ProtocolError(code, "Gone: message expired");
            case "428" -> {
                // Precondition failed - usually missing prekeys
                var missingDevices = parseStanzaMissingDevices(errorNode);
                yield new MessageSendResult.MissingPreKeys(missingDevices);
            }
            case "480" -> new MessageSendResult.ProtocolError(code, "Temporarily unavailable");
            case "500" -> new MessageSendResult.ProtocolError(code, "Server error: " + text);
            case "503" -> new MessageSendResult.NetworkError(new Exception("Service unavailable"));
            default -> new MessageSendResult.ProtocolError(code, text);
        };
    }

    /**
     * Parses missing devices from an error response.
     */
    private List<Jid> parseStanzaMissingDevices(Node errorNode) {
        var result = new ArrayList<Jid>();
        for (var userNode : errorNode.getChildren("user")) {
            var jid = userNode.getAttributeAsJid("jid");
            jid.ifPresent(result::add);
        }
        return result;
    }

    /**
     * Handles phash mismatch by refreshing device lists and retrying.
     */
    private MessageSendResult handlePhashMismatch(
            MessageSendInput.Chat options,
            String expectedPhash,
            Set<Jid> fanoutDevices
    ) {
        var info = options.info();
        var userJids = fanoutDevices.stream()
                .map(Jid::toUserJid)
                .distinct()
                .toList();

        return retryHandler.handlePhashMismatch(
                info.id(),
                expectedPhash,
                userJids,
                () -> sendToChat(options, info.chatJid(), 0)
        );
    }

    /**
     * Handles send errors with retry logic.
     */
    private MessageSendResult handleSendError(
            MessageSendInput.Chat options,
            Exception e,
            int retryCount
    ) {
        var info = options.info();
        if (retryCount >= options.maxRetries()) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Max retries ({0}) exceeded for message {1}",
                    options.maxRetries(), info.id());
            return new MessageSendResult.NetworkError(e);
        }

        // Retry for network errors
        if (e instanceof IOException) {
            return retryHandler.handleNetworkError(
                    info.id(),
                    retryCount,
                    () -> sendToChat(options, info.chatJid(), retryCount + 1)
            );
        }

        return new MessageSendResult.ProtocolError("500", e.getMessage());
    }

    /**
     * Determines the addressing mode for a recipient.
     */
    private MessageAddressingMode determineAddressingMode(Jid recipient) {
        // Already LID
        if (recipient.hasLidServer()) {
            return new LidMessageAddressingMode(recipient);
        }

        // Check if LID is available for this recipient
        var lidJid = lidMigrationService.lookupLid(recipient).orElse(null);
        var shouldUseLid = lidMigrationService.shouldUseLidAddressing(recipient);

        if (lidJid != null && shouldUseLid) {
            // Mixed mode during migration
            return new MixedMessageAddressingMode(recipient, lidJid, true);
        } else if (lidJid != null) {
            // We have a LID but shouldn't use it yet
            return new PhoneNumberMessageAddressingMode(recipient);
        } else {
            // No LID available
            return new PhoneNumberMessageAddressingMode(recipient);
        }
    }

    /**
     * Prepares the message for sending, adding message secret if needed.
     */
    private MessageContainer prepareMessage(MessageContainer message, MessageSendInput.Chat options) {
        // Generate message secret for view-once or when explicitly requested
        if (options.generateMessageSecret()) {
            var messageSecret = SecureBytes.random(32);
            // TODO: Add message secret to device info
            // This requires integrating with DeviceContextInfo
        }

        // Convert to ephemeral if needed
        if (options instanceof MessageSendInput.Chat.Ephemeral) {
            return message.toEphemeral();
        }

        // Convert to view-once if needed
        if (options instanceof MessageSendInput.Chat.ViewOnce) {
            return message.toViewOnce();
        }

        return message;
    }

    /**
     * Gets the current user's device JID.
     */
    private Jid getMyDeviceJid() {
        return store.jid()
                .orElseThrow(() -> new IllegalStateException("Not connected - no JID available"));
    }

    /**
     * Gets device identity bytes if any encryptions require prekey messages.
     */
    private byte[] getDeviceIdentityIfNeeded(List<MessageDeviceEncryption> encryptions) {
        if (encryptions == null || encryptions.isEmpty()) {
            return null;
        }

        var hasPreKeyMessage = encryptions.stream().anyMatch(MessageDeviceEncryption::isPreKeyMessage);
        if (!hasPreKeyMessage) {
            return null;
        }

        // Return signed device identity for prekey messages
        return store.signedDeviceIdentity()
                .map(SignedDeviceIdentity::details)
                .orElse(null);
    }

    /**
     * Wraps a message in a DeviceSentMessage for delivery to own devices.
     * <p>
     * Per WhatsApp Web: when encrypting for the sender's own devices, the message
     * is wrapped in a DeviceSentMessage protobuf that includes the destination JID.
     *
     * @param message       the message to wrap
     * @param destinationJid the intended recipient JID
     * @param phash         the phash for multi-device delivery (may be null)
     * @return the encoded DeviceSentMessage protobuf bytes
     */
    private byte[] wrapInDeviceSentMessage(MessageContainer message, Jid destinationJid, String phash) {
        var deviceSentMessage = new com.github.auties00.cobalt.model.message.server.DeviceSentMessageBuilder()
                .destinationJid(destinationJid)
                .message(message)
                .phash(phash)
                .build();
        // Wrap in a MessageContainer and encode
        var wrappedContainer = MessageContainer.of(deviceSentMessage);
        return MessageContainerSpec.encode(wrappedContainer);
    }
}
