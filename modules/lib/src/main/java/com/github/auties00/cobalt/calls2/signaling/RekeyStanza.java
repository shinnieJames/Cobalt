package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayloadSpec;
import com.github.auties00.cobalt.model.call.datachannel.RekeyKeyEntry;
import com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents an {@code <enc_rekey>} action: a group-call end-to-end key rotation.
 *
 * <p>A rekey rotates the per-call media keys when the group membership changes. The engine emits one
 * on participant join, leave, and other lifecycle events so every connected device converges on a
 * fresh key generation. This is the one signaling action whose payload is a protobuf rather than flat
 * attributes: an {@link E2eRekeyPayload} carrying one {@link RekeyKeyEntry} per encryption domain
 * ({@link RekeyKeyType#AUDIO}, {@link RekeyKeyType#VIDEO}, {@link RekeyKeyType#APPDATA}, each a
 * thirty-two-byte key). This record carries that payload alongside the universal call header, the
 * rotation transaction identifier, the key-generation version, the retry counter, and an optional
 * key-request flag.
 *
 * <p>The {@link #transactionId() transaction id} drives the receiver's ordering: a rekey whose
 * transaction id is older than the one already applied is dropped as stale, an exactly-matching one is
 * idempotent, and a newer one is buffered until the call catches up. The sender bounds the transaction
 * id below {@value #MAX_TRANSACTION_ID}; a rekey carrying a key payload is ignored before the call is
 * accepted and in one-to-one calls, where the simpler offer/accept key fanout applies instead.
 *
 * <p>On the wire the element is
 * {@code <enc_rekey call-id="..." call-creator="..." transaction-id="N" ver="2" retry="R">PAYLOAD</enc_rekey>}
 * where {@code PAYLOAD} is the serialized {@link E2eRekeyPayload} protobuf. A key-request rekey carries
 * the {@code request_keys} attribute and no payload: it asks connected peers to re-send their current
 * keys rather than installing new ones.
 *
 * @implNote This implementation models the message-type {@code 18} key rotation
 * ({@link Calls2SignalingType#REKEY}) built by {@code make_and_send_rekey_msg} (fn11448) and parsed by
 * {@code handle_enc_rekey} (fn11457) in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code call_signaling_sender.cc} and {@code handlers/group_call.cc}). The top-level wire child tag
 * is {@code enc_rekey} (data offset {@code 0x5e1d}, double-byte dictionary token page 1 index 205),
 * confirmed as the {@code <call>}-child element and as the child the {@code <receipt>} mirrors
 * (fn11551). The sender stamps the rotation transaction id (call context offset {@code 0x61ca4}), the
 * retry counter, and the keygen version (the only supported value is {@code 2}), and serializes the
 * repeated {@code RekeyKeyEntry} list as a nanopb protobuf decoded on receive by {@code pb_decode}
 * (fn9428); the engine's internal key-domain register names ({@code tx}/{@code rx}/{@code aux} at
 * key-type offset {@code 0xc}) are the audio, video, and appdata domains of the protobuf enum
 * ({@code REKEY_KEY_AUDIO=0}, {@code REKEY_KEY_VIDEO=1}, {@code REKEY_KEY_APPDATA=2}). The maximum
 * transaction id {@value #MAX_TRANSACTION_ID} is the {@code make_and_send_rekey_msg} guard
 * (transaction_id strictly less than {@code 6}). The top-level element is the bare {@code <enc_rekey>}
 * (data offset {@code 0x5e1d}), not a {@code <rekey>} wrapper: a connected group call observed the
 * stanza as a top-level {@code <enc_rekey call-id call-creator transaction-id>} with no enclosing
 * {@code <rekey>} element (live capture {@code re/calls2-spec/captures/group-rekey.json}), the inbound
 * dispatcher {@code handleIncomingSignalingMessage} (fn861) compares the child tag against
 * {@code enc_rekey} alone, and the {@code rekey} literal at data offset {@code 0x5e7a} is the boolean
 * {@code rekey} attribute on {@code <group_info>} (parsed by fn11614 in
 * {@code shared_elements/group.cc}, seen as {@code rekey="1"} in the same capture), not a wrapping
 * element.
 *
 * @param callId        the call identifier; never {@code null}
 * @param callCreator   the call creator's device JID; never {@code null}
 * @param transactionId the rotation transaction id used for stale-versus-fresh ordering, or
 *                      {@code -1} when absent
 * @param version       the key-generation version, or {@code -1} when absent
 * @param retry         the retry counter, or {@code -1} when absent
 * @param requestKeys   whether this rekey is a {@code request_keys} probe asking peers to re-send their
 *                      current keys rather than installing new ones
 * @param payload       the protobuf key bundle, or {@code null} for a key-request rekey carrying no new
 *                      keys
 * @see E2eRekeyPayload
 * @see Calls2SignalingType#REKEY
 */
public record RekeyStanza(String callId, Jid callCreator, int transactionId, int version, int retry,
                          boolean requestKeys, E2eRekeyPayload payload) implements CallMessage {
    /**
     * The wire element tag for a rekey action.
     */
    public static final String ELEMENT = "enc_rekey";

    /**
     * The wire attribute naming the rotation transaction id on an {@code <enc_rekey>} element.
     */
    private static final String TRANSACTION_ID_ATTRIBUTE = "transaction-id";

    /**
     * The wire attribute naming the key-generation version on an {@code <enc_rekey>} element.
     */
    private static final String VERSION_ATTRIBUTE = "ver";

    /**
     * The wire attribute naming the retry counter on an {@code <enc_rekey>} element.
     */
    private static final String RETRY_ATTRIBUTE = "retry";

    /**
     * The wire attribute marking a rekey as a key-request probe.
     */
    private static final String REQUEST_KEYS_ATTRIBUTE = "request_keys";

    /**
     * The exclusive upper bound the sender enforces on a rekey's transaction id.
     */
    public static final int MAX_TRANSACTION_ID = 6;

    /**
     * The only key-generation version the engine supports for a rekey.
     */
    public static final int SUPPORTED_VERSION = 2;

    /**
     * Canonicalizes the record components, validating the required header.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public RekeyStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * Returns a rekey carrying a fresh key bundle.
     *
     * @param callId        the call identifier
     * @param callCreator   the call creator's device JID
     * @param transactionId the rotation transaction id
     * @param version       the key-generation version
     * @param retry         the retry counter
     * @param payload       the protobuf key bundle
     * @return the key-bearing rekey
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code payload} is
     *                              {@code null}
     */
    public static RekeyStanza withKeys(String callId, Jid callCreator, int transactionId, int version, int retry, E2eRekeyPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        return new RekeyStanza(callId, callCreator, transactionId, version, retry, false, payload);
    }

    /**
     * Returns a key-request rekey carrying no new keys.
     *
     * <p>A key-request rekey asks the connected peers to re-send their current keys; it carries the
     * {@code request_keys} flag and no payload.
     *
     * @param callId        the call identifier
     * @param callCreator   the call creator's device JID
     * @param transactionId the rotation transaction id, or {@code -1} when not applicable
     * @return the key-request rekey
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public static RekeyStanza requestKeys(String callId, Jid callCreator, int transactionId) {
        return new RekeyStanza(callId, callCreator, transactionId, -1, -1, true, null);
    }

    /**
     * Returns the rotation transaction id, if present.
     *
     * @return an {@link OptionalInt} holding the transaction id, or empty when absent
     */
    public OptionalInt transactionIdValue() {
        return transactionId < 0 ? OptionalInt.empty() : OptionalInt.of(transactionId);
    }

    /**
     * Returns the key-generation version, if present.
     *
     * @return an {@link OptionalInt} holding the version, or empty when absent
     */
    public OptionalInt versionValue() {
        return version < 0 ? OptionalInt.empty() : OptionalInt.of(version);
    }

    /**
     * Returns the retry counter, if present.
     *
     * @return an {@link OptionalInt} holding the retry counter, or empty when absent
     */
    public OptionalInt retryValue() {
        return retry < 0 ? OptionalInt.empty() : OptionalInt.of(retry);
    }

    /**
     * Returns the protobuf key bundle, if present.
     *
     * @return an {@link Optional} holding the {@link E2eRekeyPayload}, or empty for a key-request rekey
     */
    public Optional<E2eRekeyPayload> payloadValue() {
        return Optional.ofNullable(payload);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#REKEY}, the message type of a rekey
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.REKEY;
    }

    /**
     * Builds the {@code <enc_rekey>} action stanza.
     *
     * <p>The common header is stamped first, then the transaction-id, version, and retry attributes
     * when present, then the {@code request_keys} attribute when this is a key-request rekey. The
     * serialized {@link E2eRekeyPayload} protobuf, when present, becomes the element's binary content.
     * Absent attributes are omitted rather than written as sentinels.
     *
     * @return the rekey action stanza
     */
    @Override
    public Stanza toStanza() {
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(TRANSACTION_ID_ATTRIBUTE, transactionId, transactionId >= 0)
                .attribute(VERSION_ATTRIBUTE, version, version >= 0)
                .attribute(RETRY_ATTRIBUTE, retry, retry >= 0)
                .attribute(REQUEST_KEYS_ATTRIBUTE, true, requestKeys);
        if (payload != null) {
            builder.content(E2eRekeyPayloadSpec.encode(payload));
        }
        return builder.build();
    }

    /**
     * Decodes an {@code <enc_rekey>} action stanza into a {@link RekeyStanza}.
     *
     * <p>The element's binary content, when present, is parsed as an {@link E2eRekeyPayload} protobuf;
     * an element with no content decodes to a payload-less rekey. The {@code request_keys} attribute,
     * the transaction id, the version, and the retry counter are read from the element's attributes.
     *
     * @param stanza the {@code <enc_rekey>} stanza
     * @return the decoded rekey
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static RekeyStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var transactionId = stanza.getAttributeAsInt(TRANSACTION_ID_ATTRIBUTE, -1);
        var version = stanza.getAttributeAsInt(VERSION_ATTRIBUTE, -1);
        var retry = stanza.getAttributeAsInt(RETRY_ATTRIBUTE, -1);
        var requestKeys = stanza.getAttributeAsBool(REQUEST_KEYS_ATTRIBUTE, false);
        var payload = stanza.toContentBytes()
                .map(E2eRekeyPayloadSpec::decode)
                .orElse(null);
        return new RekeyStanza(callId, callCreator, transactionId, version, retry, requestKeys, payload);
    }
}
