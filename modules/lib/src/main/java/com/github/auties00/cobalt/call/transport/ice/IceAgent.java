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
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The driver for RFC 8445 ICE — gathers local candidates, ingests
 * remote candidates from signaling, forms candidate pairs, runs STUN
 * binding-request connectivity checks via the
 * {@link com.github.auties00.cobalt.call.wasm.relay relay protocol
 * primitives}, and nominates the highest-priority succeeded pair.
 *
 * <p>The agent does not own a UDP socket directly. Outbound STUN
 * packets are handed to the {@link Consumer} supplied at
 * construction (typically wired to a
 * {@link java.nio.channels.DatagramChannel} send loop), and inbound
 * packets are delivered via {@link #handleInboundStun(byte[])}. This
 * separation keeps the agent unit-testable without real network I/O
 * and lets the call layer multiplex DTLS / SRTP / STUN on a single
 * socket.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>{@link #addLocalCandidate} for each candidate the gathering
 *       phase produces (host candidates from
 *       {@link #gatherLocalHostCandidates}, relay candidates from
 *       a TURN Allocate, etc.).</li>
 *   <li>{@link #addRemoteCandidate} for each candidate the peer
 *       advertised through call signaling.</li>
 *   <li>{@link #start()} — forms pairs, transitions the highest-foundation
 *       pair to {@link IceCheckState#WAITING}, and fires the first
 *       check via the outbound sink.</li>
 *   <li>{@link #handleInboundStun} on each received STUN packet —
 *       either an inbound binding response (closes our in-flight
 *       check) or an inbound binding request (we respond with a
 *       success).</li>
 *   <li>The agent calls {@link Listener#onNominated} once a pair
 *       reaches {@link IceCheckState#SUCCEEDED} and is the highest
 *       priority of all succeeded pairs.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 *
 * <p>The agent's mutating operations (add candidate, start, handle
 * inbound) are intended to be driven from a single thread (typically
 * the call's transport scheduler). The outbound {@link Consumer}
 * fires synchronously on the calling thread. {@link Listener#onNominated}
 * also fires on the calling thread.
 */
public final class IceAgent {
    /**
     * Default per-check timeout — enough for a roundtrip to the
     * relay plus retransmits.
     */
    private static final Duration CHECK_TIMEOUT = Duration.ofMillis(500);

    /**
     * Outbound packet sink — the agent writes encoded STUN packets
     * here, pairing each with the destination address via the per-pair
     * routing map. The actual datagram-channel send is the caller's
     * responsibility.
     */
    private final OutboundSink outboundSink;

    /**
     * The local + remote ufrag/password pairs.
     */
    private final IceCredentials credentials;

    /**
     * Whether the local agent is in the controlling role per
     * RFC 8445 §6.1.1.
     */
    private final boolean controlling;

    /**
     * Wall-clock source — defaults to {@link Clock#systemUTC()}.
     */
    private final Clock clock;

    /**
     * Random source for transaction ids and ufrags.
     */
    private final SecureRandom random;

    /**
     * Local candidates accumulated by {@link #addLocalCandidate}.
     */
    private final List<IceCandidate> localCandidates = new CopyOnWriteArrayList<>();

    /**
     * Remote candidates accumulated by {@link #addRemoteCandidate}.
     */
    private final List<IceCandidate> remoteCandidates = new CopyOnWriteArrayList<>();

    /**
     * The current pair check list — populated from the cartesian
     * product of local × remote candidates by {@link #buildCheckList}.
     */
    private final List<IceCandidatePair> checkList = new CopyOnWriteArrayList<>();

    /**
     * In-flight pair lookup keyed by the hex-encoded transaction id
     * of the binding request — used to find the right pair when a
     * binding response arrives.
     */
    private final ConcurrentHashMap<String, IceCandidatePair> inFlight = new ConcurrentHashMap<>();

    /**
     * Application listener, fired once the agent has a nominated
     * pair.
     */
    private volatile Listener listener;

    /**
     * Whether {@link #start()} has been called.
     */
    private volatile boolean started;

    /**
     * The currently-nominated pair, set once we observe a SUCCEEDED
     * pair we want to commit to.
     */
    private volatile IceCandidatePair nominatedPair;

    /**
     * Functional interface for outbound STUN packets. The agent
     * passes the encoded packet bytes plus the destination address
     * (the candidate-pair's remote transport address) so the caller
     * can route to the right peer over a multiplexed UDP socket.
     */
    @FunctionalInterface
    public interface OutboundSink {
        /**
         * Sends one STUN packet to the given destination.
         *
         * @param packet the encoded STUN packet
         * @param destination the destination address
         */
        void send(byte[] packet, InetSocketAddress destination);
    }

    /**
     * Listener for agent-driven events.
     */
    public interface Listener {
        /**
         * Called once the agent has nominated a pair — the
         * connection is now ready for the next layer (DTLS-SRTP).
         *
         * @param pair the nominated pair
         */
        default void onNominated(IceCandidatePair pair) {
        }

        /**
         * Called when the agent observes a successful connectivity
         * check on a pair (before any nomination).
         *
         * @param pair the pair that succeeded
         */
        default void onCheckSucceeded(IceCandidatePair pair) {
        }

        /**
         * Called when a connectivity check fails (timeout or error
         * response).
         *
         * @param pair the pair that failed
         */
        default void onCheckFailed(IceCandidatePair pair) {
        }
    }

    /**
     * Constructs a new ICE agent.
     *
     * @param controlling   whether the local agent is in the
     *                      controlling role per RFC 8445 §6.1.1
     * @param credentials   local and remote ufrag/password pair
     * @param outboundSink  outbound STUN packet sink
     * @throws NullPointerException if any required argument is
     *                              {@code null}
     */
    public IceAgent(boolean controlling, IceCredentials credentials, OutboundSink outboundSink) {
        this(controlling, credentials, outboundSink, Clock.systemUTC(), new SecureRandom());
    }

    /**
     * Test-friendly constructor with overrideable clock + random
     * sources.
     *
     * @param controlling   whether the local agent is controlling
     * @param credentials   credentials
     * @param outboundSink  outbound sink
     * @param clock         clock for {@link IceCandidatePair#lastCheckSent()}
     * @param random        random source for transaction ids
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
     * Registers an application listener.
     *
     * @param listener the listener; may be {@code null} to clear
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Returns whether the local agent is controlling.
     *
     * @return {@code true} for controlling
     */
    public boolean controlling() {
        return controlling;
    }

    /**
     * Returns an unmodifiable snapshot of the current local
     * candidate set.
     *
     * @return the local candidates
     */
    public List<IceCandidate> localCandidates() {
        return List.copyOf(localCandidates);
    }

    /**
     * Returns an unmodifiable snapshot of the remote candidate set.
     *
     * @return the remote candidates
     */
    public List<IceCandidate> remoteCandidates() {
        return List.copyOf(remoteCandidates);
    }

    /**
     * Returns the agent's check list — pairs sorted by descending
     * priority.
     *
     * @return the check list
     */
    public List<IceCandidatePair> checkList() {
        return List.copyOf(checkList);
    }

    /**
     * Returns the currently-nominated pair, or {@code null} if no
     * pair has been nominated yet.
     *
     * @return the nominated pair, or {@code null}
     */
    public IceCandidatePair nominatedPair() {
        return nominatedPair;
    }

    /**
     * Adds a local candidate. May be called repeatedly during the
     * gathering phase.
     *
     * @param candidate the candidate
     * @throws NullPointerException if {@code candidate} is
     *                              {@code null}
     * @throws IllegalStateException if {@link #start()} has already
     *                               been called
     */
    public void addLocalCandidate(IceCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (started) {
            throw new IllegalStateException("agent already started");
        }
        localCandidates.add(candidate);
    }

    /**
     * Adds a remote candidate received from signaling.
     *
     * @param candidate the candidate
     * @throws NullPointerException if {@code candidate} is
     *                              {@code null}
     * @throws IllegalStateException if {@link #start()} has already
     *                               been called
     */
    public void addRemoteCandidate(IceCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (started) {
            throw new IllegalStateException("agent already started");
        }
        remoteCandidates.add(candidate);
    }

    /**
     * Starts the agent: forms the check list, transitions the first
     * pair (and any other foundation-unique pairs) to
     * {@link IceCheckState#WAITING}, and sends the first connectivity
     * check.
     *
     * @throws IllegalStateException if there are no candidate pairs
     *                               to check
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
     * Drives a single tick — checks for timed-out in-flight requests
     * and fires the next {@link IceCheckState#WAITING} pair if any.
     * Callers should invoke this at a steady cadence (typically every
     * 50 ms) until the agent reports a nominated pair.
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
    }

    /**
     * Routes one inbound STUN packet through the agent. Determines
     * whether it's a binding response (closes our in-flight check)
     * or a binding request (we respond with a success), and updates
     * the relevant pair's state.
     *
     * @param packet the inbound STUN packet
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
        // Error responses, allocate response, etc. are handled by
        // the relay layer above us — silently dropped here.
    }

    /**
     * Forms candidate pairs from the cartesian product of local ×
     * remote and sorts by descending priority.
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
     * Returns whether two transport addresses can plausibly carry a
     * pair — same address family.
     *
     * @param a one address
     * @param b another address
     * @return {@code true} if compatible
     */
    private static boolean isCompatible(InetSocketAddress a, InetSocketAddress b) {
        boolean aIp4 = a.getAddress() instanceof Inet4Address;
        boolean bIp4 = b.getAddress() instanceof Inet4Address;
        if (aIp4 != bIp4) {
            return false;
        }
        boolean aIp6 = a.getAddress() instanceof Inet6Address;
        boolean bIp6 = b.getAddress() instanceof Inet6Address;
        return aIp6 == bIp6;
    }

    /**
     * Implements RFC 8445 §6.1.2.6 — the pair with the lowest
     * component-id and highest priority for each foundation enters
     * the {@link IceCheckState#WAITING} state.
     */
    private void unfreezeFoundations() {
        var seenFoundations = new java.util.HashSet<String>();
        for (var pair : checkList) {
            var foundation = pairFoundation(pair);
            if (seenFoundations.add(foundation)) {
                pair.transition(IceCheckState.FROZEN, IceCheckState.WAITING);
            }
        }
    }

    /**
     * Returns the foundation key for the pair — concatenation of
     * local and remote candidate foundations, used to group pairs
     * for unfreezing.
     *
     * @param pair the pair
     * @return the foundation key
     */
    private static String pairFoundation(IceCandidatePair pair) {
        return pair.local().foundation() + ":" + pair.remote().foundation();
    }

    /**
     * Fires the next {@link IceCheckState#WAITING} pair, in priority
     * order. Returns once one has been transitioned to
     * {@link IceCheckState#IN_PROGRESS}, or immediately if none are
     * waiting.
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
     * Builds and sends a STUN binding request for the given pair.
     *
     * @param pair the pair to check
     */
    private void fireBindingRequest(IceCandidatePair pair) {
        var txId = newTransactionId();
        var attrs = new ArrayList<WaRelayAttribute>();
        attrs.add(new WaRelayAttribute(
                /* USERNAME = 0x0006 per RFC 5389 §15.3 */ 0x0006,
                credentials.outboundUsername().getBytes(StandardCharsets.UTF_8)));
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
     * Handles a STUN binding success response — closes the matching
     * in-flight check.
     *
     * @param packet  the parsed response
     * @param verifyMi whether to verify the MESSAGE-INTEGRITY
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
     * Handles a STUN binding request from the peer — by RFC 8445
     * §7.3.1 we always respond with a binding success once the
     * USERNAME and MESSAGE-INTEGRITY check out. Triggered checks
     * (RFC 8445 §7.3.1.4) that imply a previously-frozen pair should
     * be unfrozen are not yet implemented here; this is enough for
     * full-candidate-set ICE.
     *
     * @param packet the parsed request
     */
    private void handleBindingRequest(WaRelayPacket packet) {
        // Verify against the LOCAL password since the peer used it
        // when stamping the request (their "remotePassword" is our
        // "localPassword").
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
        // The peer's address is unknown to us at this layer (we
        // don't carry inbound source addresses through
        // {@link #handleInboundStun}); the call layer is expected to
        // pair the request with the source address it observed and
        // route the response back.
        // For now, drop the response onto the same sink with a null
        // address — the call layer's pump can stamp the source.
        try {
            outboundSink.send(encoded, null);
        } catch (RuntimeException _) {
        }
    }

    /**
     * Verifies MESSAGE-INTEGRITY using the configured remote
     * password.
     *
     * @param packet the parsed packet (must already have been
     *               re-encodable)
     * @return {@code true} if the integrity check passes
     */
    private boolean verifyMessageIntegrity(WaRelayPacket packet) {
        return verifyMessageIntegrity(packet, credentials.remotePassword());
    }

    /**
     * Verifies MESSAGE-INTEGRITY against an arbitrary key.
     *
     * @param packet the parsed packet
     * @param key    the HMAC key
     * @return {@code true} if integrity matches
     */
    private static boolean verifyMessageIntegrity(WaRelayPacket packet, byte[] key) {
        try {
            return WaRelayMessageIntegrity.verify(packet.encode(), key);
        } catch (RuntimeException _) {
            return false;
        }
    }

    /**
     * Marks a pair as failed and notifies the listener.
     *
     * @param pair the pair
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
     * Considers a freshly-succeeded pair for nomination. The
     * controlling agent nominates the highest-priority succeeded
     * pair; the controlled agent waits for the controlling agent's
     * USE-CANDIDATE flag (which we don't yet emit on outbound
     * checks; only the controlling agent reaches this branch with
     * USE-CANDIDATE today).
     *
     * @param pair the pair
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
     * Generates a fresh 12-byte transaction id for a STUN request.
     *
     * @return the transaction id
     */
    private byte[] newTransactionId() {
        var id = new byte[WaRelayPacket.TRANSACTION_ID_LENGTH];
        random.nextBytes(id);
        return id;
    }

    /**
     * Hex-encodes a transaction id to its lookup key.
     *
     * @param bytes the bytes
     * @return the hex string
     */
    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Returns a list of HOST candidates derived from every site-local
     * or global IPv4 address on every up, non-loopback network
     * interface — the basic gathering pass. Higher layers can add
     * relay / server-reflexive candidates as they're discovered.
     *
     * @param component the RTP/RTCP component these candidates serve
     * @param port      the local UDP port the candidates will be
     *                  served on (the agent does not bind sockets;
     *                  the call layer does)
     * @return the host candidates
     * @throws WhatsAppCallException.Ice if {@link NetworkInterface#getNetworkInterfaces}
     *                      throws
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
