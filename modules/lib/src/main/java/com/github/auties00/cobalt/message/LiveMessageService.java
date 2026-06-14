package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.ack.AckParser;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.ack.CallAck;
import com.github.auties00.cobalt.call.signaling.CallStanza;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.message.crypto.SignalCryptoLocks;
import com.github.auties00.cobalt.message.receive.LiveMessageReceivingService;
import com.github.auties00.cobalt.message.receive.MessageReceivingService;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.LiveMessageSendingService;
import com.github.auties00.cobalt.message.send.MessageSendingService;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.call.CallOfferMessageBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Live implementation of {@link MessageService} that fans message traffic between the outbound
 * send pipeline and the inbound receive pipeline behind a single facade.
 *
 * <p>The two sub-services are assembled from the supplied collaborators in the constructor and
 * share the {@link LinkedWhatsAppClient#store() client store}, so the send and receive sides
 * observe a single source of truth for sessions, devices, and pending-message caches.
 *
 * @implNote This implementation collapses WA Web's two separate entry points,
 * {@code WAWebSendMsgJob.encryptAndSendMsg} for outbound fanout and
 * {@code WAWebCommsHandleMessagingStanza.handleMessagingStanza} for inbound
 * dispatch, into one facade that owns no state of its own.
 */
public final class LiveMessageService implements MessageService {
    /**
     * Holds the owning client; used by {@link #sendCall(Jid, String, byte[], boolean)} to
     * dispatch the offer stanza and read the call host's JID off the account store.
     */
    private final LinkedWhatsAppClient client;

    /**
     * Holds the {@link MessageEncryption} used to wrap the call-key plaintext per peer device.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the {@link MessageDecryption} used by
     * {@link #processCall(Jid, MessageEncryptionType, byte[])}.
     */
    private final MessageDecryption decryption;

    /**
     * Holds the {@link DeviceService} used by {@link #sendCall(Jid, String, byte[], boolean)}
     * to sync the peer's device list and ensure Signal sessions exist before encryption.
     */
    private final DeviceService deviceService;

    /**
     * Holds the {@link LidMigrationService} used by
     * {@link #sendCall(Jid, String, byte[], boolean)} to resolve self and peer to the
     * call's canonical addressing mode (LID where migrated, PN otherwise).
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Holds the outbound pipeline owning device fetch, fanout, encryption, and
     * stanza emission.
     */
    private final MessageSendingService sendingService;

    /**
     * Holds the inbound pipeline owning stanza parsing, Signal decryption, and
     * {@link MessageInfo} construction.
     */
    private final MessageReceivingService receivingService;


    /**
     * Wires the send and receive pipelines from the supplied collaborators.
     *
     * <p>The two pipelines share the {@link LinkedWhatsAppClient#store() client store}
     * via the supplied {@link MessageEncryption} and {@link MessageDecryption}, which
     * must themselves share a single {@link SignalCryptoLocks} registry so a concurrent
     * encrypt and decrypt for the same device session or sender-key chain serialise rather
     * than racing the non-atomic Signal ratchet. The caller (typically
     * {@code LiveLinkedWhatsAppClient}) constructs both encryption helpers from one
     * lock registry and hands them in here.
     *
     * @param client              the {@link LinkedWhatsAppClient} used to send
     *                            stanzas and to register inbound stanza
     *                            handlers
     * @param encryption          the {@link MessageEncryption} used by the outbound
     *                            pipeline; the same instance must be shared with any
     *                            other service that encrypts to Signal sessions (the
     *                            call-signaling layer's offer fanout)
     * @param decryption          the {@link MessageDecryption} used by the inbound
     *                            pipeline; must share its lock registry with {@code encryption}
     * @param deviceService       the {@link DeviceService} consulted to
     *                            resolve per-user device lists before each
     *                            fanout
     * @param lidMigrationService the {@link LidMigrationService} that gates
     *                            the PN-to-LID stanza rewrite in the
     *                            user-chat send path
     * @param abPropsService      the {@link ABPropsService} consulted to
     *                            gate optional protocol behaviour
     * @param wamService             the {@link WamService} forwarded to
     *                               the sending pipeline for end-to-end
     *                               telemetry events
     * @param mediaTranscoderService the {@link MediaTranscoderService}
     *                               threaded into the sending pipeline
     *                               for link-preview decoration
     * @throws NullPointerException if any argument is {@code null}
     */
    public LiveMessageService(
            LinkedWhatsAppClient client,
            MessageEncryption encryption,
            MessageDecryption decryption,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            WamService wamService,
            MediaTranscoderService mediaTranscoderService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(decryption, "decryption");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(lidMigrationService, "lidMigrationService");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(wamService, "wamService");
        Objects.requireNonNull(mediaTranscoderService, "mediaTranscoderService");

        this.client = client;
        this.encryption = encryption;
        this.decryption = decryption;
        this.deviceService = deviceService;
        this.lidMigrationService = lidMigrationService;
        var store = client.store();
        this.sendingService = new LiveMessageSendingService(client, encryption, deviceService, lidMigrationService, abPropsService, wamService, mediaTranscoderService);
        this.receivingService = new LiveMessageReceivingService(store, decryption);
    }

    @Override
    public AckResult send(Jid chatJid, MessageContainer container) {
        return sendingService.send(chatJid, container);
    }

    @Override
    public AckResult send(MessageInfo messageInfo) {
        return sendingService.send(messageInfo);
    }

    @Override
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        return sendingService.sendPeer(targetDevice, messageInfo);
    }

    @Override
    public MessageInfo process(Node node) {
        return receivingService.process(node);
    }

    @Override
    public void clearPendingMessages() {
        receivingService.clearPendingMessages();
    }

    @Override
    public CallAck sendCall(Jid peer, String callId, byte[] callKey, boolean video) {
        Objects.requireNonNull(peer, "peer cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callKey, "callKey cannot be null");

        var store = client.store();
        var selfJid = store.accountStore().jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // Resolve self LID with device suffix preserved; fall back to PN when no LID is mapped.
        var selfUserLid = store.accountStore().lid().map(Jid::toUserJid).orElse(null);
        var resolvedSelf = selfUserLid != null && selfJid.hasDevice()
                ? selfUserLid.withDevice(selfJid.device())
                : selfJid;
        // Resolve peer LID; fall back to the input when no mapping exists.
        var peerLid = lidMigrationService.toLid(peer);
        var resolvedPeer = peerLid != null ? peerLid : peer;

        // Build the call-key envelope plaintext, wrapped as a MessageContainer{call:Call{callKey}}.
        var callOffer = new CallOfferMessageBuilder().callKey(callKey).build();
        var container = new MessageContainerBuilder().call(callOffer).build();
        var plaintext = MessageContainerSpec.encode(container);

        // Resolve the per-call <privacy> payload: the peer's trusted-contact token, which the receiver's
        // voip wasm validates against the token it issued (rejecting with status 70019 on a mismatch).
        // Mirrors WAWebVoipStartCall reading getTcToken; an empty result sends the offer with no
        // <privacy>, which the peer still accepts.
        var privacyBytes = client.queryTrustedContactToken(resolvedPeer).orElse(null);

        // Hand the local user's own trusted-contact token to the peer (when due) so it keeps a current
        // token to validate our future offers across identity rotations, matching WAWebVoipStartCall
        // which does this on every call placement. Fire-and-forget on a virtual thread: the reciprocal
        // token is not needed here and the offer must not block on it.
        var tokenPeer = resolvedPeer;
        Thread.ofVirtual().name("tc-token-" + callId).start(() -> client.issueTrustedContactToken(tokenPeer));

        // Sync the peer's device list in the resolved addressing mode.
        var deviceLists = deviceService.syncAndGetDeviceList(List.of(resolvedPeer));
        var peerDeviceJids = new ArrayList<Jid>();
        for (var list : deviceLists) {
            for (var device : list.devices()) {
                peerDeviceJids.add(list.userJid().withDevice(device.id()));
            }
        }
        // A peer whose cached device record is a deleted tombstone yields no devices; fall back to the
        // primary device so the offer still addresses the peer, matching WA Web's getFanOutList primary
        // fallback. Without this the offer would carry no <destination> and the peer would never ring.
        if (peerDeviceJids.isEmpty()) {
            peerDeviceJids.add(resolvedPeer.toUserJid().withDevice(0));
        }

        // Establish a Signal session only for peer devices that lack one, reusing existing sessions
        // exactly as WA Web's fanOutOffer does (ensureE2ESessions with no force). Re-establishing a
        // session for a device that already has a healthy one would send it a pkmsg it cannot decrypt;
        // the recipient's primary device in particular must receive a msg over its existing session or
        // the phone silently drops the offer and never rings.
        // DIAGNOSTIC: the peer holds a one-sided session (Cobalt sends pkmsg to every device because it
        // has no persisted session, and the desktop rejects a pkmsg for a device it already has a session
        // with). Force a fresh prekey fetch + session so the desktop accepts a clean pkmsg and rings.
        deviceService.ensureSessions(peerDeviceJids, true);

        // Encrypt the plaintext per peer device. WA Web treats the per-device fanout as all-or-nothing:
        // if encryption fails for any device, every <enc> is stripped and each device is addressed with
        // a bare <to jid/> so the call still rings, rather than aborting the whole offer.
        var destinationPayloads = new ArrayList<MessageEncryptedPayload>(peerDeviceJids.size());
        var encryptionFailed = false;
        for (var deviceJid : peerDeviceJids) {
            try {
                destinationPayloads.add(encryption.encryptForDevice(deviceJid, plaintext));
            } catch (RuntimeException _) {
                encryptionFailed = true;
            }
        }
        if (encryptionFailed) {
            destinationPayloads.clear();
            for (var deviceJid : peerDeviceJids) {
                destinationPayloads.add(MessageEncryptedPayload.bareDestination(deviceJid));
            }
        }

        var deviceIdentity = store.signalStore().signedDeviceIdentity()
                .map(ADVSignedDeviceIdentitySpec::encode)
                .orElse(new byte[0]);

        // caller_pn is the caller's phone-number JID (with device); the receiver's web companion renders
        // the incoming call from it. The creator is sent in LID form, so pass the PN self JID separately.
        var ackNode = client.sendNode(CallStanza.offer(
                resolvedPeer, resolvedSelf, callId, video,
                privacyBytes, null, destinationPayloads, deviceIdentity, null, null, selfJid));
        var parsed = AckParser.parse(ackNode);
        if (parsed instanceof CallAck callAck) {
            return callAck;
        }
        throw new IllegalStateException(
                "Server returned a non-call ACK for the call offer: " + parsed);
    }

    @Override
    public CallAck sendGroupCall(Jid group, java.util.Collection<Jid> participants, String callId,
                                 byte[] callKey, boolean video) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callKey, "callKey cannot be null");

        var store = client.store();
        var selfJid = store.accountStore().jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var selfUserLid = store.accountStore().lid().map(Jid::toUserJid).orElse(null);
        // The device-less user LID identifies the caller as a <user> in <group_info>; the captured
        // member-received offer shows the call-creator rewritten to this device-less form on delivery.
        var resolvedSelf = selfUserLid != null ? selfUserLid : selfJid.toUserJid();
        // The OUTGOING offer's call-creator carries the caller's device suffix, exactly as the captured
        // working 1:1 caller offer does (`<user>:<device>@lid`); the server strips it to the device-less
        // form before fanning out. A companion that sends a device-less creator has its offer dropped.
        var resolvedSelfDevice = selfJid.hasDevice() ? resolvedSelf.withDevice(selfJid.device()) : resolvedSelf;
        // caller_pn is the caller's phone-number JID (user@s.whatsapp.net), captured on real group
        // offers; the server uses it to identify the caller.
        var callerPn = selfJid.toUserJid().withServer(com.github.auties00.cobalt.model.jid.JidServer.user());

        var deviceIdentity = store.signalStore().signedDeviceIdentity()
                .map(ADVSignedDeviceIdentitySpec::encode)
                .orElse(new byte[0]);

        // Shape matched verbatim to a captured WA Web SENT group offer: the <call> targets the
        // CALL-ID @call (NOT the group jid); the <offer> carries only call-id/call-creator/group-jid;
        // and <group_info> is BARE (no attributes) listing one <user jid=..> per participant (the caller
        // first) with each user's device JIDs, and a <capability> on the caller's own device. The server
        // adds state/user_pn/transaction-id/media/connected-limit/joinable/relay when it fans the offer
        // out and when it acks the caller; the caller sends none of those.
        var capabilityBytes = new byte[]{0x01, 0x05, (byte) 0xF7, 0x09, (byte) 0xE4, (byte) 0xBB, 0x13};
        // Each <user> lists that account's FULL device set (so the per-user phash the server computes
        // over the offer matches its records; phashV2 = sha256 of the sorted legacy-full device WIDs).
        // The caller is listed first and the <capability> rides on the caller's own device.
        var userJids = new java.util.LinkedHashSet<Jid>();
        userJids.add(resolvedSelf);
        for (var participant : participants) {
            var lid = lidMigrationService.toLid(participant);
            userJids.add((lid != null ? lid : participant).toUserJid());
        }
        var groupInfoChildren = new java.util.ArrayList<Node>();
        for (var list : deviceService.syncAndGetDeviceList(new java.util.ArrayList<>(userJids))) {
            var userJid = list.userJid().toUserJid();
            var deviceNodes = new java.util.ArrayList<Node>(list.devices().size());
            for (var device : list.devices()) {
                var deviceJid = userJid.withDevice(device.id());
                var deviceBuilder = new NodeBuilder()
                        .description("device")
                        .attribute("jid", deviceJid);
                if (deviceJid.equals(resolvedSelfDevice)) {
                    deviceBuilder.content(new NodeBuilder()
                            .description("capability")
                            .attribute("ver", "1")
                            .content(capabilityBytes)
                            .build());
                }
                deviceNodes.add(deviceBuilder.build());
            }
            groupInfoChildren.add(new NodeBuilder()
                    .description("user")
                    .attribute("jid", userJid)
                    .content(deviceNodes)
                    .build());
        }
        var groupInfo = new NodeBuilder()
                .description("group_info")
                .content(groupInfoChildren)
                .build();

        var callTarget = com.github.auties00.cobalt.model.jid.Jid.of(callId + "@call");
        var ackNode = client.sendNode(CallStanza.offer(
                callTarget, resolvedSelfDevice, callId, video,
                null, null, java.util.List.of(), deviceIdentity, group, null, callerPn, groupInfo));
        var parsed = AckParser.parse(ackNode);
        if (parsed instanceof CallAck callAck) {
            return callAck;
        }
        throw new IllegalStateException(
                "Server returned a non-call ACK for the group-call offer: " + parsed);
    }

    @Override
    public byte[] processCall(Jid senderJid, MessageEncryptionType encType, byte[] ciphertext) {
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(encType, "encType cannot be null");
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        return decryption.decryptFromDevice(ciphertext, senderJid, encType);
    }

}
