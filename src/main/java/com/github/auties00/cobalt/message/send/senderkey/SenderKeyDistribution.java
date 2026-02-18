package com.github.auties00.cobalt.message.send.senderkey;

import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.icdc.IcdcEnricher;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.server.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.server.SenderKeyDistributionMessageBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.*;

/**
 * Distributes the sender's group encryption key to participants that do
 * not yet possess it.
 *
 * <p>Before a group message encrypted with a sender key (SKMSG) can be
 * decrypted by a participant, that participant must have received the
 * sender's {@code SenderKeyDistributionMessage}.  This class creates
 * the per-device encrypted distribution messages, populating ICDC
 * metadata per recipient and wrapping companion device payloads in
 * {@code DeviceSentMessage}.
 *
 * @apiNote WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: creates
 * the distribution protobuf, populates ICDC metadata per recipient, and
 * encrypts it for each device in the sender-key distribution list.
 * WAWebSendGroupSkmsgJob: uses the distribution messages to build the
 * {@code <participants>} node in the group SKMSG stanza.
 */
public final class SenderKeyDistribution {
    private static final System.Logger LOGGER = System.getLogger("SenderKeyDistribution");

    private final MessageEncryption encryption;
    private final DeviceService deviceService;
    private final WhatsAppStore store;

    public SenderKeyDistribution(
            MessageEncryption encryption,
            DeviceService deviceService,
            WhatsAppStore store
    ) {
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Encrypts the sender-key distribution message for each device in the
     * distribution list, populating ICDC metadata per device.
     *
     * <p>Each device receives the distribution protobuf encrypted
     * individually via the Signal session cipher (pkmsg or msg).
     * Companion devices (same account as the sender) receive a
     * {@code DeviceSentMessage} wrapper with the group JID as
     * destination.  All devices receive ICDC metadata appropriate
     * to their role (sender-only for self, sender+recipient for others).
     * Devices that fail encryption are omitted with a warning log.
     *
     * @param groupJid       the group JID
     * @param senderKeyBytes the serialised {@code SenderKeyDistributionMessage}
     *                       from the Signal group cipher
     * @param devices        the device JIDs that need the sender key
     * @return the per-device encrypted payloads
     * @throws NullPointerException if any argument is {@code null}
     *
     * @apiNote WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg:
     * builds {@code {senderKeyDistributionMessage: {groupId, axolotlSenderKeyDistributionMessage}}},
     * populates ICDC metadata per recipient via
     * {@code WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord},
     * wraps in {@code DeviceSentMessage} for self devices, then encrypts
     * via {@code WAWebEncryptMsgProtobuf.encryptMsgProtobuf} for each device.
     */
    public List<MessageEncryptedPayload> encrypt(
            Jid groupJid,
            byte[] senderKeyBytes,
            Collection<Jid> devices
    ) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(senderKeyBytes, "senderKeyBytes");
        Objects.requireNonNull(devices, "devices");

        // WAWebSendMsgCreateFanoutStanza: ensureE2ESessions before encrypting SK distribution
        deviceService.ensureSessions(devices);

        var selfJid = store.jid().orElse(null);

        // WAWebGetGroupKeyDistributionMsg: precalculate ICDC for sender
        var senderIcdc = selfJid != null ? deviceService.computeIcdc(selfJid).orElse(null) : null;

        // WAWebGetGroupKeyDistributionMsg: build the distribution protobuf
        var skDistMessage = new SenderKeyDistributionMessageBuilder()
                .groupJid(groupJid)
                .data(senderKeyBytes)
                .build();
        var container = MessageContainer.of(skDistMessage);

        // WAWebGetGroupKeyDistributionMsg: encrypt for each device individually
        // with per-device ICDC metadata and DSM wrapping for self devices
        var results = new ArrayList<MessageEncryptedPayload>(devices.size());
        for (var device : devices) {
            try {
                var isSelf = selfJid != null && device.toUserJid().equals(selfJid.toUserJid());

                // WAWebGetGroupKeyDistributionMsg: for recipient devices,
                // resolve their ICDC metadata
                IcdcResult recipientIcdc = null;
                if (!isSelf) {
                    recipientIcdc = deviceService.computeIcdc(device.toUserJid())
                            .orElse(null);
                }

                // WAWebE2EProtoGenerator.populateMessageContextInfo:
                // self devices get sender ICDC only, recipients get both
                var enriched = IcdcEnricher.enrich(
                        container, senderIcdc, isSelf ? null : recipientIcdc);

                byte[] plaintext;
                if (isSelf) {
                    // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage
                    var dsm = new DeviceSentMessageBuilder()
                            .destinationJid(groupJid)
                            .message(enriched)
                            .build();
                    plaintext = MessageContainerSpec.encode(MessageContainer.of(dsm));
                } else {
                    plaintext = MessageContainerSpec.encode(enriched);
                }

                var payload = encryption.encryptForDevice(device, plaintext);
                results.add(payload);
            } catch (Exception e) {
                // WAWebGetGroupKeyDistributionMsg: logs encryption failure per device,
                // continues for companion devices but rejects for primary devices
                LOGGER.log(System.Logger.Level.WARNING,
                        "getKeyDistributionMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());
            }
        }

        // WAWebSendMsgCommonApi.updateIdentityRange
        store.updateIdentityRange(devices);

        return Collections.unmodifiableList(results);
    }
}
