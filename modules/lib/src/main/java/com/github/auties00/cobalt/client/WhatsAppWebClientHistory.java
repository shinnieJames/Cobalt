package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * The chat-history-sync policy a web companion advertises to the
 * primary device after linking.
 *
 * @apiNote
 * Embedded into the {@code DeviceProps.historySyncConfig} block of the
 * companion's first handshake so the primary knows how much past
 * history to ship. Pick the volume via {@link #discard(boolean)},
 * {@link #standard(boolean)}, {@link #extended(boolean)}, or a custom
 * {@link #custom(int, boolean)} cap, plus a flag for whether the
 * newsletter surface is included. Bigger caps cost memory, bandwidth,
 * and pairing time.
 *
 * @implNote
 * This implementation is the wire-level counterpart of WA Web's
 * {@code DeviceProps$HistorySyncConfig} message in
 * {@code WAWebProtobufsCompanionReg.pb}. The full WA Web message
 * carries 24 fields (storage quota, recent-sync window, on-demand
 * readiness, support flags); Cobalt collapses them into the two
 * fields most embedders need to set explicitly. The 59206 cap on the
 * standard preset is Cobalt-specific; WA Web computes its quota
 * dynamically from {@code MdSyncFieldStatsMeta.getStorageEstimation}.
 *
 * @see WhatsAppClient
 */
@ProtobufMessage
@WhatsAppWebModule(moduleName = "WAWebProtobufsCompanionReg.pb")
public final class WhatsAppWebClientHistory {
    /**
     * The cached zero-history policy with newsletters disabled.
     */
    private static final WhatsAppWebClientHistory ZERO = new WhatsAppWebClientHistory(0, false);

    /**
     * The cached zero-history policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory ZERO_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(0, true);

    /**
     * The cached standard-volume policy with newsletters disabled.
     *
     * @apiNote
     * The 59206 cap is Cobalt's chosen mid-range value; WA Web
     * computes its own per-session quota from local storage
     * estimation.
     */
    private static final WhatsAppWebClientHistory STANDARD = new WhatsAppWebClientHistory(59206, false);

    /**
     * The cached standard-volume policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory STANDARD_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(59206, true);

    /**
     * The cached extended-volume policy (request as much as the server
     * will deliver) with newsletters disabled.
     */
    private static final WhatsAppWebClientHistory EXTENDED = new WhatsAppWebClientHistory(Integer.MAX_VALUE, false);

    /**
     * The cached extended-volume policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory EXTENDED_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(Integer.MAX_VALUE, true);

    /**
     * The maximum number of history items to request during the
     * initial sync.
     *
     * @apiNote
     * Maps to {@code DeviceProps$HistorySyncConfig.fullSyncDaysLimit}
     * conceptually, though Cobalt expresses the cap as item count
     * rather than day count. {@link Integer#MAX_VALUE} means
     * unlimited.
     */
    @WhatsAppWebExport(moduleName = "WAWebProtobufsCompanionReg.pb",
            exports = "DeviceProps$HistorySyncConfigSpec", adaptation = WhatsAppAdaptation.ADAPTED)
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    final int size;

    /**
     * Whether the newsletter surface is included in the initial
     * history sync.
     */
    @WhatsAppWebExport(moduleName = "WAWebProtobufsCompanionReg.pb",
            exports = "DeviceProps$HistorySyncConfigSpec", adaptation = WhatsAppAdaptation.ADAPTED)
    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    final boolean newsletters;

    /**
     * Constructs a new policy from raw size and newsletter components.
     *
     * @apiNote
     * Package-private; instances reach embedders through the protobuf
     * deserialiser and the static factories. Application code should
     * use {@link #discard(boolean)}, {@link #standard(boolean)},
     * {@link #extended(boolean)}, or {@link #custom(int, boolean)}.
     *
     * @param size        the history size cap
     * @param newsletters whether newsletters are included
     */
    WhatsAppWebClientHistory(int size, boolean newsletters) {
        this.size = size;
        this.newsletters = newsletters;
    }

    /**
     * Returns a policy that discards all chat history, keeping only
     * messages received after the session is established.
     *
     * @apiNote
     * Use this for real-time-only embedders; pairing and resource
     * usage are minimised.
     *
     * @param newsletters whether newsletters should be synchronised
     *                    during the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory discard(boolean newsletters) {
        return newsletters ? ZERO_WITH_NEWSLETTERS : ZERO;
    }

    /**
     * Returns a policy that mirrors a typical WhatsApp Web
     * history-sync volume.
     *
     * @apiNote
     * The recommended option for most embedders; balances availability
     * of recent history with resource cost. The 59206-item cap is
     * Cobalt-specific (WA Web computes its quota dynamically).
     *
     * @param newsletters whether newsletters should be synchronised
     *                    during the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory standard(boolean newsletters) {
        return newsletters ? STANDARD_WITH_NEWSLETTERS : STANDARD;
    }

    /**
     * Returns a policy that requests as much chat history as the
     * server is willing to deliver.
     *
     * @apiNote
     * The replay may include several months or years of messages
     * depending on account age. Memory and bandwidth use can be
     * substantial; prefer this only for embedders that need the full
     * archive.
     *
     * @param newsletters whether newsletters should be synchronised
     *                    during the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory extended(boolean newsletters) {
        return newsletters ? EXTENDED_WITH_NEWSLETTERS : EXTENDED;
    }

    /**
     * Returns a policy with a caller-supplied history-size cap.
     *
     * @apiNote
     * The actual amount delivered may be smaller than requested if
     * the account does not have enough historical data or if the
     * WhatsApp servers impose a lower per-account cap.
     *
     * @param size        the maximum number of historical items to
     *                    synchronise; must be non-negative
     * @param newsletters whether newsletters should be synchronised
     *                    during the initial connection
     * @return the configured policy
     * @throws IllegalArgumentException if {@code size} is negative
     */
    public static WhatsAppWebClientHistory custom(int size, boolean newsletters) {
        return new WhatsAppWebClientHistory(size, newsletters);
    }

    /**
     * Returns whether this policy discards all chat history.
     *
     * @return {@code true} if the size cap is zero, {@code false}
     *         otherwise
     */
    public boolean isZero() {
        return size == 0;
    }

    /**
     * Returns whether this policy requests more history than the
     * standard preset.
     *
     * @return {@code true} if the size cap exceeds the standard
     *         preset, {@code false} otherwise
     */
    public boolean isExtended() {
        return size > STANDARD.size();
    }

    /**
     * Returns the upper bound on the number of historical items this
     * policy requests.
     *
     * @apiNote
     * The actual amount synchronised may be smaller due to server
     * limits or account history.
     *
     * @return the history-size cap, or {@link Integer#MAX_VALUE} for
     *         an unbounded request
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this policy includes newsletters in the initial
     * synchronisation.
     *
     * @return {@code true} if newsletters are included, {@code false}
     *         otherwise
     */
    public boolean hasNewsletters() {
        return newsletters;
    }

    /**
     * Compares this policy to another object for structural equality.
     *
     * @param o the object to compare with
     * @return {@code true} if {@code o} is a
     *         {@code WhatsAppWebClientHistory} with the same size and
     *         newsletter flag
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof WhatsAppWebClientHistory that
                && size == that.size
                && newsletters == that.newsletters;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(size, newsletters);
    }

    /**
     * Returns a human-readable description suitable for logs.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "WhatsappWebHistorySetting[" +
                "size=" + size + ", " +
                "newsletters=" + newsletters + ']';
    }
}
