package com.github.auties00.cobalt.calls2.core;

import com.github.auties00.cobalt.calls2.signaling.CallMessage;
import com.github.auties00.cobalt.calls2.signaling.Calls2SignalingType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Validates, de-duplicates, and classifies a decoded inbound call signaling message, then resolves the
 * call context it must be routed to.
 *
 * <p>This is the engine-side message router that runs after the signaling layer has decoded a
 * {@code <call>} child element into a typed {@link CallMessage}: where the signaling-layer classifier
 * gates on the raw envelope (header presence, LID addressing, whether the call exists yet), this router
 * applies the finer per-message decisions the engine makes against live call state. It re-checks the
 * LID-only addressing rule on the decoded message, de-duplicates a replayed message by its
 * {@code (type, call-id, transaction-id)} key, distinguishes an offer that re-rings an existing call from
 * a fresh offer, suppresses signaling that arrives for a call the local user already rejected, and routes
 * an accept onto the accept-handling path; every other well-formed message is routed for normal
 * processing. The verdict is one of the {@link RoutingClass} values, six of them matching the engine's
 * routing-class return codes one-to-one and a seventh ({@link RoutingClass#BUFFER_PENDING}) folding in the
 * native pending-call message-queue capture, paired with the resolved call context the receiver dispatches
 * the message against.
 *
 * <p>The router is parameterised on the call-context handle type {@code <C>} and resolves a context from
 * a call identifier through a caller-supplied {@link Function locator}, so it depends on no concrete
 * context or manager type and stays a pure classifier: the integrator binds {@code <C>} to the engine's
 * call-context type and supplies the call manager's by-call-id lookup as the locator. The router holds no
 * per-call state itself; the de-duplication state it needs is supplied per call through the
 * {@link DedupState} the caller threads in and out, so the same router instance classifies every inbound
 * call regardless of which calls are active.
 *
 * <p>The class is stateless and therefore thread-safe; concurrency of the per-call de-duplication state
 * is the caller's responsibility, matching the engine's single serial message-router queue per call.
 *
 * @param <C> the call-context handle type the {@link Function locator} resolves and the {@link Verdict}
 *            carries; bound by the integrator to the engine's call-context type
 * @implNote This implementation ports {@code message_router} (fn11497) from the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code core/call_state.cc}). The native router resolves the active call context
 * (fn10929), validates the sender and peer JIDs (fn11498/fn11499) and drops a non-LID stanza
 * ({@code "dropping non-LID stanza"}), computes a per-type de-duplication key over the message buffers
 * (fn11480/fn11481) and classifies a duplicate as ignore, classifies an offer whose seen-id or relay
 * changed as a re-ring (so the offer ack is re-sent or buffered), confirms a transport or accept message
 * matches the call-id and is not handled elsewhere, and returns one of the routing classes
 * {@code 0=process, 1=drop-unknown, 2=ignore, 3=offer-rering, 4=ignore-while-rejected, 5=accept-handle}
 * to {@code wa_call_handle_incoming_signaling_xmpp_msg} (fn10724) which then invokes the per-type handler.
 * This port reads the universal {@code call-id} and {@code call-creator} header and the optional
 * {@code transaction-id} attribute off the decoded message's rendered {@link Stanza} rather than off a flat
 * C struct, because every {@link CallMessage} stamps the universal header through its serializer; the
 * first six {@link RoutingClass} values are the native routing-class return codes one-to-one, and the
 * added {@link RoutingClass#BUFFER_PENDING} routes the busy/lobby pending-call capture (native
 * {@code fn10961}) the native engine performs off the drop-unknown path rather than as a router return.
 */
public final class Calls2IncomingMessageRouter<C> {
    /**
     * The wire attribute naming the call identifier on a {@code <call>} child element.
     */
    private static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the call creator's device JID on a {@code <call>} child element.
     */
    private static final String CALL_CREATOR_ATTRIBUTE = "call-creator";

    /**
     * The wire attribute naming the rotation or message transaction id on a {@code <call>} child element.
     */
    private static final String TRANSACTION_ID_ATTRIBUTE = "transaction-id";

    /**
     * The sentinel transaction id meaning a message carries no transaction id.
     */
    private static final int NO_TRANSACTION_ID = -1;

    /**
     * Classifies how the receiver must route a decoded inbound call message against live call state.
     *
     * <p>The classes are the engine's routing-class return codes one-to-one: a well-formed message routed
     * for normal per-type handling, a message dropped because it names no known sender or call, a
     * duplicate or stale message ignored, an offer that re-rings an existing call, signaling ignored
     * because the local user already rejected the call, and an accept routed onto the accept-handling
     * path.
     */
    public enum RoutingClass {
        /**
         * Routes a well-formed, non-duplicate message to the engine's per-type handler.
         *
         * @implNote This implementation binds native routing class {@code 0} ({@code process}).
         */
        PROCESS,

        /**
         * Drops a message that names no resolvable call context or whose sender fails validation.
         *
         * @implNote This implementation binds native routing class {@code 1} ({@code drop-unknown}).
         */
        DROP,

        /**
         * Ignores a message whose {@code (type, call-id, transaction-id)} key duplicates one already seen
         * or whose transaction id is stale.
         *
         * @implNote This implementation binds native routing class {@code 2} ({@code ignore}).
         */
        IGNORE,

        /**
         * Routes an offer that re-rings an already-known call so the offer acknowledgement is re-sent
         * rather than the call re-created.
         *
         * @implNote This implementation binds native routing class {@code 3} ({@code offer-rering}).
         */
        OFFER_RERING,

        /**
         * Ignores signaling that arrives for a call the local user has already rejected.
         *
         * @implNote This implementation binds native routing class {@code 4} ({@code ignore-while-rejected}).
         */
        IGNORE_REJECTED,

        /**
         * Routes an accept message onto the accept-handling path that brings up transport and media.
         *
         * @implNote This implementation binds native routing class {@code 5} ({@code accept-handle}).
         */
        ACCEPT_HANDLE,

        /**
         * Buffers a non-offer message that names the busy/lobby pending call held out of the active-calls
         * map, so it is queued for replay when the local user joins rather than dropped as unknown.
         *
         * <p>This class is reached only for a message whose {@code call-id} matches the buffered pending
         * call and that would otherwise be a {@link #DROP} (no active call context resolves for it). The
         * caller appends the message to the pending call's queue instead of dispatching it.
         *
         * @implNote This implementation has no native {@code message_router} (fn11497) return code of its
         * own: the native router still classifies the message as {@code 1} ({@code drop-unknown}) against
         * the active-calls map, and it is the pending-call message-queue fill ({@code fn10961}, the queue
         * filled by {@code handle_pending_call_offer}, fn10959) that captures the message off the busy path
         * instead. Cobalt folds that pending-queue capture into a distinct routing class so the single
         * inbound seam consults the pending holder before honouring the {@link #DROP}.
         */
        BUFFER_PENDING
    }

    /**
     * Holds the result of routing a decoded inbound call message: the routing class and the resolved call
     * context.
     *
     * <p>A {@link RoutingClass#PROCESS}, {@link RoutingClass#OFFER_RERING}, or
     * {@link RoutingClass#ACCEPT_HANDLE} verdict carries the resolved context the receiver dispatches the
     * message against; a {@link RoutingClass#DROP}, {@link RoutingClass#IGNORE},
     * {@link RoutingClass#IGNORE_REJECTED}, or {@link RoutingClass#BUFFER_PENDING} verdict carries an empty
     * context because the message is not dispatched against an active call. Callers branch on
     * {@link #routingClass()} first and read {@link #context()} only for the dispatched classes.
     *
     * @param <C>          the call-context handle type
     * @param routingClass the routing decision; never {@code null}
     * @param context      the resolved call context for a dispatched verdict, empty otherwise; never
     *                     {@code null}
     */
    public record Verdict<C>(RoutingClass routingClass, Optional<C> context) {
        /**
         * Canonicalizes the verdict.
         *
         * @throws NullPointerException if {@code routingClass} or {@code context} is {@code null}
         */
        public Verdict {
            Objects.requireNonNull(routingClass, "routingClass cannot be null");
            Objects.requireNonNull(context, "context cannot be null");
        }
    }

    /**
     * Carries the per-call de-duplication state the router reads and the caller updates.
     *
     * <p>The router de-duplicates by the latest transaction id it has seen for a call and by whether the
     * local user has rejected the call. The caller holds one instance per call (on the call context), and
     * threads it into {@link Calls2IncomingMessageRouter#route(CallMessage, Jid, DedupState, Function)};
     * the router reports a verdict and the caller advances the state for a processed message through
     * {@link #withTransactionId(int)} or {@link #markRejected()}. The state is an immutable record so it can
     * be published safely; the caller swaps the reference rather than mutating in place.
     *
     * @param latestTransactionId the highest transaction id processed for the call, or {@code -1} when
     *                            none has been processed
     * @param rejected            whether the local user has rejected the call, so later signaling is
     *                            ignored
     */
    public record DedupState(int latestTransactionId, boolean rejected) {
        /**
         * The initial de-duplication state for a call with no processed transaction id and not rejected.
         */
        public static final DedupState INITIAL = new DedupState(NO_TRANSACTION_ID, false);

        /**
         * Returns a copy of this state with the latest transaction id advanced to the larger of the
         * current value and the supplied one.
         *
         * <p>Advancing keeps the newest transaction id so an out-of-order older message cannot lower the
         * recorded value; a transaction id at or below the current value leaves the state unchanged.
         *
         * @param transactionId the transaction id of a just-processed message
         * @return a state carrying the advanced transaction id and this state's rejected flag
         */
        public DedupState withTransactionId(int transactionId) {
            return transactionId > latestTransactionId
                    ? new DedupState(transactionId, rejected)
                    : this;
        }

        /**
         * Returns a copy of this state marked as rejected so later signaling for the call is ignored.
         *
         * @return a state carrying this state's latest transaction id and a set rejected flag
         */
        public DedupState markRejected() {
            return new DedupState(latestTransactionId, true);
        }
    }

    /**
     * Routes a decoded inbound call message against live call state to a routing class and call context.
     *
     * <p>Validates the universal header and LID addressing, resolves the call context through the
     * supplied locator, and applies the per-message routing decisions:
     * <ul>
     *   <li>a message with no non-empty {@code call-id} or no {@code call-creator}, or one that is not
     *       LID-addressed, yields {@link RoutingClass#DROP} with no context;</li>
     *   <li>a message whose call context the locator does not resolve yields {@link RoutingClass#DROP}
     *       with no context, except an {@link Calls2SignalingType#OFFER offer}, which is allowed through
     *       as {@link RoutingClass#PROCESS} so the lifecycle layer can create the call, and a non-offer
     *       message whose {@code call-id} names the buffered busy/lobby pending call, which yields
     *       {@link RoutingClass#BUFFER_PENDING} with no context so the caller queues it for join-time
     *       replay instead of dropping it;</li>
     *   <li>a message for a call the local user has rejected yields {@link RoutingClass#IGNORE_REJECTED}
     *       with no context;</li>
     *   <li>a message whose transaction id is stale (strictly less than the latest processed for the
     *       call) yields {@link RoutingClass#IGNORE} with no context;</li>
     *   <li>an {@link Calls2SignalingType#OFFER offer} for a call that already exists yields
     *       {@link RoutingClass#OFFER_RERING} with the resolved context;</li>
     *   <li>an {@link Calls2SignalingType#ACCEPT accept} yields {@link RoutingClass#ACCEPT_HANDLE} with
     *       the resolved context;</li>
     *   <li>every other message for an existing call yields {@link RoutingClass#PROCESS} with the resolved
     *       context.</li>
     * </ul>
     * The router does not advance the {@link DedupState}; the caller advances it for a routed message
     * through {@link DedupState#withTransactionId(int)} once it decides to process the message.
     *
     * @param message   the decoded inbound call message; must not be {@code null}
     * @param senderLid the {@code sender_lid} attribute from the {@code <call>} envelope, or {@code null}
     *                  when the stanza is not LID-addressed
     * @param dedup     the per-call de-duplication state; must not be {@code null}
     * @param locator   resolves a call identifier to its call context, returning {@code null} when no
     *                  context exists; must not be {@code null}
     * @return the routing verdict; never {@code null}
     * @throws NullPointerException if {@code message}, {@code dedup}, or {@code locator} is {@code null}
     */
    public Verdict<C> route(CallMessage message, Jid senderLid, DedupState dedup, Function<String, C> locator) {
        return route(message, senderLid, dedup, locator, callId -> false);
    }

    /**
     * Routes a decoded inbound call message against live call state and a buffered pending call to a
     * routing class and call context.
     *
     * <p>Behaves exactly like {@link #route(CallMessage, Jid, DedupState, Function)}, with one added
     * decision: before dropping a non-offer message that resolves no active call context, the router
     * consults {@code pendingCall} to learn whether the message's {@code call-id} names the buffered
     * busy/lobby pending call. When it does, the router returns {@link RoutingClass#BUFFER_PENDING} with no
     * context so the caller queues the message for join-time replay instead of dropping it as unknown; when
     * it does not, the message is dropped as before. An offer, a message for an existing call, a rejected
     * call, and a stale transaction id are all decided before this consult and are unaffected by it.
     *
     * @param message     the decoded inbound call message; must not be {@code null}
     * @param senderLid   the {@code sender_lid} attribute from the {@code <call>} envelope, or {@code null}
     *                    when the stanza is not LID-addressed
     * @param dedup       the per-call de-duplication state; must not be {@code null}
     * @param locator     resolves a call identifier to its call context, returning {@code null} when no
     *                    context exists; must not be {@code null}
     * @param pendingCall reports whether a call identifier names the buffered busy/lobby pending call; must
     *                    not be {@code null}
     * @return the routing verdict; never {@code null}
     * @throws NullPointerException if {@code message}, {@code dedup}, {@code locator}, or {@code pendingCall}
     *                              is {@code null}
     */
    public Verdict<C> route(CallMessage message, Jid senderLid, DedupState dedup, Function<String, C> locator,
                            Predicate<String> pendingCall) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(dedup, "dedup cannot be null");
        Objects.requireNonNull(locator, "locator cannot be null");
        Objects.requireNonNull(pendingCall, "pendingCall cannot be null");

        // TODO: read the header fields off the decoded record instead of rebuilding toStanza() here (and
        //  again in Calls2LifecycleController.handleIncomingMessage/advanceDedup). This would add a
        //  transactionId() default (returning -1) on CallMessage overridden by Rekey/RelayLatency/Terminate/
        //  FlowControl, and use message.callId()/callCreator()/transactionId() record accessors, dropping the
        //  OfferStanza codec/capability subtree rebuild per inbound packet. Blocked here because callId() and
        //  callCreator() are not on the sealed CallMessage interface, and adding them would force every
        //  permitted type outside this owned set (Destination/GroupInfo/Heartbeat/InCallAction/Link*/Reject/
        //  Ringing/Transport/WaitingRoom*) to implement them, so toStanza() stays the only header source.
        // TODO (item 3): thread this route()'s Verdict.context() orchestrated handle through dispatchInbound
        //  and the per-type handlers so they stop re-running calls.get(callId), and resolve+lock once in
        //  transition()/tearDown() via the context-taking Calls2CallStateMachine.transition(context, state)
        //  overload. Cross-cuts Calls2LifecycleController's dispatch/transition/teardown seams (outside this
        //  file); deferred until those seams are widened together so the dispatch/emit decisions stay identical.
        var node = message.toStanza();
        var callId = node.getAttributeAsString(CALL_ID_ATTRIBUTE, null);
        var callCreator = node.getAttributeAsJid(CALL_CREATOR_ATTRIBUTE, null);
        if (callId == null || callId.isEmpty() || callCreator == null || !isLidAddressed(senderLid, callCreator)) {
            return drop();
        }

        var type = message.type();
        var isOffer = type == Calls2SignalingType.OFFER;
        var context = locator.apply(callId);
        if (context == null) {
            if (isOffer) {
                return new Verdict<>(RoutingClass.PROCESS, Optional.empty());
            }
            return pendingCall.test(callId)
                    ? new Verdict<>(RoutingClass.BUFFER_PENDING, Optional.empty())
                    : drop();
        }

        if (dedup.rejected()) {
            return new Verdict<>(RoutingClass.IGNORE_REJECTED, Optional.empty());
        }

        var transactionId = transactionId(node);
        if (transactionId != NO_TRANSACTION_ID && transactionId < dedup.latestTransactionId()) {
            return new Verdict<>(RoutingClass.IGNORE, Optional.empty());
        }

        if (isOffer) {
            return new Verdict<>(RoutingClass.OFFER_RERING, Optional.of(context));
        }
        if (type == Calls2SignalingType.ACCEPT) {
            return new Verdict<>(RoutingClass.ACCEPT_HANDLE, Optional.of(context));
        }
        return new Verdict<>(RoutingClass.PROCESS, Optional.of(context));
    }

    /**
     * Returns a drop verdict carrying no context.
     *
     * @return a {@link RoutingClass#DROP} verdict with an empty context
     */
    private Verdict<C> drop() {
        return new Verdict<>(RoutingClass.DROP, Optional.empty());
    }

    /**
     * Reads the transaction id from a decoded message's rendered stanza, or the no-transaction sentinel.
     *
     * <p>A message that carries a {@code transaction-id} attribute (the rekey, relay-latency, terminate,
     * and flow-control legs) returns its parsed value; a message with no such attribute returns
     * {@link #NO_TRANSACTION_ID}, which the router treats as never stale.
     *
     * @param stanza the decoded message's rendered stanza
     * @return the parsed transaction id, or {@link #NO_TRANSACTION_ID} when absent
     */
    private static int transactionId(Stanza stanza) {
        return stanza.getAttributeAsInt(TRANSACTION_ID_ATTRIBUTE, NO_TRANSACTION_ID);
    }

    /**
     * Returns whether an inbound call message is LID-addressed.
     *
     * <p>Reproduces the engine's pre-dispatch LID-only guard on the decoded message: a stanza carrying a
     * {@code sender_lid} on the envelope is LID-addressed by definition, otherwise the call creator's
     * server is inspected. A message that is neither is dropped before it reaches a call.
     *
     * @param senderLid   the {@code sender_lid} attribute from the {@code <call>} envelope, or
     *                    {@code null} when absent
     * @param callCreator the {@code call-creator} device JID from the message
     * @return {@code true} when the message is LID-addressed, {@code false} otherwise
     */
    private boolean isLidAddressed(Jid senderLid, Jid callCreator) {
        return senderLid != null || callCreator.hasLidServer();
    }
}
