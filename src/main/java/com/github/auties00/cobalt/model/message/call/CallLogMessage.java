package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.CallLogMessage")
public final class CallLogMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isVideo;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    CallOutcome callOutcome;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    Long durationSecs;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    CallType callType;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    List<CallParticipant> participants;


    CallLogMessage(Boolean isVideo, CallOutcome callOutcome, Long durationSecs, CallType callType, List<CallParticipant> participants) {
        this.isVideo = isVideo;
        this.callOutcome = callOutcome;
        this.durationSecs = durationSecs;
        this.callType = callType;
        this.participants = participants;
    }

    public boolean isVideo() {
        return isVideo != null && isVideo;
    }

    public Optional<CallOutcome> callOutcome() {
        return Optional.ofNullable(callOutcome);
    }

    public OptionalLong durationSecs() {
        return durationSecs == null ? OptionalLong.empty() : OptionalLong.of(durationSecs);
    }

    public Optional<CallType> callType() {
        return Optional.ofNullable(callType);
    }

    public List<CallParticipant> participants() {
        return participants == null ? List.of() : Collections.unmodifiableList(participants);
    }

    public void setVideo(Boolean isVideo) {
        this.isVideo = isVideo;
    }

    public void setCallOutcome(CallOutcome callOutcome) {
        this.callOutcome = callOutcome;
    }

    public void setDurationSecs(Long durationSecs) {
        this.durationSecs = durationSecs;
    }

    public void setCallType(CallType callType) {
        this.callType = callType;
    }

    public void setParticipants(List<CallParticipant> participants) {
        this.participants = participants;
    }

    @ProtobufEnum(name = "Message.CallLogMessage.CallOutcome")
    public static enum CallOutcome {
        CONNECTED(0),
        MISSED(1),
        FAILED(2),
        REJECTED(3),
        ACCEPTED_ELSEWHERE(4),
        ONGOING(5),
        SILENCED_BY_DND(6),
        SILENCED_UNKNOWN_CALLER(7);

        CallOutcome(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "Message.CallLogMessage.CallType")
    public static enum CallType {
        REGULAR(0),
        SCHEDULED_CALL(1),
        VOICE_CHAT(2);

        CallType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.CallLogMessage.CallParticipant")
    public static final class CallParticipant {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid jid;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        CallOutcome callOutcome;


        CallParticipant(Jid jid, CallOutcome callOutcome) {
            this.jid = jid;
            this.callOutcome = callOutcome;
        }

        public Optional<Jid> jid() {
            return Optional.ofNullable(jid);
        }

        public Optional<CallOutcome> callOutcome() {
            return Optional.ofNullable(callOutcome);
        }

        public void setJid(Jid jid) {
            this.jid = jid;
    }

        public void setCallOutcome(CallOutcome callOutcome) {
            this.callOutcome = callOutcome;
    }
    }
}
