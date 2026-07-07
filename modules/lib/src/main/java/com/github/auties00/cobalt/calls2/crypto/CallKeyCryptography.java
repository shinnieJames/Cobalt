package com.github.auties00.cobalt.calls2.crypto;

import com.github.auties00.cobalt.calls2.signaling.CallKeyDistribution;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.call.CallOfferMessage;
import com.github.auties00.cobalt.model.message.call.CallOfferMessageBuilder;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mints, distributes, and recovers the thirty-two-byte end-to-end call key through the reused Signal
 * message-encryption pipeline.
 *
 * <p>Every call protects its media with a single thirty-two-byte raw end-to-end key (Cobalt's
 * {@code callKey}; the WASM engine's {@code raw_e2e} key). This key is the ONLY secret the call plane
 * distributes over the wire; the per-direction SRTP keys, the per-participant SFrame base keys, and the
 * data-channel certificate-fingerprint HMAC are all derived LOCALLY from it via HKDF and never travel.
 * This service owns the four operations that move that key:
 * <ul>
 *   <li><b>mint</b> ({@link #mintCallKey()}): draw thirty-two cryptographically-strong random bytes.</li>
 *   <li><b>wrap</b> ({@link #wrapCallKey(byte[])}): encode the key as the live-confirmed plaintext
 *       {@code MessageContainer{Call{callKey}}}.</li>
 *   <li><b>distribute</b>: encrypt the wrapped plaintext per recipient device through the existing
 *       Signal session cipher, fanned out as a {@link #encryptOfferFanout(Collection, byte[]) per-device
 *       offer block} (1:1) or a {@link #encryptRekeyFanout(Collection, byte[]) per-recipient rekey
 *       fanout} (group), with the all-or-nothing bare-destination fallback.</li>
 *   <li><b>recover</b> ({@link #decryptCallKey(Jid, MessageEncryptionType, byte[])}): Signal-decrypt an
 *       inbound {@code <enc>} or {@code <enc_rekey>} envelope back to the thirty-two-byte key.</li>
 * </ul>
 *
 * <p>This service is a thin facade: it holds NO Signal cipher of its own. Every encrypt routes through
 * {@link MessageEncryption#encryptForDevice(Jid, byte[])} and every decrypt through
 * {@link MessageService#processCall(Jid, MessageEncryptionType, byte[])}, both of which already acquire
 * the per-address lock in {@code SignalCryptoLocks}. Constructing a private
 * {@code SignalSessionCipher} or {@code SignalGroupCipher} here would bypass that lock and race the
 * non-atomic Signal ratchet against concurrent message-plane traffic on the same device session, so
 * this service deliberately never does so. The only call-plane-owned logic is the key minting, the
 * {@code MessageContainer{Call{callKey}}} wrap, the all-or-nothing bare-destination fallback, the
 * group rekey fanout shape, and the dual inbound {@code <enc>} addressing shapes.
 *
 * <p>Group versus one-to-one: SFrame, and therefore on-the-wire rekeys, are GROUP-only. A 1:1 call
 * ships the key once in the offer and never rekeys; a group call ships no key in the offer (the key
 * arrives post-join via {@code <enc_rekey>}) and re-shares a fresh key on every membership change, each
 * connected participant fanning its own key unicast to every other participant.
 *
 * @implNote This implementation is the host-side reimplementation of {@code generate_raw_e2e_keys}
 * (fn10890; thirty-two CSPRNG bytes from the host entropy slot), {@code fill_call_key} (fn11634; the
 * wasm-side consumer of the already-Signal-decrypted key bytes), and the host enc/dec glue around them,
 * as specified by {@code re/calls2-spec/parts/int-signal-crypto.json}. The plaintext wrap shape
 * {@code MessageContainer{Call{callKey}}} is confirmed byte-for-byte against the live-decrypted group
 * rekey sample in {@code re/calls2-spec/captures/group-rekey.json} ({@code 52220a20<32B callKey>...},
 * field 10 {@code Message.call} then field 1 {@code Call.callKey}); the group rekey single-key shape
 * (NOT three per-domain masters) and its unicast-per-participant fanout are taken from that same
 * capture (calls {@code 0023CDF8...} / {@code 008B7296...}). The decrypt path mirrors
 * {@code WAWebVoipValidateAndDecryptEnc}: Signal-decrypt then decode {@code MessageContainer} then read
 * {@code Call.callKey}, exactly as {@code CallReceiver.decryptOfferCallKey}.
 */
public final class CallKeyCryptography implements CallKeyExchange {
    /**
     * Holds the logger used for call-key crypto diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(CallKeyCryptography.class.getName());

    /**
     * Holds the length, in bytes, of the raw end-to-end call key.
     *
     * <p>The key is exactly thirty-two bytes: {@code generate_raw_e2e_keys} writes {@code 0x20} bytes,
     * and {@code call_update_participant_keys} rejects a {@code raw_e2e_len} greater than {@code 0x20}.
     */
    public static final int CALL_KEY_LENGTH = 32;

    /**
     * The wire attribute naming the call identifier, stamped on every action element.
     *
     * <p>Shared with {@link CallRekeyEnvelope} so the rekey stanza and this service apply the same literal
     * the wa-voip engine's {@code populate_common_call_attr} (fn11591) writes.
     */
    static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the call creator's device JID, stamped on every action element.
     */
    static final String CALL_CREATOR_ATTRIBUTE = "call-creator";

    /**
     * The wire attribute naming the Signal ciphertext type on an {@code <enc>} element.
     */
    private static final String TYPE_ATTRIBUTE = "type";

    /**
     * The retry-count value stamped on every offer call-key {@code <enc>} element.
     *
     * <p>The live captures show {@code count="0"} on every per-device offer {@code <enc>}.
     */
    private static final int ENC_COUNT = 0;

    /**
     * Holds the encryption service used to wrap the call key per recipient device.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the message service whose {@link MessageService#processCall(Jid, MessageEncryptionType, byte[])}
     * decrypts inbound call-key envelopes.
     */
    private final MessageService messageService;

    /**
     * Holds the device service used to establish Signal sessions before encrypting the call key to each
     * recipient device.
     */
    private final DeviceService deviceService;

    /**
     * Holds the store consulted for the local ADV-signed device identity attached to {@code pkmsg}
     * envelopes.
     */
    private final LinkedWhatsAppStore store;

    /**
     * Holds the cryptographically-strong random source that mints the call key.
     */
    private final SecureRandom secureRandom;

    /**
     * Constructs a call-key crypto facade bound to the reused Signal pipeline.
     *
     * <p>All four collaborators must share the same client store as the rest of the Signal pipeline so
     * the per-address lock registry and session state observed during a call-key encrypt or decrypt are
     * the same ones the message plane uses.
     *
     * @param encryption     the message-encryption service used to wrap the call key per device
     * @param messageService the message service used to decrypt inbound call-key envelopes
     * @param deviceService  the device service used to ensure Signal sessions before encryption
     * @param store          the store supplying the local ADV-signed device identity
     * @param secureRandom   the cryptographically-strong random source used to mint the call key
     * @throws NullPointerException if any argument is {@code null}
     */
    public CallKeyCryptography(MessageEncryption encryption, MessageService messageService,
                              DeviceService deviceService, LinkedWhatsAppStore store, SecureRandom secureRandom) {
        this.encryption = Objects.requireNonNull(encryption, "encryption cannot be null");
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom cannot be null");
    }

    /**
     * Mints a fresh thirty-two-byte raw end-to-end call key.
     *
     * <p>The key is drawn from the injected {@link SecureRandom}; it is the only secret the call plane
     * distributes over the wire. The caller stores it on the call runtime so accept and rekey reuse it
     * and feed it to the local HKDF chain that derives the SRTP, SFrame, and data-channel keys.
     *
     * @return a new {@value #CALL_KEY_LENGTH}-byte call key
     */
    @Override
    public byte[] mintCallKey() {
        var callKey = new byte[CALL_KEY_LENGTH];
        secureRandom.nextBytes(callKey);
        var hex = new StringBuilder(callKey.length * 2);
        for (var b : callKey) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        LOGGER.log(System.Logger.Level.INFO, "calls2 E2E call key minted ({0} bytes): {1}", callKey.length, hex);
        return callKey;
    }

    /**
     * Wraps a raw call key as the plaintext that travels Signal-encrypted inside an {@code <enc>}.
     *
     * <p>The plaintext is a {@link MessageContainer} whose only populated payload is a
     * {@link CallOfferMessage} carrying the key in its {@code callKey} field. The returned bytes are the
     * protobuf encoding of that container; the caller hands them to a per-device Signal encrypt. This is
     * the identical plaintext shape used for both the offer key and the group rekey key.
     *
     * @implNote This implementation produces the exact byte shape confirmed against the live-decrypted
     * rekey sample in {@code group-rekey.json}: {@code 52220a20<32B callKey>...}, the protobuf encoding
     * of {@code MessageContainer.call} (field 10) wrapping {@code Call.callKey} (field 1, thirty-two
     * bytes). It mirrors the inverse build in {@code LiveMessageService.sendCall}.
     *
     * @param callKey the raw call key to wrap
     * @return the protobuf-encoded {@code MessageContainer{Call{callKey}}} plaintext
     * @throws NullPointerException     if {@code callKey} is {@code null}
     * @throws IllegalArgumentException if {@code callKey} is not {@value #CALL_KEY_LENGTH} bytes long
     */
    @Override
    public byte[] wrapCallKey(byte[] callKey) {
        Objects.requireNonNull(callKey, "callKey cannot be null");
        if (callKey.length != CALL_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "callKey must be " + CALL_KEY_LENGTH + " bytes, got " + callKey.length);
        }
        var callOffer = new CallOfferMessageBuilder()
                .callKey(callKey)
                .build();
        var container = new MessageContainerBuilder()
                .call(callOffer)
                .build();
        return MessageContainerSpec.encode(container);
    }

    /**
     * Encrypts a wrapped call key to every peer device as a one-to-one offer's per-device fanout.
     *
     * <p>Each peer device becomes one {@link CallKeyDistribution} slot. The Signal session for every
     * device is established first; the existing session for every device is then dropped and
     * re-established so a peer holding a stale or one-sided session receives a clean, decryptable
     * envelope rather than silently rejecting the offer. The fanout is all-or-nothing: if encryption
     * fails for ANY device, every encrypted slot is discarded and each device is addressed with a
     * {@linkplain CallKeyDistribution#bare(Jid) bare destination} carrying no key, so the call still
     * rings.
     *
     * <p>The thirty-two-byte key never leaves this method except as a Signal ciphertext; the SKMSG
     * sender-key envelope is never used for the call-key fanout, even for group calls.
     *
     * @implNote This implementation reproduces {@code LiveMessageService.sendCall}'s fanout: a forced
     * {@link DeviceService#ensureSessions(Collection, boolean)} so a one-sided session is repaired,
     * per-device {@link MessageEncryption#encryptForDevice(Jid, byte[])} (which acquires the
     * {@code SignalCryptoLocks} session lock), and the
     * {@link MessageEncryptedPayload#bareDestination(Jid)} all-or-nothing fallback that
     * {@code add_destination_if_needed} (fn11610) mirrors. Each encrypted slot carries the Signal
     * ciphertext version {@value MessageEncryption#CIPHERTEXT_VERSION}, the per-ciphertext
     * {@code msg}/{@code pkmsg} type, and the {@value #ENC_COUNT} retry count the live offers show.
     *
     * @param deviceJids the peer device JIDs to fan the key out to; never empty in practice
     * @param plaintext  the wrapped {@code MessageContainer{Call{callKey}}} plaintext from
     *                   {@link #wrapCallKey(byte[])}
     * @return one fanout slot per device, all encrypted on success or all bare on any encryption failure
     * @throws NullPointerException if {@code deviceJids} or {@code plaintext} is {@code null}, or if
     *                              {@code deviceJids} contains a {@code null} element
     */
    @Override
    public List<CallKeyDistribution> encryptOfferFanout(Collection<Jid> deviceJids, byte[] plaintext) {
        Objects.requireNonNull(deviceJids, "deviceJids cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        var devices = List.copyOf(deviceJids);

        // Repair one-sided sessions exactly as the live caller offer does: a device that already holds a
        // healthy session must still be re-established here because a stale session makes the peer
        // silently drop the offer rather than reject it.
        deviceService.ensureSessions(devices, true);

        var slots = new ArrayList<CallKeyDistribution>(devices.size());
        var encryptionFailed = false;
        // FIXME: after encryptionFailed is set this loop keeps calling encryptForDevice() for every
        //  remaining device, each advancing that session's Signal ratchet counter, and then slots.clear()
        //  discards all ciphertext for a bare fanout, leaving the local counter ahead of the peer on
        //  sessions the message plane also uses (ratchet desync). WA's partial-fanout failure semantics
        //  (break-early vs best-effort-encrypt-all) are unconfirmed and this crosses into the shared
        //  Signal session layer; do not change ratchet consumption (e.g. break out on first failure and
        //  build the bare list post-loop) until confirmed against the message-encryption path.
        for (var deviceJid : devices) {
            try {
                var payload = encryption.encryptForDevice(deviceJid, plaintext);
                slots.add(toFanoutSlot(payload));
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Call-key encryption failed for {0}; stripping all <enc> for a bare fanout: {1}",
                        deviceJid, e.getMessage());
                encryptionFailed = true;
            }
        }
        if (encryptionFailed) {
            slots.clear();
            for (var deviceJid : devices) {
                slots.add(CallKeyDistribution.bare(deviceJid));
            }
        }
        return slots;
    }

    /**
     * Encrypts a wrapped call key to every connected participant device as a group rekey fanout.
     *
     * <p>This is the group-only key rotation: the local participant mints one fresh key and re-shares it
     * to every other connected participant device. Each recipient device becomes one
     * {@link CallRekeyEnvelope}, addressed unicast; the caller wraps each envelope in its own
     * {@code <call to="<recipientDeviceLid>"><enc_rekey>...} stanza with a per-round transaction id.
     * Existing sessions are reused (no force): a rekey continues each device's ratchet rather than
     * bootstrapping a fresh one, and a {@code pkmsg} envelope carries the local device identity so a
     * peer lacking the session can still establish it.
     *
     * <p>Unlike the offer fanout there is no all-or-nothing fallback: a rekey that fails for one
     * recipient simply omits that recipient's envelope; the others still rotate. A device whose
     * encryption fails is skipped with a logged warning.
     *
     * @implNote This implementation reproduces {@code make_and_send_rekey_msg} (fn11448) and the
     * unicast-per-participant fanout confirmed in {@code group-rekey.json}: one {@code <enc>} per
     * recipient stanza (NOT a {@code <destination>} block), an {@code <encopt keygen="2"/>} sibling, and
     * a {@code <device-identity>} only on {@code pkmsg}. The plaintext is the SAME single
     * thirty-two-byte {@code MessageContainer{Call{callKey}}} as the offer key; the three per-domain
     * keys are derived locally after decrypt, not transmitted. The per-device encrypt acquires the
     * {@code SignalCryptoLocks} session lock through {@link MessageEncryption#encryptForDevice(Jid, byte[])}.
     *
     * @param recipientDevices the connected participant devices to re-share the key to
     * @param plaintext        the wrapped {@code MessageContainer{Call{callKey}}} plaintext from
     *                         {@link #wrapCallKey(byte[])} for the freshly minted rekey key
     * @return one rekey envelope per recipient whose encryption succeeded, in input order
     * @throws NullPointerException if {@code recipientDevices} or {@code plaintext} is {@code null}, or
     *                              if {@code recipientDevices} contains a {@code null} element
     */
    @Override
    public List<CallRekeyEnvelope> encryptRekeyFanout(Collection<Jid> recipientDevices, byte[] plaintext) {
        Objects.requireNonNull(recipientDevices, "recipientDevices cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        var devices = List.copyOf(recipientDevices);

        // A rekey continues each device's existing ratchet; ensure a session exists but do not force a
        // fresh one (forcing would send a pkmsg to a device that already has a session and break it).
        deviceService.ensureSessions(devices, false);

        var deviceIdentity = signedDeviceIdentity();
        var envelopes = new ArrayList<CallRekeyEnvelope>(devices.size());
        for (var deviceJid : devices) {
            try {
                var payload = encryption.encryptForDevice(deviceJid, plaintext);
                var identity = payload.isPreKeyMessage() ? deviceIdentity : null;
                envelopes.add(new CallRekeyEnvelope(deviceJid, payload.type(), payload.ciphertext(), identity));
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Rekey encryption failed for {0}; skipping this recipient: {1}",
                        deviceJid, e.getMessage());
            }
        }
        return envelopes;
    }

    /**
     * Decrypts an inbound call-key envelope back to the thirty-two-byte raw call key.
     *
     * <p>Used for both the offer's per-device {@code <enc>} and the group {@code <enc_rekey>}'s single
     * {@code <enc>}: both wrap the same {@code MessageContainer{Call{callKey}}} plaintext. The ciphertext
     * is Signal-decrypted through {@link MessageService#processCall(Jid, MessageEncryptionType, byte[])}
     * (which strips the Signal PKCS#7 padding), the plaintext is decoded as a {@link MessageContainer},
     * and the key is read from its {@link CallOfferMessage#callKey() callKey}. This method never throws:
     * a {@code null}-equivalent empty result means "no end-to-end key recovered" so the caller can fall
     * back to the hop-by-hop key, exactly as {@code WAWebVoipValidateAndDecryptEnc} tolerates a missing
     * key.
     *
     * @implNote This implementation mirrors {@code CallReceiver.decryptOfferCallKey} and the live
     * {@code WAWebVoipValidateAndDecryptEnc} flow: {@code decryptSignalProto} (here
     * {@code processCall} under the {@code SignalCryptoLocks} session lock) then
     * {@code decodeProtobuf(MessageSpec)} then {@code r.call.callKey}. Any decrypt or decode failure is
     * swallowed to an empty result; the recovered key length is the engine's {@code raw_e2e_len}, which
     * {@code call_update_participant_keys} requires to be at most {@value #CALL_KEY_LENGTH} bytes.
     *
     * @param senderJid  the device JID that authored the Signal envelope
     * @param encType    the Signal envelope variant from the {@code <enc>} {@code type} attribute
     * @param ciphertext the Signal ciphertext bytes from the {@code <enc>} content
     * @return the recovered call key, or an empty result when it could not be recovered
     * @throws NullPointerException if any argument is {@code null}
     */
    public Optional<byte[]> decryptCallKey(Jid senderJid, MessageEncryptionType encType, byte[] ciphertext) {
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(encType, "encType cannot be null");
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        try {
            var plaintext = messageService.processCall(senderJid, encType, ciphertext);
            var container = MessageContainerSpec.decode(plaintext);
            if (container.content() instanceof CallOfferMessage offer) {
                return offer.callKey();
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Call-key decryption from {0} failed; treating as no end-to-end key: {1}",
                    senderJid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decrypts the call key from an inbound {@code <enc>} stanza, selecting its envelope variant and
     * ciphertext from the stanza's attributes and content.
     *
     * <p>This is the stanza-level convenience over
     * {@link #decryptCallKey(Jid, MessageEncryptionType, byte[])}: it reads the {@code type} attribute
     * and the binary content of a single {@code <enc>} element (the bare {@code <enc>} a callee receives
     * directly under {@code <offer>}, or the {@code <enc>} child of an {@code <enc_rekey>}). An
     * {@code <enc>} with a missing or unparseable {@code type}, missing content, or a failed decrypt
     * yields an empty result without throwing.
     *
     * @param encStanza   the {@code <enc>} stanza carrying the Signal envelope
     * @param senderJid the device JID that authored the envelope, used as the Signal decryption sender
     * @return the recovered call key, or an empty result when it could not be recovered
     * @throws NullPointerException if {@code encStanza} or {@code senderJid} is {@code null}
     */
    public Optional<byte[]> decryptCallKey(Stanza encStanza, Jid senderJid) {
        Objects.requireNonNull(encStanza, "encStanza cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var typeAttr = encStanza.getAttributeAsString(TYPE_ATTRIBUTE).orElse(null);
        if (typeAttr == null) {
            return Optional.empty();
        }
        MessageEncryptionType encType;
        try {
            encType = MessageEncryptionType.fromProtocolValue(typeAttr);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        var ciphertext = encStanza.toContentBytes().orElse(null);
        if (ciphertext == null) {
            return Optional.empty();
        }
        return decryptCallKey(senderJid, encType, ciphertext);
    }

    /**
     * Decrypts the call key from a single offer fanout slot.
     *
     * <p>This is the {@link CallKeyDistribution}-level convenience over
     * {@link #decryptCallKey(Jid, MessageEncryptionType, byte[])}. A
     * {@linkplain CallKeyDistribution#isEncrypted() bare} slot, a slot whose {@code type} is absent or
     * unparseable, or a failed decrypt yields an empty result. The slot's
     * {@link CallKeyDistribution#deviceJid() device JID} is the recipient address, so the decryption
     * sender is supplied separately by the caller from the outer call stanza's {@code from}.
     *
     * @param slot      the offer fanout slot to decrypt
     * @param senderJid the device JID that authored the envelope, used as the Signal decryption sender
     * @return the recovered call key, or an empty result when it could not be recovered
     * @throws NullPointerException if {@code slot} or {@code senderJid} is {@code null}
     */
    @Override
    public Optional<byte[]> decryptCallKey(CallKeyDistribution slot, Jid senderJid) {
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        if (!slot.isEncrypted()) {
            return Optional.empty();
        }
        var typeAttr = slot.typeValue().orElse(null);
        if (typeAttr == null) {
            return Optional.empty();
        }
        MessageEncryptionType encType;
        try {
            encType = MessageEncryptionType.fromProtocolValue(typeAttr);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        var ciphertext = slot.ciphertextBytes().orElse(null);
        if (ciphertext == null) {
            return Optional.empty();
        }
        return decryptCallKey(senderJid, encType, ciphertext);
    }

    /**
     * Returns the local ADV-signed device identity bytes, or {@code null} when none is stored.
     *
     * <p>A {@code pkmsg} call-key envelope bootstraps a new Signal session, so the recipient must learn
     * the sender's identity: the offer (when any device's {@code <enc>} is {@code pkmsg}) and every
     * {@code pkmsg} rekey carry this {@code <device-identity>} block. The caller attaches it to the
     * stanza; this service exposes it so the offer and rekey builders share one source.
     *
     * @return the encoded ADV-signed device identity, or {@code null} when none is stored
     */
    @Override
    public byte[] signedDeviceIdentity() {
        return store.signalStore().signedDeviceIdentity()
                .map(ADVSignedDeviceIdentitySpec::encode)
                .orElse(null);
    }

    /**
     * Maps a Signal-encrypted payload to its offer fanout slot.
     *
     * <p>A keyed payload becomes an {@linkplain CallKeyDistribution#encrypted(Jid, int, String, int, byte[])
     * encrypted} slot carrying the Signal ciphertext version, the envelope type wire string, and the
     * {@value #ENC_COUNT} retry count; a bare-destination marker becomes a
     * {@linkplain CallKeyDistribution#bare(Jid) bare} slot.
     *
     * @param payload the per-device Signal-encrypted payload
     * @return the matching fanout slot
     */
    private static CallKeyDistribution toFanoutSlot(MessageEncryptedPayload payload) {
        if (payload.ciphertext() == null || payload.type() == null) {
            return CallKeyDistribution.bare(payload.recipientJid());
        }
        return CallKeyDistribution.encrypted(
                payload.recipientJid(),
                MessageEncryption.CIPHERTEXT_VERSION,
                payload.type().protocolValue(),
                ENC_COUNT,
                payload.ciphertext());
    }
}
