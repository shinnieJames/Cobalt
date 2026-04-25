package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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

/**
 * Represents a user who was previously a participant in a WhatsApp group but
 * has since left or been removed.
 *
 * <p>WhatsApp groups maintain a history of past participants so that features
 * like the "member updates" panel can display who left or was removed and
 * when. Each record captures the participant's JID, the reason they left, and
 * a timestamp of their departure.
 *
 * <p>Past participant records have a limited lifespan. The {@link #isExpired()}
 * method checks whether the record is older than the configured expiration
 * duration and should be pruned from storage.
 *
 * @see GroupPastParticipants
 * @see GroupMetadata
 */
@ProtobufMessage(name = "PastParticipant")
public final class GroupPastParticipant {
    /**
     * The duration after which past participant records are considered expired
     * and eligible for pruning from the local store.
     */
    private static final Duration EXPIRATION = Duration.ofDays(60);

    /**
     * The JID of the user who left or was removed from the group, or
     * {@code null} if not available.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid userJid;

    /**
     * The reason the participant left the group, or {@code null} if the
     * reason is not known.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    LeaveReason leaveReason;

    /**
     * The instant at which the participant left or was removed, or
     * {@code null} if the timestamp is not available.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;


    /**
     * Constructs a new {@code GroupPastParticipant} with the specified JID,
     * leave reason, and timestamp.
     *
     * @param userJid     the JID of the departed participant, or {@code null}
     * @param leaveReason the reason for departure, or {@code null}
     * @param timestamp   the departure instant, or {@code null}
     */
    GroupPastParticipant(Jid userJid, LeaveReason leaveReason, Instant timestamp) {
        this.userJid = userJid;
        this.leaveReason = leaveReason;
        this.timestamp = timestamp;
    }

    /**
     * Returns the JID of the user who left or was removed from the group,
     * if available.
     *
     * @return an {@code Optional} containing the user JID, or empty if not
     *         available
     */
    public Optional<Jid> userJid() {
        return Optional.ofNullable(userJid);
    }

    /**
     * Returns the reason the participant left the group, if known.
     *
     * @return an {@code Optional} containing the leave reason, or empty if
     *         not known
     */
    public Optional<LeaveReason> leaveReason() {
        return Optional.ofNullable(leaveReason);
    }

    /**
     * Returns the instant at which the participant left or was removed, if
     * available.
     *
     * @return an {@code Optional} containing the departure timestamp, or
     *         empty if not available
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Sets the JID of the departed participant.
     *
     * @param userJid the user JID to set, or {@code null} to clear
     */
    public void setUserJid(Jid userJid) {
        this.userJid = userJid;
    }

    /**
     * Sets the reason the participant left the group.
     *
     * @param leaveReason the leave reason to set, or {@code null} to clear
     */
    public void setLeaveReason(LeaveReason leaveReason) {
        this.leaveReason = leaveReason;
    }

    /**
     * Sets the instant at which the participant left or was removed.
     *
     * @param timestamp the departure timestamp to set, or {@code null} to
     *                  clear
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns whether this past participant record has expired and should be
     * pruned from the local store.
     *
     * <p>Records older than the configured expiration duration (60 days) are
     * considered stale and can be safely removed during periodic cleanup.
     *
     * @return {@code true} if the record is older than the expiration
     *         threshold, {@code false} otherwise
     */
    public boolean isExpired() {
        var age = Duration.between(timestamp, Instant.now());
        return age.compareTo(EXPIRATION) > 0;
    }

    /**
     * Represents the reason a participant departed from a WhatsApp group.
     *
     * <p>A participant can leave a group voluntarily ({@link #LEFT}) or be
     * removed by an administrator ({@link #REMOVED}).
     *
     * @implNote This enum fills two roles that WA Web keeps separate. It is
     *           the protobuf enum {@code PastParticipant.LeaveReason} defined
     *           in {@code WAWebProtobufsHistorySync.pb} (integer-valued
     *           {@code LEFT=0}, {@code REMOVED=1}), and it also adapts the
     *           client-side string mirror {@code WAWebLeaveReasonType.LeaveReason}
     *           (string-valued {@code "Left"}, {@code "Removed"}). WA Web
     *           translates between the two in
     *           {@code WAWebHistorySyncNotificationUtils}; Cobalt collapses
     *           them because Java's enum type system already provides the
     *           symbolic mirror that the JS string enum was emulating.
     */
    @WhatsAppWebModule(moduleName = "WAWebLeaveReasonType")
    @ProtobufEnum(name = "PastParticipant.LeaveReason")
    public static enum LeaveReason {
        /**
         * The participant voluntarily left the group.
         */
        @WhatsAppWebExport(moduleName = "WAWebLeaveReasonType", exports = "LeaveReason", adaptation = WhatsAppAdaptation.ADAPTED)
        LEFT(0),

        /**
         * The participant was removed from the group by an administrator.
         */
        @WhatsAppWebExport(moduleName = "WAWebLeaveReasonType", exports = "LeaveReason", adaptation = WhatsAppAdaptation.ADAPTED)
        REMOVED(1);

        /**
         * Constructs a {@code LeaveReason} with the given protobuf index.
         *
         * @param index the protobuf enum index
         */
        LeaveReason(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf-assigned numeric index for this leave reason.
         */
        final int index;

        /**
         * Returns the protobuf-assigned numeric index for this leave reason.
         *
         * @return the protobuf enum index
         */
        public int index() {
            return this.index;
        }
    }
}
