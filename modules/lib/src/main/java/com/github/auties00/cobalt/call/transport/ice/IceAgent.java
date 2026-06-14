package com.github.auties00.cobalt.call.transport.ice;

import com.github.auties00.cobalt.call.transport.relay.WaRelayAttribute;
import com.github.auties00.cobalt.call.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayMessageIntegrity;
import com.github.auties00.cobalt.call.transport.relay.WaRelayMessageType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayPacket;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Drives the RFC 8445 ICE connectivity-establishment process for one call media stream.
 *
 * <p>The agent gathers local candidates, ingests remote candidates received through call
 * signaling, forms the candidate-pair check list, runs STUN binding-request connectivity checks
 * built from the {@link WaRelayPacket} codec and the {@link WaRelayMessageIntegrity} keying
 * primitives, and nominates the highest-priority pair that succeeds.
 *
 * <p>The agent does not own a UDP socket. Outbound STUN packets are handed to the
 * {@link OutboundSink} supplied at construction (typically wired to a {@link java.nio.channels.DatagramChannel}
 * send loop), and inbound packets are fed back through {@link #handleInboundStun(byte[])}. This
 * separation keeps the agent unit-testable without real network I/O and lets the call layer
 * multiplex DTLS, SRTP, and STUN on a single socket.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>{@link #addLocalCandidate(IceCandidate)} for each candidate the gathering phase produces
 *       (host candidates from {@link #gatherLocalHostCandidates(IceComponent, int)}, relay
 *       candidates from a TURN Allocate, and so on).</li>
 *   <li>{@link #addRemoteCandidate(IceCandidate)} for each candidate the peer advertised through
 *       call signaling.</li>
 *   <li>{@link #start()} forms the pairs, transitions the highest-foundation pair to
 *       {@link IceCheckState#WAITING}, and fires the first check through the outbound sink.</li>
 *   <li>{@link #handleInboundStun(byte[])} on each received STUN packet, which is either an inbound
 *       binding response that closes an in-flight check or an inbound binding request that the
 *       agent answers with a success.</li>
 *   <li>The agent calls {@link Listener#onNominated(IceCandidatePair)} once a pair reaches
 *       {@link IceCheckState#SUCCEEDED} and is the highest priority of all succeeded pairs.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 *
 * <p>The mutating operations (add candidate, start, tick, handle inbound) are intended to be driven
 * from a single thread, typically the call's transport scheduler. The outbound {@link OutboundSink}
 * and the {@link Listener} callbacks fire synchronously on that calling thread.
 */
public final class IceAgent {
    /**
     * The per-check timeout after which an in-flight binding request is treated as lost.
     *
     * @implNote This implementation uses {@code 500 ms}, sized to cover a single roundtrip to the
     * relay plus retransmits before the pair is failed.
     */
    private static final Duration CHECK_TIMEOUT = Duration.ofMillis(500);

    /**
     * The interval at which a STUN consent-refresh binding request is re-sent on the nominated pair
     * once a connection is established, per RFC 7675 consent freshness.
     *
     * @implNote This implementation uses {@code 2 s}. A WhatsApp edge relay forwards a participant's
     * media only while that participant keeps the bind path consent-fresh by re-sending binding
     * requests; a relay drops the forwarding roughly 30 seconds after the last successful check. A
     * 2-second cadence leaves a wide margin against that timeout while keeping the consent traffic
     * negligible (one ~28-byte packet every two seconds).
     */
    private static final Duration CONSENT_INTERVAL = Duration.ofSeconds(2);

    /**
     * The outbound packet sink the agent writes encoded STUN packets to, paired with their
     * destination address; performing the actual datagram send is the caller's responsibility.
     */
    private final OutboundSink outboundSink;

    /**
     * The local and remote ufrag/password pairs used to authenticate binding requests.
     */
    private final IceCredentials credentials;

    /**
     * Whether the local agent holds the controlling role per RFC 8445 section 6.1.1.
     */
    private final boolean controlling;

    /**
     * Whether outbound binding requests carry a STUN {@code PRIORITY} attribute, enabled for the direct
     * peer-to-peer path and left off for the relay path.
     *
     * <p>WhatsApp's native client stamps {@code PRIORITY} on its direct peer connectivity checks but not
     * on the relay Allocate path; gating the attribute keeps the relay handshake byte-for-byte unchanged
     * while letting the P2P checks match the native shape.
     */
    private volatile boolean priorityAttribute;

    /**
     * The wall-clock source used to timestamp checks and detect timeouts.
     */
    private final Clock clock;

    /**
     * The random source used for STUN transaction ids.
     */
    private final SecureRandom random;

    /**
     * The local candidates accumulated by {@link #addLocalCandidate(IceCandidate)}.
     */
    private final List<IceCandidate> localCandidates = new CopyOnWriteArrayList<>();

    /**
     * The remote candidates accumulated by {@link #addRemoteCandidate(IceCandidate)}.
     */
    private final List<IceCandidate> remoteCandidates = new CopyOnWriteArrayList<>();

    /**
     * The check list, populated from the cartesian product of local and remote candidates by
     * {@link #buildCheckList()} and sorted by descending pair priority.
     */
    private final List<IceCandidatePair> checkList = new CopyOnWriteArrayList<>();

    /**
     * The in-flight pair lookup keyed by the hex-encoded transaction id of the binding request,
     * used to find the originating pair when a binding response arrives.
     */
    private final ConcurrentHashMap<String, IceCandidatePair> inFlight = new ConcurrentHashMap<>();

    /**
     * The application listener, fired on check and nomination events, or {@code null} when none is
     * registered.
     */
    private volatile Listener listener;

    /**
     * Whether {@link #start()} has been called, which freezes the candidate set.
     */
    private volatile boolean started;

    /**
     * The currently nominated pair, set once the agent commits to a succeeded pair, or
     * {@code null} until then.
     */
    private volatile IceCandidatePair nominatedPair;

    /**
     * The instant the last consent-refresh binding request was sent on the nominated pair, or
     * {@code null} until the first one fires; used to pace consent refreshes at
     * {@link #CONSENT_INTERVAL}.
     */
    private volatile java.time.Instant lastConsentCheck;

    /**
     * Receives encoded outbound STUN packets together with their destination address.
     *
     * <p>The agent passes the candidate-pair's remote transport address as the destination so the
     * caller can route each packet to the right peer over a multiplexed UDP socket.
     */
    @FunctionalInterface
    public interface OutboundSink {
        /**
         * Sends one encoded STUN packet to the given destination.
         *
         * @implSpec Implementations must transmit the packet bytes to {@code destination} over the
         * call's UDP socket. The {@code destination} may be {@code null} for a binding-success
         * response whose source address is supplied by the caller's pump; an implementation must
         * not throw on a {@code null} destination.
         * @param packet      the encoded STUN packet
         * @param destination the destination address, or {@code null} when the caller's pump
         *                    stamps the source address
         */
        void send(byte[] packet, InetSocketAddress destination);
    }

    /**
     * Receives agent-driven connectivity-check and nomination events.
     */
    public interface Listener {
        /**
         * Called once the agent nominates a pair, signaling that the connection is ready for the
         * next layer (DTLS-SRTP).
         *
         * @implSpec The default implementation does nothing.
         * @param pair the nominated pair
         */
        default void onNominated(IceCandidatePair pair) {
        }

        /**
         * Called when the agent observes a successful connectivity check on a pair, before any
         * nomination.
         *
         * @implSpec The default implementation does nothing.
         * @param pair the pair whose check succeeded
         */
        default void onCheckSucceeded(IceCandidatePair pair) {
        }

        /**
         * Called when a connectivity check fails through timeout or an error response.
         *
         * @implSpec The default implementation does nothing.
         * @param pair the pair whose check failed
         */
        default void onCheckFailed(IceCandidatePair pair) {
        }
    }

    /**
     * Constructs an agent with the system UTC clock and a fresh {@link SecureRandom}.
     *
     * @param controlling  whether the local agent holds the controlling role per RFC 8445
     *                     section 6.1.1
     * @param credentials  the local and remote ufrag/password pairs
     * @param outboundSink the sink for outbound STUN packets
     * @throws NullPointerException if {@code credentials} or {@code outboundSink} is {@code null}
     */
    public IceAgent(boolean controlling, IceCredentials credentials, OutboundSink outboundSink) {
        this(controlling, credentials, outboundSink, Clock.systemUTC(), new SecureRandom());
    }

    /**
     * Constructs an agent with caller-supplied clock and random sources.
     *
     * <p>The overrideable sources make the agent deterministic under test by fixing both the check
     * timestamps and the generated transaction ids.
     *
     * @param controlling  whether the local agent holds the controlling role
     * @param credentials  the local and remote ufrag/password pairs
     * @param outboundSink the sink for outbound STUN packets
     * @param clock        the clock used to timestamp {@link IceCandidatePair#lastCheckSent()}
     * @param random       the random source used for STUN transaction ids
     * @throws NullPointerException if {@code credentials}, {@code outboundSink}, {@code clock}, or
     *                              {@code random} is {@code null}
     */
    public IceAgent(boolean controlling, IceCredentials credentials, OutboundSink outboundSink,
                    Clock clock, SecureRandom random) {
        this.controlling = controlling;
        this.credentials = Objects.requireNonNull(credentials, "credentials cannot be null");
        this.outboundSink = Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    /**
     * Registers the listener fired on check and nomination events, replacing any previous one.
     *
     * @param listener the listener to register, or {@code null} to clear
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Returns whether the local agent holds the controlling role.
     *
     * @return {@code true} if the local agent is controlling
     */
    public boolean controlling() {
        return controlling;
    }

    /**
     * Enables the STUN {@code PRIORITY} attribute on outbound binding requests for the direct
     * peer-to-peer path.
     *
     * <p>Off by default so the relay handshake stays byte-for-byte as captured. The direct P2P branch
     * calls this so its connectivity checks match WhatsApp's native client, which carries
     * {@code PRIORITY} alongside {@code MESSAGE-INTEGRITY} on checks sent to a peer host candidate.
     *
     * @return this agent, for chaining at construction
     */
    public IceAgent enablePriorityAttribute() {
        this.priorityAttribute = true;
        return this;
    }

    /**
     * Returns an unmodifiable snapshot of the current local candidate set.
     *
     * @return the local candidates
     */
    public List<IceCandidate> localCandidates() {
        return List.copyOf(localCandidates);
    }

    /**
     * Returns an unmodifiable snapshot of the current remote candidate set.
     *
     * @return the remote candidates
     */
    public List<IceCandidate> remoteCandidates() {
        return List.copyOf(remoteCandidates);
    }

    /**
     * Returns an unmodifiable snapshot of the check list, ordered by descending pair priority.
     *
     * @return the check list
     */
    public List<IceCandidatePair> checkList() {
        return List.copyOf(checkList);
    }

    /**
     * Returns the currently nominated pair.
     *
     * @return the nominated pair, or {@code null} if no pair has been nominated yet
     */
    public IceCandidatePair nominatedPair() {
        return nominatedPair;
    }

    /**
     * Adds a local candidate to the gathering set.
     *
     * <p>May be called repeatedly during the gathering phase, but only before {@link #start()}
     * freezes the candidate set.
     *
     * @param candidate the candidate to add
     * @throws NullPointerException  if {@code candidate} is {@code null}
     * @throws IllegalStateException if {@link #start()} has already been called
     */
    public void addLocalCandidate(IceCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (started) {
            throw new IllegalStateException("agent already started");
        }
        localCandidates.add(candidate);
    }

    /**
     * Adds a remote candidate received from call signaling.
     *
     * <p>May be called repeatedly, but only before {@link #start()} freezes the candidate set.
     *
     * @param candidate the candidate to add
     * @throws NullPointerException  if {@code candidate} is {@code null}
     * @throws IllegalStateException if {@link #start()} has already been called
     */
    public void addRemoteCandidate(IceCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (started) {
            throw new IllegalStateException("agent already started");
        }
        remoteCandidates.add(candidate);
    }

    /**
     * Starts the agent by forming the check list and firing the first connectivity check.
     *
     * <p>The first call builds the check list from the gathered candidates, unfreezes one pair per
     * distinct foundation to {@link IceCheckState#WAITING}, and sends the first binding request.
     * Subsequent calls are no-ops.
     *
     * @throws IllegalStateException if there are no candidate pairs to check
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        buildCheckList();
        if (checkList.isEmpty()) {
            throw new IllegalStateException(
                    "cannot start ICE with no candidate pairs (locals="
                            + localCandidates.size() + ", remotes=" + remoteCandidates.size() + ")");
        }
        unfreezeFoundations();
        triggerNextWaitingCheck();
    }

    /**
     * Advances the agent by one tick, failing timed-out in-flight checks and firing the next
     * waiting pair.
     *
     * <p>Each {@link IceCheckState#IN_PROGRESS} pair whose send timestamp is older than
     * {@link #CHECK_TIMEOUT} is failed, then one {@link IceCheckState#WAITING} pair, if any, is
     * moved to {@link IceCheckState#IN_PROGRESS} and checked. Callers drive this at a steady
     * cadence, typically every 50 ms, until a pair is nominated.
     */
    public void tick() {
        var now = clock.instant();
        for (var pair : checkList) {
            if (pair.state() != IceCheckState.IN_PROGRESS) {
                continue;
            }
            var sent = pair.lastCheckSent();
            if (sent == null) {
                continue;
            }
            if (Duration.between(sent, now).compareTo(CHECK_TIMEOUT) > 0) {
                failPair(pair);
            }
        }
        triggerNextWaitingCheck();
        maybeRefreshConsent(now);
    }

    /**
     * Re-sends a STUN consent-refresh binding request on the nominated pair when the configured
     * {@link #CONSENT_INTERVAL} has elapsed since the last one, per RFC 7675.
     *
     * <p>Once a pair is nominated the connectivity-check state machine goes idle, but a WhatsApp edge
     * relay only keeps forwarding this participant's media while the participant keeps the bind path
     * consent-fresh. This method, driven from {@link #tick()}, fires a fresh binding request on the
     * nominated pair at a steady cadence for the call's lifetime. The request reuses the pair's remote
     * address and the per-call relay key but does not alter the pair's check state; the matching
     * binding success arrives with a transaction id absent from {@link #inFlight} and is harmlessly
     * ignored, because keeping the relay's consent alive only requires that the request be sent.
     *
     * @param now the current instant, supplied by the calling {@link #tick()}
     */
    private void maybeRefreshConsent(java.time.Instant now) {
        var pair = nominatedPair;
        if (pair == null) {
            return;
        }
        var last = lastConsentCheck;
        if (last != null && Duration.between(last, now).compareTo(CONSENT_INTERVAL) < 0) {
            return;
        }
        lastConsentCheck = now;
        var txId = newTransactionId();
        var attrs = new ArrayList<WaRelayAttribute>();
        attrs.add(new WaRelayAttribute(
                WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(),
                new byte[WaRelayMessageIntegrity.MAC_LENGTH]));
        var request = new WaRelayPacket(
                WaRelayMessageType.BINDING_REQUEST.wireValue(), txId, attrs);
        var encoded = request.encode();
        WaRelayMessageIntegrity.stamp(encoded, credentials.remotePassword());
        try {
            outboundSink.send(encoded, pair.remote().transportAddress());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Routes one inbound STUN packet through the agent.
     *
     * <p>The packet is decoded and dispatched by message type: a binding success closes the
     * matching in-flight check, while a binding request is answered with a success. A packet that
     * fails to decode, and any other message type, is silently dropped because error responses,
     * allocate responses, and similar messages are handled by the relay layer above the agent.
     *
     * @param packet the inbound STUN packet
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    public void handleInboundStun(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        WaRelayPacket parsed;
        try {
            parsed = WaRelayPacket.decode(packet);
        } catch (RuntimeException _) {
            return;
        }
        var msgType = parsed.messageType();
        if (msgType == WaRelayMessageType.BINDING_SUCCESS.wireValue()) {
            handleBindingResponse(parsed, true);
        } else if (msgType == WaRelayMessageType.BINDING_REQUEST.wireValue()) {
            handleBindingRequest(parsed);
        }
    }

    /**
     * Forms candidate pairs from the cartesian product of local and remote candidates and sorts
     * the check list by descending pair priority.
     *
     * <p>Only pairs whose candidates share a {@link IceComponent} and a compatible address family
     * are added.
     */
    private void buildCheckList() {
        for (var local : localCandidates) {
            for (var remote : remoteCandidates) {
                if (local.component() != remote.component()) {
                    continue;
                }
                if (!isCompatible(local.transportAddress(), remote.transportAddress())) {
                    continue;
                }
                checkList.add(new IceCandidatePair(local, remote, controlling));
            }
        }
        checkList.sort(Comparator.comparingLong(IceCandidatePair::priority).reversed());
    }

    /**
     * Returns whether two transport addresses can plausibly form a pair by sharing an address
     * family.
     *
     * @param a one transport address
     * @param b another transport address
     * @return {@code true} if both addresses are IPv4 or both are IPv6
     */
    private static boolean isCompatible(InetSocketAddress a, InetSocketAddress b) {
        var aIp4 = a.getAddress() instanceof Inet4Address;
        var bIp4 = b.getAddress() instanceof Inet4Address;
        if (aIp4 != bIp4) {
            return false;
        }
        var aIp6 = a.getAddress() instanceof Inet6Address;
        var bIp6 = b.getAddress() instanceof Inet6Address;
        return aIp6 == bIp6;
    }

    /**
     * Unfreezes one pair per distinct foundation, moving it from {@link IceCheckState#FROZEN} to
     * {@link IceCheckState#WAITING} per RFC 8445 section 6.1.2.6.
     *
     * <p>Because the check list is already in descending priority order, the first pair seen for
     * each foundation is the highest-priority one and is the one unfrozen.
     */
    private void unfreezeFoundations() {
        var seenFoundations = new HashSet<String>();
        for (var pair : checkList) {
            var foundation = pairFoundation(pair);
            if (seenFoundations.add(foundation)) {
                pair.transition(IceCheckState.FROZEN, IceCheckState.WAITING);
            }
        }
    }

    /**
     * Returns the foundation key for a pair, used to group pairs for unfreezing.
     *
     * <p>The key is the local candidate foundation and the remote candidate foundation joined by a
     * {@code ':'}.
     *
     * @param pair the pair
     * @return the foundation key
     */
    private static String pairFoundation(IceCandidatePair pair) {
        return pair.local().foundation() + ":" + pair.remote().foundation();
    }

    /**
     * Fires the next {@link IceCheckState#WAITING} pair in priority order.
     *
     * <p>The first waiting pair is moved to {@link IceCheckState#IN_PROGRESS} and a binding request
     * is sent for it; the method returns immediately once one pair has been triggered, or without
     * effect if none are waiting.
     */
    private void triggerNextWaitingCheck() {
        for (var pair : checkList) {
            if (pair.state() == IceCheckState.WAITING
                    && pair.transition(IceCheckState.WAITING, IceCheckState.IN_PROGRESS)) {
                fireBindingRequest(pair);
                return;
            }
        }
    }

    /**
     * Builds and sends a STUN binding request for the given pair, recording it as in flight.
     *
     * <p>The request carries only a zeroed MESSAGE-INTEGRITY placeholder that is then stamped with
     * the remote password (the per-call relay key); if the outbound send throws, the pair is failed.
     *
     * @implNote This implementation omits the STUN USERNAME attribute. WhatsApp's relay connectivity
     * checks authenticate solely through a shared-secret MESSAGE-INTEGRITY keyed by the per-call
     * relay key, with no USERNAME on the wire; live {@code ws2_32!sendto} captures of the native
     * client show binding requests of the form {@code [type][len]2112a442[txid:12]{PRIORITY}?[MI]}
     * and never a {@code 0x0006} USERNAME. Including a USERNAME would shift the MESSAGE-INTEGRITY
     * coverage and the relay would reject the check.
     *
     * @param pair the pair to check
     */
    private void fireBindingRequest(IceCandidatePair pair) {
        var txId = newTransactionId();
        var attrs = new ArrayList<WaRelayAttribute>();
        // PRIORITY precedes MESSAGE-INTEGRITY so the integrity HMAC covers it; the relay path leaves it
        // off to keep the captured Allocate handshake byte-for-byte unchanged.
        if (priorityAttribute) {
            var priority = (int) pair.local().priority();
            attrs.add(new WaRelayAttribute(
                    WaRelayAttributeType.PRIORITY.wireValue(),
                    new byte[]{
                            (byte) (priority >>> 24), (byte) (priority >>> 16),
                            (byte) (priority >>> 8), (byte) priority}));
        }
        attrs.add(new WaRelayAttribute(
                WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(),
                new byte[WaRelayMessageIntegrity.MAC_LENGTH]));
        var request = new WaRelayPacket(
                WaRelayMessageType.BINDING_REQUEST.wireValue(), txId, attrs);
        var encoded = request.encode();
        WaRelayMessageIntegrity.stamp(encoded, credentials.remotePassword());
        pair.markInFlight(txId, clock.instant());
        inFlight.put(hex(txId), pair);
        try {
            outboundSink.send(encoded, pair.remote().transportAddress());
        } catch (RuntimeException _) {
            failPair(pair);
        }
    }

    /**
     * Handles a STUN binding-success response by closing the matching in-flight check.
     *
     * <p>The originating pair is found by the response's transaction id. When MESSAGE-INTEGRITY
     * verification is requested and fails, the pair is failed; otherwise the pair moves to
     * {@link IceCheckState#SUCCEEDED}, the listener is notified, the pair is considered for
     * nomination, and the next waiting check is fired. A response whose transaction id matches no
     * in-flight pair is ignored.
     *
     * @param packet   the parsed binding-success response
     * @param verifyMi whether to verify the response's MESSAGE-INTEGRITY
     */
    private void handleBindingResponse(WaRelayPacket packet, boolean verifyMi) {
        var txKey = hex(packet.transactionId());
        var pair = inFlight.remove(txKey);
        if (pair == null) {
            return;
        }
        if (verifyMi && !verifyMessageIntegrity(packet)) {
            failPair(pair);
            return;
        }
        pair.transition(IceCheckState.IN_PROGRESS, IceCheckState.SUCCEEDED);
        pair.clearInFlight();
        var l = listener;
        if (l != null) {
            try {
                l.onCheckSucceeded(pair);
            } catch (Throwable _) {
            }
        }
        maybeNominate(pair);
        triggerNextWaitingCheck();
    }

    /**
     * Handles a STUN binding request from the peer by answering with a binding success, per RFC
     * 8445 section 7.3.1, once the request's MESSAGE-INTEGRITY verifies against the local password.
     *
     * <p>The request is verified against the local password because the peer keyed it with what is,
     * from its viewpoint, the remote password. A failed verification drops the request silently.
     *
     * <p>The response is sent with a {@code null} destination because this layer does not carry the
     * inbound source address; the call layer's pump pairs the request with the source address it
     * observed and routes the response back. Triggered checks per RFC 8445 section 7.3.1.4, which
     * would unfreeze a previously frozen pair, are not implemented here, which is sufficient for
     * full-candidate-set ICE.
     *
     * @param packet the parsed binding request
     */
    private void handleBindingRequest(WaRelayPacket packet) {
        if (!verifyMessageIntegrity(packet, credentials.localPassword())) {
            return;
        }
        var attrs = new ArrayList<WaRelayAttribute>();
        attrs.add(new WaRelayAttribute(
                WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(),
                new byte[WaRelayMessageIntegrity.MAC_LENGTH]));
        var response = new WaRelayPacket(
                WaRelayMessageType.BINDING_SUCCESS.wireValue(),
                packet.transactionId(),
                attrs);
        var encoded = response.encode();
        WaRelayMessageIntegrity.stamp(encoded, credentials.localPassword());
        try {
            outboundSink.send(encoded, null);
        } catch (RuntimeException _) {
        }
    }

    /**
     * Verifies a packet's MESSAGE-INTEGRITY against the configured remote password.
     *
     * @param packet the parsed packet, which must be re-encodable
     * @return {@code true} if the integrity check passes
     */
    private boolean verifyMessageIntegrity(WaRelayPacket packet) {
        return verifyMessageIntegrity(packet, credentials.remotePassword());
    }

    /**
     * Verifies a packet's MESSAGE-INTEGRITY against an arbitrary HMAC key.
     *
     * <p>A re-encoding or verification failure is reported as a failed check rather than thrown.
     *
     * @param packet the parsed packet
     * @param key    the HMAC key
     * @return {@code true} if the integrity check passes
     */
    private static boolean verifyMessageIntegrity(WaRelayPacket packet, byte[] key) {
        try {
            return WaRelayMessageIntegrity.verify(packet.encode(), key);
        } catch (RuntimeException _) {
            return false;
        }
    }

    /**
     * Fails a pair, clearing its in-flight bookkeeping and notifying the listener.
     *
     * <p>The pair is forced to {@link IceCheckState#FAILED}, removed from the in-flight lookup, and
     * reported through {@link Listener#onCheckFailed(IceCandidatePair)}.
     *
     * @param pair the pair to fail
     */
    private void failPair(IceCandidatePair pair) {
        pair.forceState(IceCheckState.FAILED);
        var txId = pair.inFlightTxId();
        if (txId != null) {
            inFlight.remove(hex(txId));
        }
        pair.clearInFlight();
        var l = listener;
        if (l != null) {
            try {
                l.onCheckFailed(pair);
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Considers a freshly succeeded pair for nomination.
     *
     * <p>Only the controlling agent nominates; it commits to the highest-priority succeeded pair,
     * marking it nominated and notifying {@link Listener#onNominated(IceCandidatePair)}. The
     * controlled agent waits for the controlling agent's USE-CANDIDATE flag, which this agent does
     * not yet emit on outbound checks, so only the controlling agent reaches the nomination branch.
     *
     * @param pair the freshly succeeded pair
     */
    private void maybeNominate(IceCandidatePair pair) {
        if (!controlling) {
            return;
        }
        var current = nominatedPair;
        if (current == null || pair.priority() > current.priority()) {
            pair.nominate();
            nominatedPair = pair;
            var l = listener;
            if (l != null) {
                try {
                    l.onNominated(pair);
                } catch (Throwable _) {
                }
            }
        }
    }

    /**
     * Generates a fresh STUN transaction id for a binding request.
     *
     * <p>The id is {@link WaRelayPacket#TRANSACTION_ID_LENGTH} bytes drawn from the agent's random
     * source.
     *
     * @return the transaction id
     */
    private byte[] newTransactionId() {
        var id = new byte[WaRelayPacket.TRANSACTION_ID_LENGTH];
        random.nextBytes(id);
        return id;
    }

    /**
     * Hex-encodes a transaction id to its lowercase lookup key for the in-flight map.
     *
     * @param bytes the transaction id bytes
     * @return the lowercase hex string
     */
    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Gathers host candidates from every routable IPv4 or IPv6 address on every up, non-loopback
     * network interface.
     *
     * <p>This is the basic gathering pass: link-local and multicast addresses are skipped, and each
     * surviving address becomes a {@link IceCandidateType#HOST} candidate served on {@code port}.
     * The agent does not bind sockets, so {@code port} is the port the call layer has bound; higher
     * layers add relay and server-reflexive candidates as they are discovered. The foundation of
     * each candidate combines the interface name with the address family.
     *
     * @param component the RTP or RTCP component these candidates serve
     * @param port      the local UDP port the candidates are served on
     * @return the gathered host candidates
     * @throws WhatsAppCallException.Ice if {@link NetworkInterface#getNetworkInterfaces()} throws
     */
    public static List<IceCandidate> gatherLocalHostCandidates(IceComponent component, int port) {
        try {
            var out = new ArrayList<IceCandidate>();
            var nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                var nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                var addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var addr = addrs.nextElement();
                    if (addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
                        continue;
                    }
                    var sock = new InetSocketAddress(addr, port);
                    var foundation = nic.getName() + ":" + (addr instanceof Inet4Address ? "4" : "6");
                    out.add(IceCandidate.host(component, sock, foundation));
                }
            }
            return out;
        } catch (SocketException e) {
            throw new WhatsAppCallException.Ice("failed to enumerate local network interfaces", e);
        }
    }
}
