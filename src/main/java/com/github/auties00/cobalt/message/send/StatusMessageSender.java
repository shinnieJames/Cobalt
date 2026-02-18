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
import com.github.auties00.cobalt.model.info.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.server.ProtocolMessage;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

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
    private static final System.Logger LOGGER = System.getLogger("StatusMessageSender");

    private final MessageEncryption encryption;
    private final DeviceService deviceService;
    private final SenderKeyDistribution senderKeyDistribution;
    private final MetaStanza metaStanza;
    private final ReportingStanza reportingStanza;

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

    @Override
    AckResult send(Jid statusJid, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();
        var container = messageInfo.message();
        var selfJid = requireSelfJid();

        // WAWebEncryptAndSendStatusMsg: get status audience fanout
        var fanout = deviceService.getGroupFanout(statusJid);

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
        var rotateKey = store.checkAndClearSenderKeyRotationNeeded(statusJid);
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

        // WAWebEncryptAndSendStatusMsg: SKMSG <enc> node
        var skmsgEncNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", MessageEncryptionType.SKMSG.protocolValue())
                .content(skmsgPayload.ciphertext())
                .build();

        // WAWebEncryptAndSendStatusMsg: identity node when any pkmsg
        var identityNode = ParticipantsStanza.requiresIdentityNode(skDistPayloads)
                ? buildIdentityNode() : null;


        // WAWebEncryptAndSendStatusMsg: status_setting from user's
        // status privacy preference (contacts/allowlist/denylist)
        var statusSetting = resolveStatusSetting();
        var metaNode = metaStanza.buildChat(statusJid, container, statusSetting);

        var reportingNode = reportingStanza.build(messageInfo, selfJid, statusJid);

        // WAWebEncryptAndSendStatusMsg: build the stanza
        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", messageInfo.key().id())
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
     * @apiNote WAWebEncryptAndSendStatusMsg: maps
     * {@code StatusPrivacySettingType.Contact → "contacts"},
     * {@code AllowList → "allowlist"}, {@code DenyList → "denylist"}.
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
     * @apiNote WAWebEncryptAndSendStatusMsg: checks if
     * calculateRevokeSenderList contains devices outside the current
     * status list. If so, calls encryptAndSendStatusDirectMsg with the
     * full recipient union; otherwise narrows to the intersection.
     */
    private RevokeResolution resolveRevokeDevices(
            MessageContainer container,
            Collection<Jid> currentAudience
    ) {
        if (!(container.content() instanceof ProtocolMessage pm)
            || pm.protocolType() != ProtocolMessage.Type.REVOKE) {
            return new RevokeResolution(false, currentAudience);
        }

        var originalKey = pm.key().orElse(null);
        if (originalKey == null) {
            return new RevokeResolution(false, currentAudience);
        }

        var originalRecipients = store.findReceiptRecords(originalKey.id());
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
     */
    private record RevokeResolution(boolean useDirect, Collection<Jid> devices) {

    }

    /**
     * Sends a status revoke via GROUP_DIRECT fanout (per-device encryption)
     * when the original recipients include devices outside the current
     * status audience.
     *
     * @apiNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusDirectMsg:
     * uses createFanoutMsgStanza with FANOUT_TYPE.GROUP_DIRECT.
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
                messageInfo.key().id(),
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
