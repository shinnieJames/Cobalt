package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a {@code <transport>} signaling message, the transport-plane exchange of a call.
 *
 * <p>The transport message is the carrier for transport bring-up and maintenance. Its
 * {@code transport-message-type} attribute selects which transport payload it conveys, modeled by
 * {@link CallTransportSubType}: a candidate, a candidate list, the negotiated transport protocol, a
 * relay-latency probe, the peer network-health status, or the web-peer-to-peer ICE/DTLS material.
 * Two payload shapes are carried inline by this message rather than by a separate child message: the
 * relay-and-network-health path (sub-type {@code 11}) adds a {@code health_status} to the
 * {@code <net>} element, and the ICE/DTLS path (sub-type {@code 13}) adds the {@code ice-ufrag} and
 * {@code ice-pwd} attributes and a {@code <certificate>} child. The message may also carry a
 * {@link RelayInfo relay} block and a list of peer-to-peer candidates, each with a {@code priority}.
 *
 * <p>The message stamps the common {@code call-id} and {@code call-creator} attributes like every
 * call action. The {@code <net>} element reports the network medium and protocol the device selected;
 * the {@code p2p-cand-round} attribute counts the candidate-gathering round; the {@code has-bot}
 * attribute marks a call that includes a bot.
 *
 * <p>On the wire the message is
 * {@snippet lang="xml" :
 * <transport call-id="..." call-creator="..." transport-message-type="13" p2p-cand-round="1"
 *            ice-ufrag="..." ice-pwd="...">
 *   <net medium="2" protocol="0"/>
 *   <certificate algorithm="sha-256" fingerprint="..."/>
 *   <relay>...</relay>
 *   <candidate priority="1"/>
 * </transport>
 * }
 * with the ICE/DTLS attributes and {@code <certificate>} present only for sub-type {@code 13} and the
 * {@code health_status} on {@code <net>} present only for sub-type {@code 11}.
 *
 * @implNote This implementation models the {@code <transport>} element built by
 * {@code serialize_transport} (fn11725) and parsed by {@code deserialize_transport} (fn11726) in the
 * wa-voip WASM module {@code ff-tScznZ8P} ({@code stanzas/transport.cc}). The sub-type selector is
 * the {@code transport-message-type} attribute: {@code 11} is the relay-and-network-health path and
 * {@code 13} is the ICE/DTLS web-peer-to-peer path. The {@code health_status} child attribute is
 * emitted only for sub-type {@code 11} and the {@code ice-ufrag}/{@code ice-pwd}/{@code <certificate>}
 * only for sub-type {@code 13}, matching the serializer's branches. The candidate list is parsed by
 * {@code deserialize_candidate_list} (fn11625) with a per-candidate {@code priority}.
 *
 * @param callId           the {@code call-id} attribute; never {@code null}
 * @param callCreator      the {@code call-creator} device JID; never {@code null}
 * @param hasBot           whether the {@code has-bot} attribute marks a bot-bearing call
 * @param transportSubType the {@code transport-message-type} sub-type, or {@code null} when the
 *                         message carries the implicit remote-candidate sub-type with no explicit
 *                         attribute
 * @param p2pCandRound     the {@code p2p-cand-round} attribute, or {@code -1} when absent
 * @param net              the {@code <net>} network descriptor, or {@code null} when absent
 * @param iceUfrag         the {@code ice-ufrag} attribute, present for sub-type {@code 13}, or
 *                         {@code null} otherwise
 * @param icePwd           the {@code ice-pwd} attribute, present for sub-type {@code 13}, or
 *                         {@code null} otherwise
 * @param certificate      the {@code <certificate>} descriptor, present for sub-type {@code 13}, or
 *                         {@code null} otherwise
 * @param relay            the {@code <relay>} block, or {@code null} when absent
 * @param candidates       the peer-to-peer {@code <candidate>} list, in wire order; never
 *                         {@code null}
 * @see CallTransportSubType
 * @see RelayInfo
 */
public record TransportStanza(String callId,
                              Jid callCreator,
                              boolean hasBot,
                              CallTransportSubType transportSubType,
                              int p2pCandRound,
                              Net net,
                              String iceUfrag,
                              String icePwd,
                              Certificate certificate,
                              RelayInfo relay,
                              List<Candidate> candidates) implements CallMessage {
    /**
     * The wire element tag for a transport message.
     */
    public static final String ELEMENT = "transport";

    /**
     * The sentinel value standing in for an absent {@code p2p-cand-round} attribute.
     */
    private static final int UNSET = -1;

    /**
     * The wire attribute marking a bot-bearing call.
     */
    private static final String HAS_BOT_ATTRIBUTE = "has-bot";

    /**
     * The wire attribute naming the transport sub-message type.
     */
    private static final String TRANSPORT_MESSAGE_TYPE_ATTRIBUTE = "transport-message-type";

    /**
     * The wire attribute naming the peer-to-peer candidate-gathering round.
     */
    private static final String P2P_CAND_ROUND_ATTRIBUTE = "p2p-cand-round";

    /**
     * The wire attribute naming the ICE username fragment.
     */
    private static final String ICE_UFRAG_ATTRIBUTE = "ice-ufrag";

    /**
     * The wire attribute naming the ICE password.
     */
    private static final String ICE_PWD_ATTRIBUTE = "ice-pwd";

    /**
     * The wire literal a boolean attribute carries when set; booleans on the call plane serialize as
     * {@code '1'}/{@code '0'} rather than {@code true}/{@code false}.
     */
    private static final String FLAG_TRUE = "1";

    /**
     * Canonicalizes the record components, copying the candidate list immutably.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code candidates} is
     *                              {@code null}
     */
    public TransportStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates cannot be null"));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#TRANSPORT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.TRANSPORT;
    }

    /**
     * Returns the transport sub-message type this message conveys, if it carries an explicit one.
     *
     * @return an {@link Optional} holding the {@link CallTransportSubType}, or empty for the implicit
     *         remote-candidate sub-type
     */
    public Optional<CallTransportSubType> transportSubTypeValue() {
        return Optional.ofNullable(transportSubType);
    }

    /**
     * Returns the peer-to-peer candidate-gathering round, if present.
     *
     * @return an {@link OptionalInt} holding the {@code p2p-cand-round}, or empty when absent
     */
    public OptionalInt p2pCandRoundValue() {
        return p2pCandRound == UNSET ? OptionalInt.empty() : OptionalInt.of(p2pCandRound);
    }

    /**
     * Returns the {@code <net>} network descriptor, if present.
     *
     * @return an {@link Optional} holding the network descriptor, or empty when absent
     */
    public Optional<Net> netValue() {
        return Optional.ofNullable(net);
    }

    /**
     * Returns the ICE username fragment, if present.
     *
     * @return an {@link Optional} holding the {@code ice-ufrag}, or empty when absent
     */
    public Optional<String> iceUfragValue() {
        return Optional.ofNullable(iceUfrag);
    }

    /**
     * Returns the ICE password, if present.
     *
     * @return an {@link Optional} holding the {@code ice-pwd}, or empty when absent
     */
    public Optional<String> icePwdValue() {
        return Optional.ofNullable(icePwd);
    }

    /**
     * Returns the {@code <certificate>} descriptor, if present.
     *
     * @return an {@link Optional} holding the certificate descriptor, or empty when absent
     */
    public Optional<Certificate> certificateValue() {
        return Optional.ofNullable(certificate);
    }

    /**
     * Returns the {@code <relay>} block, if present.
     *
     * @return an {@link Optional} holding the relay block, or empty when absent
     */
    public Optional<RelayInfo> relayValue() {
        return Optional.ofNullable(relay);
    }

    /**
     * Builds the {@code <transport>} action stanza for this message.
     *
     * <p>The stanza stamps {@code call-id} and {@code call-creator} as every action does. The sub-type
     * attribute is omitted for the implicit remote-candidate sub-type; {@code has-bot} is written only
     * when set; {@code p2p-cand-round} is omitted when absent. The ICE attributes, certificate, relay
     * block, network descriptor, and candidate children are emitted only when present.
     *
     * @return the transport action stanza
     */
    @Override
    public Stanza toStanza() {
        var children = new ArrayList<Stanza>();
        if (net != null) {
            children.add(net.toNode());
        }
        if (certificate != null) {
            children.add(certificate.toNode());
        }
        if (relay != null) {
            children.add(relay.toNode());
        }
        for (var candidate : candidates) {
            children.add(candidate.toNode());
        }
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(HAS_BOT_ATTRIBUTE, FLAG_TRUE, hasBot)
                .attribute(TRANSPORT_MESSAGE_TYPE_ATTRIBUTE, transportSubType == null ? null : transportSubType.wireValue())
                .attribute(P2P_CAND_ROUND_ATTRIBUTE, p2pCandRound, p2pCandRound != UNSET)
                .attribute(ICE_UFRAG_ATTRIBUTE, iceUfrag)
                .attribute(ICE_PWD_ATTRIBUTE, icePwd);
        if (!children.isEmpty()) {
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes a {@code <transport>} action stanza into a {@link TransportStanza}.
     *
     * <p>The sub-type is resolved through {@link CallTransportSubType#ofWireValue(int)}; an absent or
     * unrecognized {@code transport-message-type} leaves the {@link #transportSubType()} null, modeling
     * the implicit remote-candidate sub-type. The optional {@code <net>}, {@code <certificate>},
     * {@code <relay>}, and {@code <candidate>} children are decoded when present.
     *
     * @param stanza the {@code <transport>} stanza
     * @return the decoded message
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static TransportStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var hasBot = FLAG_TRUE.equals(stanza.getAttributeAsString(HAS_BOT_ATTRIBUTE, null));
        var transportSubType = stanza.getAttributeAsInt(TRANSPORT_MESSAGE_TYPE_ATTRIBUTE)
                .stream()
                .boxed()
                .flatMap(value -> CallTransportSubType.ofWireValue(value).stream())
                .findFirst()
                .orElse(null);
        var p2pCandRound = stanza.getAttributeAsInt(P2P_CAND_ROUND_ATTRIBUTE, UNSET);
        var iceUfrag = stanza.getAttributeAsString(ICE_UFRAG_ATTRIBUTE, null);
        var icePwd = stanza.getAttributeAsString(ICE_PWD_ATTRIBUTE, null);
        var net = stanza.getChild(Net.ELEMENT).flatMap(Net::of).orElse(null);
        var certificate = stanza.getChild(Certificate.ELEMENT).flatMap(Certificate::of).orElse(null);
        var relay = stanza.getChild(RelayInfo.ELEMENT).flatMap(RelayInfo::of).orElse(null);
        var candidates = new ArrayList<Candidate>();
        for (var child : stanza.getChildren(Candidate.ELEMENT)) {
            Candidate.of(child).ifPresent(candidates::add);
        }
        return new TransportStanza(callId, callCreator, hasBot, transportSubType, p2pCandRound,
                net, iceUfrag, icePwd, certificate, relay, candidates);
    }

    /**
     * Represents the {@code <net>} network descriptor of a transport message.
     *
     * <p>The network descriptor reports the network medium and transport protocol the device is using.
     * It carries a {@code health_status} only for the relay-and-network-health sub-type
     * ({@link CallTransportSubType#PEER_HEALTH}).
     *
     * @param medium       the {@code medium} attribute, the network medium code, or {@code -1} when
     *                     absent
     * @param protocol     the {@code protocol} attribute, the transport protocol code, or {@code -1}
     *                     when absent
     * @param healthStatus the {@code health_status} attribute, present for the peer-health sub-type, or
     *                     {@code -1} when absent
     */
    public record Net(int medium, int protocol, int healthStatus) {
        /**
         * The wire element tag for a network descriptor.
         */
        public static final String ELEMENT = "net";

        /**
         * The wire attribute naming the network medium.
         */
        private static final String MEDIUM_ATTRIBUTE = "medium";

        /**
         * The wire attribute naming the transport protocol.
         */
        private static final String PROTOCOL_ATTRIBUTE = "protocol";

        /**
         * The wire attribute naming the network health status.
         */
        private static final String HEALTH_STATUS_ATTRIBUTE = "health_status";

        /**
         * Returns the network medium code, if present.
         *
         * @return an {@link OptionalInt} holding the {@code medium}, or empty when absent
         */
        public OptionalInt mediumValue() {
            return medium < 0 ? OptionalInt.empty() : OptionalInt.of(medium);
        }

        /**
         * Returns the transport protocol code, if present.
         *
         * @return an {@link OptionalInt} holding the {@code protocol}, or empty when absent
         */
        public OptionalInt protocolValue() {
            return protocol < 0 ? OptionalInt.empty() : OptionalInt.of(protocol);
        }

        /**
         * Returns the network health status, if present.
         *
         * @return an {@link OptionalInt} holding the {@code health_status}, or empty when absent
         */
        public OptionalInt healthStatusValue() {
            return healthStatus < 0 ? OptionalInt.empty() : OptionalInt.of(healthStatus);
        }

        /**
         * Builds the {@code <net medium=... protocol=... health_status=.../>} stanza for this descriptor.
         *
         * <p>Absent attributes are omitted rather than written as a sentinel.
         *
         * @return the network descriptor stanza
         */
        public Stanza toNode() {
            return new StanzaBuilder()
                    .description(ELEMENT)
                    .attribute(MEDIUM_ATTRIBUTE, medium, medium >= 0)
                    .attribute(PROTOCOL_ATTRIBUTE, protocol, protocol >= 0)
                    .attribute(HEALTH_STATUS_ATTRIBUTE, healthStatus, healthStatus >= 0)
                    .build();
        }

        /**
         * Decodes a {@code <net>} stanza into a {@link Net}.
         *
         * @param stanza the {@code <net>} stanza
         * @return the decoded network descriptor, or an empty result when the stanza is not a
         *         {@code <net>} element
         */
        public static Optional<Net> of(Stanza stanza) {
            if (stanza == null || !stanza.hasDescription(ELEMENT)) {
                return Optional.empty();
            }
            var medium = stanza.getAttributeAsInt(MEDIUM_ATTRIBUTE, -1);
            var protocol = stanza.getAttributeAsInt(PROTOCOL_ATTRIBUTE, -1);
            var healthStatus = stanza.getAttributeAsInt(HEALTH_STATUS_ATTRIBUTE, -1);
            return Optional.of(new Net(medium, protocol, healthStatus));
        }
    }

    /**
     * Represents the {@code <certificate>} descriptor of an ICE/DTLS transport message.
     *
     * <p>The certificate descriptor carries the DTLS fingerprint a web-peer-to-peer transport offers,
     * present only for the ICE/DTLS sub-type ({@link CallTransportSubType#ICE_DTLS}). The
     * {@code algorithm} names the fingerprint hash and the {@code fingerprint} is its textual value.
     *
     * @param algorithm   the {@code algorithm} attribute, the fingerprint hash name, or {@code null}
     *                    when absent
     * @param fingerprint the {@code fingerprint} attribute, the textual fingerprint, or {@code null}
     *                    when absent
     */
    public record Certificate(String algorithm, String fingerprint) {
        /**
         * The wire element tag for a certificate descriptor.
         */
        public static final String ELEMENT = "certificate";

        /**
         * The wire attribute naming the fingerprint hash algorithm.
         */
        private static final String ALGORITHM_ATTRIBUTE = "algorithm";

        /**
         * The wire attribute naming the fingerprint value.
         */
        private static final String FINGERPRINT_ATTRIBUTE = "fingerprint";

        /**
         * Returns the fingerprint hash algorithm name, if present.
         *
         * @return an {@link Optional} holding the {@code algorithm}, or empty when absent
         */
        public Optional<String> algorithmValue() {
            return Optional.ofNullable(algorithm);
        }

        /**
         * Returns the textual fingerprint, if present.
         *
         * @return an {@link Optional} holding the {@code fingerprint}, or empty when absent
         */
        public Optional<String> fingerprintValue() {
            return Optional.ofNullable(fingerprint);
        }

        /**
         * Builds the {@code <certificate algorithm=... fingerprint=.../>} stanza for this descriptor.
         *
         * @return the certificate descriptor stanza
         */
        public Stanza toNode() {
            return new StanzaBuilder()
                    .description(ELEMENT)
                    .attribute(ALGORITHM_ATTRIBUTE, algorithm)
                    .attribute(FINGERPRINT_ATTRIBUTE, fingerprint)
                    .build();
        }

        /**
         * Decodes a {@code <certificate>} stanza into a {@link Certificate}.
         *
         * @param stanza the {@code <certificate>} stanza
         * @return the decoded certificate descriptor, or an empty result when the stanza is not a
         *         {@code <certificate>} element
         */
        public static Optional<Certificate> of(Stanza stanza) {
            if (stanza == null || !stanza.hasDescription(ELEMENT)) {
                return Optional.empty();
            }
            var algorithm = stanza.getAttributeAsString(ALGORITHM_ATTRIBUTE, null);
            var fingerprint = stanza.getAttributeAsString(FINGERPRINT_ATTRIBUTE, null);
            return Optional.of(new Certificate(algorithm, fingerprint));
        }
    }

    /**
     * Represents one {@code <candidate>} of a transport message's peer-to-peer candidate list.
     *
     * <p>Each candidate is one local transport address the peer may try, ordered by its
     * {@code priority}. The candidate's attributes beyond {@code priority} are retained as a raw
     * attribute view so an unrecognized candidate shape round-trips without loss.
     *
     * @param priority the {@code priority} attribute, the candidate priority, or {@code -1} when absent
     * @param stanza     the underlying candidate stanza, preserving every attribute and child; never
     *                 {@code null}
     */
    public record Candidate(int priority, Stanza stanza) {
        /**
         * The wire element tag for a peer-to-peer candidate.
         */
        public static final String ELEMENT = "candidate";

        /**
         * The wire attribute naming the candidate priority.
         */
        private static final String PRIORITY_ATTRIBUTE = "priority";

        /**
         * Canonicalizes the record components.
         *
         * @throws NullPointerException if {@code stanza} is {@code null}
         */
        public Candidate {
            Objects.requireNonNull(stanza, "stanza cannot be null");
        }

        /**
         * Returns the candidate priority, if present.
         *
         * @return an {@link OptionalInt} holding the {@code priority}, or empty when absent
         */
        public OptionalInt priorityValue() {
            return priority < 0 ? OptionalInt.empty() : OptionalInt.of(priority);
        }

        /**
         * Returns the underlying candidate stanza.
         *
         * @return the candidate stanza preserving every attribute and child; never {@code null}
         */
        public Stanza toNode() {
            return stanza;
        }

        /**
         * Decodes a {@code <candidate>} stanza into a {@link Candidate}.
         *
         * <p>The stanza is retained verbatim so a re-encode preserves every attribute. A stanza that is not
         * a {@code <candidate>} element yields an empty result.
         *
         * @param stanza the {@code <candidate>} stanza
         * @return the decoded candidate, or an empty result when the stanza is not a {@code <candidate>}
         *         element
         */
        public static Optional<Candidate> of(Stanza stanza) {
            if (stanza == null || !stanza.hasDescription(ELEMENT)) {
                return Optional.empty();
            }
            var priority = stanza.getAttributeAsInt(PRIORITY_ATTRIBUTE, -1);
            return Optional.of(new Candidate(priority, stanza));
        }
    }
}
