package com.github.auties00.cobalt.model.call;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A WhatsApp VoIP call offer, representing the signaling data and lifecycle
 * status of either a one-to-one or a group call.
 *
 * <p>Instances of this class are created when a call offer stanza is received
 * from the WhatsApp server (incoming call) or when the local user initiates
 * an outgoing call. Each call is uniquely identified by its
 * {@linkplain #callId() call identifier}. The fields in this class correspond
 * to properties of the WhatsApp Web call model, including the peer JID, call
 * direction, video flag, and the
 * {@linkplain #offlineOffer() offline offer indicator}.
 *
 * <p>The {@linkplain #status() status} tracks where the call is in its
 * lifecycle, from {@link Status#RINGING} when the offer arrives, through
 * {@link Status#ACCEPTED} when a media session is established, to a terminal
 * state such as {@link Status#REJECTED}, {@link Status#TIMED_OUT},
 * {@link Status#MISSED}, or {@link Status#CANCELLED}.
 *
 * <p>When the call offer was received while the device was disconnected from
 * the WhatsApp server, the {@link #offlineOffer()} flag is set to
 * {@code true}. In the WhatsApp Web client, when a call offer is received
 * while offline, the ringing UI is not shown and the call cannot be answered
 * in real time.
 */
@ProtobufMessage
public final class CallOffer {
    /**
     * The JID of the chat where this call's log entry appears.
     *
     * <p>For one-to-one calls this is the peer's JID. For group calls this
     * is the group JID. In the WhatsApp Web client, the call log target is
     * resolved by determining the appropriate chat based on the call
     * creator, group context, and any LID-to-PN migrations.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid chatJid;

    /**
     * The JID of the user who initiated (created) this call.
     *
     * <p>In the WhatsApp Web call model, this corresponds to the call
     * creator whose JID is used to determine whether the call is incoming
     * or outgoing relative to the current account.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid callerJid;

    /**
     * The unique identifier for this call.
     *
     * <p>In the WhatsApp Web call model, this is the {@code id} property
     * that uniquely identifies the call within the call collection and is
     * used as the message key ID for the corresponding call log message.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String callId;

    /**
     * The instant at which the call offer was sent or received, expressed
     * as epoch seconds.
     *
     * <p>In the WhatsApp Web call model, this corresponds to the
     * {@code offerTime} property.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;

    /**
     * Whether this is a video call ({@code true}) or a voice-only call
     * ({@code false}).
     *
     * <p>In the WhatsApp Web call model, this corresponds to the
     * {@code isVideo} property and determines whether the video UI is
     * shown during the call.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    boolean video;

    /**
     * The current lifecycle status of this call.
     *
     * @see Status
     */
    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    Status status;

    /**
     * Whether the call offer was received while this device was offline.
     *
     * <p>In the WhatsApp Web call model, this corresponds to the
     * {@code offerReceivedWhileOffline} property. When {@code true}, the
     * device was not connected to the WhatsApp server at the time the
     * offer arrived, so the ringing notification is not shown to the user
     * and the call cannot be answered in real time.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    boolean offlineOffer;

    /**
     * Whether this is a group call ({@code true}) or a one-to-one call
     * ({@code false}).
     *
     * <p>In the WhatsApp Web call model, this corresponds to the
     * {@code isGroup} property. Group calls may also carry a
     * {@link #groupJid} and have per-participant state tracking.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    boolean group;

    /**
     * The JID of the group, if this is a group call.
     *
     * <p>This field is only set when {@link #group()} returns
     * {@code true}. In the WhatsApp Web call model, this corresponds to
     * the {@code groupJid} property.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    Jid groupJid;

    /**
     * Whether this call was initiated by the local user ({@code true}) or
     * is an incoming call from a remote peer ({@code false}).
     *
     * <p>In the WhatsApp Web call model, this corresponds to the
     * {@code outgoing} property.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    boolean outgoing;

    /**
     * Constructs a new {@code CallOffer} with all the specified values.
     *
     * @param chatJid      the chat JID where the call log appears
     * @param callerJid    the JID of the call initiator
     * @param callId       the unique call identifier
     * @param timestamp    the instant the call was initiated
     * @param video        whether this is a video call
     * @param status       the lifecycle status
     * @param offlineOffer whether the offer was received while offline
     * @param group        whether this is a group call
     * @param groupJid     the group JID, or {@code null} for one-to-one
     *                     calls
     * @param outgoing     whether the local user initiated the call
     * @throws NullPointerException if {@code chatJid}, {@code callerJid},
     *         {@code callId}, {@code timestamp}, or {@code status} is
     *         {@code null}
     */
    CallOffer(Jid chatJid, Jid callerJid, String callId, Instant timestamp, boolean video, Status status, boolean offlineOffer, boolean group, Jid groupJid, boolean outgoing) {
        this.chatJid = Objects.requireNonNull(chatJid, "chatJid cannot be null");
        this.callerJid = Objects.requireNonNull(callerJid, "callerJid cannot be null");
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.video = video;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.offlineOffer = offlineOffer;
        this.group = group;
        this.groupJid = groupJid;
        this.outgoing = outgoing;
    }

    /**
     * Returns the JID of the chat where this call's log entry appears.
     *
     * <p>For one-to-one calls this is the peer's JID. For group calls
     * this is the group JID.
     *
     * @return the chat JID, never {@code null}
     */
    public Jid chatJid() {
        return chatJid;
    }

    /**
     * Returns the JID of the user who initiated the call.
     *
     * @return the caller JID, never {@code null}
     */
    public Jid callerJid() {
        return callerJid;
    }

    /**
     * Returns the unique identifier for this call.
     *
     * @return the call identifier, never {@code null}
     */
    public String callId() {
        return callId;
    }

    /**
     * Returns the instant at which the call offer was sent or received.
     *
     * @return the call timestamp, never {@code null}
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns whether this is a video call.
     *
     * @return {@code true} if this is a video call, {@code false} for
     *         voice-only
     */
    public boolean video() {
        return video;
    }

    /**
     * Returns the current lifecycle status of this call.
     *
     * @return the call status, never {@code null}
     */
    public Status status() {
        return status;
    }

    /**
     * Returns whether the call offer was received while this device was
     * offline.
     *
     * <p>When {@code true}, the device was not connected to the WhatsApp
     * server at the time the offer arrived. The ringing notification is
     * not displayed and the user cannot answer the call in real time.
     *
     * @return {@code true} if the offer arrived while the device was
     *         disconnected
     */
    public boolean offlineOffer() {
        return offlineOffer;
    }

    /**
     * Returns whether this is a group call.
     *
     * @return {@code true} for group calls, {@code false} for one-to-one
     *         calls
     */
    public boolean group() {
        return group;
    }

    /**
     * Returns the JID of the group, if this is a group call.
     *
     * @return an {@code Optional} describing the group JID, or an empty
     *         {@code Optional} for one-to-one calls
     */
    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    /**
     * Returns whether this call was initiated by the local user.
     *
     * @return {@code true} for outgoing calls, {@code false} for incoming
     *         calls
     */
    public boolean outgoing() {
        return outgoing;
    }

    /**
     * Sets the JID of the chat where this call's log entry appears.
     *
     * @param chatJid the chat JID to set
     */
    public void setChatJid(Jid chatJid) {
        this.chatJid = chatJid;
    }

    /**
     * Sets the JID of the user who initiated this call.
     *
     * @param callerJid the caller JID to set
     */
    public void setCallerJid(Jid callerJid) {
        this.callerJid = callerJid;
    }

    /**
     * Sets the unique identifier for this call.
     *
     * @param callId the call identifier to set
     */
    public void setCallId(String callId) {
        this.callId = callId;
    }

    /**
     * Sets the instant at which the call offer was sent or received.
     *
     * @param timestamp the call timestamp to set
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Sets whether this is a video call.
     *
     * @param video whether this is a video call
     */
    public void setVideo(boolean video) {
        this.video = video;
    }

    /**
     * Sets the current lifecycle status of this call.
     *
     * @param status the call status to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Sets whether the call offer was received while this device was offline.
     *
     * @param offlineOffer whether the offer arrived while the device was
     *        disconnected
     */
    public void setOfflineOffer(boolean offlineOffer) {
        this.offlineOffer = offlineOffer;
    }

    /**
     * Sets whether this is a group call.
     *
     * @param group whether this is a group call
     */
    public void setGroup(boolean group) {
        this.group = group;
    }

    /**
     * Sets the JID of the group for group calls.
     *
     * @param groupJid the group JID to set
     */
    public void setGroupJid(Jid groupJid) {
        this.groupJid = groupJid;
    }

    /**
     * Sets whether this call was initiated by the local user.
     *
     * @param outgoing whether the local user initiated the call
     */
    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        return o instanceof CallOffer call
                && video == call.video
                && offlineOffer == call.offlineOffer
                && group == call.group
                && outgoing == call.outgoing
                && Objects.equals(chatJid, call.chatJid)
                && Objects.equals(callerJid, call.callerJid)
                && Objects.equals(callId, call.callId)
                && Objects.equals(timestamp, call.timestamp)
                && status == call.status
                && Objects.equals(groupJid, call.groupJid);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(chatJid, callerJid, callId, timestamp, video, status, offlineOffer, group, groupJid, outgoing);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Call[" +
                "chat=" + chatJid + ", " +
                "caller=" + callerJid + ", " +
                "callId=" + callId + ", " +
                "timestamp=" + timestamp + ", " +
                "video=" + video + ", " +
                "status=" + status + ", " +
                "offlineOffer=" + offlineOffer + ", " +
                "group=" + group + ", " +
                "groupJid=" + groupJid + ", " +
                "outgoing=" + outgoing + ']';
    }

    /**
     * The lifecycle status of a WhatsApp VoIP call offer.
     *
     * <p>A call transitions through these states as signaling stanzas are
     * exchanged between participants. The WhatsApp Web call model tracks
     * call state internally using a richer set of states (such as
     * {@code Calling}, {@code PreacceptReceived}, {@code AcceptSent},
     * {@code AcceptReceived}, {@code CallActive}, and
     * {@code CallActiveElseWhere}), which this enum simplifies into a
     * reduced lifecycle.
     *
     * <p>The typical progression for an incoming call that is answered
     * begins with {@link #RINGING} when the offer stanza arrives and the
     * device alerts the user, then transitions to {@link #ACCEPTED} once
     * the callee answers and a media session is established. If the call
     * is not answered, the terminal state depends on the reason: the
     * callee may have explicitly declined ({@link #REJECTED}), the call
     * may have rung until the timeout elapsed ({@link #TIMED_OUT}), the
     * user may not have interacted with the notification
     * ({@link #MISSED}), or the caller may have hung up before the callee
     * answered ({@link #CANCELLED}).
     */
    @ProtobufEnum
    public enum Status {
        /**
         * The call offer has been received and the device is ringing.
         *
         * <p>This is the initial state for every incoming call. If the
         * offer was received while the device was offline
         * ({@link CallOffer#offlineOffer()}), the ringing UI is not shown.
         * In the WhatsApp Web call model, this corresponds to the
         * {@code ReceivedCall} state.
         */
        RINGING(0),

        /**
         * The call has been accepted and a media session is (or was)
         * active between the participants.
         *
         * <p>In the WhatsApp Web call model, this corresponds to the
         * {@code CallActive} state where the VoIP media connection has
         * been established.
         */
        ACCEPTED(1),

        /**
         * The callee explicitly rejected the incoming call.
         *
         * <p>In the WhatsApp Web native call result, this corresponds to
         * the {@code Declined} value.
         */
        REJECTED(2),

        /**
         * The call was not answered within the server-defined timeout
         * period.
         *
         * <p>In the WhatsApp Web call model, this corresponds to the
         * {@code TimedOut} participant state.
         */
        TIMED_OUT(3),

        /**
         * The call was missed because the user did not interact with the
         * incoming call notification. This can occur when the device is
         * in Do Not Disturb mode, the caller is silenced by privacy
         * settings, or the device was offline when the offer arrived.
         *
         * <p>In the WhatsApp Web native call result, both the
         * {@code Missed} and {@code MissedNotificationsMuted} values
         * produce this state.
         */
        MISSED(4),

        /**
         * The caller cancelled the call before it was answered.
         *
         * <p>In the WhatsApp Web native call result, this corresponds to
         * the {@code Canceled} value.
         */
        CANCELLED(5);

        /**
         * The protobuf index of this status.
         */
        final int index;

        /**
         * Constructs a new {@code Status} with the specified protobuf index.
         *
         * @param index the protobuf enum index
         */
        Status(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * Returns the protobuf index of this status.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }
}
