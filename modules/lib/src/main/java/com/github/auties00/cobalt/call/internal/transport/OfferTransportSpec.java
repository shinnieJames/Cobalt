package com.github.auties00.cobalt.call.internal.transport;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Captures the transport-layer setup carried inside a WhatsApp Web {@code <offer>} payload.
 *
 * <p>WA's call offers ship the full WebRTC-equivalent transport blueprint as XML children: the
 * TURN-like relay endpoints ({@code <te2>}), the per-call session tokens ({@code <token>},
 * {@code <auth_token>}, {@code <key>}, {@code <hbh_key>}), the negotiated participants
 * ({@code <participant>}), and a single Real-Time-Engine identifier ({@code <rte>}). Downstream
 * layers use these to perform the relay handshake, derive SRTP keying material, and route media.
 *
 * <p>The fields below mirror the captured-offer attribute and child shape exactly; the
 * source-of-truth fixtures live in {@code modules/lib/src/test/resources/fixtures/call/1to1/}.
 *
 * @param relayUuid    the {@code uuid} attribute on {@code <relay>}, the opaque session id used by WA's relay protocol
 * @param peerPid      the {@code peer_pid} attribute, the peer's participant id within this call's relay session
 * @param selfPid      the {@code self_pid} attribute, the local participant id
 * @param participants the parsed {@code <participant>} children, one entry per group-call participant in
 *                     participant-id order
 * @param tokens       the parsed {@code <token>} children, the per-relay session tokens indexed by their
 *                     {@code id} attribute
 * @param authTokens   the parsed {@code <auth_token>} children, the authentication tokens paired with
 *                     {@code te2} endpoints by {@code auth_token_id}
 * @param te2Endpoints the parsed {@code <te2>} children, each naming one candidate WA relay endpoint with its
 *                     {@code domain_name}, {@code relay_name}, and opaque address bytes
 * @param callKey      the bytes of the {@code <key>} child, the per-call session key
 * @param hbhKey       the bytes of the {@code <hbh_key>} child, the hop-by-hop key
 * @param rte          the bytes of the {@code <rte>} child, the Real-Time-Engine identifier (typically 6 bytes)
 */
public record OfferTransportSpec(
        String relayUuid,
        Integer peerPid,
        Integer selfPid,
        List<Participant> participants,
        List<RelayToken> tokens,
        List<AuthToken> authTokens,
        List<Te2Endpoint> te2Endpoints,
        byte[] callKey,
        byte[] hbhKey,
        byte[] rte
) {
    /**
     * Normalizes the list fields to unmodifiable copies, mapping {@code null} to an empty list.
     *
     * <p>The byte-array fields ({@code callKey}, {@code hbhKey}, {@code rte}) are left as supplied,
     * including {@code null}.
     */
    public OfferTransportSpec {
        participants = participants == null ? List.of() : List.copyOf(participants);
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        authTokens = authTokens == null ? List.of() : List.copyOf(authTokens);
        te2Endpoints = te2Endpoints == null ? List.of() : List.copyOf(te2Endpoints);
    }

    /**
     * Represents one {@code <participant pid jid />} child of the {@code <relay>} node.
     *
     * @param pid the {@code pid} attribute
     * @param jid the {@code jid} attribute
     */
    public record Participant(int pid, Jid jid) {
    }

    /**
     * Represents one {@code <token id>bytes</token>} child of the {@code <relay>} node.
     *
     * @param id    the {@code id} attribute
     * @param bytes the raw token bytes, that is, the element's content
     */
    public record RelayToken(int id, byte[] bytes) {
    }

    /**
     * Represents one {@code <auth_token id>bytes</auth_token>} child of the {@code <relay>} node.
     *
     * @param id    the {@code id} attribute
     * @param bytes the raw auth-token bytes
     */
    public record AuthToken(int id, byte[] bytes) {
    }

    /**
     * Represents one {@code <te2>} child, a candidate WA relay endpoint.
     *
     * @param authTokenId the {@code auth_token_id} attribute
     * @param relayId     the {@code relay_id} attribute
     * @param tokenId     the {@code token_id} attribute
     * @param domainName  the {@code domain_name} attribute
     * @param relayName   the {@code relay_name} attribute
     * @param c2rRtt      the {@code c2r_rtt} attribute
     * @param bytes       the raw {@code <te2>} content bytes, the encoded relay address
     */
    public record Te2Endpoint(int authTokenId, int relayId, int tokenId,
                              String domainName, String relayName, int c2rRtt,
                              byte[] bytes) {
    }

    /**
     * Parses an {@code <offer>} payload node into a transport spec.
     *
     * <p>The supplied node must be the inner {@code <offer>} child of the {@code <call>} envelope,
     * not the envelope itself. The {@code <relay>} child supplies the {@code uuid}, {@code peer_pid},
     * and {@code self_pid} attributes and is iterated for its {@code participant}, {@code token},
     * {@code auth_token}, {@code key}, {@code hbh_key}, and {@code te2} children; unrecognised
     * children are skipped. The {@code rte} child is read from the {@code <offer>} node directly. A
     * {@code participant}, {@code token}, {@code auth_token}, or {@code te2} child is admitted only
     * when all of its required attributes and content are present; partially populated children are
     * dropped. The {@code key} and {@code hbh_key} contents are decoded as raw bytes, falling back to
     * the UTF-8 bytes of the content string.
     *
     * @param offer the {@code <offer>} payload, or {@code null}
     * @return the parsed spec, or {@link Optional#empty()} when {@code offer} is {@code null} or has
     *         no {@code <relay>} child, which happens for signaling-only offers such as the output of
     *         {@link com.github.auties00.cobalt.call.internal.signaling.CallStanza#offer
     *         CallStanza.offer}
     */
    public static Optional<OfferTransportSpec> parse(Node offer) {
        if (offer == null) {
            return Optional.empty();
        }
        var relay = offer.getChild("relay").orElse(null);
        if (relay == null) {
            return Optional.empty();
        }
        var rteBytes = offer.getChild("rte")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        var uuid = relay.getAttributeAsString("uuid", null);
        var peerPid = relay.getAttributeAsLong("peer_pid", (Long) null);
        var selfPid = relay.getAttributeAsLong("self_pid", (Long) null);

        var participants = new ArrayList<Participant>();
        var tokens = new ArrayList<RelayToken>();
        var authTokens = new ArrayList<AuthToken>();
        var te2Endpoints = new ArrayList<Te2Endpoint>();
        byte[] callKey = null;
        byte[] hbhKey = null;

        for (var child : relay.children()) {
            switch (child.description()) {
                case "participant" -> {
                    var pid = child.getAttributeAsLong("pid", (Long) null);
                    var jid = child.getAttributeAsJid("jid", null);
                    if (pid != null && jid != null) {
                        participants.add(new Participant(pid.intValue(), jid));
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
                case "key" -> callKey = child.toContentBytes()
                        .orElseGet(() -> child.toContentString()
                                .map(s -> s.getBytes(StandardCharsets.UTF_8)).orElse(null));
                case "hbh_key" -> hbhKey = child.toContentBytes()
                        .orElseGet(() -> child.toContentString()
                                .map(s -> s.getBytes(StandardCharsets.UTF_8)).orElse(null));
                case "te2" -> {
                    var authTokenId = child.getAttributeAsLong("auth_token_id", (Long) null);
                    var relayId = child.getAttributeAsLong("relay_id", (Long) null);
                    var tokenId = child.getAttributeAsLong("token_id", (Long) null);
                    var domainName = child.getAttributeAsString("domain_name", null);
                    var relayName = child.getAttributeAsString("relay_name", null);
                    var c2rRtt = child.getAttributeAsLong("c2r_rtt", (Long) null);
                    var bytes = child.toContentBytes().orElse(null);
                    if (authTokenId != null && relayId != null && tokenId != null
                            && domainName != null && relayName != null
                            && c2rRtt != null && bytes != null) {
                        te2Endpoints.add(new Te2Endpoint(
                                authTokenId.intValue(),
                                relayId.intValue(),
                                tokenId.intValue(),
                                domainName, relayName,
                                c2rRtt.intValue(),
                                bytes));
                    }
                }
                default -> {}
            }
        }

        return Optional.of(new OfferTransportSpec(
                uuid,
                peerPid == null ? null : peerPid.intValue(),
                selfPid == null ? null : selfPid.intValue(),
                participants, tokens, authTokens, te2Endpoints,
                callKey, hbhKey, rteBytes));
    }
}
