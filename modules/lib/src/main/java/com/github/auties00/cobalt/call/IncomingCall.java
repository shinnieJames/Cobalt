package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A pending inbound call offer, surfaced to the user before any
 * media is exchanged. Delivered via
 * {@code WhatsAppClientListener.onCall} when an
 * {@code <offer>} stanza arrives from the peer; the listener must
 * respond by calling either
 * {@link WhatsAppClient#acceptCall(IncomingCall, CallOptions)} or
 * {@link WhatsAppClient#rejectCall(IncomingCall, com.github.auties00.cobalt.call.signaling.CallEndReason)}
 * within the WhatsApp-imposed timeout (~30 s) or the offer will
 * expire on its own.
 *
 * <p>This class carries the full protocol metadata of the offer
 * (callId, peer JID, timestamps, group/video flags). It is a pure
 * value type: accept/reject live on {@link WhatsAppClient}, which
 * uses {@link #markResponded()} to enforce one-shot semantics.
 *
 * <p>Distinct from {@link ActiveCall} because there are no media
 * ports or live state until acceptance — only the protocol-level
 * "who is calling and is it audio or video" view.
 */
public final class IncomingCall {
    /**
     * The unique identifier for this call, assigned by the caller's
     * device. Also the message-key id of the corresponding call-log
     * chat entry.
     */
    private final String callId;

    /**
     * The {@link Jid} of the user who initiated the call — the peer
     * the local user is talking to.
     */
    private final Jid peer;

    /**
     * The chat where this call's log entry appears: the peer's JID
     * for one-to-one calls or the group JID for group calls.
     */
    private final Jid chatJid;

    /**
     * The instant at which the offer was received locally.
     */
    private final Instant timestamp;

    /**
     * {@code true} if the caller offered video; {@code false} for an
     * audio-only call.
     */
    private final boolean videoOffered;

    /**
     * {@code true} for a group call.
     */
    private final boolean group;

    /**
     * The group's {@link Jid} for group calls; {@code null} for
     * one-to-one.
     */
    private final Jid groupJid;

    /**
     * {@code true} if the offer was server-replayed because the
     * local device was disconnected when the call originally rang.
     * The notification is informational only — the call cannot be
     * answered in real time once an offline offer's natural timeout
     * has elapsed.
     */
    private final boolean offlineOffer;

    /**
     * One-shot guard so accept and reject can each succeed at most
     * once for the lifetime of an offer.
     */
    private final AtomicBoolean responded = new AtomicBoolean(false);

    /**
     * Constructs a new offer.
     *
     * @param callId       the unique call identifier
     * @param peer         the caller's JID
     * @param chatJid      the chat the offer arrived in (peer JID
     *                     for 1:1, group JID for group calls)
     * @param timestamp    when the offer was received
     * @param videoOffered whether video was offered
     * @param group        whether this is a group call
     * @param groupJid     the group JID, or {@code null} for 1:1
     * @param offlineOffer whether the offer was server-replayed
     * @throws NullPointerException if any non-{@code null} argument
     *                              is missing
     */
    public IncomingCall(String callId, Jid peer, Jid chatJid, Instant timestamp,
                        boolean videoOffered, boolean group, Jid groupJid,
                        boolean offlineOffer) {
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.peer = Objects.requireNonNull(peer, "peer cannot be null");
        this.chatJid = Objects.requireNonNull(chatJid, "chatJid cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.videoOffered = videoOffered;
        this.group = group;
        this.groupJid = groupJid;
        this.offlineOffer = offlineOffer;
    }

    /**
     * Returns the unique call identifier.
     *
     * @return the call id
     */
    public String callId() {
        return callId;
    }

    /**
     * Returns the JID of the peer who placed the call.
     *
     * @return the peer JID
     */
    public Jid peer() {
        return peer;
    }

    /**
     * Returns the chat the offer arrived in — the peer JID for
     * one-to-one calls, the group JID for group calls.
     *
     * @return the chat JID
     */
    public Jid chatJid() {
        return chatJid;
    }

    /**
     * Returns the instant the offer was received locally.
     *
     * @return the offer timestamp
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns whether the caller offered video. The local side may
     * still accept as audio-only by passing
     * {@link CallOptions#audio()} to
     * {@link WhatsAppClient#acceptCall(IncomingCall, CallOptions)}.
     *
     * @return {@code true} if video was offered
     */
    public boolean videoOffered() {
        return videoOffered;
    }

    /**
     * Returns whether this is a group call.
     *
     * @return {@code true} for group calls
     */
    public boolean group() {
        return group;
    }

    /**
     * Returns the group's JID for group calls.
     *
     * @return the group JID, or empty for one-to-one calls
     */
    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    /**
     * Returns whether the offer was server-replayed because the
     * local device was offline at ring time.
     *
     * @return {@code true} for offline-replayed offers
     */
    public boolean offlineOffer() {
        return offlineOffer;
    }

    /**
     * Atomically marks the offer as responded-to. The first caller
     * succeeds; every subsequent caller throws.
     *
     * <p>Invoked by
     * {@link WhatsAppClient#acceptCall(IncomingCall, CallOptions)}
     * and
     * {@link WhatsAppClient#rejectCall(IncomingCall, com.github.auties00.cobalt.call.signaling.CallEndReason)}
     * before they touch the call engine, so accept-after-reject and
     * double-accept fail loudly instead of silently producing a
     * second {@link ActiveCall}.
     *
     * @throws IllegalStateException if the offer has already been
     *                               responded to
     */
    public void markResponded() {
        if (!responded.compareAndSet(false, true)) {
            throw new IllegalStateException("call " + callId + " has already been responded to");
        }
    }
}
