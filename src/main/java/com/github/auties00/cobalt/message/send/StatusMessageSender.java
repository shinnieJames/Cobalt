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
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.*;

/**
 * Sends status updates ({@code status@broadcast}).
 *
 * <p>Status messages use sender-key encryption, similar to group messages,
 * but are addressed to the status broadcast JID.  The audience is
 * determined by the user's status privacy list (contacts, allowlist,
 * or denylist).
 *
 * <p>The stanza includes a {@code <meta status_setting="...">} attribute
 * indicating the privacy setting.
 *
 * @apiNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg: builds
 * the status stanza with sender-key encryption, SK distribution for
 * new devices, and optional status_setting meta attribute.
 */
final class StatusMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Logger for status message sending diagnostics.
     *
     * @implNote ADAPTED: WAWebEncryptAndSendStatusMsg uses WALogger;
     * Cobalt uses {@link System.Logger}.
     */
    private static final System.Logger LOGGER = System.getLogger(StatusMessageSender.class.getName());

    /**
     * The encryption service for sender-key and per-device encryption.
     *
     * @implNote WAWebEncryptAndSendStatusMsg: uses WAWebSignal.Cipher
     * for sender-key encryption and WAWebEncryptMsgProtobuf for per-device.
     */
    private final MessageEncryption encryption;

    /**
     * The device service for fanout list resolution and session management.
     *
     * @implNote WAWebEncryptAndSendStatusMsg: uses
     * WAWebDBDeviceListFanout.getFanOutList and
     * WAWebManageE2ESessionsJob.ensureE2ESessions.
     */
    private final DeviceService deviceService;

    /**
     * The sender-key distribution service for distributing keys to new devices.
     *
     * @implNote WAWebEncryptAndSendStatusMsg.genMessageBody: calls
     * WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg for SK distribution.
     */
    private final SenderKeyDistribution senderKeyDistribution;

    /**
     * The meta stanza builder for status_setting and other meta attributes.
     *
     * @implNote WAWebEncryptAndSendStatusMsg: builds meta node inline
     * with status_setting; Cobalt delegates to MetaStanza.
     */
    private final MetaStanza metaStanza;

    /**
     * The reporting stanza builder for reporting token generation.
     *
     * @implNote WAWebEncryptAndSendStatusMsg: calls
     * WAWebReportingTokenUtils.genReportingTokenBody for the reporting node.
     */
    private final ReportingStanza reportingStanza;

    /**
     * Creates a new status message sender.
     *
     * @param client                the WhatsApp client for sending stanzas
     * @param encryption            the message encryption service
     * @param deviceService         the device service for fanout calculation
     * @param senderKeyDistribution the sender-key distribution service
     * @param metaStanza            the meta stanza builder
     * @param reportingStanza       the reporting stanza builder
     *
     * @implNote ADAPTED: WAWebEncryptAndSendStatusMsg uses module-level
     * imports; Cobalt uses constructor-based DI instead.
     */
    StatusMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            SenderKeyDistribution senderKeyDistribution,
            MetaStanza metaStanza,
            ReportingStanza reportingStanza
    ) {
        super(client);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.senderKeyDistribution = Objects.requireNonNull(senderKeyDistribution, "senderKeyDistribution");
        this.metaStanza = Objects.requireNonNull(metaStanza, "metaStanza");
        this.reportingStanza = Objects.requireNonNull(reportingStanza, "reportingStanza");
    }

    /**
     * Sends a status update to all devices in the user's status audience.
     *
     * <p>Encrypts the message using sender-key encryption (SKMSG),
     * distributes the sender key to new devices, creates receipt records
     * for all recipients, and sends the stanza with a
     * {@code status_setting} meta attribute indicating the privacy
     * setting.
     *
     * @param statusJid   the status broadcast JID ({@code status@broadcast})
     * @param messageInfo the outgoing status message
     * @return the server ack result
     *
     * @implNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg:
     * orchestrates the full status send flow including audience
     * resolution, revoke handling, SK distribution, receipt record
     * creation, sender-key encryption, and stanza building.
     */
    @Override
    AckResult send(Jid statusJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        var container = messageInfo.message();
        var selfJid = requireSelfJid();

        // WAWebEncryptAndSendStatusMsg: get status audience fanout
        // WAWebEncryptAndSendStatusMsg: R = getMaybeMeDeviceLid() — sender LID for phash
        var fanout = deviceService.getGroupFanout(statusJid, selfLidOrPn());

        // WAWebEncryptAndSendStatusMsg: for revokes, resolve the target
        // device list in one pass. Returns a direct-revoke list if any
        // original recipients are outside the current audience, otherwise
        // a narrowed SKMSG audience (or the full list for non-revokes).
        var revokeResult = resolveRevokeDevices(container, fanout.devices());
        if (revokeResult.useDirect()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Status revoke requires direct path for {0}", messageInfo.key().id());
            return sendDirectRevoke(statusJid, messageInfo, revokeResult.devices());
        }

        var allDevices = revokeResult.devices();

        // WAWebEncryptAndSendStatusMsg: createOrMergeReceiptRecords for all
        // devices before encryption, so revoke can find original recipients
        var messageId = messageInfo.key().id().orElseThrow();
        store.createOrMergeReceiptRecords(messageId, allDevices);

        // WAWebEncryptAndSendStatusMsg: split into SK distribution and existing
        var skDistribDevices = new ArrayList<Jid>();
        var skExistingDevices = new ArrayList<Jid>();
        for (var device : allDevices) {
            if (store.hasSenderKeyDistributed(statusJid, device)) {
                skExistingDevices.add(device);
            } else {
                skDistribDevices.add(device);
            }
        }

        // WAWebUserPrefsStatus.getStatusSkDistribList: rotate sender key
        // when viewers have been removed from the status audience
        var rotateKey = store.clearKeyRotation(statusJid);
        if (rotateKey) {
            encryption.rotateSenderKey(statusJid, selfJid);
            skDistribDevices.addAll(skExistingDevices);
            skExistingDevices.clear();
        }

        // WAWebEncryptAndSendStatusMsg: encrypt with sender key
        // The status JID acts as the "group" for sender-key purposes
        var plaintext = MessageContainerSpec.encode(container);
        var skmsgPayload = encryption.encryptForGroup(statusJid, selfJid, plaintext);
        var senderKeyBytes = encryption.getSenderKeyBytes(statusJid, selfJid);

        // WAWebEncryptAndSendStatusMsg: encrypt SK distribution for new devices
        // WAWebGetGroupKeyDistributionMsg: populates ICDC per device
        var skDistPayloads = skDistribDevices.isEmpty()
                ? List.<MessageEncryptedPayload>of()
                : senderKeyDistribution.encrypt(statusJid, senderKeyBytes, skDistribDevices);

        // WAWebEncryptAndSendStatusMsg: build participants node
        // Contains both SK distribution <to> and existing device <to> nodes
        var participantsChildren = new ArrayList<Node>();
        for (var payload : skDistPayloads) {
            if (payload.recipientJid() == null) {
                continue;
            }
            // WAWebEncryptAndSendStatusMsg: SK distribution <to> with <enc>
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
        // WAWebEncryptAndSendStatusMsg: existing SK device <to> nodes (no enc)
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

        // WAWebEncryptAndSendStatusMsg: updateIdentityRange with the full
        // device list (not just SK distribution devices)
        store.updateIdentityRange(allDevices);

        // WAWebEncryptAndSendStatusMsg.genMessageBody: SKMSG <enc> node
        // with mediatype from the protobuf (WAWebBackendJobsCommon.mediaTypeFromProtobuf)
        var skmsgEncNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                .attribute("mediatype", resolveMediaType(container))
                .content(skmsgPayload.ciphertext())
                .build();

        // WAWebEncryptAndSendStatusMsg: identity node when any pkmsg
        var identityNode = ParticipantsStanza.requiresIdentityNode(skDistPayloads)
                ? buildIdentityNode() : null;


        // WAWebEncryptAndSendStatusMsg: status_setting meta is only
        // included for non-revoke messages. For revokes (narrowed or
        // direct), the meta node omits status_setting.
        var isRevoke = container.content() instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.REVOKE;
        var statusSetting = isRevoke ? null : resolveStatusSetting();
        var metaNode = metaStanza.buildChat(statusJid, container, statusSetting);

        var reportingNode = reportingStanza.build(messageInfo, selfJid, statusJid);

        // WAWebEncryptAndSendStatusMsg: build the stanza
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

        // WAWebEncryptAndSendStatusMsg: mark SK as distributed on success
        if (ack.isSuccess()) {
            for (var device : skDistribDevices) {
                store.markSenderKeyDistributed(statusJid, device);
            }
        }

        return ack;
    }

    /**
     * Resolves the {@code status_setting} meta attribute from the user's
     * status privacy preference.
     *
     * @return {@code "contacts"}, {@code "allowlist"}, {@code "denylist"},
     *         or {@code null} if unavailable
     *
     * @implNote WAWebEncryptAndSendStatusMsg.I: maps
     * {@code StatusPrivacySettingType.Contact → "contacts"},
     * {@code AllowList → "allowlist"}, {@code DenyList → "denylist"}.
     * WA Web throws on unknown values; Cobalt returns {@code null} (ADAPTED).
     */
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
     * Resolves the target device list for a message in a single pass.
     *
     * @implNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg:
     * calls calculateRevokeSenderList (T) for revoke messages, then
     * checks D(F, M.list) to decide between direct and narrowed paths.
     * WAWebEncryptAndSendStatusMsg.D: returns true if any original
     * recipient is not self and not in the current status list.
     */
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

        // Build a HashSet of current audience user JIDs for O(1) membership checks
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
     * Result of resolving the revoke device list.
     *
     * @param useDirect whether to use the direct (per-device) path
     * @param devices   the target device list
     *
     * @implNote ADAPTED: WAWebEncryptAndSendStatusMsg uses separate
     * variables for direct vs narrowed resolution; Cobalt encapsulates
     * them in a record for clarity.
     */
    private record RevokeResolution(boolean useDirect, Collection<Jid> devices) {

    }

    /**
     * Sends a status revoke via GROUP_DIRECT fanout (per-device encryption)
     * when the original recipients include devices outside the current
     * status audience.
     *
     * @implNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusDirectMsg:
     * uses createFanoutMsgStanza with FANOUT_TYPE.GROUP_DIRECT and
     * SessionScope.STATUS. Encrypts per-device, builds via
     * ChatFanoutStanza, and sends via deprecatedSendStanzaAndReturnAck.
     */
    private AckResult sendDirectRevoke(
            Jid statusJid,
            ChatMessageInfo messageInfo,
            Collection<Jid> allDevices
    ) {
        var container = messageInfo.message();

        deviceService.ensureSessions(allDevices);
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
}
