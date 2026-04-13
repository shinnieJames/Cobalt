package com.github.auties00.cobalt.message.send.senderkey;

import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.icdc.IcdcEnricher;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessageBuilder;
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
 * @implNote WAWebGetGroupKeyDistributionMsg: creates the distribution
 * protobuf, populates ICDC metadata per recipient, and encrypts it for
 * each device in the sender-key distribution list.  Also provides
 * {@code getCompanionDsmPhashMsg} for companion device DSM with phash.
 */
public final class SenderKeyDistribution {
    /**
     * Logger for sender-key distribution diagnostics.
     *
     * @implNote ADAPTED: WAWebGetGroupKeyDistributionMsg uses
     * {@code WALogger.LOG} for info/warning logging; Cobalt uses
     * {@link System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(SenderKeyDistribution.class.getName());

    /**
     * The encryption service for per-device Signal encryption.
     *
     * @implNote ADAPTED: WAWebGetGroupKeyDistributionMsg delegates to
     * {@code WAWebEncryptMsgProtobuf.encryptMsgProtobuf}; Cobalt uses
     * constructor-based DI for the encryption service.
     */
    private final MessageEncryption encryption;

    /**
     * The device service for ICDC computation and session management.
     *
     * @implNote ADAPTED: WAWebGetGroupKeyDistributionMsg delegates to
     * {@code WAWebApiDeviceList.bulkGetDeviceRecord} and
     * {@code WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord}; Cobalt
     * uses constructor-based DI for the device service.
     */
    private final DeviceService deviceService;

    /**
     * The WhatsApp store for self JID lookup and identity range updates.
     *
     * @implNote ADAPTED: WAWebGetGroupKeyDistributionMsg uses
     * {@code WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE()} and
     * {@code WAWebSendMsgCommonApi.updateIdentityRange}; Cobalt uses
     * constructor-based DI for the store.
     */
    private final WhatsAppStore store;

    /**
     * Creates a new sender-key distribution service.
     *
     * @param encryption    the encryption service for per-device encryption
     * @param deviceService the device service for ICDC and sessions
     * @param store         the store for self JID and identity range
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote ADAPTED: WAWebGetGroupKeyDistributionMsg uses module-level
     * imports; Cobalt uses constructor-based DI instead.
     */
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
     * distribution list without DSM wrapping and without phash.
     *
     * <p>This is a convenience overload equivalent to calling
     * {@link #encrypt(Jid, byte[], Collection, boolean, String)} with
     * {@code shouldWrapDsm = false} and {@code phash = null}.  This matches
     * the group SKMSG and status sending flows where DSM wrapping is not
     * performed at the key distribution level.
     *
     * @param groupJid       the group JID
     * @param senderKeyBytes the serialised {@code SenderKeyDistributionMessage}
     *                       from the Signal group cipher
     * @param devices        the device JIDs that need the sender key
     * @return the per-device encrypted payloads
     * @throws NullPointerException if any argument is {@code null}
     * @throws WhatsAppMessageException.Send if encryption fails for a
     *         primary device
     *
     * @implNote WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg:
     * called from WAWebSendGroupSkmsgJob with
     * {@code shouldWrapDSM=false} and no phash, and from
     * WAWebEncryptAndSendStatusMsg with {@code shouldWrapDSM=false} and
     * no phash.
     */
    public List<MessageEncryptedPayload> encrypt(
            Jid groupJid,
            byte[] senderKeyBytes,
            Collection<Jid> devices
    ) {
        return encrypt(groupJid, senderKeyBytes, devices, false, null);
    }

    /**
     * Encrypts the sender-key distribution message for each device in the
     * distribution list, populating ICDC metadata per device.
     *
     * <p>Each device receives the distribution protobuf encrypted
     * individually via the Signal session cipher (pkmsg or msg).
     * When {@code shouldWrapDsm} is {@code true}, companion devices
     * (same account as the sender) receive a {@code DeviceSentMessage}
     * wrapper with the group JID as destination and the optional
     * {@code phash}.  All devices receive ICDC metadata appropriate
     * to their role (sender-only for self, sender+recipient for others).
     * Devices that fail encryption are omitted with a warning log,
     * unless they are primary devices, in which case the failure is
     * propagated as an exception.
     *
     * @param groupJid       the group JID
     * @param senderKeyBytes the serialised {@code SenderKeyDistributionMessage}
     *                       from the Signal group cipher
     * @param devices        the device JIDs that need the sender key
     * @param shouldWrapDsm  whether to wrap self-device protos in a
     *                       {@code DeviceSentMessage}
     * @param phash          the participant hash to include in the DSM,
     *                       or {@code null} if not applicable
     * @return the per-device encrypted payloads
     * @throws NullPointerException if {@code groupJid}, {@code senderKeyBytes},
     *         or {@code devices} is {@code null}
     * @throws WhatsAppMessageException.Send if encryption fails for a
     *         primary device
     *
     * @implNote WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg:
     * builds {@code {senderKeyDistributionMessage: {groupId, axolotlSenderKeyDistributionMessage}}},
     * calls {@code generateMsgProtobufs} to precalculate per-user protos
     * with ICDC and optional DSM wrapping, then encrypts via
     * {@code WAWebEncryptMsgProtobuf.encryptMsgProtobuf} for each device.
     * On encryption failure, logs a warning; if
     * {@code WAWebSendMsgCommonApi.isPrimaryDevice(device)} is {@code true},
     * the failure is rejected (thrown).
     */
    public List<MessageEncryptedPayload> encrypt(
            Jid groupJid,
            byte[] senderKeyBytes,
            Collection<Jid> devices,
            boolean shouldWrapDsm,
            String phash
    ) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(senderKeyBytes, "senderKeyBytes");
        Objects.requireNonNull(devices, "devices");

        // ADAPTED: WAWebSendGroupSkmsgJob calls ensureE2ESessions before
        // getKeyDistributionMsg; Cobalt inlines it here since the caller
        // (GroupMessageSender) does not call ensureSessions for SK distrib devices
        deviceService.ensureSessions(devices);

        // WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: build the
        // base distribution protobuf
        var skDistMessage = new SenderKeyDistributionMessageBuilder()
                .groupJid(groupJid)
                .axolotlSenderKeyDistributionMessage(senderKeyBytes)
                .build();
        var baseContainer = MessageContainer.of(skDistMessage);

        // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs: precalculate
        // per-user protos with ICDC and optional DSM wrapping
        var protosByUser = generateMsgProtobufs(
                baseContainer, devices, shouldWrapDsm, groupJid, phash);

        // WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: encrypt for
        // each device individually using the per-user proto (or fallback to base)
        var results = new ArrayList<MessageEncryptedPayload>(devices.size());
        for (var device : devices) {
            try {
                // WAWebGetGroupKeyDistributionMsg: _.get(asUserWidOrThrow(device).toString()) ?? {...p}
                var userKey = device.toUserJid();
                var proto = protosByUser.getOrDefault(userKey, baseContainer);
                var plaintext = MessageContainerSpec.encode(proto);
                var payload = encryption.encryptForDevice(device, plaintext);
                results.add(payload);
            } catch (Exception e) {
                // WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: logs
                // encryption failure per device, rejects for primary devices
                LOGGER.log(System.Logger.Level.WARNING,
                        "getKeyDistributionMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());
                // WAWebSendMsgCommonApi.isPrimaryDevice: device == null || device === DEFAULT_DEVICE_ID
                if (isPrimaryDevice(device)) { // WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg
                    throw new WhatsAppMessageException.Send.Unknown(
                            "getKeyDistributionMsg: encryption fail for primary device " + device, e);
                }
            }
        }

        // WAWebSendMsgCommonApi.updateIdentityRange
        store.updateIdentityRange(devices);

        return Collections.unmodifiableList(results);
    }

    /**
     * Creates encrypted companion device-sent messages containing the
     * participant hash for group sync.
     *
     * <p>This method is used in the broadcast flow to send a DSM-wrapped
     * message with phash to companion (self-account) devices.  The message
     * is wrapped as a {@code DeviceSentMessage} with the group JID as
     * destination and the phash set, then enriched with the sender's ICDC
     * metadata (no recipient ICDC for self devices).
     *
     * @param message          the message proto to wrap as DSM
     * @param companionDevices the companion device JIDs
     * @param phash            the participant hash to include in the DSM
     * @param groupJid         the group/broadcast JID for the DSM destination
     * @return the per-device encrypted payloads, or {@code null} if
     *         {@code companionDevices} is empty
     * @throws NullPointerException if {@code message}, {@code phash}, or
     *         {@code groupJid} is {@code null}
     *
     * @implNote WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg:
     * wraps the message proto via
     * {@code WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage(groupJid, msgProto)},
     * adds phash to the DSM if present, populates sender-only ICDC via
     * {@code WAWebE2EProtoGenerator.populateMessageContextInfo(m, selfIcdc, null)},
     * then encrypts for each companion device.
     */
    public List<MessageEncryptedPayload> encryptCompanionDsmPhash(
            MessageContainer message,
            Collection<Jid> companionDevices,
            String phash,
            Jid groupJid
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(phash, "phash");
        Objects.requireNonNull(groupJid, "groupJid");

        // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg: if t.length === 0 return null
        if (companionDevices == null || companionDevices.isEmpty()) {
            return null;
        }

        // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg: compute self ICDC
        var selfJid = store.jid().orElse(null);
        IcdcResult senderIcdc = null; // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg
        if (selfJid != null) {
            senderIcdc = deviceService.computeIcdc(selfJid).orElse(null);
        }

        // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage(groupJid, msgProto):
        // wraps message as DSM with groupJid as destination
        var dsm = new DeviceSentMessageBuilder()
                .destinationJid(groupJid)
                .messageContainer(message)
                .phash(phash) // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg: adds phash
                .build();
        var dsmContainer = MessageContainer.of(dsm);

        // WAWebE2EProtoGenerator.populateMessageContextInfo(m, selfIcdc, null):
        // sender-only ICDC, no recipient for companion devices
        var enriched = IcdcEnricher.enrich(dsmContainer, senderIcdc, null);
        var plaintext = MessageContainerSpec.encode(enriched);

        // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg: encrypt
        // for each companion device
        var results = new ArrayList<MessageEncryptedPayload>(companionDevices.size());
        for (var device : companionDevices) {
            try {
                var payload = encryption.encryptForDevice(device, plaintext);
                results.add(payload);
            } catch (Exception e) {
                // WAWebGetGroupKeyDistributionMsg.getCompanionDsmPhashMsg:
                // logs encryption failure, continues (no isPrimaryDevice check)
                LOGGER.log(System.Logger.Level.WARNING,
                        "getCompanionDsmPhashMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());
            }
        }

        return results.isEmpty() ? null : results;
    }

    /**
     * Precalculates per-user message protos with ICDC metadata and
     * optional DSM wrapping.
     *
     * <p>Deduplicates devices by user JID, computes the sender's ICDC
     * metadata once, then for each unique user:
     * <ul>
     *   <li>If the user is the sender's own account and {@code shouldWrapDsm}
     *       is {@code true}, wraps the proto as a {@code DeviceSentMessage}
     *       with the group JID as destination and optional phash.</li>
     *   <li>If the user is a different account, computes the recipient's
     *       ICDC metadata.</li>
     *   <li>Populates ICDC on the proto (sender-only for self, sender +
     *       recipient for others).</li>
     * </ul>
     *
     * @param baseContainer the base message container with the SK distribution
     * @param devices       the device JIDs to process
     * @param shouldWrapDsm whether to wrap self-device protos as DSM
     * @param groupJid      the group JID for DSM destination
     * @param phash         the participant hash for DSM, or {@code null}
     * @return a map from user JID to enriched message container
     *
     * @implNote WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
     * deduplicates devices by user JID via
     * {@code new Set(t.map(asUserWidOrThrow))}, batch-fetches device records
     * via {@code WAWebApiDeviceList.bulkGetDeviceRecord}, computes ICDC per
     * user via {@code WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord},
     * wraps self-device protos via
     * {@code WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage} when
     * {@code shouldWrapDsm} is {@code true}, adds phash to DSM when present,
     * then calls {@code WAWebE2EProtoGenerator.populateMessageContextInfo}.
     */
    private Map<Jid, MessageContainer> generateMsgProtobufs(
            MessageContainer baseContainer,
            Collection<Jid> devices,
            boolean shouldWrapDsm,
            Jid groupJid,
            String phash
    ) {
        var selfJid = store.jid().orElse(null);

        // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs: compute sender ICDC
        IcdcResult senderIcdc = null;
        if (selfJid != null) {
            senderIcdc = deviceService.computeIcdc(selfJid).orElse(null);
        }

        // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
        // deduplicate devices by user JID
        var uniqueUsers = devices.stream()
                .map(Jid::toUserJid)
                .distinct()
                .toList();

        var result = new HashMap<Jid, MessageContainer>(uniqueUsers.size());

        for (var userJid : uniqueUsers) {
            var isSelf = selfJid != null && userJid.equals(selfJid.toUserJid());

            // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
            // var u = {...e} (copy base proto)
            MessageContainer proto;

            IcdcResult recipientIcdc = null;
            if (isSelf) {
                // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
                // if isMeAccount(user) && shouldWrapDsm: wrap as DSM
                if (shouldWrapDsm) {
                    // WAWebDeviceSentMessageProtoUtils.wrapDeviceSentMessage:
                    // base proto has no messageContextInfo, so wraps simply
                    var dsmBuilder = new DeviceSentMessageBuilder()
                            .destinationJid(groupJid)
                            .messageContainer(baseContainer);
                    // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
                    // if phash != null && dsm.deviceSentMessage != null: add phash
                    if (phash != null) {
                        dsmBuilder.phash(phash);
                    }
                    proto = MessageContainer.of(dsmBuilder.build());
                } else {
                    // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
                    // no DSM wrapping, just use base proto copy
                    proto = baseContainer;
                }
                // recipientIcdc stays null for self
            } else {
                // WAWebGetGroupKeyDistributionMsg.generateMsgProtobufs:
                // compute recipient ICDC for non-self users
                proto = baseContainer;
                recipientIcdc = deviceService.computeIcdc(userJid).orElse(null);
            }

            // WAWebE2EProtoGenerator.populateMessageContextInfo(u, selfIcdc, recipientIcdc)
            proto = IcdcEnricher.enrich(proto, senderIcdc, recipientIcdc);
            result.put(userJid, proto);
        }

        return result;
    }

    /**
     * Returns whether the given device JID identifies a primary device.
     *
     * <p>A primary device has a device identifier of {@code 0} (the
     * default device ID).
     *
     * @param device the device JID to check
     * @return {@code true} if this is a primary device
     *
     * @implNote WAWebSendMsgCommonApi.isPrimaryDevice: checks
     * {@code e.device == null || e.device === DEFAULT_DEVICE_ID}
     * where {@code DEFAULT_DEVICE_ID} is {@code 0}.
     */
    private static boolean isPrimaryDevice(Jid device) {
        return device.device() == 0; // WAWebSendMsgCommonApi.isPrimaryDevice
    }
}
