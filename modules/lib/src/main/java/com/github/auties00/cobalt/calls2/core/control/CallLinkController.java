package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.calls2.core.Calls2CallLinkState;
import com.github.auties00.cobalt.calls2.signaling.LinkCreateAck;
import com.github.auties00.cobalt.calls2.signaling.LinkCreateStanza;
import com.github.auties00.cobalt.calls2.signaling.LinkEditAck;
import com.github.auties00.cobalt.calls2.signaling.LinkEditStanza;
import com.github.auties00.cobalt.calls2.signaling.LinkJoinAck;
import com.github.auties00.cobalt.calls2.signaling.LinkJoinStanza;
import com.github.auties00.cobalt.calls2.signaling.LinkQueryAck;
import com.github.auties00.cobalt.calls2.signaling.LinkQueryStanza;
import com.github.auties00.cobalt.model.call.CallLinkMedia;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Drives the call-link control: previewing, editing, and joining a call through a link token.
 *
 * <p>This controller owns the call-link query-and-join handshake and the link sub-state it advances
 * through. {@link #query(String, CallLinkMedia, CallLinkQueryAction)} resolves a link token to its
 * metadata, moving the sub-state from {@link Calls2CallLinkState#LINK_QUERY_SENT} to
 * {@link Calls2CallLinkState#LINK_QUERY_ACKED} and emitting the resolved link as a {@link LinkQueryAcked};
 * {@link #preview(String, CallLinkMedia)} and {@link #getInfo(String, CallLinkMedia)} are the
 * preview-action and edit-action conveniences over it. {@link #join(String, int)} requests admission,
 * moving the sub-state from {@link Calls2CallLinkState#LINK_JOIN_SENT} to
 * {@link Calls2CallLinkState#LINK_JOIN_ACKED}. {@link #edit(String, boolean)} changes a link's
 * waiting-room setting. Every sub-state change emits a {@link CallLinkStateChanged}.
 *
 * <p>The link control plane rides request-reply IQs addressed to the {@code call} service, so the
 * controller dispatches through the blocking {@link CallLinkIqSender} and parses the typed ack rather than
 * the fire-and-forget signaling seam. The media-negotiation payload a real join also attaches (audio
 * capabilities, the capability version, the end-to-end key, the video codec capability) is owned by the
 * offer and crypto units and is not built here. The link sub-state is held behind a lock; the controller
 * owns no timers.
 *
 * @implNote This implementation reproduces the call-link control of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc}, {@code messages/senders}): the query is
 * IQ type {@code 0x84} with the {@code action} verb {@code preview} or {@code link_edit} (message type
 * {@code 31}, ack {@code 32}); the join is {@code make_and_send_link_join} (fn11454, type {@code 0x21},
 * ack {@code 34}); the edit is type {@code 55} (ack {@code 56}). The sub-state advances through the
 * five-value {@link Calls2CallLinkState} table (data offset {@code 0x1284fc}) and each change emits event
 * {@code 0x6a} ({@code CallLinkStateChanged}); the resolved query emits event {@code 0xa2}
 * ({@code LinkQueryAcked}). The info-mutex is replaced by a {@link ReentrantLock} per the threading
 * design.
 */
public final class CallLinkController {
    /**
     * The request-reply egress link IQs are dispatched through.
     */
    private final CallLinkIqSender iqSender;

    /**
     * The event sink link events are emitted into.
     */
    private final CallEventSink events;

    /**
     * Guards the link sub-state.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The current call-link join sub-state.
     */
    private Calls2CallLinkState linkState = Calls2CallLinkState.NONE;

    /**
     * Constructs a call-link controller bound to its IQ sender and event sink.
     *
     * @param iqSender the request-reply egress to dispatch link IQs through; never {@code null}
     * @param events   the event sink to emit link events into; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public CallLinkController(CallLinkIqSender iqSender, CallEventSink events) {
        this.iqSender = Objects.requireNonNull(iqSender, "iqSender cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
    }

    /**
     * Resolves a call-link token to its metadata under the given query action.
     *
     * <p>Moves the sub-state to {@link Calls2CallLinkState#LINK_QUERY_SENT}, dispatches a
     * {@code link_query} request carrying the token, media, and action verb, parses the reply into a
     * {@link LinkQueryAck}, moves the sub-state to {@link Calls2CallLinkState#LINK_QUERY_ACKED}, and emits a
     * {@link LinkQueryAcked} with the resolved link. Each sub-state change emits a
     * {@link CallLinkStateChanged}.
     *
     * @param token  the call-link token to resolve; never {@code null}
     * @param media  the media kind the caller intends to use; never {@code null}
     * @param action the query action selecting a preview versus an edit lookup; never {@code null}
     * @return the resolved call-link metadata
     * @throws NullPointerException if any argument is {@code null}
     */
    public LinkQueryAck query(String token, CallLinkMedia media, CallLinkQueryAction action) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        setLinkState(Calls2CallLinkState.LINK_QUERY_SENT);
        var request = new LinkQueryStanza(token, media, Optional.of(action.wireValue()),
                Optional.empty(), Optional.empty());
        var reply = iqSender.sendForReply(request);
        var ack = LinkQueryAck.of(reply);
        setLinkState(Calls2CallLinkState.LINK_QUERY_ACKED);
        events.emit(new LinkQueryAcked(ack.link()));
        return ack;
    }

    /**
     * Mints a fresh shareable call-link token bound to a media kind and waiting-room setting.
     *
     * <p>Dispatches a {@code link_create} request carrying the media kind and the requested waiting-room
     * gate, parses the reply into a {@link LinkCreateAck}, and returns it; the ack surfaces the minted token
     * and composes to a {@link com.github.auties00.cobalt.model.call.CallLink} through
     * {@link LinkCreateAck#toCallLink()}. Unlike {@link #query(String, CallLinkMedia, CallLinkQueryAction)}
     * and {@link #join(String, int)}, creating a link mints a standalone token rather than advancing a call
     * through the query-and-join handshake, so it does not touch the {@link Calls2CallLinkState} join
     * sub-state and emits no {@link CallLinkStateChanged}; the minted link is delivered to the caller through
     * the return value.
     *
     * @implNote This implementation reproduces {@code serialize_link_create} (fn11673, message type
     * {@code 0xb8}) and its {@code deserialize_link_create_ack} (message type {@code 28}) reply in the
     * wa-voip WASM module {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc}); the five-value
     * {@link Calls2CallLinkState} table carries no create state, so create leaves the sub-state untouched.
     *
     * @param media              the media kind the link is created with; never {@code null}
     * @param waitingRoomEnabled {@code true} to request the link's waiting-room gate at creation time
     * @return the create acknowledgement carrying the minted token
     * @throws NullPointerException if {@code media} is {@code null}
     */
    public LinkCreateAck create(CallLinkMedia media, boolean waitingRoomEnabled) {
        Objects.requireNonNull(media, "media cannot be null");
        var request = LinkCreateStanza.of(media, waitingRoomEnabled);
        var reply = iqSender.sendForReply(request);
        return LinkCreateAck.of(reply);
    }

    /**
     * Resolves a call-link token for a passive preview before joining.
     *
     * <p>The {@link CallLinkQueryAction#PREVIEW} convenience over {@link #query(String, CallLinkMedia,
     * CallLinkQueryAction)}.
     *
     * @param token the call-link token to resolve; never {@code null}
     * @param media the media kind the caller intends to use; never {@code null}
     * @return the resolved call-link metadata
     * @throws NullPointerException if either argument is {@code null}
     */
    public LinkQueryAck preview(String token, CallLinkMedia media) {
        return query(token, media, CallLinkQueryAction.PREVIEW);
    }

    /**
     * Resolves a call-link token for an edit lookup by the link owner.
     *
     * <p>The {@link CallLinkQueryAction#LINK_EDIT} convenience over {@link #query(String, CallLinkMedia,
     * CallLinkQueryAction)}; this is the get-info lookup the owner issues before editing the link.
     *
     * @param token the call-link token to resolve; never {@code null}
     * @param media the media kind the caller intends to use; never {@code null}
     * @return the resolved call-link metadata
     * @throws NullPointerException if either argument is {@code null}
     */
    public LinkQueryAck getInfo(String token, CallLinkMedia media) {
        return query(token, media, CallLinkQueryAction.LINK_EDIT);
    }

    /**
     * Requests admission into the call a resolved call-link token points at.
     *
     * <p>Moves the sub-state to {@link Calls2CallLinkState#LINK_JOIN_SENT}, dispatches a {@code link_join}
     * request carrying the token and join-state leg, parses the reply into a {@link LinkJoinAck}, and moves
     * the sub-state to {@link Calls2CallLinkState#LINK_JOIN_ACKED}. Each sub-state change emits a
     * {@link CallLinkStateChanged}. The media-negotiation payload a join also requires is supplied by the
     * offer and crypto units, not here.
     *
     * @param token     the call-link token to join; never {@code null}
     * @param joinState the two-step join handshake leg ({@code 1} or {@code 2}), or a negative value to
     *                  omit it
     * @return the join acknowledgement, carrying the call creator and any waiting-room participants
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public LinkJoinAck join(String token, int joinState) {
        Objects.requireNonNull(token, "token cannot be null");
        setLinkState(Calls2CallLinkState.LINK_JOIN_SENT);
        var request = new LinkJoinStanza(token, joinState);
        var reply = iqSender.sendForReply(request);
        var ack = LinkJoinAck.of(reply);
        setLinkState(Calls2CallLinkState.LINK_JOIN_ACKED);
        return ack;
    }

    /**
     * Edits a call link's waiting-room setting.
     *
     * <p>Dispatches a {@code link_edit} request toggling the link's waiting-room gate and parses the reply
     * into a {@link LinkEditAck}. The edit does not itself change the join sub-state.
     *
     * @param token               the call-link token to edit; never {@code null}
     * @param waitingRoomEnabled  {@code true} to enable the link's waiting room, {@code false} to disable
     *                            it
     * @return the edit acknowledgement, echoing the applied waiting-room setting
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public LinkEditAck edit(String token, boolean waitingRoomEnabled) {
        Objects.requireNonNull(token, "token cannot be null");
        var request = LinkEditStanza.ofWaitingRoom(token, waitingRoomEnabled);
        var reply = iqSender.sendForReply(request);
        return LinkEditAck.of(reply);
    }

    /**
     * Returns the current call-link join sub-state.
     *
     * @return the current link sub-state; never {@code null}
     */
    public Calls2CallLinkState linkState() {
        lock.lock();
        try {
            return linkState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the link sub-state and emits a state-change event when it differs.
     *
     * <p>Records the new sub-state under the lock; when it differs from the current one, emits a
     * {@link CallLinkStateChanged}.
     *
     * @param next the new link sub-state
     */
    private void setLinkState(Calls2CallLinkState next) {
        var changed = false;
        lock.lock();
        try {
            if (linkState != next) {
                linkState = next;
                changed = true;
            }
        } finally {
            lock.unlock();
        }
        if (changed) {
            events.emit(new CallLinkStateChanged(next));
        }
    }
}
