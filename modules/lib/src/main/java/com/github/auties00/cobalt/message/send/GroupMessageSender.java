package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.ack.NackReason;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.message.send.token.ContentBindingToken;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.group.GroupParticipantBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.AddressingModeMismatchEventBuilder;
import com.github.auties00.cobalt.wam.event.MdDeviceSyncAckEventBuilder;
import com.github.auties00.cobalt.wam.event.MdGroupParticipantMissAckEventBuilder;
import com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder;
import com.github.auties00.cobalt.wam.type.AddressingMode;
import com.github.auties00.cobalt.wam.type.ClientGroupSizeBucket;
import com.github.auties00.cobalt.wam.type.E2eDestination;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.MismatchOriginType;
import com.github.auties00.cobalt.wam.type.PrekeysFetchContext;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;
import com.github.auties00.cobalt.wam.type.WamSizeBuckets;

import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sends messages to group chats. The default path uses sender-key (SKMSG)
 * encryption: the payload is encrypted once with the group sender key, and a
 * separate sender-key distribution message is encrypted per device for
 * members that do not yet hold the key. When the server reports a phash
 * mismatch, the delta devices receive the same message as a per-device
 * group-direct fanout.
 */
@WhatsAppWebModule(moduleName = "WAWebSendGroupMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendGroupSkmsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendGroupDirectJob")
@WhatsAppWebModule(moduleName = "WAWebSendGroupKeyDistributionMsgJob")
final class GroupMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Holds the logger used for group-send diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(GroupMessageSender.class.getName());

    /**
     * Holds the encryption service used for both sender-key and per-device
     * encryption paths.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the device service used for fanout resolution and session management.
     */
    private final DeviceService deviceService;

    /**
     * Holds the sender-key distribution service used to encrypt the
     * per-device sender-key distribution payloads.
     */
    private final SenderKeyDistribution senderKeyDistribution;

    /**
     * Holds the bot-specific stanza builder.
     */
    private final BotStanza botStanza;

    /**
     * Holds the business-specific stanza builder used for payment native flows.
     */
    private final BizStanza bizStanza;

    /**
     * Holds the meta stanza builder.
     */
    private final MetaStanza metaStanza;

    /**
     * Holds the reporting stanza builder.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Holds the per-group locks used to serialise sender-key encryption.
     * Concurrent sends to the same group are serialised because the Signal
     * sender-key counter must increase monotonically per group per sender.
     */
    private final ConcurrentMap<String, ReentrantLock> locks;

    /**
     * Constructs a group sender bound to the given dependencies.
     *
     * @param client                the WhatsApp client used to dispatch stanzas
     * @param encryption            the message encryption service
     * @param deviceService         the device service
     * @param abPropsService        the AB-props service
     * @param senderKeyDistribution the sender-key distribution service
     * @param botStanza             the bot stanza builder
     * @param bizStanza             the business stanza builder
     * @param metaStanza            the meta stanza builder
     * @param reportingStanza       the reporting stanza builder
     * @param wamService            the WAM telemetry service shared with the base sender
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupMsgJob", exports = "encryptAndSendGroupMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    GroupMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            SenderKeyDistribution senderKeyDistribution,
            BotStanza botStanza,
            BizStanza bizStanza,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza,
            WamService wamService
    ) {
        super(client, abPropsService, wamService);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.senderKeyDistribution = Objects.requireNonNull(senderKeyDistribution, "senderKeyDistribution");
        this.botStanza = Objects.requireNonNull(botStanza, "botStanza");
        this.bizStanza = Objects.requireNonNull(bizStanza, "bizStanza");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
        this.locks = new ConcurrentHashMap<>();
    }

    /**
     * Runs the given task while holding the lock associated with {@code key}.
     * Tasks under the same key are serialised; tasks under different keys may
     * run concurrently.
     *
     * @param <T>  the task result type
     * @param key  the lock key, typically the group JID string
     * @param task the task to execute under the lock
     * @return the value returned by {@code task}
     * @throws NullPointerException if any argument is {@code null}
     * @throws Exception            propagated from {@code task}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgQueueMap", exports = "sendMsgQueueMap",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

    /**
     * Sends the given message to a group chat. Resolves the addressing mode,
     * encrypts the payload with the sender key, distributes the sender key to
     * devices that do not yet hold it, and reacts to the server ack by
     * migrating the addressing mode and/or resending to the delta devices
     * when needed.
     *
     * @param groupJid    the target group JID
     * @param messageInfo the outgoing message
     * @return the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupMsgJob", exports = "encryptAndSendGroupMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendGroupSkmsgJob", exports = "encryptAndSendSenderKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult send(Jid groupJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        try {
            return enqueue(groupJid.toString(), () -> {
                var rawContainer = messageInfo.message();

                var chatMetadata = store.findChatMetadata(groupJid).orElse(null);
                var isCag = chatMetadata instanceof GroupMetadata gm
                        && gm.isDefaultSubgroup();
                var isCagAddon = isCag && isCagAddonMessage(rawContainer);
                var isLidAddressingMode = (chatMetadata != null && chatMetadata.isLidAddressingMode())
                        || isCagAddon;
                var addressingMode = isLidAddressingMode ? "lid" : "pn";

                var senderJid = isLidAddressingMode ? selfLidOrPn() : requireSelfJid();

                var fanout = deviceService.getGroupFanout(groupJid, senderJid);
                var allDevices = fanout.devices();
                var phash = fanout.phash();

                var skDistribDevices = new ArrayList<Jid>();
                var skExistingDevices = new ArrayList<Jid>();
                for (var device : allDevices) {
                    if (store.hasSenderKeyDistributed(groupJid, device)) {
                        skExistingDevices.add(device);
                    } else {
                        skDistribDevices.add(device);
                    }
                }

                if (isLidAddressingMode) {
                    skDistribDevices.removeIf(d -> !d.hasLidServer());
                    skExistingDevices.removeIf(d -> !d.hasLidServer());
                } else {
                    skDistribDevices.removeIf(Jid::hasLidServer);
                    skExistingDevices.removeIf(Jid::hasLidServer);
                }

                var isCapiGroup = chatMetadata instanceof GroupMetadata gm2
                        && gm2.hasCapi();
                var container = isCapiGroup
                        ? applyCapiFlag(rawContainer)
                        : rawContainer;

                var rotateKey = store.clearKeyRotation(groupJid);
                if (rotateKey) {
                    encryption.rotateSenderKey(groupJid, senderJid);
                    skDistribDevices.addAll(skExistingDevices);
                    skExistingDevices.clear();
                }

                var participantUserJids = Stream.concat(skDistribDevices.stream(), skExistingDevices.stream())
                        .map(Jid::toUserJid)
                        .distinct()
                        .toList();
                var contentBindings = generateContentBindings(messageInfo, participantUserJids);

                var allSkDevices = Stream.concat(skDistribDevices.stream(), skExistingDevices.stream())
                        .toList();
                store.createOrMergeReceiptRecords(messageInfo.key().id().orElseThrow(), allSkDevices);

                var senderKeyBytes = encryption.getSenderKeyBytes(groupJid, senderJid);
                List<MessageEncryptedPayload> skDistPayloads;
                if (skDistribDevices.isEmpty()) {
                    skDistPayloads = List.of();
                } else {
                    var depletedPrekeyCount = deviceService.ensureSessions(skDistribDevices);
                    emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.GROUP, allSkDevices.size());
                    skDistPayloads = senderKeyDistribution.encrypt(groupJid, senderKeyBytes, skDistribDevices);
                }

                var isBotFeedback = container.content() instanceof ProtocolMessage pm
                        && pm.type().orElse(null) == ProtocolMessage.Type.BOT_FEEDBACK_MESSAGE;

                byte[] skmsgCiphertext;
                if (isBotFeedback) {
                    skmsgCiphertext = null;
                } else {
                    var plaintext = MessageContainerSpec.encode(container);
                    try {
                        skmsgCiphertext = encryption.encryptForGroup(groupJid, senderJid, plaintext)
                                .ciphertext();
                        emitE2eMessageSendSenderKeyEvent(
                                groupJid, container,
                                E2eDestination.GROUP,
                                isLidAddressingMode, true);
                    } catch (RuntimeException skmsgError) {
                        emitE2eMessageSendSenderKeyEvent(
                                groupJid, container,
                                E2eDestination.GROUP,
                                isLidAddressingMode, false);
                        throw skmsgError;
                    }
                }

                var decryptFail = resolveDecryptFail(container);
                Node participantsNode;
                if (!isBotFeedback && !skDistPayloads.isEmpty()) {
                    participantsNode = ParticipantsStanza.buildSenderKeyDistribution(
                            skDistPayloads, contentBindings, decryptFail);
                } else if (contentBindings != null) {
                    participantsNode = ParticipantsStanza.buildContentBindingOnly(
                            skExistingDevices, contentBindings);
                } else {
                    participantsNode = null;
                }

                var isOpenBotGroup = chatMetadata != null && chatMetadata.isOpenBotGroup()
                        && abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT)
                        && abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ENABLED);
                Node openBotNode = null;
                if (isOpenBotGroup) {
                    deviceService.ensureSessions(List.of(Jid.metaAiBotAccount()));
                    store.createOrMergeReceiptRecords(
                            messageInfo.key().id().orElseThrow(), List.of(Jid.metaAiBotAccount()));
                    openBotNode = botStanza.buildForGroup(messageInfo, true);
                }

                var needsIdentity = ParticipantsStanza.requiresIdentityNode(skDistPayloads);
                if (!needsIdentity && openBotNode != null) {
                    needsIdentity = openBotNode.streamChild("to")
                            .flatMap(to -> to.streamChild("enc"))
                            .anyMatch(enc -> "pkmsg".equals(enc.getAttributeAsString("type", null)));
                }
                var identityNode = needsIdentity ? buildIdentityNode() : null;

                var mediaType = resolveMediaType(container);
                var botNode = openBotNode != null
                        ? openBotNode
                        : botStanza.build(messageInfo, groupJid);
                var stanzaPhash = isBotFeedback ? null : phash;
                var stanza = GroupSkmsgFanoutStanza.build(
                        messageInfo.key().id().orElseThrow(),
                        groupJid,
                        resolveStanzaType(container),
                        stanzaPhash,
                        skmsgCiphertext,
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

                store.updateIdentityRange(allSkDevices);

                flushStore();
                var ackNode = client.sendNode(stanza);
                var ack = AckParser.parse(ackNode);

                if (!ack.isSuccess()) {
                    var errorCode = ack.error().orElse(-1);
                    if (errorCode == NackReason.STALE_GROUP_ADDRESSING_MODE.code()) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "encryptAndSendSenderKeyMsg: ack with error code 421 for {0}, refreshing metadata",
                                groupJid);
                        migrateAddressingMode(groupJid, !isLidAddressingMode);
                        throw new WhatsAppMessageException.Send.Unknown(
                                "Stale group addressing mode for " + groupJid, null);
                    }
                    throw new WhatsAppMessageException.Send.Unknown(
                            "Invalid ack from server for group " + groupJid
                            + ", error: " + errorCode, null);
                }

                for (var device : skDistribDevices) {
                    store.markSenderKeyDistributed(groupJid, device);
                }

                var serverPhash = ack.phash().orElse(null);
                if (serverPhash != null && !serverPhash.equals(phash)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "encryptAndSendSenderKeyMsg: phash mismatch for {0}, server: {1}",
                            messageInfo.key().id(), serverPhash);
                    var serverAddressingMode = ack.addressingMode().orElse(null);
                    resendAsGroupDirect(groupJid, messageInfo, allSkDevices,
                            addressingMode, serverAddressingMode, chatMetadata, senderJid);
                }

                ack.addressingMode().ifPresent(serverMode -> {
                    if (!serverMode.equals(addressingMode)) {
                        LOGGER.log(System.Logger.Level.INFO,
                                "Addressing mode mismatch for {0}: local={1}, server={2}, migrating",
                                groupJid, addressingMode, serverMode);
                        wamService.commit(new AddressingModeMismatchEventBuilder()
                                .localAddressingMode(wamAddressingMode(addressingMode))
                                .serverAddressingMode(wamAddressingMode(serverMode))
                                .mismatchOrigin(MismatchOriginType.ACK_OUTGOING_MESSAGE)
                                .build());
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
     * Sends a standalone sender-key distribution to the group, with no
     * message content. The stanza carries the per-device encrypted
     * {@code SenderKeyDistributionMessage} payloads, the {@code text} type
     * marker, and {@code device_fanout="false"}. Returns silently when every
     * audience device already holds the sender key.
     *
     * @param groupJid the group JID to distribute keys for
     * @param msgId    the id used for the distribution stanza
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupKeyDistributionMsgJob",
            exports = "encryptAndSendGroupKeyDistributionMsg", adaptation = WhatsAppAdaptation.DIRECT)
    void sendKeyDistribution(Jid groupJid, String msgId) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(msgId, "msgId");

        waitForOfflineDelivery();

        try {
            enqueue(groupJid.toString(), () -> {
                var fanout = deviceService.getGroupFanout(groupJid, requireSelfJid());
                var allDevices = fanout.devices();

                var skDistribDevices = new ArrayList<Jid>();
                var skExistingDevices = new ArrayList<Jid>();
                for (var device : allDevices) {
                    if (store.hasSenderKeyDistributed(groupJid, device)) {
                        skExistingDevices.add(device);
                    } else {
                        skDistribDevices.add(device);
                    }
                }

                if (skDistribDevices.isEmpty()) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "encryptAndSendGroupKeyDistributionMsg: skip sending {0}: " +
                                    "sender key distribution list is empty", groupJid);
                    return null;
                }

                var allSkDevices = Stream.concat(skDistribDevices.stream(), skExistingDevices.stream())
                        .toList();
                store.createOrMergeReceiptRecords(msgId, allSkDevices);

                var allLid = skDistribDevices.stream().allMatch(Jid::hasLidServer);
                var senderJid = allLid ? selfLidOrPn() : requireSelfJid();

                var rotateKey = store.clearKeyRotation(groupJid);
                if (rotateKey) {
                    encryption.rotateSenderKey(groupJid, senderJid);
                }

                var senderKeyBytes = encryption.getSenderKeyBytes(groupJid, senderJid);
                deviceService.ensureSessions(skDistribDevices);
                var skDistPayloads = senderKeyDistribution.encrypt(
                        groupJid, senderKeyBytes, skDistribDevices);

                var phash = fanout.phash();

                Node participantsNode = null;
                if (!skDistPayloads.isEmpty()) {
                    participantsNode = ParticipantsStanza.buildSenderKeyDistribution(
                            skDistPayloads, null, "hide");
                }

                var needsIdentity = ParticipantsStanza.requiresIdentityNode(skDistPayloads);
                var identityNode = needsIdentity ? buildIdentityNode() : null;

                var metaNode = new NodeBuilder()
                        .description("meta")
                        .attribute("appdata", "default")
                        .build();
                var encNode = new NodeBuilder()
                        .description("enc")
                        .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                        .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                        .attribute("decrypt-fail", "hide")
                        .build();
                var stanza = new NodeBuilder()
                        .description("message")
                        .attribute("id", msgId)
                        .attribute("to", groupJid)
                        .attribute("phash", phash)
                        .attribute("type", "text")
                        .attribute("device_fanout", "false")
                        .content(metaNode, encNode, participantsNode, identityNode);

                flushStore();
                var ackNode = client.sendNode(stanza);
                var ack = AckParser.parse(ackNode);

                if (ack.error().isPresent()) {
                    throw new WhatsAppMessageException.Send.Unknown(
                            "encryptAndSendSenderKeyMsg: Invalid ack from server for " + groupJid, null);
                }

                for (var device : skDistribDevices) {
                    store.markSenderKeyDistributed(groupJid, device);
                }

                return ack;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send key distribution to " + groupJid, e);
        }
    }

    /**
     * Generates the per-recipient RCAT content-binding tags for the message,
     * or {@code null} when the conditions to emit them are not met (no
     * message secret, non-URL payload, or group size above the configured
     * RCAT limit).
     *
     * @param messageInfo     the outgoing message
     * @param participantJids the list of participant user JIDs
     * @return the per-recipient RCAT tags, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "genContentBindingForMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Map<Jid, byte[]> generateContentBindings(
            ChatMessageInfo messageInfo,
            List<Jid> participantJids
    ) {
        var messageSecret = messageInfo.messageSecret().orElse(null);
        if (messageSecret == null) {
            return null;
        }

        var message = messageInfo.message().content();
        if (!(message instanceof ExtendedTextMessage text) || text.matchedText().isEmpty()) {
            return null;
        }

        var maxGroupSize = abPropsService.getInt(ABProp.MAXIMUM_GROUP_SIZE_FOR_RCAT);
        if (participantJids.size() > maxGroupSize) {
            return null;
        }

        var contentId = ContentBindingToken.resolveContentId(text.matchedText().get());
        var selfJid = requireSelfJid().toUserJid();

        try {
            return ContentBindingToken.generate(
                    messageInfo.key().id().orElseThrow(), messageSecret,
                    selfJid, participantJids, contentId);
        } catch (GeneralSecurityException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to generate content bindings: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns the given container with {@code capiCreatedGroup} set to
     * {@code true} on its message-context info, mutating the existing
     * context info when present and otherwise creating a new one.
     *
     * @param container the original message container
     * @return the container with the CAPI flag applied
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "updateGroupMsgProtoWithCapiFlag",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MessageContainer applyCapiFlag(MessageContainer container) {
        var existingCtxInfo = container.messageContextInfo().orElse(null);
        if (existingCtxInfo != null) {
            existingCtxInfo.setCapiCreatedGroup(true);
            return container;
        }

        return container.withMessageContextInfo(
                new ChatMessageContextInfoBuilder()
                        .capiCreatedGroup(true)
                        .build());
    }

    /**
     * Returns whether the given container holds a CAG addon payload that
     * must always reach LID-addressed participants (reactions, comments,
     * event responses, and poll votes).
     *
     * @param container the message container
     * @return {@code true} when the payload is a CAG addon
     */
    @WhatsAppWebExport(moduleName = "WAWebSendGroupMsgJob", exports = "isCagAddon",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isCagAddonMessage(MessageContainer container) {
        return switch (container.content()) {
            case EncReactionMessage _, EncCommentMessage _, EncEventResponseMessage _, PollUpdateMessage _ -> true;
            default -> false;
        };
    }

    /**
     * Resends the message to the delta devices using per-device group-direct
     * encryption after a phash mismatch. Emits the {@code MdDeviceSyncAck}
     * WAM event before re-querying the group, then encrypts and dispatches
     * the resend stanza for the new devices.
     *
     * @param groupJid               the target group JID
     * @param messageInfo            the message being resent
     * @param originalDevices        the device list used for the initial send
     * @param addressingMode         the local addressing mode used on the wire
     *                               ({@code "lid"} or {@code "pn"})
     * @param serverAddressingMode   the addressing mode reported on the ack,
     *                               possibly {@code null}
     * @param groupMetadataCandidate the group metadata used to derive the
     *                               {@code localAddressingMode} property on
     *                               the emitted {@code MdDeviceSyncAck} event
     * @param senderJid              the sender device JID used for the
     *                               original send; its server type drives
     *                               the {@code isLid} property
     */
    @WhatsAppWebExport(moduleName = "WAWebResendGroupMsg", exports = "resendGroupMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendGroupDirectJob", exports = "encryptAndSendGroupDirectMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebPostMdDeviceSyncAckMetric",
            exports = "postMdDeviceSyncAckMetric", adaptation = WhatsAppAdaptation.DIRECT)
    private void resendAsGroupDirect(
            Jid groupJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            String addressingMode,
            String serverAddressingMode,
            ChatMetadata groupMetadataCandidate,
            Jid senderJid
    ) {
        var senderIsLid = senderJid != null && senderJid.hasLidServer();
        AddressingMode localWamMode = null;
        if (groupMetadataCandidate instanceof GroupMetadata gm) {
            localWamMode = gm.isLidAddressingMode() ? AddressingMode.LID : AddressingMode.PN;
        }
        wamService.commit(new MdDeviceSyncAckEventBuilder()
                .revoke(UserMessageSender.isRevokeMessage(messageInfo))
                .chatType(UserMessageSender.chatTypeFromJid(groupJid))
                .isLid(senderIsLid)
                .localAddressingMode(localWamMode)
                .serverAddressingMode(wamAddressingMode(serverAddressingMode))
                .build());

        var refreshedFanout = deviceService.getGroupFanout(groupJid, requireSelfJid());

        var refreshedMetadata = store.findChatMetadata(groupJid).orElse(null);
        emitMdGroupParticipantMissAck(messageInfo, originalDevices, refreshedMetadata);

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

        var container = messageInfo.message();
        var depletedPrekeyCount = deviceService.ensureSessions(deltaDevices);
        emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.GROUP, deltaDevices.size());
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

        var emptySkmsgNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                .attribute("mediatype", resolveMediaType(container))
                .build();

        var stanza = ChatFanoutStanza.build(
                messageInfo.key().id().orElseThrow(),
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
     * Commits the {@code MdGroupParticipantMissAck} WAM event when the
     * group's participant record changed between the original SKMSG fan-out
     * and the post-phash-mismatch re-query. The event is suppressed when no
     * participants were added or removed.
     *
     * @param messageInfo       the message being resent
     * @param originalDevices   the device list used for the initial SKMSG send
     * @param refreshedMetadata the refreshed chat metadata, possibly {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMaybePostMdGroupSyncMetrics",
            exports = "maybePostGroupSyncMetrics", adaptation = WhatsAppAdaptation.DIRECT)
    private void emitMdGroupParticipantMissAck(
            ChatMessageInfo messageInfo,
            Collection<Jid> originalDevices,
            ChatMetadata refreshedMetadata
    ) {
        var originalUserJids = originalDevices.stream()
                .map(Jid::toUserJid)
                .map(Jid::toString)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> currentUserJids;
        if (refreshedMetadata instanceof GroupMetadata gm) {
            currentUserJids = gm.participants().stream()
                    .map(p -> p.userJid().toString())
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            currentUserJids = Set.of();
        }

        var added = 0;
        for (var jid : currentUserJids) {
            if (!originalUserJids.contains(jid)) {
                added++;
            }
        }
        var removed = 0;
        for (var jid : originalUserJids) {
            if (!currentUserJids.contains(jid)) {
                removed++;
            }
        }

        if (added == 0 && removed == 0) {
            return;
        }

        var isLid = originalDevices.stream().anyMatch(Jid::hasLidServer);

        int participantCount = 0;
        if (refreshedMetadata instanceof GroupMetadata gm) {
            participantCount = gm.participants().size();
        }
        var groupSizeBucket = toGroupSizeBucket(Math.max(participantCount, 32));

        var typeOfGroup = refreshedMetadata instanceof GroupMetadata gm
                ? typeOfGroupFromMetadata(gm)
                : TypeOfGroupEnum.GROUP;

        wamService.commit(new MdGroupParticipantMissAckEventBuilder()
                .messageIsRevoke(UserMessageSender.isRevokeMessage(messageInfo))
                .groupSizeBucket(groupSizeBucket)
                .typeOfGroup(typeOfGroup)
                .isLid(isLid)
                .participantAddCount(added)
                .participantRemoveCount(removed)
                .build());
    }

    /**
     * Maps the given participant count to its {@link ClientGroupSizeBucket}.
     *
     * @param count the participant count, already capped to a minimum of 32
     *              by the caller
     * @return the matching bucket; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamNumberToClientGroupSizeBucket",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static ClientGroupSizeBucket toGroupSizeBucket(int count) {
        if (count <= 33) return ClientGroupSizeBucket.SMALL;
        if (count <= 65) return ClientGroupSizeBucket.MEDIUM;
        if (count <= 129) return ClientGroupSizeBucket.LARGE;
        if (count <= 257) return ClientGroupSizeBucket.EXTRA_LARGE;
        if (count <= 513) return ClientGroupSizeBucket.XX_LARGE;
        if (count <= 1025) return ClientGroupSizeBucket.LT1024;
        if (count <= 1501) return ClientGroupSizeBucket.LT1500;
        if (count <= 2001) return ClientGroupSizeBucket.LT2000;
        if (count <= 2501) return ClientGroupSizeBucket.LT2500;
        if (count <= 3001) return ClientGroupSizeBucket.LT3000;
        if (count <= 3501) return ClientGroupSizeBucket.LT3500;
        if (count <= 4001) return ClientGroupSizeBucket.LT4000;
        if (count <= 4501) return ClientGroupSizeBucket.LT4500;
        if (count <= 5001) return ClientGroupSizeBucket.LT5000;
        return ClientGroupSizeBucket.LARGEST_BUCKET;
    }

    /**
     * Maps the given {@link GroupMetadata} to the WAM {@link TypeOfGroupEnum}
     * used on metrics events.
     *
     * @param metadata the group metadata
     * @return the matching {@link TypeOfGroupEnum}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupType",
            exports = {"getGroupTypeFromGroupMetadata", "groupTypeToWamEnum"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static TypeOfGroupEnum typeOfGroupFromMetadata(GroupMetadata metadata) {
        if (metadata.isDefaultSubgroup()) {
            return TypeOfGroupEnum.DEFAULT_SUBGROUP;
        }
        if (metadata.isGeneralSubgroup()) {
            return TypeOfGroupEnum.GROUP;
        }
        if (metadata.parentCommunityJid().isPresent()) {
            return TypeOfGroupEnum.SUBGROUP;
        }
        return TypeOfGroupEnum.GROUP;
    }

    /**
     * Migrates the group's addressing mode by converting every participant
     * JID to the target server, updating the metadata flag, and clearing the
     * sender-key distribution state so the next send redistributes the keys.
     *
     * @param groupJid the group JID
     * @param toLid    {@code true} to migrate to LID, {@code false} to PN
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupHandleAddressingModeMismatch", exports = "handleAddressingModeMismatch",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebDBGroupParticipant", exports = "migrateParticipantInfoAddressingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void migrateAddressingMode(Jid groupJid, boolean toLid) {
        var metadata = store.findChatMetadata(groupJid).orElse(null);
        if (metadata == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot migrate addressing mode for {0}: no metadata", groupJid);
            return;
        }

        var migratedParticipants = new ArrayList<GroupParticipant>();
        for (var participant : metadata.participants()) {
            var convertedJid = convertJid(participant.userJid(), toLid);
            if (convertedJid != null) {
                migratedParticipants.add(new GroupParticipantBuilder()
                        .userJid(convertedJid)
                        .rank(participant.rank().orElse(null))
                        .build());
            } else {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "No {0} mapping for {1}, keeping original",
                        toLid ? "LID" : "PN", participant.userJid());
                migratedParticipants.add(participant);
            }
        }

        metadata.clearParticipants();
        metadata.addAllParticipants(migratedParticipants);
        metadata.setLidAddressingMode(toLid);

        store.clearSenderKeyDistribution(groupJid);

        LOGGER.log(System.Logger.Level.INFO,
                "Migrated addressing mode for {0} to {1} ({2} participants)",
                groupJid, toLid ? "lid" : "pn", migratedParticipants.size());
    }

    /**
     * Converts the given JID to the target addressing mode by consulting the
     * LID/PN mapping in the store.
     *
     * @param jid   the JID to convert
     * @param toLid {@code true} for PN to LID, {@code false} for LID to PN
     * @return the converted JID, or {@code null} when no mapping exists
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toAddressingModeFactory",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid convertJid(Jid jid, boolean toLid) {
        if (toLid) {
            return jid.hasLidServer() ? jid : store.findLidByPhone(jid).orElse(null);
        } else {
            return jid.hasUserServer() ? jid : store.findPhoneByLid(jid).orElse(null);
        }
    }

    /**
     * Maps the stanza addressing-mode string to the WAM
     * {@link AddressingMode} enum used by
     * {@link AddressingModeMismatchEventBuilder}.
     *
     * @param mode the stanza addressing-mode string, possibly {@code null}
     * @return {@link AddressingMode#LID} for {@code "lid"},
     *         {@link AddressingMode#PN} for any other non-null value, or
     *         {@code null} when {@code mode} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamAddressingModeUtils",
            exports = "getWamAddressingModeFromString",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static AddressingMode wamAddressingMode(String mode) {
        if (mode == null) {
            return null;
        }
        return "lid".equals(mode) ? AddressingMode.LID : AddressingMode.PN;
    }

    /**
     * Commits one {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEvent}
     * per depleted one-time pre-key reported by the last
     * {@code ensureSessions} call. No-op when {@code depletedPrekeyCount} is
     * not positive.
     *
     * @param depletedPrekeyCount the number of depleted one-time pre-keys
     * @param messageType         the WAM message type for this send
     * @param deviceCount         the device count used for the
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
        var bucket = deviceCount == null ? null : WamSizeBuckets.numberToSizeBucket(deviceCount);
        for (var i = 0; i < depletedPrekeyCount; i++) {
            wamService.commit(new PrekeysDepletionEventBuilder()
                    .prekeysFetchReason(PrekeysFetchContext.SEND_MESSAGE)
                    .messageType(messageType)
                    .deviceSizeBucket(bucket)
                    .build());
        }
    }
}
