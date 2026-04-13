package com.github.auties00.cobalt.message.send.ack;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The result of parsing a server acknowledgement node returned after
 * sending a message stanza.
 *
 * <p>The server always returns the same set of attributes; some are
 * optional and represented here via {@link Optional} accessors.  The
 * presence of an {@linkplain #error() error code} indicates a
 * server-side rejection, while a non-empty {@linkplain #phash() phash}
 * indicates a device-list mismatch requiring a resend.
 *
 * @implNote WAWebSendMsgCommonApi.sendMsgAckSyncParser: the parsed ack
 * structure.  A non-null {@code error} indicates a server-side rejection
 * (e.g., 421 for stale group addressing mode).
 * @see AckParser
 * @see NackReason
 */
public final class AckResult {
    /**
     * The server timestamp from the {@code t} attribute.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.attrTime("t")}
     */
    private final Instant timestamp;

    /**
     * The sync attribute value.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("sync")}
     */
    private final String sync;

    /**
     * The participant hash for device-list verification.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("phash")}
     */
    private final String phash;

    /**
     * Whether the server requests a LID refresh.
     *
     * @implNote WAWebSendMsgCommonApi:
     * {@code e.hasAttr("refresh_lid") ? e.attrString("refresh_lid") === "true" : false}
     */
    private final boolean refreshLid;

    /**
     * The addressing mode the server expects.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("addressing_mode")}
     */
    private final String addressingMode;

    /**
     * The recipient count reported by the server.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrInt("count")}
     */
    private final Integer count;

    /**
     * The error code included by the server, or {@code null} for success.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrInt("error")}
     */
    private final Integer error;

    /**
     * Constructs a new ack result with the given attribute values.
     *
     * @implNote WAWebSendMsgCommonApi.sendMsgAckSyncParser.parse: maps
     * each ack attribute into this structure.
     * @param timestamp      the server timestamp, or {@code null}
     * @param sync           the sync attribute, or {@code null}
     * @param phash          the participant hash, or {@code null}
     * @param refreshLid     whether a LID refresh is requested
     * @param addressingMode the addressing mode, or {@code null}
     * @param count          the recipient count, or {@code null}
     * @param error          the error code, or {@code null}
     */
    AckResult(
            Instant timestamp,
            String sync,
            String phash,
            boolean refreshLid,
            String addressingMode,
            Integer count,
            Integer error
    ) {
        this.timestamp = timestamp;
        this.sync = sync;
        this.phash = phash;
        this.refreshLid = refreshLid;
        this.addressingMode = addressingMode;
        this.count = count;
        this.error = error;
    }

    /**
     * Returns the server timestamp.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.attrTime("t")}
     * @return the timestamp, or empty if absent
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Returns the sync attribute.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("sync")}
     * @return the sync value, or empty if absent
     */
    public Optional<String> sync() {
        return Optional.ofNullable(sync);
    }

    /**
     * Returns the participant hash returned by the server for group messages.
     *
     * <p>A non-empty value indicates the server's participant list differs
     * from the client's, requiring a device-list resync and message resend.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("phash")}.
     * WAWebSendGroupSkmsgJob: triggers resendPersistedGroupMsgWrapper when
     * {@code phash != null && phash !== localPhash}.
     * WAWebSendUserMsgJob: triggers resendUserMsg when {@code phash != null}.
     * @return the server phash, or empty if the hashes matched
     */
    public Optional<String> phash() {
        return Optional.ofNullable(phash);
    }

    /**
     * Returns whether the server requests a LID refresh for the recipient.
     *
     * @implNote WAWebSendMsgCommonApi:
     * {@code e.hasAttr("refresh_lid") ? e.attrString("refresh_lid") === "true" : false}.
     * WAWebSendUserMsgJob.maybeRefreshLid: triggers a contact list sync
     * when {@code true}.
     * @return {@code true} if a LID refresh is requested
     */
    public boolean refreshLid() {
        return refreshLid;
    }

    /**
     * Returns the addressing mode the server expects for this chat.
     *
     * <p>When the returned mode differs from the mode the client used to
     * send the message, the client must migrate the group's participant
     * data and resend.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrString("addressing_mode")}.
     * WAWebSendGroupSkmsgJob: compares against local addressing mode and
     * calls handleAddressingModeMismatch on difference.
     * @return the addressing mode ({@code "pn"} or {@code "lid"}),
     *         or empty if absent
     */
    public Optional<String> addressingMode() {
        return Optional.ofNullable(addressingMode);
    }

    /**
     * Returns the recipient count reported by the server.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrInt("count")}.
     * WAWebSendGroupSkmsgJob: merges into the message table when present.
     * @return the count, or empty if absent
     */
    public OptionalInt count() {
        return count != null
                ? OptionalInt.of(count)
                : OptionalInt.empty();
    }

    /**
     * Returns the error code included by the server.
     *
     * <p>An empty return value indicates success.
     *
     * @implNote WAWebSendMsgCommonApi: {@code e.maybeAttrInt("error")}.
     * WAWebSendGroupSkmsgJob: checks for
     * {@link NackReason#STALE_GROUP_ADDRESSING_MODE} (421).
     * @return the error code, or empty if successful
     */
    public OptionalInt error() {
        return error != null
                ? OptionalInt.of(error)
                : OptionalInt.empty();
    }

    /**
     * Returns whether the ack indicates success (no error code).
     *
     * @implNote NO_WA_BASIS: convenience method derived from {@link #error()}.
     * @return {@code true} if {@link #error()} is empty
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Returns whether the server's participant hash differs from the
     * client's, indicating a device-list mismatch that requires a resend.
     *
     * @implNote NO_WA_BASIS: convenience method derived from {@link #phash()}.
     * @return {@code true} if a phash is present
     */
    public boolean hasPhashMismatch() {
        return phash != null;
    }

    @Override
    public String toString() {
        return "AckResult[" +
                "timestamp=" + timestamp +
                ", sync=" + sync +
                ", phash=" + phash +
                ", refreshLid=" + refreshLid +
                ", addressingMode=" + addressingMode +
                ", count=" + count +
                ", error=" + error +
                ']';
    }
}
