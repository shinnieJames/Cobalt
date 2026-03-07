package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.ack.NackReason;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.message.send.token.ContentBindingToken;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sends messages to group chats ({@code group@g.us}).
 *
 * <p>The primary path uses sender-key (SKMSG) encryption: the message is
 * encrypted once with the group sender key, and a separate sender-key
 * distribution message is sent individually to new members who don't
 * yet have the key.
 *
 * <p>When the server returns a phash mismatch, the delta devices receive
 * the message as a group-direct fanout (per-device encryption).
 *
 * @apiNote WAWebSendGroupMsgJob.encryptAndSendGroupMsg: queues the send
 * per group, resolves group data and participant lists, dispatches to
 * SKMSG or DIRECT path.
 * WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg: SKMSG path with
 * phash verification, SK distribution, and addressing mode handling.
 * WAWebSendGroupDirectJob.encryptAndSendGroupDirectMsg: DIRECT path
 * used for resends after phash mismatch.
 */
final class GroupMessageSender extends MessageSender<ChatMessageInfo> {
    private static final System.Logger LOGGER = System.getLogger(GroupMessageSender.class.getName());


    private final MessageEncryption encryption;
    private final DeviceService deviceService;
    private final ABPropsService abPropsService;
    private final SenderKeyDistribution senderKeyDistribution;
    private final BotStanza botStanza;
    private final BizStanza bizStanza;
    private final MetaStanza metaStanza;
    private final ReportingStanza reportingStanza;
    private final ConcurrentMap<String, ReentrantLock> locks;

    GroupMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            SenderKeyDistribution senderKeyDistribution,
            BotStanza botStanza,
            BizStanza bizStanza,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza
    ) {
        super(client);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.senderKeyDistribution = Objects.requireNonNull(senderKeyDistribution, "senderKeyDistribution");
        this.botStanza = Objects.requireNonNull(botStanza, "botStanza");
        this.bizStanza = Objects.requireNonNull(bizStanza, "bizStanza");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
        this.locks = new ConcurrentHashMap<>();
    }

    /**
     * Executes the given task while holding the lock for {@code key},
     * ensuring mutual exclusion with other tasks enqueued under the same key.
     *
     * <p>Tasks enqueued under different keys may run concurrently.
     *
     * @param <T>  the result type
     * @param key  the queue key (typically the group JID string)
     * @param task the task to execute
     * @return the result produced by {@code task}
     * @throws NullPointerException if any argument is {@code null}
     * @throws Exception if {@code task} throws
     *
     * @apiNote WAWebSendMsgQueueMap.sendMsgQueueMap.enqueue: serialises
     * the send task per group JID string key.
     */
    private  <T> T enqueue(String key, Callable<T> task) throws Exception {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(task, "task");
        var lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
        lock.lock();
        try {
            return task.call();
        } finally {
            lock.unlock();
        }
    }

    @Override
    AckResult send(Jid groupJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        try {
            // WAWebSendGroupMsgJob.encryptAndSendGroupMsg: enqueue per group to
            // serialise sender-key encryption (monotonic counter requirement)
            return enqueue(groupJid.toString(), () -> {
                var container = messageInfo.message();

                // WAWebSendGroupSkmsgJob: resolve group metadata for addressing mode
                var chatMetadata = store.findChatMetadata(groupJid).orElse(null);
                var isCag = chatMetadata instanceof GroupMetadata gm
                        && gm.isDefaultSubgroup();
                var isCagAddon = isCag && isCagAddonMessage(container);
                var isLidAddressingMode = (chatMetadata != null && chatMetadata.isLidAddressingMode())
                        || isCagAddon;
                var addressingMode = isLidAddressingMode ? "lid" : "pn";

                // WAWebSendGroupSkmsgJob: determine sender JID based on addressing mode
                // isCagAddon || isLidAddressingMode → getMeDeviceLid, else getMeDevicePn
                var senderJid = isLidAddressingMode ? selfLidOrPn() : requireSelfJid();

                // WAWebSendGroupSkmsgJob: get group fanout (all devices + phash)
                var fanout = deviceService.getGroupFanout(groupJid);
                var allDevices = fanout.devices();
                var phash = fanout.phash();

                // WAWebApiParticipantStore.getGroupSenderKeyListFromParticipantRecord:
                // split devices into those needing SK distribution and those with keys
                var skDistribDevices = new ArrayList<Jid>();
                var skExistingDevices = new ArrayList<Jid>();
                for (var device : allDevices) {
                    if (store.hasSenderKeyDistributed(groupJid, device)) {
                        skExistingDevices.add(device);
                    } else {
                        skDistribDevices.add(device);
                    }
                }

                // WAWebSendGroupMsgJob.filterIncorrectlyAddressedDevices:
                // filter devices by addressing mode for LID groups
                if (isLidAddressingMode) {
                    skDistribDevices.removeIf(d -> !d.hasLidServer());
                    skExistingDevices.removeIf(d -> !d.hasLidServer());
                } else if (isCag) {
                    skDistribDevices.removeIf(Jid::hasLidServer);
                    skExistingDevices.removeIf(Jid::hasLidServer);
                }

                // WAWebSendGroupSkmsgJob: rotate sender key if needed
                // (triggered when participants are removed from the group)
                var rotateKey = store.clearKeyRotation(groupJid);
                if (rotateKey) {
                    // WAWebSignal.Session.deleteGroupSenderKeyInfo
                    encryption.rotateSenderKey(groupJid, senderJid);
                    // After rotation, all devices need redistribution
                    skDistribDevices.addAll(skExistingDevices);
                    skExistingDevices.clear();
                }

                // WAWebMsgRcatUtils.genContentBindingForMsg: generate RCAT content bindings
                var participantUserJids = Stream.concat(skDistribDevices.stream(), skExistingDevices.stream())
                        .map(Jid::toUserJid)
                        .distinct()
                        .toList();
                var contentBindings = generateContentBindings(messageInfo, participantUserJids);

                // WAWebSendGroupSkmsgJob: create receipt records for all devices
                store.createOrMergeReceiptRecords(messageInfo.key().id(), allDevices);

                // WAWebSendGroupSkmsgJob: get sender key bytes and encrypt SK distribution
                // WAWebGetGroupKeyDistributionMsg: populates ICDC per device
                var senderKeyBytes = encryption.getSenderKeyBytes(groupJid, senderJid);
                var skDistPayloads = skDistribDevices.isEmpty()
                        ? List.<MessageEncryptedPayload>of()
                        : senderKeyDistribution.encrypt(groupJid, senderKeyBytes, skDistribDevices);

                // WAWebEncryptMsgProtobuf.encryptMsgSenderKey: encrypt with sender key
                var plaintext = MessageContainerSpec.encode(container);
                var skmsgPayload = encryption.encryptForGroup(groupJid, senderJid, plaintext);

                // WAWebSendGroupSkmsgJob: build participants node
                // SK distribution → <to> with <enc> + optional <content_binding>
                // No distribution but bindings → <to> with just <content_binding>
                var decryptFail = resolveDecryptFail(container);
                Node participantsNode;
                if (!skDistPayloads.isEmpty()) {
                    participantsNode = ParticipantsStanza.buildSenderKeyDistribution(
                            skDistPayloads, contentBindings, decryptFail);
                } else if (contentBindings != null) {
                    participantsNode = ParticipantsStanza.buildContentBindingOnly(
                            skExistingDevices, contentBindings);
                } else {
                    participantsNode = null;
                }

                // WAWebSendGroupSkmsgJob: build open group bot node when applicable
                // WAWebBotGroupGatingUtils.isOpenGroupBotSendEnabled: AB prop gate
                var isOpenBotGroup = groupMetadata != null && groupMetadata.isOpenBotGroup()
                        && abPropsService.getBool(ABProp.OPEN_GROUP_BOT_SEND_ENABLED);
                Node openBotNode = null;
                if (isOpenBotGroup) {
                    // WAWebSendGroupSkmsgJob function L: ensure sessions with
                    // the bot device before encrypting
                    deviceService.ensureSessions(List.of(Jid.metaAiBotAccount()));
                    store.createOrMergeReceiptRecords(
                            messageInfo.key().id(), List.of(Jid.metaAiBotAccount()));
                    openBotNode = botStanza.buildForGroup(messageInfo, true);
                }

                // WAWebSendGroupSkmsgJob: identity node when any pkmsg
                // Also needed when open group bot encryption produced pkmsg
                var needsIdentity = ParticipantsStanza.requiresIdentityNode(skDistPayloads);
                if (!needsIdentity && openBotNode != null) {
                    needsIdentity = openBotNode.streamChild("to")
                            .flatMap(to -> to.streamChild("enc"))
                            .anyMatch(enc -> "pkmsg".equals(enc.getAttributeAsString("type", null)));
                }
                var identityNode = needsIdentity ? buildIdentityNode() : null;

                // WAWebSendGroupSkmsgJob: build and send the stanza
                // Use the existing botStanza for 1:1 bot/feedback, or open group bot
                var mediaType = resolveMediaType(container);
                var botNode = openBotNode != null
                        ? openBotNode
                        : botStanza.build(messageInfo, groupJid);
                var stanza = GroupSkmsgFanoutStanza.build(
                        messageInfo.key().id(),
                        groupJid,
                        resolveStanzaType(container),
                        phash,
                        skmsgPayload.ciphertext(),
                        mediaType,
                        decryptFail,
                        resolveEditAttribute(container),
                        addressingMode,
                        participantsNode,
                        identityNode,
                        metaStanza.buildChat(groupJid, container, null),
                        bizStanza.buildGroup(container),
                        botNode,
                        reportingStanza.build(messageInfo, requireSelfJid(), groupJid),
                        SenderContentBindingStanza.build(senderJid, contentBindings)
                );

                flushStore();
                var ackNode = client.sendNode(stanza);
                var ack = AckParser.parse(ackNode);

                // WAWebSendGroupSkmsgJob: handle ack errors
                if (!ack.isSuccess()) {
                    var errorCode = ack.error().orElse(-1);
                    switch (errorCode) {
                        case NackReason.STALE_GROUP_ADDRESSING_MODE -> {
                            // WAWebSendGroupSkmsgJob: error 421 → query group, migrate, mark FAILED
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "encryptAndSendSenderKeyMsg: ack with error code 421 for {0}, refreshing metadata",
                                    groupJid);
                            // Flip addressing mode so next retry uses the correct one
                            migrateAddressingMode(groupJid, !isLidAddressingMode);
                            throw new WhatsAppMessageException.Send.Unknown(
                                    "Stale group addressing mode for " + groupJid, null);
                        }
                        default -> {
                            throw new WhatsAppMessageException.Send.Unknown(
                                    "Invalid ack from server for group " + groupJid
                                    + ", error: " + errorCode, null);
                        }
                    }
                }

                // WAWebApiParticipantStore.markHasSenderKey: mark SK as distributed
                for (var device : skDistribDevices) {
                    store.markSenderKeyDistributed(groupJid, device);
                }

                // WAWebSendGroupSkmsgJob: handle phash mismatch → resend as group direct
                var serverPhash = ack.phash().orElse(null);
                if (serverPhash != null && !serverPhash.equals(phash)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "encryptAndSendSenderKeyMsg: phash mismatch for {0}, server: {1}",
                            messageInfo.key().id(), serverPhash);
                    resendAsGroupDirect(groupJid, messageInfo, allDevices, addressingMode);
                }

                // WAWebSendGroupSkmsgJob: handle addressing mode mismatch
                // WAWebGroupHandleAddressingModeMismatch.handleAddressingModeMismatch
                ack.addressingMode().ifPresent(serverMode -> {
                    if (!serverMode.equals(addressingMode)) {
                        LOGGER.log(System.Logger.Level.INFO,
                                "Addressing mode mismatch for {0}: local={1}, server={2}, migrating",
                                groupJid, addressingMode, serverMode);
                        migrateAddressingMode(groupJid, "lid".equals(serverMode));
                    }
                });

                return ack;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send group message to " + groupJid, e);
        }
    }

    /**
     * Generates RCAT content bindings for all group participants.
     *
     * <p>Only applicable for URL messages with a messageSecret when the
     * group size is within the configured RCAT limit.
     *
     * @return the per-recipient RCAT tags, or {@code null} if not applicable
     *
     * @apiNote WAWebMsgRcatUtils.genContentBindingForMsg: checks type=CHAT,
     * isUrlMessage, isSentByMe, messageSecret != null,
     * recipients.length <= maximum_group_size_for_rcat.
     */
    private Map<Jid, byte[]> generateContentBindings(
            ChatMessageInfo messageInfo,
            List<Jid> participantJids
    ) {
        var messageSecret = messageInfo.messageSecret().orElse(null);
        if (messageSecret == null) {
            return null;
        }

        // WAWebMsgRcatUtils: only for URL messages (extendedTextMessage with matchedText)
        var message = messageInfo.message().content();
        if (!(message instanceof TextMessage text) || text.matchedText().isEmpty()) {
            return null;
        }

        // WAWebMsgRcatUtils: check group size limit
        var maxGroupSize = abPropsService.getInt(ABProp.MAXIMUM_GROUP_SIZE_FOR_RCAT);
        if (participantJids.size() > maxGroupSize) {
            return null;
        }

        var contentId = ContentBindingToken.resolveContentId(text.matchedText().get());
        var selfJid = requireSelfJid().toUserJid();

        try {
            return ContentBindingToken.generate(
                    messageInfo.key().id(), messageSecret,
                    selfJid, participantJids, contentId);
        } catch (GeneralSecurityException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to generate content bindings: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines whether a message is a CAG addon type that should be
     * sent to LID-addressed participants.
     *
     * @param container the message container
     * @return {@code true} for reactions, comments, event responses,
     *         and poll votes
     *
     * @apiNote WAWebSendGroupMsgJob.isCagAddon: returns {@code true}
     * for reaction_enc, comment, event_response, poll_vote, and
     * protocol addon revokes.
     */
    private static boolean isCagAddonMessage(MessageContainer container) {
        return switch (container.content()) {
            case EncryptedReactionMessage _ -> true;
            case EncryptedCommentMessage _ -> true;
            case EncryptedEventResponseMessage _ -> true;
            case PollUpdateMessage _ -> true;
            default -> false;
        };
    }

    /**
     * Resends the message to delta devices using group-direct (per-device)
     * encryption after a phash mismatch.
     *
     * @apiNote WAWebResendGroupMsg.resendGroupMsg: re-queries the group,
     * computes delta device list, sends via sendDirectMsgToDeviceList
     * with GROUP_DIRECT fanout type.
     */
    private void resendAsGroupDirect(
            Jid groupJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            String addressingMode
    ) {
        // WAWebResendGroupMsg: re-query group, get refreshed fanout
        var refreshedFanout = deviceService.getGroupFanout(groupJid);

        // WAWebResendGroupMsg: delta = refreshed - original
        var originalJids = originalDevices.stream()
                .map(Jid::toString)
                .collect(Collectors.toUnmodifiableSet());
        var deltaDevices = refreshedFanout.devices().stream()
                .filter(device -> !originalJids.contains(device.toString()))
                .toList();

        if (deltaDevices.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "No new devices after group phash resync for {0}", groupJid);
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Resending as group-direct to {0} new devices for {1}",
                deltaDevices.size(), groupJid);

        // WAWebSendDirectMsgToDeviceList: GROUP_DIRECT fanout
        var container = messageInfo.message();
        deviceService.ensureSessions(deltaDevices);
        var senderIcdc = deviceService.computeIcdc(requireSelfJid())
                .orElse(null);
        var payloads = encryptForDevices(encryption, deltaDevices, container, groupJid, senderIcdc, null);
        if (payloads.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Group direct: encryption failed for all delta devices for {0}",
                    groupJid);
            return;
        }

        var identityNode = ParticipantsStanza.requiresIdentityNode(payloads)
                ? buildIdentityNode() : null;

        // WAWebSendDirectMsgToDeviceList: includes empty <enc type="skmsg">
        // to signal group-direct fanout to the server
        var emptySkmsgNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                .attribute("mediatype", resolveMediaType(container))
                .build();

        var stanza = ChatFanoutStanza.build(
                messageInfo.key().id(),
                groupJid,
                resolveStanzaType(container),
                payloads,
                resolveEditAttribute(container),
                addressingMode,
                null,
                resolveMediaType(container),
                resolveDecryptFail(container),
                resolveNativeFlowName(container),
                null,
                false,
                null,
                null,
                null,
                null,
                identityNode,
                metaStanza.buildChat(groupJid, container, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                emptySkmsgNode
        );

        flushStore();
        client.sendNode(stanza);
    }

    /**
     * Migrates a group's addressing mode from PN→LID or LID→PN.
     *
     * <p>Converts each participant's JID to the target addressing mode,
     * rebuilds the group metadata with the updated participants and
     * addressing mode flag, and clears all sender key distribution state
     * so that keys are redistributed on the next send.
     *
     * @param groupJid the group JID
     * @param toLid    {@code true} to migrate to LID, {@code false} to PN
     *
     * @apiNote WAWebGroupHandleAddressingModeMismatch.handleAddressingModeMismatch:
     * calls {@code migrateParticipantInfoAddressingMode} to convert all
     * participant, admin, and sender-key device JIDs, then updates the
     * group metadata's {@code isLidAddressingMode} flag.
     * WAWebDBGroupParticipant.migrateParticipantInfoAddressingMode:
     * maps each JID through {@code toAddressingModeFactory(isLid)},
     * resets all sender keys to not-distributed, and persists the changes.
     */
    private void migrateAddressingMode(Jid groupJid, boolean toLid) {
        var metadata = store.findChatMetadata(groupJid).orElse(null);
        if (metadata == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot migrate addressing mode for {0}: no metadata", groupJid);
            return;
        }

        // WAWebDBGroupParticipant.migrateParticipantInfoAddressingMode:
        // convert each participant JID to the target addressing mode
        var migratedParticipants = new ArrayList<GroupParticipant>();
        for (var participant : metadata.participants()) {
            var convertedJid = convertJid(participant.userJid(), toLid);
            if (convertedJid != null) {
                migratedParticipants.add(new com.github.auties00.cobalt.model.group.GroupParticipantBuilder()
                        .userJid(convertedJid)
                        .rank(participant.rank().orElse(null))
                        .build());
            } else {
                // WAWebDBGroupParticipant: if conversion fails, keep original
                LOGGER.log(System.Logger.Level.DEBUG,
                        "No {0} mapping for {1}, keeping original",
                        toLid ? "LID" : "PN", participant.userJid());
                migratedParticipants.add(participant);
            }
        }

        // WAWebDBGroupParticipant: update metadata in place
        metadata.clearParticipants();
        metadata.addAllParticipants(migratedParticipants);
        metadata.setLidAddressingMode(toLid);

        // WAWebDBGroupParticipant: reset all sender keys to not-distributed
        store.clearSenderKeyDistribution(groupJid);

        LOGGER.log(System.Logger.Level.INFO,
                "Migrated addressing mode for {0} to {1} ({2} participants)",
                groupJid, toLid ? "lid" : "pn", migratedParticipants.size());
    }

    /**
     * Converts a JID to the target addressing mode.
     *
     * @param jid   the JID to convert
     * @param toLid {@code true} to convert PN→LID, {@code false} for LID→PN
     * @return the converted JID, or {@code null} if no mapping exists
     *
     * @apiNote WAWebLidMigrationUtils.toAddressingModeFactory: returns a
     * function that converts between PN and LID addressing modes.
     */
    private Jid convertJid(Jid jid, boolean toLid) {
        if (toLid) {
            return jid.hasLidServer() ? jid : store.findLidByPhone(jid).orElse(null);
        } else {
            return jid.hasUserServer() ? jid : store.findPhoneByLid(jid).orElse(null);
        }
    }
}
