package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "PastParticipant")
public final class GroupPastParticipant {
    /**
     * Duration after which past participant records expire.
     *
     * @apiNote WAWebDbPastParticipant: PAST_PARTICIPANT_EXPIRATION_DAYS
     */
    private static final Duration EXPIRATION = Duration.ofDays(180);

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid userJid;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    LeaveReason leaveReason;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;


    GroupPastParticipant(Jid userJid, LeaveReason leaveReason, Instant timestamp) {
        this.userJid = userJid;
        this.leaveReason = leaveReason;
        this.timestamp = timestamp;
    }

    public Optional<Jid> userJid() {
        return Optional.ofNullable(userJid);
    }

    public Optional<LeaveReason> leaveReason() {
        return Optional.ofNullable(leaveReason);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public void setUserJid(Jid userJid) {
        this.userJid = userJid;
    }

    public void setLeaveReason(LeaveReason leaveReason) {
        this.leaveReason = leaveReason;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns whether this past participant record has expired.
     * <p>
     * Per WhatsApp Web: past participant records are pruned after 180 days.
     *
     * @return true if the record is expired and should be pruned
     *
     * @apiNote WAWebDbPastParticipant: checks if record exceeds PAST_PARTICIPANT_EXPIRATION_DAYS
     */
    public boolean isExpired() {
        var age = Duration.between(timestamp, Instant.now());
        return age.compareTo(EXPIRATION) > 0;
    }

    @ProtobufEnum(name = "PastParticipant.LeaveReason")
    public static enum LeaveReason {
        LEFT(0),
        REMOVED(1);

        LeaveReason(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
