package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.ChatFanoutStanza;
import com.github.auties00.cobalt.message.send.stanza.MetaStanza;
import com.github.auties00.cobalt.message.send.stanza.ParticipantsStanza;
import com.github.auties00.cobalt.message.send.stanza.ReportingStanza;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.PrekeysFetchContext;
import com.github.auties00.cobalt.wam.type.WamSizeBuckets;

import java.util.*;

/**
 * Sends status updates to the {@code status@broadcast} broadcast JID. Status
 * messages use sender-key encryption (like groups) but the audience is
 * derived from the user's status privacy preferences. The wire stanza
 * includes a {@code <meta status_setting="...">} attribute that mirrors the
 * configured privacy mode.
 */
@WhatsAppWebModule(moduleName = "WAWebEncryptAndSendStatusMsg")
final class StatusMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Holds the logger used for status-message diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(StatusMessageSender.class.getName());

    /**
     * Holds the encryption service used for sender-key and per-device
     * encryption paths.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the device service used for audience fanout and session management.
     */
    private final DeviceService deviceService;

    /**
     * Holds the sender-key distribution service used to push the sender key to
     * audience devices that do not yet hold it.
     */
    private final SenderKeyDistribution senderKeyDistribution;

    /**
     * Holds the meta stanza builder responsible for the {@code status_setting}
     * and other meta attributes.
     */
    private final MetaStanza metaStanza;

    /**
     * Holds the reporting stanza builder responsible for generating the
     * {@code reporting} node payload.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Constructs a status sender bound to the given dependencies.
     *
     * @param client                the WhatsApp client used to dispatch stanzas
     * @param encryption            the message encryption service
     * @param deviceService         the device service used for audience fanout
     * @param abPropsService        the AB-props service consulted by the base sender
     * @param senderKeyDistribution the sender-key distribution service
     * @param metaStanza            the meta stanza builder
     * @param reportingStanza       the reporting stanza builder
     * @param wamService            the WAM telemetry service shared with the base sender
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptAndSendStatusMsg", exports = "encryptAndSendStatusMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    StatusMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            SenderKeyDistribution senderKeyDistribution,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza,
            WamService wamService
    ) {
        super(client, abPropsService, wamService);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.senderKeyDistribution = Objects.requireNonNull(senderKeyDistribution, "senderKeyDistribution");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
    }

    /**
     * Sends a status update to every device in the resolved audience. The
     * payload is encrypted with the sender key (SKMSG), the sender key is
     * distributed to devices that do not yet hold it, and the wire stanza
     * carries a {@code <meta>} child whose {@code status_setting} attribute
     * mirrors the privacy preference (omitted for revokes).
     *
     * @param statusJid   the status broadcast JID
     * @param messageInfo the outgoing status message
     * @return the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptAndSendStatusMsg", exports = "encryptAndSendStatusMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public AckResult send(Jid statusJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        var container = messageInfo.message();
        var selfJid = requireSelfJid();

        var fanout = deviceService.getGroupFanout(statusJid, selfLidOrPn());

        var revokeResult = resolveRevokeDevices(container, fanout.devices());
        if (revokeResult.useDirect()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Status revoke requires direct path for {0}", messageInfo.key().id());
            return sendDirectRevoke(statusJid, messageInfo, revokeResult.devices());
        }

        var allDevices = revokeResult.devices();

        var messageId = messageInfo.key().id().orElseThrow();
        store.createOrMergeReceiptRecords(messageId, allDevices);

        var skDistribDevices = new ArrayList<Jid>();
        var skExistingDevices = new ArrayList<Jid>();
        for (var device : allDevices) {
            if (store.hasSenderKeyDistributed(statusJid, device)) {
                skExistingDevices.add(device);
            } else {
                skDistribDevices.add(device);
            }
        }

        var rotateKey = store.clearKeyRotation(statusJid);
        if (rotateKey) {
            encryption.rotateSenderKey(statusJid, selfJid);
            skDistribDevices.addAll(skExistingDevices);
            skExistingDevices.clear();
        }

        var plaintext = MessageContainerSpec.encode(container);
        var skmsgPayload = encryption.encryptForGroup(statusJid, selfJid, plaintext);
        var senderKeyBytes = encryption.getSenderKeyBytes(statusJid, selfJid);

        List<MessageEncryptedPayload> skDistPayloads;
        if (skDistribDevices.isEmpty()) {
            skDistPayloads = List.of();
        } else {
            var depletedPrekeyCount = deviceService.ensureSessions(skDistribDevices);
            emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.STATUS, allDevices.size());
            skDistPayloads = senderKeyDistribution.encrypt(statusJid, senderKeyBytes, skDistribDevices);
        }

        var participantsChildren = new ArrayList<Node>();
        for (var payload : skDistPayloads) {
            if (payload.recipientJid() == null) {
                continue;
            }
            var encNode = new NodeBuilder()
                    .description("enc")
                    .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                    .attribute("type", payload.type().protocolValue())
                    .content(payload.ciphertext())
                    .build();
            participantsChildren.add(new NodeBuilder()
                    .description("to")
                    .attribute("jid", payload.recipientJid())
                    .content(encNode)
                    .build());
        }
        for (var device : skExistingDevices) {
            participantsChildren.add(new NodeBuilder()
                    .description("to")
                    .attribute("jid", device)
                    .build());
        }

        var participantsNode = participantsChildren.isEmpty() ? null : new NodeBuilder()
                .description("participants")
                .content(participantsChildren)
                .build();

        store.updateIdentityRange(allDevices);

        var skmsgEncNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                .attribute("mediatype", resolveMediaType(container))
                .content(skmsgPayload.ciphertext())
                .build();

        var identityNode = ParticipantsStanza.requiresIdentityNode(skDistPayloads)
                ? buildIdentityNode() : null;

        var isRevoke = container.content() instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.REVOKE;
        var statusSetting = isRevoke ? null : resolveStatusSetting();
        var metaNode = metaStanza.buildChat(statusJid, container, statusSetting);

        var reportingNode = reportingStanza.build(messageInfo, selfJid, statusJid);

        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .attribute("to", statusJid)
                .attribute("type", resolveStanzaType(container))
                .attribute("edit", resolveEditAttribute(container))
                .content(
                        participantsNode,
                        skmsgEncNode,
                        identityNode,
                        metaNode,
                        reportingNode
                );

        flushStore();
        var ackNode = client.sendNode(stanza);
        var ack = AckParser.parse(ackNode);

        if (ack.isSuccess()) {
            for (var device : skDistribDevices) {
                store.markSenderKeyDistributed(statusJid, device);
            }
        }

        return ack;
    }

    /**
     * Resolves the {@code status_setting} meta attribute from the user's
     * status privacy preference. Cobalt returns {@code null} for unknown
     * privacy values rather than throwing, departing from WA Web on this
     * one branch.
     *
     * @return {@code "contacts"}, {@code "allowlist"}, {@code "denylist"},
     *         or {@code null} when the preference is unavailable or unknown
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptAndSendStatusMsg", exports = "encryptAndSendStatusMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private String resolveStatusSetting() {
        var entry = store.findPrivacySetting(PrivacySettingType.STATUS)
                .orElse(null);
        if (entry == null) {
            return null;
        }
        return switch (entry.value()) {
            case CONTACTS -> "contacts";
            case CONTACTS_ONLY -> "allowlist";
            case CONTACTS_EXCEPT -> "denylist";
            default -> null;
        };
    }

    /**
     * Resolves the target device list for the given outgoing container in a
     * single pass. Returns the full audience for non-revokes; for revokes
     * either narrows the audience to the original recipients (when they all
     * remain inside the audience) or marks the result for the per-device
     * direct path (when at least one original recipient is outside).
     *
     * @param container       the outgoing message container
     * @param currentAudience the resolved status audience
     * @return the resolution describing the device list and dispatch path
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptAndSendStatusMsg", exports = "encryptAndSendStatusMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private RevokeResolution resolveRevokeDevices(
            MessageContainer container,
            Collection<Jid> currentAudience
    ) {
        if (!(container.content() instanceof ProtocolMessage pm)
            || pm.type().orElse(null) != ProtocolMessage.Type.REVOKE) {
            return new RevokeResolution(false, currentAudience);
        }

        var originalKey = pm.key().orElse(null);
        if (originalKey == null) {
            return new RevokeResolution(false, currentAudience);
        }

        var originalRecipients = originalKey.id()
                .map(store::findReceiptRecords)
                .orElse(Set.of());
        if (originalRecipients.isEmpty()) {
            return new RevokeResolution(false, currentAudience);
        }

        var currentUserJids = new HashSet<String>(currentAudience.size());
        for (var device : currentAudience) {
            currentUserJids.add(device.toUserJid().toString());
        }

        var selfUserJid = store.jid().map(j -> j.toUserJid().toString()).orElse(null);
        var hasOutOfAudience = false;

        for (var recipient : originalRecipients) {
            var recipientStr = recipient.toUserJid().toString();
            if (!recipientStr.equals(selfUserJid) && !currentUserJids.contains(recipientStr)) {
                hasOutOfAudience = true;
                break;
            }
        }

        if (hasOutOfAudience) {
            var merged = new LinkedHashSet<>(currentAudience);
            merged.addAll(originalRecipients);
            return new RevokeResolution(true, merged);
        }

        var originalUserJids = new HashSet<String>(originalRecipients.size());
        for (var recipient : originalRecipients) {
            originalUserJids.add(recipient.toUserJid().toString());
        }
        var narrowed = currentAudience.stream()
                .filter(d -> originalUserJids.contains(d.toUserJid().toString()))
                .toList();
        return new RevokeResolution(false, narrowed.isEmpty() ? currentAudience : narrowed);
    }

    /**
     * Carries the result of resolving the device list for a status revoke.
     *
     * @param useDirect {@code true} when the revoke must use the per-device
     *                  direct path
     * @param devices   the target device list
     */
    private record RevokeResolution(boolean useDirect, Collection<Jid> devices) {

    }

    /**
     * Sends a status revoke via per-device fanout encryption (the GROUP_DIRECT
     * branch). This path is used when at least one original recipient is no
     * longer part of the current status audience.
     *
     * @param statusJid   the status broadcast JID
     * @param messageInfo the outgoing revoke message
     * @param allDevices  the union of original recipients and current audience
     * @return the server ack result
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails for every device
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptAndSendStatusMsg", exports = "encryptAndSendStatusDirectMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private AckResult sendDirectRevoke(
            Jid statusJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> allDevices
    ) {
        var container = messageInfo.message();

        var depletedPrekeyCount = deviceService.ensureSessions(allDevices);
        emitPrekeysDepletionEvents(depletedPrekeyCount, MessageType.GROUP, allDevices.size());
        var senderIcdc = deviceService.computeIcdc(requireSelfJid()).orElse(null);
        var payloads = encryptForDevices(encryption, allDevices, container, statusJid, senderIcdc, null);
        if (payloads.isEmpty()) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Encryption failed for all devices in direct status revoke to " + statusJid, null);
        }

        var identityNode = ParticipantsStanza.requiresIdentityNode(payloads)
                ? buildIdentityNode() : null;

        var stanza = ChatFanoutStanza.build(
                messageInfo.key().id().orElseThrow(),
                statusJid,
                resolveStanzaType(container),
                payloads,
                resolveEditAttribute(container),
                null,
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        flushStore();
        var ackNode = client.sendNode(stanza);
        return AckParser.parse(ackNode);
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
