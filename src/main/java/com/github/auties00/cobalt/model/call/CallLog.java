package com.github.auties00.cobalt.model.call;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A VoIP call log record synchronized across linked devices via the WhatsApp
 * app state sync mechanism.
 *
 * <p>This class mirrors the {@code CallLogRecord} protobuf defined in the
 * WhatsApp Web sync action protocol. Call log records are exchanged as part
 * of the {@code call_log} sync action within the
 * {@code SyncActionValue.CallLogAction} wrapper, allowing call history to
 * remain consistent across all linked devices. They are also included in
 * the {@code HistorySync} message as part of the {@code callLogRecords}
 * repeated field for initial device synchronization.
 *
 * <p>Each record captures the metadata of a single call: its
 * {@linkplain #callResult() outcome}, {@linkplain #duration() duration},
 * {@linkplain #startTime() timing}, {@linkplain #callType() classification},
 * and {@linkplain #participants() per-participant results}. The record
 * distinguishes between regular calls, scheduled calls, and voice chats
 * through the {@link Type} enum.
 *
 * <p>When a call notification was suppressed, the {@link #isDndMode()} flag
 * and the {@link #silenceReason()} field describe why the call was silenced.
 * The WhatsApp Web client maps each {@code SilenceReason} value to a display
 * label used in the call log UI.
 */
@ProtobufMessage(name = "CallLogRecord")
public final class CallLog {
    /**
     * The outcome of this call, indicating how it ended or whether it is
     * still in progress.
     *
     * <p>Corresponds to protobuf field {@code 1} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Result callResult;

    /**
     * Whether the device was in Do Not Disturb mode when this call was
     * received.
     *
     * <p>Corresponds to protobuf field {@code 2} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean isDndMode;

    /**
     * The reason why the incoming call notification was silenced.
     *
     * <p>Corresponds to protobuf field {@code 3} in {@code CallLogRecord}.
     *
     * @see SilenceReason
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    SilenceReason silenceReason;

    /**
     * The duration of the call in seconds.
     *
     * <p>Corresponds to protobuf field {@code 4} in {@code CallLogRecord}.
     * This value is only meaningful when the call result is
     * {@link Result#CONNECTED}.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT64)
    Long duration;

    /**
     * The instant at which the call was started, expressed as epoch seconds.
     *
     * <p>Corresponds to protobuf field {@code 5} in {@code CallLogRecord}.
     * This timestamp is used to sort call log records chronologically during
     * history synchronization.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant startTime;

    /**
     * Whether this was an incoming call ({@code true}) or an outgoing call
     * ({@code false}).
     *
     * <p>Corresponds to protobuf field {@code 6} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean isIncoming;

    /**
     * Whether this was a video call ({@code true}) or a voice-only call
     * ({@code false}).
     *
     * <p>Corresponds to protobuf field {@code 7} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    Boolean isVideo;

    /**
     * Whether this call was initiated through a shareable call link.
     *
     * <p>Corresponds to protobuf field {@code 8} in {@code CallLogRecord}.
     * When {@code true}, the {@link #callLinkToken} identifies the specific
     * link used.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    Boolean isCallLink;

    /**
     * The token identifying the call link, if this is a call link call.
     *
     * <p>Corresponds to protobuf field {@code 9} in {@code CallLogRecord}.
     *
     * @see #isCallLink
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String callLinkToken;

    /**
     * The identifier of the scheduled call, if this call was pre-scheduled.
     *
     * <p>Corresponds to protobuf field {@code 10} in {@code CallLogRecord}.
     * This field is only set for calls of type {@link Type#SCHEDULED_CALL}.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String scheduledCallId;

    /**
     * The unique identifier for this call.
     *
     * <p>Corresponds to protobuf field {@code 11} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String callId;

    /**
     * The JID of the user who created (initiated) this call.
     *
     * <p>Corresponds to protobuf field {@code 12} in {@code CallLogRecord}.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    Jid callCreatorJid;

    /**
     * The JID of the group, if this is a group call.
     *
     * <p>Corresponds to protobuf field {@code 13} in {@code CallLogRecord}.
     * This field is {@code null} for one-to-one calls.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    Jid groupJid;

    /**
     * The per-participant metadata for this call, capturing each
     * participant's JID and their individual call outcome.
     *
     * <p>Corresponds to protobuf field {@code 14} in {@code CallLogRecord},
     * a repeated {@code CallLogRecord.ParticipantInfo} message.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    List<ParticipantInfo> participants;

    /**
     * The classification of this call.
     *
     * <p>Corresponds to protobuf field {@code 15} in {@code CallLogRecord}.
     *
     * @see Type
     */
    @ProtobufProperty(index = 15, type = ProtobufType.ENUM)
    Type callType;


    /**
     * Constructs a new {@code CallLog} with all the specified field values.
     *
     * @param callResult     the outcome of this call
     * @param isDndMode      whether the device was in Do Not Disturb mode
     * @param silenceReason  the reason the call notification was silenced
     * @param duration       the call duration in seconds
     * @param startTime      the instant the call was started
     * @param isIncoming     whether this was an incoming call
     * @param isVideo        whether this was a video call
     * @param isCallLink     whether this was a call link call
     * @param callLinkToken  the call link token
     * @param scheduledCallId the scheduled call identifier
     * @param callId         the unique call identifier
     * @param callCreatorJid the JID of the call creator
     * @param groupJid       the group JID, or {@code null} for one-to-one calls
     * @param participants   the per-participant information
     * @param callType       the call type classification
     */
    CallLog(Result callResult, Boolean isDndMode, SilenceReason silenceReason, Long duration, Instant startTime, Boolean isIncoming, Boolean isVideo, Boolean isCallLink, String callLinkToken, String scheduledCallId, String callId, Jid callCreatorJid, Jid groupJid, List<ParticipantInfo> participants, Type callType) {
        this.callResult = callResult;
        this.isDndMode = isDndMode;
        this.silenceReason = silenceReason;
        this.duration = duration;
        this.startTime = startTime;
        this.isIncoming = isIncoming;
        this.isVideo = isVideo;
        this.isCallLink = isCallLink;
        this.callLinkToken = callLinkToken;
        this.scheduledCallId = scheduledCallId;
        this.callId = callId;
        this.callCreatorJid = callCreatorJid;
        this.groupJid = groupJid;
        this.participants = participants;
        this.callType = callType;
    }

    /**
     * Returns the outcome of this call.
     *
     * <p>The result indicates how the call ended or whether it is still in
     * progress. For group calls, per-participant outcomes are available
     * through {@link #participants()}.
     *
     * @return an {@code Optional} describing the call result, or an empty
     *         {@code Optional} if the result has not been set
     */
    public Optional<Result> callResult() {
        return Optional.ofNullable(callResult);
    }

    /**
     * Returns whether the device was in Do Not Disturb mode when this call
     * was received.
     *
     * @return {@code true} if the device was in DND mode, {@code false}
     *         otherwise
     */
    public boolean isDndMode() {
        return isDndMode != null && isDndMode;
    }

    /**
     * Returns the reason why the call notification was silenced, if any.
     *
     * <p>A non-{@link SilenceReason#NONE NONE} value indicates that the
     * incoming call notification was suppressed. The WhatsApp Web client
     * maps each value to a display label: {@link SilenceReason#SCHEDULED}
     * maps to {@code "scheduled"}, {@link SilenceReason#PRIVACY} maps to
     * {@code "privacy"}, and {@link SilenceReason#LIGHTWEIGHT} maps to
     * {@code "lightweight"}.
     *
     * @return an {@code Optional} describing the silence reason, or an empty
     *         {@code Optional} if the reason is not available
     * @see #isDndMode()
     */
    public Optional<SilenceReason> silenceReason() {
        return Optional.ofNullable(silenceReason);
    }

    /**
     * Returns the duration of the call in seconds.
     *
     * <p>This value is only meaningful when the call result is
     * {@link Result#CONNECTED}.
     *
     * @return an {@code OptionalLong} containing the duration in seconds, or
     *         an empty {@code OptionalLong} if the duration is not available
     */
    public OptionalLong duration() {
        return duration == null ? OptionalLong.empty() : OptionalLong.of(duration);
    }

    /**
     * Returns the instant at which the call was started.
     *
     * <p>The timestamp is expressed in epoch seconds and is used to sort
     * call log records chronologically during history synchronization.
     *
     * @return an {@code Optional} describing the start time, or an empty
     *         {@code Optional} if the start time is not available
     */
    public Optional<Instant> startTime() {
        return Optional.ofNullable(startTime);
    }

    /**
     * Returns whether this was an incoming call.
     *
     * @return {@code true} if the call was incoming, {@code false} if it was
     *         outgoing
     */
    public boolean isIncoming() {
        return isIncoming != null && isIncoming;
    }

    /**
     * Returns whether this was a video call.
     *
     * @return {@code true} for video calls, {@code false} for voice-only
     *         calls
     */
    public boolean isVideo() {
        return isVideo != null && isVideo;
    }

    /**
     * Returns whether this call was initiated through a shareable call link.
     *
     * <p>Call links allow anyone with the link to join the call directly.
     * When {@code true}, the {@link #callLinkToken()} identifies the
     * specific link used.
     *
     * @return {@code true} if this call was a call link call, {@code false}
     *         otherwise
     * @see #callLinkToken()
     */
    public boolean isCallLink() {
        return isCallLink != null && isCallLink;
    }

    /**
     * Returns the token identifying the call link, if this is a call link
     * call.
     *
     * @return an {@code Optional} describing the call link token, or an empty
     *         {@code Optional} if this is not a call link call
     * @see #isCallLink()
     */
    public Optional<String> callLinkToken() {
        return Optional.ofNullable(callLinkToken);
    }

    /**
     * Returns the identifier of the scheduled call, if this call was
     * pre-scheduled.
     *
     * @return an {@code Optional} describing the scheduled call identifier, or
     *         an empty {@code Optional} if this is not a scheduled call
     * @see Type#SCHEDULED_CALL
     */
    public Optional<String> scheduledCallId() {
        return Optional.ofNullable(scheduledCallId);
    }

    /**
     * Returns the unique identifier for this call.
     *
     * @return an {@code Optional} describing the call identifier, or an empty
     *         {@code Optional} if the identifier is not available
     */
    public Optional<String> callId() {
        return Optional.ofNullable(callId);
    }

    /**
     * Returns the JID of the user who created (initiated) this call.
     *
     * @return an {@code Optional} describing the call creator's JID, or an
     *         empty {@code Optional} if the creator is not available
     */
    public Optional<Jid> callCreatorJid() {
        return Optional.ofNullable(callCreatorJid);
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
     * Returns the list of participants in this call with their individual
     * call results.
     *
     * <p>For group calls, each participant's outcome may differ: one
     * participant may have connected while another missed the call.
     *
     * @return an unmodifiable list of participant information, or an empty
     *         list if no participant information is available
     */
    public List<ParticipantInfo> participants() {
        return participants == null ? List.of() : Collections.unmodifiableList(participants);
    }

    /**
     * Returns the type of this call.
     *
     * @return an {@code Optional} describing the call type, or an empty
     *         {@code Optional} if the type is not available
     */
    public Optional<Type> callType() {
        return Optional.ofNullable(callType);
    }

    /**
     * Sets the outcome of this call.
     *
     * @param callResult the call result to set
     */
    public void setCallResult(Result callResult) {
        this.callResult = callResult;
    }

    /**
     * Sets whether the device was in Do Not Disturb mode when this call
     * was received.
     *
     * @param isDndMode whether DND mode was active
     */
    public void setDndMode(Boolean isDndMode) {
        this.isDndMode = isDndMode;
    }

    /**
     * Sets the reason why the call notification was silenced.
     *
     * @param silenceReason the silence reason to set
     */
    public void setSilenceReason(SilenceReason silenceReason) {
        this.silenceReason = silenceReason;
    }

    /**
     * Sets the duration of the call in seconds.
     *
     * @param duration the call duration in seconds
     */
    public void setDuration(Long duration) {
        this.duration = duration;
    }

    /**
     * Sets the instant at which the call was started.
     *
     * @param startTime the start time to set
     */
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    /**
     * Sets whether this was an incoming call.
     *
     * @param isIncoming whether the call was incoming
     */
    public void setIncoming(Boolean isIncoming) {
        this.isIncoming = isIncoming;
    }

    /**
     * Sets whether this was a video call.
     *
     * @param isVideo whether the call was a video call
     */
    public void setVideo(Boolean isVideo) {
        this.isVideo = isVideo;
    }

    /**
     * Sets whether this call was initiated through a shareable call link.
     *
     * @param isCallLink whether this was a call link call
     */
    public void setCallLink(Boolean isCallLink) {
        this.isCallLink = isCallLink;
    }

    /**
     * Sets the token identifying the call link.
     *
     * @param callLinkToken the call link token to set
     */
    public void setCallLinkToken(String callLinkToken) {
        this.callLinkToken = callLinkToken;
    }

    /**
     * Sets the identifier of the scheduled call.
     *
     * @param scheduledCallId the scheduled call identifier to set
     */
    public void setScheduledCallId(String scheduledCallId) {
        this.scheduledCallId = scheduledCallId;
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
     * Sets the JID of the user who created this call.
     *
     * @param callCreatorJid the call creator's JID to set
     */
    public void setCallCreatorJid(Jid callCreatorJid) {
        this.callCreatorJid = callCreatorJid;
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
     * Sets the list of participants and their individual call results.
     *
     * @param participants the participant information list to set
     */
    public void setParticipants(List<ParticipantInfo> participants) {
        this.participants = participants;
    }

    /**
     * Sets the type of this call.
     *
     * @param callType the call type to set
     */
    public void setCallType(Type callType) {
        this.callType = callType;
    }

    /**
     * The outcome of a VoIP call as recorded in the synchronized call log.
     *
     * <p>These values are defined by the {@code CallLogRecord.CallResult}
     * protobuf enum in the WhatsApp Web sync action protocol. The WhatsApp
     * Web client maps several of these results to a simplified display
     * outcome: {@link #CONNECTED} maps to completed, {@link #UNAVAILABLE}
     * and {@link #ABANDONED} are both displayed as missed calls, and
     * {@link #INVALID}, {@link #UPCOMING}, and {@link #FAILED} are all
     * treated as failed calls.
     */
    @ProtobufEnum(name = "CallLogRecord.CallResult")
    public static enum Result {
        /**
         * The call was successfully connected and a media session was
         * established between the participants.
         *
         * <p>In the WhatsApp Web client this result is displayed as a
         * completed call.
         */
        CONNECTED(0),

        /**
         * The callee explicitly rejected the incoming call.
         *
         * <p>This value is produced when the native call result is
         * {@code Declined}.
         */
        REJECTED(1),

        /**
         * The caller cancelled the call before it was answered.
         */
        CANCELLED(2),

        /**
         * The call was accepted on another linked device belonging to the
         * same account.
         */
        ACCEPTEDELSEWHERE(3),

        /**
         * The call was not answered. This includes scenarios where the
         * device was in Do Not Disturb mode or notifications were muted.
         *
         * <p>This value is produced both when the native call result is
         * {@code Missed} and when it is {@code MissedNotificationsMuted}.
         */
        MISSED(4),

        /**
         * The call result is invalid or could not be determined.
         *
         * <p>This value is produced when the native call result is
         * {@code Undefined}. In the WhatsApp Web client this result is
         * treated as a failed call.
         */
        INVALID(5),

        /**
         * The callee was unavailable to take the call.
         *
         * <p>In the WhatsApp Web client this result is displayed as a
         * missed call.
         */
        UNAVAILABLE(6),

        /**
         * A scheduled call that has not yet started.
         *
         * <p>This result is used for {@link Type#SCHEDULED_CALL scheduled
         * calls} during the period before the call begins. In the WhatsApp
         * Web client this result is treated as a failed call when it
         * appears in the call log outcome mapping.
         */
        UPCOMING(7),

        /**
         * The call failed due to a technical error, such as a network or
         * media session failure.
         */
        FAILED(8),

        /**
         * The call was abandoned by the participant without completing.
         *
         * <p>In the WhatsApp Web client this result is displayed as a
         * missed call.
         */
        ABANDONED(9),

        /**
         * The call is currently in progress.
         */
        ONGOING(10);

        /**
         * Constructs a new {@code Result} with the specified protobuf index.
         *
         * @param index the protobuf enum index
         */
        Result(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index of this call result.
         */
        final int index;

        /**
         * Returns the protobuf index of this call result.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * The classification of a VoIP call in the synchronized call log.
     *
     * <p>These values are defined by the {@code CallLogRecord.CallType}
     * protobuf enum and distinguish between standard calls, pre-scheduled
     * calls, and persistent voice chat sessions.
     */
    @ProtobufEnum(name = "CallLogRecord.CallType")
    public static enum Type {
        /**
         * A standard one-to-one or group call initiated in real time.
         */
        REGULAR(0),

        /**
         * A pre-scheduled call with a defined start time.
         *
         * <p>Scheduled calls have an associated
         * {@linkplain CallLog#scheduledCallId() scheduled call identifier}
         * and begin in the {@link Result#UPCOMING} state until the
         * scheduled time arrives.
         */
        SCHEDULED_CALL(1),

        /**
         * A persistent voice chat session within a group.
         *
         * <p>Voice chats allow group members to join and leave freely
         * without a formal call invitation, similar to an always-available
         * audio channel.
         */
        VOICE_CHAT(2);

        /**
         * Constructs a new {@code Type} with the specified protobuf index.
         *
         * @param index the protobuf enum index
         */
        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index of this call type.
         */
        final int index;

        /**
         * Returns the protobuf index of this call type.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * The reason why an incoming call notification was silenced.
     *
     * <p>These values are defined by the
     * {@code CallLogRecord.SilenceReason} protobuf enum. In the WhatsApp
     * Web client, each non-{@link #NONE} value is mapped to a display label:
     * {@link #SCHEDULED} maps to {@code "scheduled"}, {@link #PRIVACY}
     * maps to {@code "privacy"}, and {@link #LIGHTWEIGHT} maps to
     * {@code "lightweight"}. Additionally, when a call is silenced, the
     * call log entry may display the text "Silenced unknown caller".
     *
     * @see CallLog#isDndMode()
     */
    @ProtobufEnum(name = "CallLogRecord.SilenceReason")
    public static enum SilenceReason {
        /**
         * The call notification was not silenced.
         *
         * <p>This is the default value, indicating that the incoming call
         * notification was displayed normally.
         */
        NONE(0),

        /**
         * The call was silenced because of a scheduled Do Not Disturb
         * window or notification schedule.
         */
        SCHEDULED(1),

        /**
         * The call was silenced due to privacy settings, such as the
         * "Silence Unknown Callers" feature.
         */
        PRIVACY(2),

        /**
         * The call was silenced because it used a lightweight call
         * notification.
         *
         * <p>Lightweight calls display a minimal notification banner
         * instead of the full-screen ringing UI. The WhatsApp Web client
         * exposes dedicated surfaces for lightweight call interactions,
         * including the lightweight call banner and a new user experience
         * overlay.
         */
        LIGHTWEIGHT(3);

        /**
         * Constructs a new {@code SilenceReason} with the specified protobuf
         * index.
         *
         * @param index the protobuf enum index
         */
        SilenceReason(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index of this silence reason.
         */
        final int index;

        /**
         * Returns the protobuf index of this silence reason.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * Per-participant metadata within a {@link CallLog} record, capturing
     * each participant's JID and their individual call outcome.
     *
     * <p>This class mirrors the {@code CallLogRecord.ParticipantInfo}
     * protobuf defined in the WhatsApp Web sync action protocol. In group
     * calls, each participant may have a different outcome: one participant
     * may have connected while another missed the call. The WhatsApp Web
     * client maps participant call results to internal participant states
     * for rendering, where {@link Result#CONNECTED} maps to a connected
     * state, {@link Result#REJECTED} maps to a rejected state, and
     * several other results such as {@link Result#MISSED},
     * {@link Result#UNAVAILABLE}, and {@link Result#ABANDONED} map to a
     * terminated state.
     */
    @ProtobufMessage(name = "CallLogRecord.ParticipantInfo")
    public static final class ParticipantInfo {
        /**
         * The JID of this participant.
         *
         * <p>Corresponds to protobuf field {@code 1} in
         * {@code CallLogRecord.ParticipantInfo}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid userJid;

        /**
         * The individual call outcome for this participant.
         *
         * <p>Corresponds to protobuf field {@code 2} in
         * {@code CallLogRecord.ParticipantInfo}. This uses the same
         * {@link Result} enum as the top-level
         * {@link CallLog#callResult() call result}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        Result callResult;


        /**
         * Constructs a new {@code ParticipantInfo} with the specified JID
         * and call result.
         *
         * @param userJid    the participant's JID
         * @param callResult the participant's individual call outcome
         */
        ParticipantInfo(Jid userJid, Result callResult) {
            this.userJid = userJid;
            this.callResult = callResult;
        }

        /**
         * Returns the JID of this participant.
         *
         * @return an {@code Optional} describing the participant's JID, or
         *         an empty {@code Optional} if the JID is not available
         */
        public Optional<Jid> userJid() {
            return Optional.ofNullable(userJid);
        }

        /**
         * Returns the individual call outcome for this participant.
         *
         * @return an {@code Optional} describing the participant's call
         *         result, or an empty {@code Optional} if the result is
         *         not available
         */
        public Optional<Result> callResult() {
            return Optional.ofNullable(callResult);
        }

        /**
         * Sets the JID of this participant.
         *
         * @param userJid the participant's JID to set
         * @return this instance for method chaining
         */
        public void setUserJid(Jid userJid) {
            this.userJid = userJid;
    }

        /**
         * Sets the individual call outcome for this participant.
         *
         * @param callResult the participant's call result to set
         * @return this instance for method chaining
         */
        public void setCallResult(Result callResult) {
            this.callResult = callResult;
    }
    }
}
