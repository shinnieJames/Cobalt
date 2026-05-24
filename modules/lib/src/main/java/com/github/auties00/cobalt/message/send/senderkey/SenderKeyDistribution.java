package com.github.auties00.cobalt.message.send.senderkey;

import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.icdc.IcdcEnricher;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessageBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.*;

/**
 * Distributes the sender's group encryption key to participants that do not
 * yet hold it.
 *
 * @apiNote
 * Required before any SKMSG produced by
 * {@link MessageEncryption#encryptForGroup} can be decrypted: each recipient
 * device must have received the sender's
 * {@code SenderKeyDistributionMessage} via this service, encrypted with that
 * device's per-device Signal session. Invoked by the group/status/broadcast
 * send jobs (matching WA Web's {@code WAWebSendGroupKeyDistributionMsgJob} /
 * {@code WAWebSendGroupSkmsgJob} / {@code WAWebEncryptAndSendStatusMsg} /
 * {@code WAWebEncryptAndSendBroadcastMsg}) before the SKMSG itself goes out.
 */
@WhatsAppWebModule(moduleName = "WAWebGetGroupKeyDistributionMsg")
public final class SenderKeyDistribution {
    /**
     * Logger for sender-key distribution diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(SenderKeyDistribution.class.getName());

    /**
     * The encryption service used to encrypt the distribution payload per
     * device.
     */
    private final MessageEncryption encryption;

    /**
     * The device service used to compute ICDC metadata for the sender and
     * each recipient.
     */
    private final DeviceService deviceService;

    /**
     * The store used to resolve the self JID and to update the identity range
     * after distribution.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a sender-key distribution service bound to the given
     * dependencies.
     *
     * @apiNote
     * Held by the send-pipeline orchestrator as a long-lived singleton; the
     * three collaborators must share the same {@link WhatsAppStore} so
     * post-send {@link WhatsAppStore#updateIdentityRange} and per-recipient
     * ICDC lookups see consistent state.
     *
     * @param encryption    the {@link MessageEncryption} service for per-device
     *                      encryption
     * @param deviceService the {@link DeviceService} for ICDC metadata
     * @param store         the {@link WhatsAppStore} for self JID and identity
     *                      range
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "getKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Encrypts the sender-key distribution message for each device without
     * DSM wrapping and without a participant hash.
     *
     * @apiNote
     * Convenience overload matching the group SKMSG and status send paths
     * (matching {@code WAWebSendGroupSkmsgJob} and
     * {@code WAWebEncryptAndSendStatusMsg}), where the distribution is not
     * wrapped in a {@code DeviceSentMessage}. Equivalent to calling
     * {@link #encrypt(Jid, byte[], Collection, boolean, String)} with
     * {@code shouldWrapDsm = false} and {@code phash = null}.
     *
     * @param groupJid       the group {@link Jid}
     * @param senderKeyBytes the serialised {@code SenderKeyDistributionMessage}
     *                       bytes produced by
     *                       {@link MessageEncryption#getSenderKeyBytes}
     * @param devices        the device {@link Jid}s that need the sender key
     * @return one {@link MessageEncryptedPayload} per device that successfully
     *         encrypted
     * @throws NullPointerException                  if any argument is
     *                                               {@code null}
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails for a
     *                                               primary device
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "getKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MessageEncryptedPayload> encrypt(
            Jid groupJid,
            byte[] senderKeyBytes,
            Collection<Jid> devices
    ) {
        return encrypt(groupJid, senderKeyBytes, devices, false, null);
    }

    /**
     * Encrypts the sender-key distribution message for each device, with
     * optional DSM wrapping and per-recipient ICDC metadata.
     *
     * @apiNote
     * Mirrors WA Web's {@code getKeyDistributionMsg(t, a, i, l, u, d, m)}: for
     * each device the distribution proto is encrypted through the Signal
     * session cipher (producing PKMSG for fresh sessions, MSG once the
     * recipient has acknowledged a prior PreKey), wrapped as a
     * {@code DeviceSentMessage} with the group as destination plus the
     * {@code phash} when {@code shouldWrapDsm} is {@code true} and the
     * recipient is a self-companion, and stamped with the appropriate ICDC
     * (sender-only for self, sender plus recipient for other accounts).
     * Companion devices whose encryption fails are dropped from the result;
     * primary-device failures propagate as
     * {@link WhatsAppMessageException.Send.Unknown} so the orchestrator can
     * abort the whole send.
     * @implNote
     * This implementation post-calls {@link WhatsAppStore#updateIdentityRange}
     * with the supplied device set so the next fanout sees the freshly-keyed
     * recipients in the identity range cache; WA Web does the same range
     * bump inside {@code WAWebSendGroupKeyDistributionMsgJob}.
     *
     * @param groupJid       the group {@link Jid}
     * @param senderKeyBytes the serialised {@code SenderKeyDistributionMessage}
     *                       bytes
     * @param devices        the device {@link Jid}s that need the sender key
     * @param shouldWrapDsm  whether to wrap self-device protos in a
     *                       {@code DeviceSentMessage}
     * @param phash          the participant hash to include in the DSM, or
     *                       {@code null}
     * @return the per-device encrypted payloads, in source iteration order,
     *         excluding any failed companions
     * @throws NullPointerException                  if {@code groupJid},
     *                                               {@code senderKeyBytes}, or
     *                                               {@code devices} is
     *                                               {@code null}
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails for a
     *                                               primary device
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "getKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

        var skDistMessage = new SenderKeyDistributionMessageBuilder()
                .groupJid(groupJid)
                .axolotlSenderKeyDistributionMessage(senderKeyBytes)
                .build();
        var baseContainer = MessageContainer.of(skDistMessage);

        var protosByUser = generateMsgProtobufs(
                baseContainer, devices, shouldWrapDsm, groupJid, phash);

        var results = new ArrayList<MessageEncryptedPayload>(devices.size());
        for (var device : devices) {
            try {
                var userKey = device.toUserJid();
                var proto = protosByUser.getOrDefault(userKey, baseContainer);
                var plaintext = MessageContainerSpec.encode(proto);
                var payload = encryption.encryptForDevice(device, plaintext);
                results.add(payload);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "getKeyDistributionMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());
                if (isPrimaryDevice(device)) {
                    throw new WhatsAppMessageException.Send.Unknown(
                            "getKeyDistributionMsg: encryption fail for primary device " + device, e);
                }
            }
        }

        store.updateIdentityRange(devices);

        return Collections.unmodifiableList(results);
    }

    /**
     * Encrypts a {@code DeviceSentMessage} carrying the participant hash for
     * group sync, delivered to the caller's own companion devices.
     *
     * @apiNote
     * Mirrors WA Web's {@code getCompanionDsmPhashMsg}: used by the broadcast
     * send path (matching {@code WAWebEncryptAndSendBroadcastMsg}) when the
     * caller has at least one companion device that also needs to learn the
     * {@code phash} (participant hash) of the broadcast list. The supplied
     * {@code message} is wrapped as a
     * {@link com.github.auties00.cobalt.model.message.system.DeviceSentMessage}
     * with the group/broadcast as destination, stamped with the sender's ICDC
     * only (no recipient ICDC because the targets are self), and encrypted per
     * companion device. Failures are logged and dropped so the broadcast can
     * still ship.
     *
     * @param message          the message proto to wrap as DSM
     * @param companionDevices the companion device {@link Jid}s
     * @param phash            the participant hash to include in the DSM
     * @param groupJid         the group or broadcast {@link Jid} used as DSM
     *                         destination
     * @return the per-device encrypted payloads, or {@code null} when
     *         {@code companionDevices} is {@code null} or empty, or when every
     *         encryption attempt fails
     * @throws NullPointerException if {@code message}, {@code phash}, or
     *                              {@code groupJid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "getCompanionDsmPhashMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebDeviceSentMessageProtoUtils", exports = "wrapDeviceSentMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MessageEncryptedPayload> encryptCompanionDsmPhash(
            MessageContainer message,
            Collection<Jid> companionDevices,
            String phash,
            Jid groupJid
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(phash, "phash");
        Objects.requireNonNull(groupJid, "groupJid");

        if (companionDevices == null || companionDevices.isEmpty()) {
            return null;
        }

        var selfJid = store.jid().orElse(null);
        IcdcResult senderIcdc = null;
        if (selfJid != null) {
            senderIcdc = deviceService.computeIcdc(selfJid).orElse(null);
        }

        var dsm = new DeviceSentMessageBuilder()
                .destinationJid(groupJid)
                .messageContainer(message)
                .phash(phash)
                .build();
        var dsmContainer = MessageContainer.of(dsm);

        var enriched = IcdcEnricher.enrich(dsmContainer, senderIcdc, null);
        var plaintext = MessageContainerSpec.encode(enriched);

        var results = new ArrayList<MessageEncryptedPayload>(companionDevices.size());
        for (var device : companionDevices) {
            try {
                var payload = encryption.encryptForDevice(device, plaintext);
                results.add(payload);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "getCompanionDsmPhashMsg: encryption fail for {0}: {1}",
                        device, e.getMessage());
            }
        }

        return results.isEmpty() ? null : results;
    }

    /**
     * Precomputes the per-user message proto by deduplicating devices on
     * their user JID and stamping ICDC plus optional DSM wrapping.
     *
     * @apiNote
     * Mirrors WA Web's {@code generateMsgProtobufs}: the sender's ICDC is
     * computed exactly once and the recipient ICDC is computed per unique
     * non-self user, so a multi-device recipient does not pay the ICDC cost
     * per companion. Self devices receive a DSM-wrapped proto only when
     * {@code shouldWrapDsm} is {@code true}; other accounts receive the plain
     * proto.
     *
     * @param baseContainer the base distribution container shared across users
     * @param devices       the device {@link Jid}s to plan for
     * @param shouldWrapDsm whether self-device protos are DSM-wrapped
     * @param groupJid      the group {@link Jid} used as DSM destination
     * @param phash         the participant hash for DSM, or {@code null}
     * @return a map from unique user {@link Jid} to the proto for that user's
     *         devices
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "generateMsgProtobufs",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebDeviceSentMessageProtoUtils", exports = "wrapDeviceSentMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Map<Jid, MessageContainer> generateMsgProtobufs(
            MessageContainer baseContainer,
            Collection<Jid> devices,
            boolean shouldWrapDsm,
            Jid groupJid,
            String phash
    ) {
        var selfJid = store.jid().orElse(null);

        IcdcResult senderIcdc = null;
        if (selfJid != null) {
            senderIcdc = deviceService.computeIcdc(selfJid).orElse(null);
        }

        var uniqueUsers = devices.stream()
                .map(Jid::toUserJid)
                .distinct()
                .toList();

        var result = new HashMap<Jid, MessageContainer>(uniqueUsers.size());

        for (var userJid : uniqueUsers) {
            var isSelf = selfJid != null && userJid.equals(selfJid.toUserJid());

            MessageContainer proto;

            IcdcResult recipientIcdc = null;
            if (isSelf) {
                if (shouldWrapDsm) {
                    var dsmBuilder = new DeviceSentMessageBuilder()
                            .destinationJid(groupJid)
                            .messageContainer(baseContainer);
                    if (phash != null) {
                        dsmBuilder.phash(phash);
                    }
                    proto = MessageContainer.of(dsmBuilder.build());
                } else {
                    proto = baseContainer;
                }
            } else {
                proto = baseContainer;
                recipientIcdc = deviceService.computeIcdc(userJid).orElse(null);
            }

            proto = IcdcEnricher.enrich(proto, senderIcdc, recipientIcdc);
            result.put(userJid, proto);
        }

        return result;
    }

    /**
     * Returns whether the given device JID identifies a primary device.
     *
     * @apiNote
     * Used by {@link #encrypt} to distinguish primary-device encryption
     * failures (which must abort the whole send) from companion failures
     * (which are logged and dropped). Mirrors WA Web's
     * {@code WAWebSendMsgCommonApi.isPrimaryDevice}: device id {@code 0} is
     * the primary, every other id is a companion.
     *
     * @param device the device {@link Jid} to check
     * @return {@code true} when {@code device} is the primary device
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "isPrimaryDevice",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isPrimaryDevice(Jid device) {
        return device.device() == 0;
    }
}
