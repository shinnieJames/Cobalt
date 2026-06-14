package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedCollection;

/**
 * Decoded {@code <relay>} child of a server {@code <ack class="call" type="offer">} stanza or an
 * inbound {@code <call><offer>} payload.
 *
 * <p>WhatsApp's call signaling delivers two structurally identical relay blocks: one nested in
 * the peer-side inbound offer (which the callee parses to discover the relay the caller already
 * allocated), and one as the only child of the ACK the server returns to the caller's outbound
 * offer (which the caller parses to learn the relay the server picked on its behalf). Both
 * shapes flatten into this class. On a NACK the {@code <relay>} block is still present but
 * carries only the denormalised {@code call-creator} / {@code call-id} attributes.
 *
 * <p>Captured ACK shape:
 *
 * {@snippet lang="xml" :
 * <ack class="call" type="offer" id="...">
 *   <relay attribute_padding="1" peer_pid="0" self_pid="1" uuid="..."
 *          call-creator="..." call-id="..." joinable="1">
 *     <participant pid="..." jid="..."/> ...
 *     <token id="0">...182 bytes...</token>
 *     <token id="1">...</token>
 *     <token id="2">...</token>
 *     <auth_token id="0">...70 bytes...</auth_token>
 *     <key>"<base64-encoded 16 bytes>"</key>
 *     <te2 auth_token_id="..." domain_name="..." relay_name="..."
 *          c2r_rtt="..." relay_id="..." token_id="...">...bytes...</te2> ...
 *     <hbh_key>"<base64-encoded 30 bytes>"</hbh_key>
 *   </relay>
 * </ack>
 * }
 */
public final class CallRelay {
    /**
     * The {@code uuid} attribute of the {@code <relay>} node, or {@code null} when absent.
     */
    private final String uuid;

    /**
     * The {@code peer_pid} attribute, or {@code null} when absent.
     */
    private final Integer peerPid;

    /**
     * The {@code self_pid} attribute, or {@code null} when absent.
     */
    private final Integer selfPid;

    /**
     * The {@code call-creator} attribute (denormalised on the ACK shape), or {@code null}.
     */
    private final Jid callCreator;

    /**
     * The {@code call-id} attribute (denormalised on the ACK shape), or {@code null}.
     */
    private final String callId;

    /**
     * Whether the {@code joinable} attribute was {@code "1"}.
     */
    private final boolean joinable;

    /**
     * Unmodifiable snapshot of the {@code <participant>} children.
     */
    private final SequencedCollection<CallParticipant> participants;

    /**
     * Unmodifiable snapshot of the {@code <token>} children.
     */
    private final SequencedCollection<RelayToken> tokens;

    /**
     * Unmodifiable snapshot of the {@code <auth_token>} children.
     */
    private final SequencedCollection<AuthToken> authTokens;

    /**
     * Unmodifiable snapshot of the {@code <te2>} children.
     */
    private final SequencedCollection<RelayEndpoint> endpoints;

    /**
     * The raw {@code <key>} child bytes (the ASCII bytes of a base64 string), or {@code null} when
     * absent. The bytes are intentionally NOT base64-decoded because the wasm engine passes them
     * verbatim as the pjsip authentication password. Defensive copy held
     * internally.
     */
    private final byte[] callKey;

    /**
     * The decoded {@code <hbh_key>} child bytes, or {@code null} when absent. Defensive copy held
     * internally.
     */
    private final byte[] hbhKey;

    /**
     * The {@code warp_mi_tag_len} attribute of the {@code <relay>} node, or {@code null} when absent.
     *
     * <p>This is the truncated HMAC-SHA1 authentication tag length, in bytes, the relay requires on the
     * media SRTP and SRTCP it forwards. The media-plane bring-up feeds it to the SRTP endpoints so the
     * tag length matches what the relay and peer expect; when absent the profile default applies.
     */
    private final Integer warpMiTagLen;

    /**
     * Constructs an immutable relay snapshot.
     *
     * <p>Collections are snapshotted via {@link List#copyOf(java.util.Collection)} so the result
     * is immutable; byte arrays are cloned defensively. Package-private; the only caller is the
     * parser.
     *
     * @param uuid         the {@code uuid} attribute, or {@code null}
     * @param peerPid      the {@code peer_pid} attribute, or {@code null}
     * @param selfPid      the {@code self_pid} attribute, or {@code null}
     * @param callCreator  the {@code call-creator} attribute, or {@code null}
     * @param callId       the {@code call-id} attribute, or {@code null}
     * @param joinable     the parsed {@code joinable} flag
     * @param participants the {@code <participant>} children
     * @param tokens       the {@code <token>} children
     * @param authTokens   the {@code <auth_token>} children
     * @param endpoints    the {@code <te2>} children
     * @param callKey      the decoded {@code <key>} bytes, or {@code null}
     * @param hbhKey       the decoded {@code <hbh_key>} bytes, or {@code null}
     * @param warpMiTagLen the {@code warp_mi_tag_len} attribute, or {@code null}
     */
    CallRelay(String uuid,
              Integer peerPid,
              Integer selfPid,
              Jid callCreator,
              String callId,
              boolean joinable,
              SequencedCollection<CallParticipant> participants,
              SequencedCollection<RelayToken> tokens,
              SequencedCollection<AuthToken> authTokens,
              SequencedCollection<RelayEndpoint> endpoints,
              byte[] callKey,
              byte[] hbhKey,
              Integer warpMiTagLen) {
        this.uuid = uuid;
        this.peerPid = peerPid;
        this.selfPid = selfPid;
        this.callCreator = callCreator;
        this.callId = callId;
        this.joinable = joinable;
        this.warpMiTagLen = warpMiTagLen;
        this.participants = participants == null
                ? Collections.emptyList()
                : Collections.unmodifiableSequencedCollection(new ArrayList<>(participants));
        this.tokens = tokens == null
                ? Collections.emptyList()
                : Collections.unmodifiableSequencedCollection(new ArrayList<>(tokens));
        this.authTokens = authTokens == null
                ? Collections.emptyList()
                : Collections.unmodifiableSequencedCollection(new ArrayList<>(authTokens));
        this.endpoints = endpoints == null
                ? Collections.emptyList()
                : Collections.unmodifiableSequencedCollection(new ArrayList<>(endpoints));
        this.callKey = callKey == null ? null : callKey.clone();
        this.hbhKey = hbhKey == null ? null : hbhKey.clone();
    }

    /**
     * Returns the {@code uuid} attribute of the {@code <relay>} node.
     *
     * @return the UUID, or {@link Optional#empty()} when absent
     */
    public Optional<String> uuid() {
        return Optional.ofNullable(uuid);
    }

    /**
     * Returns the {@code peer_pid} attribute.
     *
     * @return the peer pid, or {@link OptionalInt#empty()} when absent
     */
    public OptionalInt peerPid() {
        return peerPid != null ? OptionalInt.of(peerPid) : OptionalInt.empty();
    }

    /**
     * Returns the {@code self_pid} attribute.
     *
     * @return the self pid, or {@link OptionalInt#empty()} when absent
     */
    public OptionalInt selfPid() {
        return selfPid != null ? OptionalInt.of(selfPid) : OptionalInt.empty();
    }

    /**
     * Returns the {@code call-creator} attribute denormalised on the {@code <relay>} node.
     *
     * @return the creator JID, or {@link Optional#empty()} when absent
     */
    public Optional<Jid> callCreator() {
        return Optional.ofNullable(callCreator);
    }

    /**
     * Returns the {@code call-id} attribute denormalised on the {@code <relay>} node.
     *
     * @return the call id, or {@link Optional#empty()} when absent
     */
    public Optional<String> callId() {
        return Optional.ofNullable(callId);
    }

    /**
     * Returns whether the {@code joinable} attribute was {@code "1"}.
     *
     * @return {@code true} when joinable
     */
    public boolean joinable() {
        return joinable;
    }

    /**
     * Returns an unmodifiable snapshot of the {@code <participant>} children.
     *
     * @return the participants; never {@code null}, possibly empty
     */
    public SequencedCollection<CallParticipant> participants() {
        return participants;
    }

    /**
     * Returns an unmodifiable snapshot of the {@code <token>} children.
     *
     * @return the tokens; never {@code null}, possibly empty
     */
    public SequencedCollection<RelayToken> tokens() {
        return tokens;
    }

    /**
     * Returns an unmodifiable snapshot of the {@code <auth_token>} children.
     *
     * @return the auth tokens; never {@code null}, possibly empty
     */
    public SequencedCollection<AuthToken> authTokens() {
        return authTokens;
    }

    /**
     * Returns an unmodifiable snapshot of the {@code <te2>} children, one per relay candidate per
     * address family.
     *
     * @return the endpoints; never {@code null}, possibly empty
     */
    public SequencedCollection<RelayEndpoint> endpoints() {
        return endpoints;
    }

    /**
     * Returns a defensive copy of the raw {@code <key>} child bytes.
     *
     * <p>The returned bytes are the ASCII characters of the base64 string the wire carries,
     * including any trailing {@code "="} padding, NOT the base64-decoded binary key. The wasm
     * engine uses them in this form as the pjsip "password" parameter for both the relay HMAC
     * stamp and ICE STUN MESSAGE-INTEGRITY.
     *
     * @return the call key bytes, or {@link Optional#empty()} when the child was absent
     */
    public Optional<byte[]> callKey() {
        return callKey == null ? Optional.empty() : Optional.of(callKey.clone());
    }

    /**
     * Returns a defensive copy of the decoded {@code <hbh_key>} child bytes.
     *
     * @return the hop-by-hop key bytes, or {@link Optional#empty()} when the child was absent
     */
    public Optional<byte[]> hbhKey() {
        return hbhKey == null ? Optional.empty() : Optional.of(hbhKey.clone());
    }

    /**
     * Returns the {@code warp_mi_tag_len} attribute of the {@code <relay>} node, the truncated
     * HMAC-SHA1 authentication tag length, in bytes, the relay requires on the media SRTP and SRTCP.
     *
     * @return the tag length, or {@link OptionalInt#empty()} when the attribute was absent
     */
    public OptionalInt warpMiTagLen() {
        return warpMiTagLen != null ? OptionalInt.of(warpMiTagLen) : OptionalInt.empty();
    }

    /**
     * Parses the {@code <relay>} child of an {@code <ack class="call" type="offer">} into a typed
     * relay block.
     *
     * <p>Returns {@link Optional#empty()} when the supplied node is {@code null} or is not a
     * {@code <relay>} node.
     *
     * @param relay the {@code <relay>} node, or {@code null}
     * @return the parsed block, or empty when none could be parsed
     */
    public static Optional<CallRelay> parse(Node relay) {
        if (relay == null || !relay.hasDescription("relay")) {
            return Optional.empty();
        }
        var uuid = relay.getAttributeAsString("uuid", null);
        var peerPid = relay.getAttributeAsLong("peer_pid", (Long) null);
        var selfPid = relay.getAttributeAsLong("self_pid", (Long) null);
        var callCreator = relay.getAttributeAsJid("call-creator", null);
        var callId = relay.getAttributeAsString("call-id", null);
        var joinable = "1".equals(relay.getAttributeAsString("joinable", null));
        var warpMiTagLen = relay.getAttributeAsLong("warp_mi_tag_len", (Long) null);

        var participants = new ArrayList<CallParticipant>();
        var tokens = new ArrayList<RelayToken>();
        var authTokens = new ArrayList<AuthToken>();
        var endpoints = new ArrayList<RelayEndpoint>();
        byte[] callKey = null;
        byte[] hbhKey = null;

        for (var child : relay.children()) {
            switch (child.description()) {
                case "participant" -> {
                    var pid = child.getAttributeAsLong("pid", (Long) null);
                    var jid = child.getAttributeAsJid("jid", null);
                    if (pid != null && jid != null) {
                        participants.add(new CallParticipant(pid.intValue(), jid));
                    }
                }
                case "token" -> {
                    var id = child.getAttributeAsLong("id", (Long) null);
                    var bytes = child.toContentBytes().orElse(null);
                    if (id != null && bytes != null) {
                        tokens.add(new RelayToken(id.intValue(), bytes));
                    }
                }
                case "auth_token" -> {
                    var id = child.getAttributeAsLong("id", (Long) null);
                    var bytes = child.toContentBytes().orElse(null);
                    if (id != null && bytes != null) {
                        authTokens.add(new AuthToken(id.intValue(), bytes));
                    }
                }
                // The wire content of <key> is the ASCII bytes of a base64 string. The wasm engine
                // passes these ASCII bytes verbatim as the pjsip "password" parameter for both the
                // relay HMAC stamp (WaRelayMessageIntegrity) and ICE STUN MESSAGE-INTEGRITY, so we
                // keep the raw bytes here instead of base64-decoding them.
                case "key" -> callKey = stripOuterQuotes(child);
                case "hbh_key" -> hbhKey = decodeQuotedBase64(child);
                case "te2" -> {
                    var authTokenId = child.getAttributeAsLong("auth_token_id", (Long) null);
                    var relayId = child.getAttributeAsLong("relay_id", (Long) null);
                    var tokenId = child.getAttributeAsLong("token_id", (Long) null);
                    var domainName = child.getAttributeAsString("domain_name", null);
                    var relayName = child.getAttributeAsString("relay_name", null);
                    var c2rRtt = child.getAttributeAsLong("c2r_rtt", (Long) null);
                    var bytes = child.toContentBytes().orElse(null);
                    // The 1:1 offer-ACK te2 carries domain_name and c2r_rtt; the group offer te2
                    // carries only relay_name plus the address bytes, so the only fields required to
                    // drive an Allocate are the relay address (content) and relay_name. The integer
                    // references default to 0 (their wire value on the group offer) when absent.
                    if (relayName != null && bytes != null) {
                        endpoints.add(new RelayEndpoint(
                                authTokenId == null ? 0 : authTokenId.intValue(),
                                relayId == null ? 0 : relayId.intValue(),
                                tokenId == null ? 0 : tokenId.intValue(),
                                domainName, relayName,
                                c2rRtt == null ? 0 : c2rRtt.intValue(),
                                bytes));
                    }
                }
                default -> {
                }
            }
        }

        return Optional.of(new CallRelay(
                uuid,
                peerPid == null ? null : peerPid.intValue(),
                selfPid == null ? null : selfPid.intValue(),
                callCreator,
                callId,
                joinable,
                participants, tokens, authTokens, endpoints,
                callKey, hbhKey,
                warpMiTagLen == null ? null : warpMiTagLen.intValue()));
    }

    /**
     * Parses the {@code <relay>} child of an inbound {@code <offer>} payload.
     *
     * <p>The inbound-offer shape is structurally identical to the ACK shape; this convenience
     * extracts the {@code <relay>} child for the callee-side parse.
     *
     * @param offer the inbound {@code <offer>} payload, or {@code null}
     * @return the parsed block, or empty when no {@code <relay>} child is present
     */
    public static Optional<CallRelay> parseOffer(Node offer) {
        if (offer == null) {
            return Optional.empty();
        }
        return offer.getChild("relay").flatMap(CallRelay::parse);
    }

    /**
     * Decodes a {@code <key>}-style child whose content is a base64 string literally wrapped in
     * {@code "..."} quotes.
     *
     * @implNote If the content cannot be read as a string, this method falls back to the raw
     * bytes. If the string is not quoted, it is base64-decoded as-is. If decoding fails, the
     * UTF-8 bytes of the string are returned as a last resort.
     *
     * @param child the {@code <key>} or {@code <hbh_key>} node
     * @return the decoded bytes, or {@code null} when no content is present
     */
    /**
     * Returns the raw content bytes of a child whose payload is a base64 string optionally wrapped
     * in {@code "..."} quotes. The bytes are returned as the wire sent them (ASCII characters of
     * the base64 string), with only the outer quote characters stripped.
     *
     * @param child the child node
     * @return the raw content bytes, or {@code null} when no content is present
     */
    private static byte[] stripOuterQuotes(Node child) {
        var bytes = child.toContentBytes().orElse(null);
        if (bytes == null) {
            // The relay <key> arrives as a quoted base64 STRING token, so a node holding string content
            // yields nothing from toContentBytes(); fall back to the string form (the same accessor
            // <hbh_key> uses) and take its ASCII bytes, which are exactly what the wasm engine passes
            // verbatim as the pjsip password.
            var str = child.toContentString().orElse(null);
            bytes = str == null ? null : str.getBytes(StandardCharsets.US_ASCII);
        }
        if (bytes == null) {
            return null;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) '"' && bytes[bytes.length - 1] == (byte) '"') {
            var trimmed = new byte[bytes.length - 2];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static byte[] decodeQuotedBase64(Node child) {
        var str = child.toContentString().orElse(null);
        if (str == null) {
            return child.toContentBytes().orElse(null);
        }
        var body = str;
        if (body.length() >= 2 && body.charAt(0) == '"' && body.charAt(body.length() - 1) == '"') {
            body = body.substring(1, body.length() - 1);
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException _) {
            return str.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CallRelay that
                && Objects.equals(this.uuid, that.uuid)
                && Objects.equals(this.peerPid, that.peerPid)
                && Objects.equals(this.selfPid, that.selfPid)
                && Objects.equals(this.callCreator, that.callCreator)
                && Objects.equals(this.callId, that.callId)
                && this.joinable == that.joinable
                && this.participants.equals(that.participants)
                && this.tokens.equals(that.tokens)
                && this.authTokens.equals(that.authTokens)
                && this.endpoints.equals(that.endpoints)
                && Arrays.equals(this.callKey, that.callKey)
                && Arrays.equals(this.hbhKey, that.hbhKey)
                && Objects.equals(this.warpMiTagLen, that.warpMiTagLen));
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, peerPid, selfPid, callCreator, callId, joinable,
                participants, tokens, authTokens, endpoints,
                Arrays.hashCode(callKey), Arrays.hashCode(hbhKey), warpMiTagLen);
    }

    @Override
    public String toString() {
        return "CallRelay[uuid=" + uuid
                + ", callCreator=" + callCreator
                + ", callId=" + callId
                + ", joinable=" + joinable
                + ", participants=" + participants.size()
                + ", tokens=" + tokens.size()
                + ", endpoints=" + endpoints.size() + ']';
    }
}
