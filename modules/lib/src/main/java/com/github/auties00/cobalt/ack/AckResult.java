package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Carries the parsed attributes of a server {@code <ack>} stanza returned in response to an outbound
 * send.
 *
 * <p>An {@code AckResult} is the typed view of every attribute the send pipeline cares about: the
 * server timestamp, the priority {@code sync} marker, the participant-hash drift indicator, the LID
 * refresh hint, the addressing-mode the server expects, the recipient count for fanouts, and the
 * {@code error} code when the send was rejected. It is produced exclusively by
 * {@link AckParser#parse}; the send pipeline gates its post-success persistence on
 * {@link #isSuccess()}, fans out per-device receipts on {@link #hasPhashMismatch()}, and routes
 * recovery off {@link #error()} via {@link NackReason#fromCode(int)}.
 *
 * @implNote This implementation adds {@link #isSuccess()} and {@link #hasPhashMismatch()} as boolean
 * shortcuts over the underlying {@link OptionalInt} and {@link Optional} accessors; both are
 * Cobalt-only conveniences with no WA Web counterpart.
 *
 * @see AckParser
 * @see NackReason
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCommonApi")
public final class AckResult {
    /**
     * The {@code t} attribute as an {@link Instant}, or {@code null} when absent.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Instant timestamp;

    /**
     * The {@code sync} attribute value, or {@code null} when absent.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String sync;

    /**
     * The {@code phash} attribute echoed on group and status fanout acks, or {@code null} when the
     * server's participant view matched the client's.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String phash;

    /**
     * The decoded {@code refresh_lid} flag.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final boolean refreshLid;

    /**
     * The {@code addressing_mode} attribute ({@code "lid"} or {@code "pn"}), or {@code null} when
     * absent.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String addressingMode;

    /**
     * The {@code count} attribute echoed on group fanout acks, or {@code null} when absent.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Integer count;

    /**
     * The {@code error} attribute carried on a NACK, or {@code null} when the server accepted the
     * stanza.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Integer error;

    /**
     * Constructs an {@code AckResult} from the per-attribute slots pulled off the raw {@code <ack>}
     * node.
     *
     * <p>Package-private; the only caller is {@link AckParser#parse}.
     *
     * @param timestamp      the parsed {@code t} attribute, or {@code null}
     * @param sync           the parsed {@code sync} attribute, or {@code null}
     * @param phash          the parsed {@code phash} attribute, or {@code null}
     * @param refreshLid     the decoded {@code refresh_lid} flag
     * @param addressingMode the parsed {@code addressing_mode} attribute, or {@code null}
     * @param count          the parsed {@code count} attribute, or {@code null}
     * @param error          the parsed {@code error} attribute, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * Returns the server timestamp carried on the ack.
     *
     * <p>Always populated on a real server ack; an empty result surfaces only when a synthetic node
     * was fed through {@link AckParser}.
     *
     * @return the parsed {@link Instant}, or {@link Optional#empty()} when the {@code t} attribute
     *         was absent
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Returns the priority {@code sync} marker carried on the ack.
     *
     * <p>Reflects the priority class the server picked for this stanza; the receiver pipeline aligns
     * the local notification-priority shelf against this value.
     *
     * @return the {@code sync} value, or {@link Optional#empty()} when absent
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> sync() {
        return Optional.ofNullable(sync);
    }

    /**
     * Returns the participant-hash echoed by the server for a group or status fanout.
     *
     * <p>A non-empty value signals that the server's participant view differs from the local one;
     * the {@code GroupMessageSender} and {@code UserMessageSender} send paths resolve the delta
     * devices and re-encrypt via the per-device group-direct path.
     *
     * @return the {@code phash} value, or {@link Optional#empty()} when the views matched
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> phash() {
        return Optional.ofNullable(phash);
    }

    /**
     * Returns whether the server asked for a LID refresh for the recipient.
     *
     * <p>When {@code true}, the {@code UserMessageSender} send path issues a follow-up device-list
     * sync so the local LID-to-PN mapping catches up with the server's.
     *
     * @return {@code true} when {@code refresh_lid="true"} was carried on the ack
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean refreshLid() {
        return refreshLid;
    }

    /**
     * Returns the addressing mode the server expects for this chat or group.
     *
     * <p>A value that differs from the mode the client used signals an addressing-mode mismatch; the
     * {@code GroupMessageSender} send path migrates participant JIDs and clears sender-key
     * distribution state in response.
     *
     * @return {@code "lid"} or {@code "pn"}, or {@link Optional#empty()} when absent
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> addressingMode() {
        return Optional.ofNullable(addressingMode);
    }

    /**
     * Returns the recipient count reported on group and CAG fanout acks.
     *
     * <p>Populates the {@code count} metric slot of the {@code WebcMessageSend} WAM event emitted by
     * the send pipeline.
     *
     * @return the {@code count} value, or {@link OptionalInt#empty()} when the server did not report
     *         one
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public OptionalInt count() {
        return count != null
                ? OptionalInt.of(count)
                : OptionalInt.empty();
    }

    /**
     * Returns the server error code carried on a NACK.
     *
     * <p>The presence of a value is the canonical accept/reject signal; compare the integer against
     * {@link NackReason#code()} or fold through {@link NackReason#fromCode(int)} to classify the
     * rejection. {@link #isSuccess()} is the boolean shortcut for callers that only need the
     * accept/reject test.
     *
     * @return the {@code error} code, or {@link OptionalInt#empty()} when the send succeeded
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "sendMsgAckSyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public OptionalInt error() {
        return error != null
                ? OptionalInt.of(error)
                : OptionalInt.empty();
    }

    /**
     * Returns whether the server accepted the outgoing stanza.
     *
     * <p>Equivalent to {@code error().isEmpty()}. The send pipeline gates per-device receipt
     * persistence and the {@code WebcMessageSend} WAM commit on this predicate.
     *
     * @return {@code true} when no {@code error} attribute was present
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Returns whether the ack carried a non-empty {@code phash}.
     *
     * <p>Equivalent to {@code phash().isPresent()}. The group and 1:1 senders branch on this to
     * drive the per-device group-direct or chat-resend fanout.
     *
     * @return {@code true} when a {@code phash} attribute was present
     */
    public boolean hasPhashMismatch() {
        return phash != null;
    }

    /**
     * Returns a debug string with every parsed slot.
     *
     * @return a debug representation; format is unspecified
     */
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
